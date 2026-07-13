package dev.phonecode.tools.mcp

import dev.phonecode.tools.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicInteger

data class McpToolDef(val name: String, val title: String, val description: String, val inputSchema: JsonObject)

data class McpServerSnapshot(
    val connected: Boolean,
    val protocolVersion: String = "",
    val serverName: String = "",
    val serverTitle: String = "",
    val serverVersion: String = "",
    val capabilities: Set<String> = emptySet(),
    val tools: List<McpToolDef> = emptyList(),
    val instructions: String = "",
    val error: String = "",
)

data class McpConnectionResult(
    val tools: List<Tool>,
    val snapshots: Map<String, McpServerSnapshot>,
)

private val JSON_MEDIA = "application/json".toMediaType()

suspend fun connectMcpServers(config: McpConfig, http: OkHttpClient): List<Tool> = connectMcpServersDetailed(config, http).tools

suspend fun probeMcpServer(name: String, config: McpServerConfig, http: OkHttpClient): McpServerSnapshot {
    val timeout = config.timeout.coerceIn(1_000, MAX_CONNECT_TIMEOUT_MS)
    return withTimeoutOrNull(timeout) {
        runCatching { McpClient(name, config.copy(enabled = true), http).probe() }
            .getOrElse { McpServerSnapshot(false, error = it.message ?: "Connection failed") }
    } ?: McpServerSnapshot(false, error = "Connection timed out")
}

suspend fun connectMcpServersDetailed(config: McpConfig, http: OkHttpClient): McpConnectionResult =
    coroutineScope {
        val servers = config.mcp.filter { it.value.enabled }.map { (name, server) ->
            async {
                val client = McpClient(name, server, http)
                val snapshot = withTimeoutOrNull(server.timeout.coerceIn(1_000, MAX_CONNECT_TIMEOUT_MS)) {
                    runCatching { client.probe() }.getOrElse { McpServerSnapshot(false, error = it.message ?: "Connection failed") }
                } ?: McpServerSnapshot(false, error = "Connection timed out")
                Triple(name, snapshot, client.tools(snapshot))
            }
        }.awaitAll()
        McpConnectionResult(
            tools = servers.flatMap { it.third },
            snapshots = servers.associate { it.first to it.second },
        )
    }

private const val MAX_CONNECT_TIMEOUT_MS = 60_000L
private const val TOOL_TIMEOUT_MS = 10L * 60L * 1000L
private const val MAX_RESPONSE_BYTES = 8L * 1024L * 1024L

private data class McpHttpResponse(val code: Int, val body: String)

