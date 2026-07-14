package dev.phonecode.app.remote

/** Compose → RN Activity 的打开请求。 */
sealed class OpenRemoteRequest {
    /** 未连接或新建远程：进入配对。 */
    data object Pair : OpenRemoteRequest()

    /** 已有 host：进入该 host 的会话列表。 */
    data class Host(val hostId: String) : OpenRemoteRequest()

    /** 打开已有远程 agent 聊天。 */
    data class Chat(val hostId: String, val agentId: String) : OpenRemoteRequest()
}

/** Intent extras / result 键名（与 paseo embedded 约定一致）。 */
object RemoteRnContract {
    const val EXTRA_ROUTE = "dev.phonecode.remote.ROUTE"
    const val EXTRA_HOST_ID = "dev.phonecode.remote.HOST_ID"
    const val EXTRA_AGENT_ID = "dev.phonecode.remote.AGENT_ID"
    const val EXTRA_EMBEDDED = "dev.phonecode.remote.EMBEDDED"
    const val RESULT_SUMMARIES_JSON = "dev.phonecode.remote.SUMMARIES_JSON"
}

/**
 * Compose ↔ RN 的薄桥：extras 编解码与摘要 JSON 解析。
 * 纯 JVM 可测；写入 Intent 时由调用方把 [extrasMap] 填进 Bundle。
 */
object RnBridge {
    fun extrasMap(req: OpenRemoteRequest): Map<String, String> = when (req) {
        OpenRemoteRequest.Pair -> mapOf(
            RemoteRnContract.EXTRA_ROUTE to "pair",
            RemoteRnContract.EXTRA_EMBEDDED to "true",
        )
        is OpenRemoteRequest.Host -> mapOf(
            RemoteRnContract.EXTRA_ROUTE to "host",
            RemoteRnContract.EXTRA_HOST_ID to req.hostId,
            RemoteRnContract.EXTRA_EMBEDDED to "true",
        )
        is OpenRemoteRequest.Chat -> mapOf(
            RemoteRnContract.EXTRA_ROUTE to "chat",
            RemoteRnContract.EXTRA_HOST_ID to req.hostId,
            RemoteRnContract.EXTRA_AGENT_ID to req.agentId,
            RemoteRnContract.EXTRA_EMBEDDED to "true",
        )
    }

    fun intent(context: android.content.Context, req: OpenRemoteRequest): android.content.Intent {
        val intent = android.content.Intent(context, RemoteRnActivity::class.java)
        extrasMap(req).forEach { (key, value) ->
            when (key) {
                RemoteRnContract.EXTRA_EMBEDDED -> intent.putExtra(key, value.toBoolean())
                else -> intent.putExtra(key, value)
            }
        }
        return intent
    }

    fun parseSummariesJson(json: String): List<UnifiedSessionRow> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            storeJsonForBridge.decodeFromString(RemoteSummariesPayload.serializer(), json).toUnifiedRows()
        }.getOrDefault(emptyList())
    }

    fun parseResult(data: android.content.Intent?): List<UnifiedSessionRow> {
        val json = data?.getStringExtra(RemoteRnContract.RESULT_SUMMARIES_JSON).orEmpty()
        return parseSummariesJson(json)
    }
}

private val storeJsonForBridge = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}
