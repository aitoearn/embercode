package dev.phonecode.app.remote

/**
 * 抽屉统一行模型。本地由 [dev.phonecode.app.data.SessionMeta] 映射；
 * 远程来自摘要缓存，不落 [dev.phonecode.app.data.SessionStore]。
 */
data class UnifiedSessionRow(
    val origin: SessionOrigin,
    val id: String,
    val title: String,
    val updatedAt: Long,
    val preview: String = "",
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val hostId: String? = null,
    val hostLabel: String? = null,
    val connectionState: RemoteConnectionState? = null,
) {
    /** 列表稳定 key，避免本地 session id 与远程 agent id 碰撞。 */
    val listKey: String
        get() = when (origin) {
            SessionOrigin.LOCAL -> "local:$id"
            SessionOrigin.REMOTE -> "remote:${hostId.orEmpty()}:$id"
        }
}
