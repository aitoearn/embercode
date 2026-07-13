package dev.phonecode.tools.mcp

import dev.phonecode.tools.skills.parseSkillMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpConfigTest {

    @Test fun parsesOpenCodeStyleConfig() {
        val cfg = parseMcpConfig(
            """{"mcp":{"weather":{"type":"remote","url":"https://x/mcp","headers":{"Authorization":"Bearer k"}},
               "off":{"type":"remote","url":"https://y","enabled":false}}}""",
        )
        assertEquals(2, cfg.mcp.size)
        assertEquals("https://x/mcp", cfg.mcp.getValue("weather").url)
        assertEquals("Bearer k", cfg.mcp.getValue("weather").headers["Authorization"])
        assertTrue(cfg.mcp.getValue("weather").enabled)
        assertEquals(5_000, cfg.mcp.getValue("weather").timeout)
        assertFalse(cfg.mcp.getValue("off").enabled)
    }

    @Test fun roundTripsThroughSerialize() {
        val cfg = McpConfig(mapOf("s" to McpServerConfig(url = "https://z/mcp", enabled = true, timeout = 12_000)))
        assertEquals(cfg, parseMcpConfig(cfg.serialize()))
    }

    @Test fun toleratesGarbage() {
        assertEquals(0, parseMcpConfig("not json at all").mcp.size)
    }

    @Test fun skillMarkdownParsesFrontmatterAndBody() {
        val skill = parseSkillMarkdown("---\nname: pdf\ndescription: Work with PDF files\n---\n# PDF\nUse pdftk.")!!
        assertEquals("pdf", skill.name)
        assertEquals("Work with PDF files", skill.description)
        assertTrue(skill.body.contains("pdftk"))
    }

    @Test fun skillMarkdownRejectsMissingName() {
        assertNull(parseSkillMarkdown("---\ndescription: no name here\n---\nbody"))
        assertNull(parseSkillMarkdown("---\nname: no-description\n---\nbody"))
        assertNull(parseSkillMarkdown("---\nname: Invalid_Name\ndescription: invalid\n---\nbody"))
        assertNull(parseSkillMarkdown("---\nname: invalid--name\ndescription: invalid\n---\nbody"))
        assertNull(parseSkillMarkdown("no frontmatter at all"))
    }

    @Test fun skillMarkdownParsesFoldedMetadata() {
        val skill = parseSkillMarkdown(
            """---
name: project-validation
description: >
  Validate focused tests, builds, and
  the packaged artifact before completion.
license: Apache-2.0
compatibility: |
  Requires a project build tool.
  Works offline.
---
Run the checks.
""",
            "/skills/project-validation/SKILL.md",
        )!!
        assertEquals("Validate focused tests, builds, and the packaged artifact before completion.", skill.description)
        assertEquals("Apache-2.0", skill.license)
        assertEquals("Requires a project build tool.\nWorks offline.", skill.compatibility)
        assertEquals("/skills/project-validation/SKILL.md", skill.location)
    }
}
