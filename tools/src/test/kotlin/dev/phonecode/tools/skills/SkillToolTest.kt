package dev.phonecode.tools.skills

import dev.phonecode.tools.ToolContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SkillToolTest {

    private object Ctx : ToolContext {
        override val workspacePath = "/tmp"
        override suspend fun requestPermission(tool: String, summary: String) = true
    }

    private val skills = listOf(SkillManifest("pdf", "Work with PDFs", "# PDF\nUse pdftk."))

    @Test fun descriptionListsNameAndDescription() {
        val description = SkillTool(skills).description
        assertTrue(description.contains("pdf"))
        assertTrue(description.contains("Work with PDFs"))
    }

    @Test fun descriptionCatalogIsBounded() {
        val many = (1..200).map { SkillManifest("skill-$it", "x".repeat(100), "body", "/skills/$it/SKILL.md") }
        assertTrue(SkillTool(many).description.length <= 8_000)
    }

    @Test fun loadsSkillBodyWrappedInSkillContent() = runBlocking {
        val result = SkillTool(skills).execute(buildJsonObject { put("name", "pdf") }, Ctx)
        assertFalse(result.isError)
        assertTrue(result.output.contains("<skill_content name=\"pdf\">"))
        assertTrue(result.output.contains("pdftk"))
    }

    @Test fun unknownSkillIsError() = runBlocking {
        val result = SkillTool(skills).execute(buildJsonObject { put("name", "nope") }, Ctx)
        assertTrue(result.isError)
    }

    @Test fun missingNameIsError() = runBlocking {
        val result = SkillTool(skills).execute(JsonObject(emptyMap()), Ctx)
        assertTrue(result.isError)
    }

    @Test fun loadsBoundedRelativeResource() = runBlocking {
        val root = Files.createTempDirectory("phonecode-skill").toFile()
        try {
            val skillFile = root.resolve("SKILL.md").apply { writeText("manifest") }
            root.resolve("references").mkdirs()
            root.resolve("references/guide.md").writeText("Use the focused check.")
            val tool = SkillTool(listOf(SkillManifest("validation", "Validate", "body", skillFile.absolutePath)))

            val result = tool.execute(
                buildJsonObject { put("name", "validation"); put("path", "references/guide.md") },
                Ctx,
            )

            assertFalse(result.isError)
            assertTrue(result.output.contains("Use the focused check."))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsResourceTraversal() = runBlocking {
        val parent = Files.createTempDirectory("phonecode-skills").toFile()
        try {
            val root = parent.resolve("skill").apply { mkdirs() }
            val skillFile = root.resolve("SKILL.md").apply { writeText("manifest") }
            parent.resolve("secret.txt").writeText("secret")
            val tool = SkillTool(listOf(SkillManifest("safe", "Safe", "body", skillFile.absolutePath)))

            val result = tool.execute(
                buildJsonObject { put("name", "safe"); put("path", "../secret.txt") },
                Ctx,
            )

            assertTrue(result.isError)
            assertFalse(result.output.contains("secret"))
        } finally {
            parent.deleteRecursively()
        }
    }
}
