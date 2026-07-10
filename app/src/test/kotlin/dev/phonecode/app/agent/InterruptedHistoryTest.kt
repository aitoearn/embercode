package dev.phonecode.app.agent

import dev.phonecode.provider.domain.ChatMessage
import dev.phonecode.provider.domain.MessagePart
import dev.phonecode.provider.domain.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InterruptedHistoryTest {
    @Test fun addsErrorResultsForUnresolvedToolCalls() {
        val history = listOf(
            ChatMessage(Role.USER, listOf(MessagePart.Text("change it"))),
            ChatMessage(
                Role.ASSISTANT,
                listOf(
                    MessagePart.ToolCall("a", "write", "{}"),
                    MessagePart.ToolCall("b", "shell", "{}"),
                ),
            ),
        )

        val repaired = repairInterruptedHistory(history)
        val results = repaired.last().parts.filterIsInstance<MessagePart.ToolResult>()
        assertEquals(listOf("a", "b"), results.map { it.callId })
        assertTrue(results.all { it.isError })
    }

    @Test fun addsOnlyMissingResults() {
        val history = listOf(
            ChatMessage(
                Role.ASSISTANT,
                listOf(MessagePart.ToolCall("done", "read", "{}"), MessagePart.ToolCall("missing", "write", "{}")),
            ),
            ChatMessage(Role.USER, listOf(MessagePart.ToolResult("done", "ok"))),
        )

        val repaired = repairInterruptedHistory(history)
        val added = repaired.last().parts.single() as MessagePart.ToolResult
        assertEquals("missing", added.callId)
    }

    @Test fun leavesCompleteHistoryUntouched() {
        val history = listOf(
            ChatMessage(Role.ASSISTANT, listOf(MessagePart.ToolCall("a", "read", "{}"))),
            ChatMessage(Role.USER, listOf(MessagePart.ToolResult("a", "ok"))),
        )

        assertSame(history, repairInterruptedHistory(history))
    }
}
