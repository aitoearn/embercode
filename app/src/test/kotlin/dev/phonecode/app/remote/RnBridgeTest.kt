package dev.phonecode.app.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RnBridgeTest {
    @Test
    fun pairRequestPutsRoute() {
        val extras = RnBridge.extrasMap(OpenRemoteRequest.Pair)
        assertEquals("pair", extras[RemoteRnContract.EXTRA_ROUTE])
        assertEquals("true", extras[RemoteRnContract.EXTRA_EMBEDDED])
    }

    @Test
    fun chatRequestPutsHostAndAgent() {
        val extras = RnBridge.extrasMap(OpenRemoteRequest.Chat(hostId = "h1", agentId = "a1"))
        assertEquals("chat", extras[RemoteRnContract.EXTRA_ROUTE])
        assertEquals("h1", extras[RemoteRnContract.EXTRA_HOST_ID])
        assertEquals("a1", extras[RemoteRnContract.EXTRA_AGENT_ID])
    }

    @Test
    fun parseSummariesJson() {
        val json = """
            {"hosts":[{"hostId":"h1","hostLabel":"desk","connectionState":"Connected",
              "sessions":[{"id":"a1","title":"T","updatedAt":9,"preview":"p"}]}]}
        """.trimIndent()
        val rows = RnBridge.parseSummariesJson(json)
        assertEquals(1, rows.size)
        assertEquals("a1", rows.single().id)
        assertEquals("h1", rows.single().hostId)
        assertEquals(RemoteConnectionState.Connected, rows.single().connectionState)
    }

    @Test
    fun parseBlankReturnsEmpty() {
        assertTrue(RnBridge.parseSummariesJson("").isEmpty())
        assertTrue(RnBridge.parseSummariesJson("not-json").isEmpty())
    }
}
