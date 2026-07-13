package dev.phonecode.app.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiReportPayloadTest {
    @Test fun containsOnlyAllowlistedUserAuthoredReportFields() {
        val payload = Json.parseToJsonElement(
            aiReportPayload("privacy", "A private value appeared", "0.2.4"),
        ).jsonObject

        assertEquals(
            setOf("version", "category", "appVersion", "platform", "note"),
            payload.keys,
        )
        assertEquals("A private value appeared", payload.getValue("note").jsonPrimitive.content)
        assertFalse("output" in payload)
        assertFalse("prompt" in payload)
        assertFalse("files" in payload)
    }

    @Test fun omitsBlankOptionalFieldsAndBoundsText() {
        val payload = Json.parseToJsonElement(
            aiReportPayload("other", " x".repeat(1200), "0.2.4"),
        ).jsonObject

        assertTrue(payload.getValue("note").jsonPrimitive.content.length <= 1000)
    }
}
