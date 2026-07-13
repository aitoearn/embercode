package dev.phonecode.agent.prompt

import dev.phonecode.agent.AgentConfig
import dev.phonecode.agent.AgentEnvironment
import dev.phonecode.agent.AgentMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptAssemblerTest {
    @Test fun unavailableShellKeepsWorkspaceDetailWithoutAdvertisingShellTools() {
        val config = AgentConfig(
            model = "test-model",
            mode = AgentMode.BUILD,
            environment = AgentEnvironment(
                shellAvailable = false,
                shellDetail = "Project folder access is approval-gated.",
            ),
        )

        val prompt = PromptAssembler.assemble(config, config.model, emptyList(), config.mode)

        assertTrue(prompt.contains("Shell: NOT available - use the file/git tools. Project folder access is approval-gated."))
        assertFalse(prompt.contains("- bash:"))
        assertFalse(prompt.contains("- process:"))
    }

    @Test fun mcpInstructionsAppearOnce() {
        val config = AgentConfig(
            model = "test-model",
            mode = AgentMode.BUILD,
            environment = AgentEnvironment(),
            mcpInstructions = listOf("docs:\nUse the repository index first."),
        )

        val prompt = PromptAssembler.assemble(config, config.model, emptyList(), config.mode)

        assertTrue(prompt.contains("# MCP server instructions"))
        assertTrue(prompt.contains("docs:\nUse the repository index first."))
        assertTrue(prompt.indexOf("Use the repository index first.") == prompt.lastIndexOf("Use the repository index first."))
    }
}