class McpClient(
    private val serverName: String,
    private val config: McpServerConfig,
    private val http: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var sessionId: String? = null
    @Volatile private var protocolVersion: String? = null
    private val nextId = AtomicInteger(1)

    // Handshake requests get a HARD OkHttp call timeout: withTimeoutOrNull only abandons the
    // waiting coroutine - the blocking execute() and its socket would otherwise live on for the
    // full read timeout (verification finding). Derived clients share the pool, so this is cheap.
    // Tool CALLS keep the long-lived client: a legitimate MCP tool may stream for minutes.
    private val handshakeHttp = http.newBuilder()
        .callTimeout(config.timeout.coerceIn(1_000, MAX_CONNECT_TIMEOUT_MS), java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()
    private val toolHttp = http.newBuilder()
        .callTimeout(TOOL_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()
    @Volatile private var handshaking = false

    suspend fun connect(): List<Tool> {
        val snapshot = probe()
        return tools(snapshot)
    }

    fun tools(snapshot: McpServerSnapshot): List<Tool> =
        if (snapshot.connected) snapshot.tools.map { McpTool(serverName, it, this) } else emptyList()

    suspend fun probe(): McpServerSnapshot {
        sessionId = null
        protocolVersion = null
        handshaking = true
        try {
            val init = request(
                "initialize",
                buildJsonObject {
                    put("protocolVersion", LATEST_PROTOCOL)
                    put("capabilities", buildJsonObject {})
                    putJsonObject("clientInfo") {
                        put("name", "PhoneCode")
                        put("title", "PhoneCode")
                        put("version", LATEST_PROTOCOL)
                        put("websiteUrl", "https://dttdrv.xyz/phonecode")
                    }
                },
            )
                ?: return McpServerSnapshot(false, error = "No response from server")
            (init["error"] as? JsonObject)?.let { error ->
                return McpServerSnapshot(false, error = error.string("message").ifBlank { "Initialization failed" })
            }
            val result = init["result"] as? JsonObject
                ?: return McpServerSnapshot(false, error = "Invalid initialize response")
            val negotiated = result.string("protocolVersion")
            if (negotiated !in SUPPORTED_PROTOCOLS) {
                return McpServerSnapshot(false, protocolVersion = negotiated, error = "Unsupported protocol version")
            }
            protocolVersion = negotiated
            if (!notify("notifications/initialized")) {
                return McpServerSnapshot(false, protocolVersion = negotiated, error = "Initialization notification failed")
            }
            val info = result["serverInfo"] as? JsonObject
            val capabilities = (result["capabilities"] as? JsonObject)?.keys.orEmpty()
            return McpServerSnapshot(
                connected = true,
                protocolVersion = negotiated,
                serverName = info?.string("name").orEmpty(),
                serverTitle = info?.string("title").orEmpty(),
                serverVersion = info?.string("version").orEmpty(),
                capabilities = capabilities,
                tools = if ("tools" in capabilities) listTools() else emptyList(),
                instructions = result.string("instructions"),
            )
        } finally {
            handshaking = false
        }
    }

    suspend fun listTools(): List<McpToolDef> {
        val out = mutableListOf<McpToolDef>()
        val cursors = mutableSetOf<String>()
        var cursor: String? = null
        do {
            val message = request("tools/list", buildJsonObject { cursor?.let { put("cursor", it) } }) ?: break
            val result = message["result"] as? JsonObject ?: break
            (result["tools"] as? JsonArray).orEmpty().take(MAX_TOOLS - out.size).forEach { element ->
                val obj = element as? JsonObject ?: return@forEach
                val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                val title = (obj["title"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val description = (obj["description"] as? JsonPrimitive)?.contentOrNull ?: ""
                val schema = (obj["inputSchema"] as? JsonObject)?.takeIf { it.containsKey("type") } ?: EMPTY_SCHEMA
                out += McpToolDef(name, title, description, schema)
            }
            cursor = (result["nextCursor"] as? JsonPrimitive)?.contentOrNull
        } while (cursor != null && cursors.add(cursor) && out.size < MAX_TOOLS)
        return out
    }

    suspend fun callTool(toolName: String, args: JsonObject): String {
        val message = request("tools/call", buildJsonObject { put("name", toolName); put("arguments", args) })
            ?: return "(no response from MCP server)"
        (message["error"] as? JsonObject)?.let { error ->
            return "ERROR: " + ((error["message"] as? JsonPrimitive)?.contentOrNull ?: error.toString())
        }
        val result = message["result"] as? JsonObject ?: return "(no response from MCP server)"
        val content = (result["content"] as? JsonArray).orEmpty()
            .mapNotNull(::renderContent)
            .joinToString("\n")
            .ifEmpty { result["structuredContent"]?.toString().orEmpty() }
        val isError = (result["isError"] as? JsonPrimitive)?.booleanOrNull == true
        return if (isError) "ERROR: $content" else content.ifEmpty { "(empty result)" }
    }

    private suspend fun request(method: String, params: JsonObject): JsonObject? = withContext(Dispatchers.IO) {
        val id = nextId.getAndIncrement()
        val payload = buildJsonObject {
            put("jsonrpc", "2.0"); put("id", id); put("method", method); put("params", params)
        }
        val response = post(payload.toString()) ?: return@withContext null
        extractMessage(response.body, id)
    }

    private suspend fun notify(method: String): Boolean = withContext(Dispatchers.IO) {
        post(buildJsonObject { put("jsonrpc", "2.0"); put("method", method); put("params", buildJsonObject {}) }.toString())
            ?.code in 200..299
    }

    private fun post(body: String): McpHttpResponse? {
        val request = Request.Builder()
            .url(config.url)
            .post(body.toRequestBody(JSON_MEDIA))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .apply {
                config.headers.forEach { (key, value) -> header(key, value) }
                sessionId?.let { header("Mcp-Session-Id", it) }
                protocolVersion?.let { header("MCP-Protocol-Version", it) }
            }
            .build()
        return (if (handshaking) handshakeHttp else toolHttp).newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} ${response.message}".trim())
            response.header("Mcp-Session-Id")?.let { sessionId = it }
            val source = response.body?.source()
            if (source == null) {
                McpHttpResponse(response.code, "")
            } else {
                source.request(MAX_RESPONSE_BYTES + 1)
                if (source.buffer.size > MAX_RESPONSE_BYTES) error("MCP response exceeds 8 MiB")
                McpHttpResponse(response.code, source.readUtf8())
            }
        }
    }

    private fun extractMessage(body: String, id: Int): JsonObject? {
        val candidates = parseCandidates(body)
        return candidates.firstOrNull { (it["id"] as? JsonPrimitive)?.intOrNull == id }
            ?: candidates.lastOrNull { it.containsKey("result") || it.containsKey("error") }
            ?: candidates.lastOrNull()
    }

    private fun parseCandidates(body: String): List<JsonObject> {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) {
            return runCatching { json.parseToJsonElement(trimmed) as? JsonObject }.getOrNull()?.let { listOf(it) }
                ?: emptyList()
        }
        return trimmed.split(Regex("\\r?\\n\\r?\\n"))
            .asSequence()
            .map { event ->
                event.lineSequence().filter { it.startsWith("data:") }
                    .joinToString("\n") { it.removePrefix("data:").trimStart() }
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .mapNotNull { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            .toList()
    }

    private fun renderContent(element: kotlinx.serialization.json.JsonElement): String? {
        val content = element as? JsonObject ?: return null
        val rendered = when (content.string("type")) {
            "text" -> content.string("text")
            "resource_link" -> listOf(content.string("name"), content.string("uri")).filter { it.isNotBlank() }.joinToString(": ")
            "resource" -> (content["resource"] as? JsonObject)?.let { it.string("text").ifBlank { it.string("uri") } }
            "image", "audio" -> "[${content.string("type")}: ${content.string("mimeType").ifBlank { "binary" }}]"
            else -> content.toString()
        } ?: return null
        return rendered.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private companion object {
        const val LATEST_PROTOCOL = "2025-11-25"
        val SUPPORTED_PROTOCOLS = setOf(LATEST_PROTOCOL, "2025-06-18", "2025-03-26")
        const val MAX_TOOLS = 500
        val EMPTY_SCHEMA = buildJsonObject { put("type", "object"); putJsonObject("properties") {} }
    }
}
