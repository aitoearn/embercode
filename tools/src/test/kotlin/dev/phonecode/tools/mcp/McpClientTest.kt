package dev.phonecode.tools.mcp

import dev.phonecode.tools.ToolContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class McpClientTest {

    private lateinit var server: MockWebServer
    private val http = OkHttpClient()

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun config() = McpServerConfig(url = server.url("/mcp").toString())

    @Test fun connectListsToolsNamespacedAndPropagatesSessionId() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setHeader("Mcp-Session-Id", "sess-1")
                .setBody("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-11-25","capabilities":{"tools":{}},"serverInfo":{"name":"weather","title":"Weather","version":"1.0"}}}"""),
        )
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"get_weather","description":"Get weather","inputSchema":{"type":"object"}}]}}"""),
        )

        val tools = McpClient("My Weather!", config(), http).connect()

        assertEquals(1, tools.size)
        assertEquals("My_Weather__get_weather", tools[0].name)
        assertEquals("Get weather", tools[0].description)

        val initialize = server.takeRequest()
        val initialized = server.takeRequest()
        val list = server.takeRequest()
        assertNull(initialize.getHeader("Mcp-Session-Id"))
        assertTrue(initialize.body.readUtf8().contains("2025-11-25"))
        assertEquals("sess-1", initialized.getHeader("Mcp-Session-Id"))
        assertEquals("2025-11-25", initialized.getHeader("MCP-Protocol-Version"))
        assertEquals("sess-1", list.getHeader("Mcp-Session-Id"))
        assertEquals("2025-11-25", list.getHeader("MCP-Protocol-Version"))
    }

    @Test fun probeExposesNegotiatedCapabilitiesAndServerIdentity() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{"tools":{},"resources":{}},"serverInfo":{"name":"files","title":"Files","version":"2"},"instructions":"Use carefully"}}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(MockResponse().setBody("""{"jsonrpc":"2.0","id":2,"result":{"tools":[]}}"""))

        val snapshot = McpClient("files", config(), http).probe()

        assertTrue(snapshot.connected)
        assertEquals("2025-06-18", snapshot.protocolVersion)
        assertEquals("Files", snapshot.serverTitle)
        assertEquals(setOf("tools", "resources"), snapshot.capabilities)
        assertEquals("Use carefully", snapshot.instructions)
    }

    @Test fun rejectsUnsupportedNegotiatedVersion() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{}}}""",
            ),
        )

        val snapshot = McpClient("old", config(), http).probe()

        assertTrue(!snapshot.connected)
        assertEquals("2024-11-05", snapshot.protocolVersion)
        assertTrue(snapshot.error.contains("Unsupported"))
        assertEquals(1, server.requestCount)
    }

    @Test fun callToolParsesSseAndTakesTheResultEvent() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "event: message\n" +
                        "data: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\",\"params\":{}}\n\n" +
                        "data: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"sunny, 22C\"}]}}\n\n",
                ),
        )
        val out = McpClient("weather", config(), http).callTool("get_weather", JsonObject(emptyMap()))
        assertEquals("sunny, 22C", out)
    }

    @Test fun callToolSurfacesServerError() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"jsonrpc":"2.0","id":1,"result":{"isError":true,"content":[{"type":"text","text":"boom"}]}}"""),
        )
        val out = McpClient("weather", config(), http).callTool("get_weather", JsonObject(emptyMap()))
        assertTrue(out.startsWith("ERROR:"))
        assertTrue(out.contains("boom"))
    }

    @Test fun mcpToolPreservesRemoteErrorState() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"jsonrpc":"2.0","id":1,"result":{"isError":true,"content":[{"type":"text","text":"denied"}]}}""",
            ),
        )
        val client = McpClient("weather", config(), http)
        val tool = McpTool("weather", McpToolDef("write", "", "Write", JsonObject(emptyMap())), client)
        val result = tool.execute(JsonObject(emptyMap()), object : ToolContext {
            override val workspacePath = "/tmp"
            override suspend fun requestPermission(tool: String, summary: String) = true
        })

        assertTrue(result.isError)
        assertEquals("denied", result.output)
    }

    @Test fun probeServerSurfacesHttpFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setStatus("HTTP/1.1 401 Unauthorized"))

        val snapshot = probeMcpServer("private", config(), http)

        assertTrue(!snapshot.connected)
        assertTrue(snapshot.error.contains("HTTP 401"))
    }

    @Test fun callToolReturnsStructuredContent() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"jsonrpc":"2.0","id":1,"result":{"structuredContent":{"temperature":22}}}""",
            ),
        )

        val out = McpClient("weather", config(), http).callTool("get_weather", JsonObject(emptyMap()))

        assertEquals("{\"temperature\":22}", out)
    }
}
