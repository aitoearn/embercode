package dev.phonecode.app.remote

import dev.phonecode.app.data.SessionMeta

/** 抽屉分区：本地与远程分行展示（轻融合）。 */
data class UnifiedDrawerModel(
    val local: List<UnifiedSessionRow>,
    val remote: List<UnifiedSessionRow>,
)

/**
 * 合并本地 [SessionMeta] 与远程摘要行。
 * pin/归档语义：本地沿用现有字段；远程 MVP 不在此写回 daemon。
 */
class SessionFacade {
    fun merge(
        localMeta: List<SessionMeta>,
        remoteRows: List<UnifiedSessionRow>,
    ): UnifiedDrawerModel {
        val local = localMeta
            .filter { !it.archived }
            .sortedWith(
                compareByDescending<SessionMeta> { it.pinned }
                    .thenByDescending { it.updatedAt },
            )
            .map {
                UnifiedSessionRow(
                    origin = SessionOrigin.LOCAL,
                    id = it.id,
                    title = it.title,
                    updatedAt = it.updatedAt,
                    preview = it.preview,
                    pinned = it.pinned,
                    archived = it.archived,
                )
            }
        val remote = remoteRows
            .filter { it.origin == SessionOrigin.REMOTE && !it.archived }
            .sortedWith(
                compareByDescending<UnifiedSessionRow> { it.pinned }
                    .thenByDescending { it.updatedAt },
            )
        return UnifiedDrawerModel(local = local, remote = remote)
    }
}
