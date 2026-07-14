package dev.phonecode.app.remote

import dev.phonecode.app.data.SessionMeta
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionFacadeTest {
    private val facade = SessionFacade()

    @Test
    fun localThenRemoteSectionsSortedInside() {
        val local = listOf(
            SessionMeta("session-1", "L1", 100L),
            SessionMeta("session-2", "L2", 300L, pinned = true),
        )
        val remote = listOf(
            UnifiedSessionRow(
                origin = SessionOrigin.REMOTE,
                id = "a1",
                title = "R1",
                updatedAt = 250L,
                hostId = "h1",
                hostLabel = "desk",
            ),
            UnifiedSessionRow(
                origin = SessionOrigin.REMOTE,
                id = "a2",
                title = "R2",
                updatedAt = 400L,
                hostId = "h1",
                hostLabel = "desk",
            ),
        )
        val merged = facade.merge(local, remote)
        assertEquals(listOf("session-2", "session-1"), merged.local.map { it.id })
        assertEquals(listOf("a2", "a1"), merged.remote.map { it.id })
    }

    @Test
    fun remoteListKeyStable() {
        val row = UnifiedSessionRow(
            origin = SessionOrigin.REMOTE,
            id = "a1",
            title = "R",
            updatedAt = 1L,
            hostId = "h",
        )
        assertEquals("remote:h:a1", row.listKey)
    }

    @Test
    fun skipsArchivedLocal() {
        val local = listOf(
            SessionMeta("alive", "A", 1L),
            SessionMeta("gone", "G", 2L, archived = true),
        )
        val merged = facade.merge(local, emptyList())
        assertEquals(listOf("alive"), merged.local.map { it.id })
    }
}
