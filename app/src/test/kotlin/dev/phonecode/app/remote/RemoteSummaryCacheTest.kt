package dev.phonecode.app.remote

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RemoteSummaryCacheTest {
    private lateinit var dir: File
    private lateinit var cache: RemoteSummaryCache

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("remote-cache").toFile()
        cache = RemoteSummaryCache(File(dir, "remote-summaries.json"))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun roundTripReplaceAll() {
        val rows = listOf(
            UnifiedSessionRow(
                origin = SessionOrigin.REMOTE,
                id = "agent-1",
                title = "Fix login",
                updatedAt = 200L,
                preview = "hi",
                hostId = "host-a",
                hostLabel = "desk",
                connectionState = RemoteConnectionState.Connected,
            ),
        )
        cache.replaceAll(rows)
        val loaded = cache.load()
        assertEquals(1, loaded.size)
        assertEquals("remote:host-a:agent-1", loaded.single().listKey)
        assertEquals(RemoteConnectionState.Connected, loaded.single().connectionState)
        assertEquals("Fix login", loaded.single().title)
    }

    @Test
    fun missingFileReturnsEmpty() {
        assertTrue(cache.load().isEmpty())
    }
}
