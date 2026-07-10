package dev.phonecode.app.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatTest {
    @Test fun formatsRetryDurationsForMobileCopy() {
        assertEquals("1s", formatDuration(1))
        assertEquals("59s", formatDuration(59_000))
        assertEquals("2m", formatDuration(120_000))
        assertEquals("2h 5m", formatDuration(7_500_000))
    }
}
