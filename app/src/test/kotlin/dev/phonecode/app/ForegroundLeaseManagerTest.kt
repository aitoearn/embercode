package dev.phonecode.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundLeaseManagerTest {
    @Test
    fun serviceStaysActiveUntilEveryOwnerReleases() {
        var starts = 0
        var stops = 0
        val leases = ForegroundLeaseManager({ starts++ }, { stops++ })

        leases.acquire("turn")
        leases.acquire("auth")
        leases.acquire("turn")
        leases.release("turn")
        leases.release("turn")

        assertEquals(1, starts)
        assertEquals(0, stops)

        leases.release("auth")
        assertEquals(1, stops)
    }
}
