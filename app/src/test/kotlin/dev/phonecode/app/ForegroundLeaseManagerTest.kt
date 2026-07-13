package dev.phonecode.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun notificationStopStopsWorkAndClearsEveryLease() {
        var starts = 0
        var stops = 0
        var workStops = 0
        val leases = ForegroundLeaseManager({ starts++ }, { stops++ })
        leases.registerStopHandler("processes") { workStops++ }

        leases.acquire("turn")
        leases.acquire("proc-1")
        leases.stopAll()
        leases.release("turn")
        leases.release("proc-1")

        assertEquals(1, starts)
        assertEquals(1, stops)
        assertEquals(1, workStops)
    }

    @Test
    fun failedServiceStartRollsBackTheLease() {
        var attempts = 0
        var fail = true
        val leases = ForegroundLeaseManager(
            start = {
                attempts++
                if (fail) error("service unavailable")
            },
            stop = {},
        )

        assertTrue(runCatching { leases.acquire("turn") }.isFailure)
        fail = false
        leases.acquire("turn")

        assertEquals(2, attempts)
    }
}
