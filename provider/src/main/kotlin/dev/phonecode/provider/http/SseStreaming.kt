package dev.phonecode.provider.http

import dev.phonecode.provider.domain.StreamEvent
import dev.phonecode.provider.domain.FailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** A stateful, per-stream mapper from raw SSE events to normalized [StreamEvent]s. */
internal interface SseStreamMapper {
    fun map(raw: RawSse): List<StreamEvent>
    fun finish(): List<StreamEvent>
}

private const val MAX_ERROR_BODY = 2048L

private fun retryableStatus(code: Int): Boolean = code in setOf(408, 409, 425, 429) || code in 500..599

private fun retryAfterMillis(value: String?, now: Long = System.currentTimeMillis()): Long? {
    if (value.isNullOrBlank()) return null
    value.toDoubleOrNull()?.let { return (it * 1_000).toLong().coerceAtLeast(0) }
    return runCatching {
        (ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - now)
            .coerceAtLeast(0)
    }.getOrNull()
}

private fun resetAfterMillis(value: String?, now: Long = System.currentTimeMillis()): Long? {
    if (value.isNullOrBlank()) return null
    val number = value.toDoubleOrNull()
    if (number != null) {
        return when {
            number > 10_000_000_000L -> (number.toLong() - now).coerceAtLeast(0)
            number > 1_000_000_000L -> ((number * 1_000).toLong() - now).coerceAtLeast(0)
            else -> (number * 1_000).toLong().coerceAtLeast(0)
        }
    }
    return retryAfterMillis(value, now)
}

internal fun classifyFailure(statusCode: Int?, code: String?, message: String): FailureKind {
    val value = listOfNotNull(code, message).joinToString(" ").lowercase()
    return when {
        "quota" in value || "usage limit" in value || "usage exceeded" in value ||
            "insufficient_quota" in value || "credit balance" in value -> FailureKind.QUOTA
        statusCode == 429 || "rate limit" in value || "too many requests" in value -> FailureKind.RATE_LIMIT
        statusCode == 401 || "unauthorized" in value || "invalid api key" in value ||
            "authentication" in value || "api_key" in value -> FailureKind.AUTH
        statusCode != null && statusCode in 400..499 -> FailureKind.INVALID_REQUEST
        statusCode != null && statusCode >= 500 || "overloaded" in value -> FailureKind.SERVER
        "parse error" in value -> FailureKind.PARSE
        else -> FailureKind.UNKNOWN
    }
}

private data class HttpErrorDetails(val message: String, val code: String?)

private fun httpErrorDetails(code: Int, contentType: String?, body: String): HttpErrorDetails {
    val errorCode = Regex("\"code\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(body)
        ?.groupValues?.get(1)?.let(::unescapeJsonString)
    return HttpErrorDetails(httpErrorMessage(code, contentType, body), errorCode)
}

/**
 * Turns an HTTP error response into a short, legible message. API errors are JSON
 * ({"error":{"message":...}} or {"message":...}); surface that message. A misconfigured base URL
 * hits a website and returns an HTML page; never dump that markup into the chat, say what's wrong.
 */
internal fun httpErrorMessage(code: Int, contentType: String?, body: String): String {
    val trimmed = body.trim()
    Regex("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(trimmed)?.groupValues?.get(1)
        ?.let { return "HTTP $code: ${unescapeJsonString(it).take(300)}" }
    val looksHtml = contentType?.contains("html", ignoreCase = true) == true ||
        trimmed.startsWith("<!doctype", ignoreCase = true) ||
        trimmed.startsWith("<html", ignoreCase = true)
    if (looksHtml || trimmed.isEmpty()) {
        return if (code == 404) "HTTP 404: endpoint not found, check the provider's base URL" else "HTTP $code"
    }
    return "HTTP $code: ${trimmed.take(300)}"
}

/** Decode the JSON string escapes the regex capture preserves (\" \\ \n \t ... \uXXXX), so the chat
 *  shows "can't do that" rather than a literal `can’t do that`. Passes raw/unknown chars through. */
private fun unescapeJsonString(s: String): String {
    if ('\\' !in s) return s
    val sb = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length) {
            when (val e = s[i + 1]) {
                '"', '\\', '/' -> sb.append(e)
                'n' -> sb.append('\n')
                't' -> sb.append('\t')
                'r' -> sb.append('\r')
                'b' -> sb.append('\b')
                'f' -> sb.append('\u000C')
                'u' -> {
                    val hex = s.substring(i + 2, minOf(i + 6, s.length))
                    val code = hex.takeIf { it.length == 4 }?.toIntOrNull(16)
                    if (code != null) { sb.append(code.toChar()); i += 4 } else { sb.append(c).append(e) }
                }
                else -> sb.append(c).append(e)
            }
            i += 2
        } else {
            sb.append(c); i++
        }
    }
    return sb.toString()
}

/**
 * The shared streaming path for every provider: execute the request, frame the
 * SSE body one line at a time through [SseParser], run each event through
 * [mapper], then flush. Errors are surfaced as [StreamEvent.Failed]; the flow
 * always completes. Providers differ only in URL, request body, and mapper.
 */
internal fun streamSse(
    client: OkHttpClient,
    request: Request,
    mapper: SseStreamMapper,
): Flow<StreamEvent> = flow {
    val parser = SseParser()
    val call = client.newCall(request)
    // Stop (collector cancel) cancels the in-flight call at once, so a blocking readUtf8Line doesn't
    // hold the socket until the next line arrives - prompt stop instead of next-token latency.
    val disposable = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
    try {
        call.execute().use { response ->
            if (!response.isSuccessful) {
                // peekBody bounds the read so a hostile error body can never be fully buffered.
                val body = response.peekBody(MAX_ERROR_BODY).string()
                val retryAfter = response.header("retry-after-ms")?.toLongOrNull()
                    ?: retryAfterMillis(response.header("retry-after"))
                    ?: resetAfterMillis(response.header("x-ratelimit-reset-requests"))
                    ?: resetAfterMillis(response.header("x-ratelimit-reset"))
                    ?: resetAfterMillis(response.header("ratelimit-reset"))
                val details = httpErrorDetails(response.code, response.header("Content-Type"), body)
                val kind = classifyFailure(response.code, details.code, details.message)
                emit(
                    StreamEvent.Failed(
                        message = details.message,
                        retryable = retryableStatus(response.code) &&
                            kind !in setOf(FailureKind.AUTH, FailureKind.QUOTA, FailureKind.INVALID_REQUEST),
                        retryAfterMillis = retryAfter,
                        kind = kind,
                        statusCode = response.code,
                        code = details.code,
                    ),
                )
                return@use
            }
            val source = response.body?.source()
            if (source == null) {
                emit(StreamEvent.Failed("empty response body", kind = FailureKind.SERVER))
                return@use
            }
            while (true) {
                val line = source.readUtf8Line() ?: break
                parser.line(line)?.let { raw -> mapper.map(raw).forEach { emit(it) } }
            }
            parser.flush()?.let { raw -> mapper.map(raw).forEach { emit(it) } }
            mapper.finish().forEach { emit(it) }
        }
    } finally {
        disposable.dispose()
    }
}.flowOn(Dispatchers.IO).catch { e ->
    // A cancelled call surfaces as an IOException; on stop, propagate cancellation instead of a spurious Failed.
    if (e is CancellationException) throw e
    val retryable = e is IOException && e !is SSLHandshakeException && e !is SSLPeerUnverifiedException
    emit(StreamEvent.Failed(e.message ?: "stream error", retryable, kind = FailureKind.NETWORK))
}
