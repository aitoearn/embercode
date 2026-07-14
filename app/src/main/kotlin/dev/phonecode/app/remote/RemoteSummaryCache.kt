package dev.phonecode.app.remote

import dev.phonecode.app.data.storeJson
import dev.phonecode.app.data.writeTextAtomically
import java.io.File

/**
 * 远程会话摘要持久化。只存列表所需字段，不写消息正文；
 * 权威会话内容仍在 paseo daemon / RN 侧。
 */
class RemoteSummaryCache(private val file: File) {
    private val lock = Any()

    fun load(): List<UnifiedSessionRow> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        val text = file.readText()
        if (text.isBlank()) return emptyList()
        runCatching {
            storeJson.decodeFromString(RemoteSummariesPayload.serializer(), text).toUnifiedRows()
        }.getOrDefault(emptyList())
    }

    fun replaceAll(rows: List<UnifiedSessionRow>) = synchronized(lock) {
        val payload = rows.toRemoteSummariesPayload()
        file.parentFile?.mkdirs()
        file.writeTextAtomically(storeJson.encodeToString(RemoteSummariesPayload.serializer(), payload))
    }
}
