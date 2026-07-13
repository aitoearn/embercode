package dev.phonecode.app.data

import dev.phonecode.tools.mcp.McpConfig
import dev.phonecode.tools.mcp.McpServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class McpSkillRepositoryTest {
    @Test fun discoversPortableRootsWithProjectPrecedence() = withRepository { config, project, repository ->
        writeSkill(config.resolve("skills/shared"), "shared", "Global skill")
        writeSkill(config.resolve(".opencode/skills/global-opencode"), "global-opencode", "Global OpenCode skill")
        writeSkill(config.resolve(".claude/skills/claude"), "claude", "Claude skill")
        writeSkill(config.resolve(".codex/skills/codex"), "codex", "Codex skill")
        writeSkill(project.resolve(".agents/skills/shared"), "shared", "Project skill")
        writeSkill(project.resolve(".opencode/skills/opencode"), "opencode", "OpenCode skill")
        writeSkill(project.resolve(".agents/skills/wrong-folder"), "wrong-name", "Invalid location")

        val skills = repository.discoverSkills(project)

        assertEquals(listOf("shared", "opencode", "global-opencode", "claude", "codex"), skills.map { it.name })
        assertEquals("Project skill", skills.first().description)
        assertTrue(skills.first().location.endsWith(".agents/skills/shared/SKILL.md"))
    }

    @Test fun inventoryShowsInvalidAndShadowedSkills() = withRepository { config, project, repository ->
        writeSkill(project.resolve(".agents/skills/shared"), "shared", "Project skill")
        writeSkill(config.resolve("skills/shared"), "shared", "Global skill")
        writeSkill(config.resolve("skills/wrong-folder"), "wrong-name", "Wrong folder")
        config.resolve("skills/missing").mkdirs()
        config.resolve("skills/broken").apply {
            mkdirs()
            resolve("SKILL.md").writeText("not frontmatter")
        }

        val inventory = repository.scanSkills(project)

        assertEquals(SkillStatus.ACTIVE, inventory.items.first { it.location.contains("project") }.status)
        assertEquals(SkillStatus.SHADOWED, inventory.items.first { it.name == "shared" && it.scope == SkillScope.GLOBAL }.status)
        assertEquals("Skill name must match its folder", inventory.items.first { it.name == "wrong-name" }.issue)
        assertEquals("Missing SKILL.md", inventory.items.first { it.name == "missing" }.issue)
        assertEquals("Invalid SKILL.md frontmatter", inventory.items.first { it.name == "broken" }.issue)
    }

    @Test fun disabledProjectOverridePersistsAndRevealsGlobalSkill() = withRepository { config, project, repository ->
        writeSkill(project.resolve(".agents/skills/shared"), "shared", "Project skill")
        writeSkill(config.resolve("skills/shared"), "shared", "Global skill")
        val projectSkill = repository.scanSkills(project).items.first { it.scope == SkillScope.PROJECT }

        assertTrue(repository.setSkillEnabled(projectSkill.id, false, project).isSuccess)

        val reloaded = McpSkillRepository(config).scanSkills(project)
        assertEquals(SkillStatus.DISABLED, reloaded.items.first { it.id == projectSkill.id }.status)
        assertEquals(SkillStatus.ACTIVE, reloaded.items.first { it.scope == SkillScope.GLOBAL }.status)
        assertEquals("Global skill", reloaded.active.single().description)
    }

    @Test fun deleteSkillOnlyAcceptsDiscoveredManagedEntries() = withRepository { config, project, repository ->
        writeSkill(config.resolve("skills/remove-me"), "remove-me", "Remove me")
        val skill = repository.scanSkills(project).items.single()
        val outside = requireNotNull(config.parentFile).resolve("outside/SKILL.md").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("outside")
        }

        assertTrue(repository.deleteSkill(outside.canonicalPath, project).isFailure)
        assertTrue(outside.exists())
        assertTrue(repository.deleteSkill(skill.id, project).isSuccess)
        assertFalse(requireNotNull(File(skill.location).parentFile).exists())
    }

    @Test fun malformedMcpConfigIsReportedAndPreservedByEveryMutation() = withRepository { config, _, repository ->
        val file = config.resolve("opencode.json").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("{ definitely not valid")
        }
        val original = file.readBytes()

        assertTrue(repository.loadMcpConfigState() is McpConfigLoad.Invalid)
        assertTrue(repository.saveMcpConfig(McpConfig()).isFailure)
        assertTrue(repository.upsertMcpServer(name = "new", server = McpServerConfig(url = "https://example.com/mcp")).isFailure)
        assertTrue(repository.removeMcpServer("new").isFailure)
        assertTrue(repository.setMcpEnabled("new", false).isFailure)
        assertTrue(original.contentEquals(file.readBytes()))
    }

    @Test fun safeMcpMutatorsAddRenameToggleAndDelete() = withRepository { _, _, repository ->
        val server = McpServerConfig(url = "https://example.com/mcp")

        assertTrue(repository.upsertMcpServer(name = "one", server = server).isSuccess)
        assertTrue(repository.upsertMcpServer(originalName = "one", name = "two", server = server).isSuccess)
        assertTrue(repository.setMcpEnabled("two", false).isSuccess)
        assertFalse(repository.loadMcpConfig().mcp.getValue("two").enabled)
        assertFalse("one" in repository.loadMcpConfig().mcp)
        assertTrue(repository.removeMcpServer("two").isSuccess)
        assertTrue(repository.loadMcpConfig().mcp.isEmpty())
    }

    @Test fun runtimeFingerprintTracksMcpSkillAndProjectChanges() = withRepository { config, project, repository ->
        val initial = repository.runtimeFingerprint(project)
        repository.replaceMcpConfig("""{"mcp":{}}""").getOrThrow()
        val afterMcp = repository.runtimeFingerprint(project)
        repository.writeSkillFile(
            SkillScope.PROJECT,
            "live-skill",
            content = "---\nname: live-skill\ndescription: Live\n---\nFirst",
            projectDir = project,
        ).getOrThrow()
        val afterProjectSkill = repository.runtimeFingerprint(project)
        repository.writeSkillFile(
            SkillScope.GLOBAL,
            "global-skill",
            content = "---\nname: global-skill\ndescription: Global\n---\nSecond",
            projectDir = project,
        ).getOrThrow()
        val afterGlobalSkill = repository.runtimeFingerprint(project)

        assertFalse(initial.mcp == afterMcp.mcp)
        assertEquals(initial.skills, afterMcp.skills)
        assertFalse(afterMcp.skills == afterProjectSkill.skills)
        assertFalse(afterProjectSkill.skills == afterGlobalSkill.skills)
        assertTrue(repository.watchedDirectories(project).contains(config.canonicalFile))
        assertTrue(repository.watchedDirectories(project).contains(project.canonicalFile))
    }

    @Test fun boundedSkillBridgeReadsWritesAndRejectsTraversal() = withRepository { _, project, repository ->
        val content = "---\nname: bridge\ndescription: Bridge\n---\nBody"

        assertTrue(repository.writeSkillFile(SkillScope.GLOBAL, "bridge", content = content, projectDir = project).isSuccess)
        assertEquals(content, repository.readSkillFile(SkillScope.GLOBAL, "bridge", projectDir = project).getOrThrow())
        assertTrue(repository.writeSkillFile(SkillScope.PROJECT, "bridge", "references/guide.md", "Guide", project).isSuccess)
        assertEquals("Guide", repository.readSkillFile(SkillScope.PROJECT, "bridge", "references/guide.md", project).getOrThrow())
        assertTrue(repository.writeSkillFile(SkillScope.GLOBAL, "bridge", "../outside", "bad", project).isFailure)
        assertTrue(repository.readSkillFile(SkillScope.GLOBAL, "bridge", "../outside", project).isFailure)
    }

    @Test fun validRawReplacementRepairsMalformedMcpConfig() = withRepository { config, _, repository ->
        config.resolve("opencode.json").apply {
            parentFile?.mkdirs()
            writeText("invalid")
        }

        val repaired = repository.replaceMcpConfig(
            """{"mcp":{"docs":{"type":"remote","url":"https://example.com/mcp","enabled":true,"timeout":5000}}}""",
        )

        assertTrue(repaired.isSuccess)
        assertEquals("https://example.com/mcp", repository.loadMcpConfig().mcp.getValue("docs").url)
    }

    private fun withRepository(block: (File, File, McpSkillRepository) -> Unit) {
        val root = Files.createTempDirectory("phonecode-skill-repository").toFile()
        try {
            val config = root.resolve("config")
            val project = root.resolve("project")
            block(config, project, McpSkillRepository(config))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun writeSkill(directory: File, name: String, description: String) {
        directory.mkdirs()
        directory.resolve("SKILL.md").writeText("---\nname: $name\ndescription: $description\n---\nBody")
    }
}
