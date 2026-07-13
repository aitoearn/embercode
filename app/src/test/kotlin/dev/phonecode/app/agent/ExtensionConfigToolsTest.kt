package dev.phonecode.app.agent

import dev.phonecode.app.data.McpSkillRepository
import dev.phonecode.tools.ToolContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ExtensionConfigToolsTest {
    private object Context : ToolContext {
        override val workspacePath = "/workspace"
        override suspend fun requestPermission(tool: String, summary: String) = true
    }

    @Test fun inventoryRedactsHeaderValues() = withTools { repository, project ->
        repository.replaceMcpConfig(
            """{"mcp":{"private":{"type":"remote","url":"https://example.com/mcp","headers":{"Authorization":"Bearer secret-value"}}}}""",
        ).getOrThrow()
        val result = runBlocking {
            ExtensionConfigReadTool(repository) { project }.execute(buildJsonObject { put("action", "inventory") }, Context)
        }

        assertFalse(result.isError)
        assertTrue(result.output.contains("Authorization"))
        assertFalse(result.output.contains("secret-value"))
    }

    @Test fun writeToolIsMutatingAndCanRepairMcpAndWriteSkill() = withTools { repository, project ->
        project.resolve("../config/opencode.json").apply {
            parentFile?.mkdirs()
            writeText("invalid")
        }
        val tool = ExtensionConfigWriteTool(repository) { project }
        val repaired = runBlocking {
            tool.execute(
                buildJsonObject {
                    put("action", "replace_mcp_config")
                    put("content", """{"mcp":{}}""")
                },
                Context,
            )
        }
        val skill = runBlocking {
            tool.execute(
                buildJsonObject {
                    put("action", "write_skill")
                    put("scope", "project")
                    put("name", "live")
                    put("content", "---\nname: live\ndescription: Live\n---\nBody")
                },
                Context,
            )
        }

        assertTrue(tool.mutating)
        assertFalse(repaired.isError)
        assertFalse(skill.isError)
        assertTrue(repository.discoverSkills(project).any { it.name == "live" })
    }

    private fun withTools(block: (McpSkillRepository, java.io.File) -> Unit) {
        val root = Files.createTempDirectory("phonecode-extension-tools").toFile()
        try {
            val config = root.resolve("config")
            val project = root.resolve("project").apply { mkdirs() }
            block(McpSkillRepository(config), project)
        } finally {
            root.deleteRecursively()
        }
    }
}
