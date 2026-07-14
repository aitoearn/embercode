package dev.phonecode.app.remote

import kotlinx.serialization.Serializable

/** RN Activity 回传 / 磁盘缓存用的远程摘要载荷（与 Bridge JSON 契约对齐）。 */
@Serializable
data class RemoteSummariesPayload(
    val hosts: List<RemoteHostPayload> = emptyList(),
)

@Serializable
data class RemoteHostPayload(
    val hostId: String,
    val hostLabel: String = "",
    val connectionState: String = "Unknown",
    val sessions: List<RemoteSessionPayload> = emptyList(),
)

@Serializable
data class RemoteSessionPayload(
    val id: String,
    val title: String = "",
    val updatedAt: Long = 0L,
    val preview: String = "",
)

fun RemoteSummariesPayload.toUnifiedRows(): List<UnifiedSessionRow> =
    hosts.flatMap { host ->
        val state = runCatching {
            RemoteConnectionState.valueOf(host.connectionState)
        }.getOrDefault(RemoteConnectionState.Unknown)
        host.sessions.map { session ->
            UnifiedSessionRow(
                origin = SessionOrigin.REMOTE,
                id = session.id,
                title = session.title.ifBlank { session.id },
                updatedAt = session.updatedAt,
                preview = session.preview,
                hostId = host.hostId,
                hostLabel = host.hostLabel,
                connectionState = state,
            )
        }
    }

fun List<UnifiedSessionRow>.toRemoteSummariesPayload(): RemoteSummariesPayload {
    val remote = filter { it.origin == SessionOrigin.REMOTE }
    val byHost = remote.groupBy { it.hostId.orEmpty() }
    return RemoteSummariesPayload(
        hosts = byHost.map { (hostId, sessions) ->
            RemoteHostPayload(
                hostId = hostId,
                hostLabel = sessions.firstOrNull()?.hostLabel.orEmpty(),
                connectionState = (sessions.firstOrNull()?.connectionState
                    ?: RemoteConnectionState.Unknown).name,
                sessions = sessions.map {
                    RemoteSessionPayload(
                        id = it.id,
                        title = it.title,
                        updatedAt = it.updatedAt,
                        preview = it.preview,
                    )
                },
            )
        },
    )
}
