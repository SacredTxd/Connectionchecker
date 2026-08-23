package com.sacredtxd.connectionchecker

import com.sacredtxd.connectionchecker.data.ConnectionEvent
import com.sacredtxd.connectionchecker.data.NetworkStatus
import com.sacredtxd.connectionchecker.data.ReachabilityResult
import com.sacredtxd.connectionchecker.data.StatusBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBadgeTest {

    private fun event(reachability: ReachabilityResult) = ConnectionEvent(
        timestampMs = 0,
        status = NetworkStatus.Offline,
        reachability = reachability,
    )

    @Test
    fun `a latency under a second is shown as its own figure`() {
        val badge = StatusBadge.format(event(ReachabilityResult.Success(42, 204)))
        assertEquals("42", badge)
    }

    @Test
    fun `a latency of exactly 999 still fits`() {
        assertEquals("999", StatusBadge.format(event(ReachabilityResult.Success(999, 204))))
    }

    @Test
    fun `a slow response collapses to a marker`() {
        assertEquals("1k+", StatusBadge.format(event(ReachabilityResult.Success(1500, 204))))
    }

    @Test
    fun `timeouts and failures are distinguishable`() {
        assertEquals("T/O", StatusBadge.format(event(ReachabilityResult.Timeout)))
        assertEquals("×", StatusBadge.format(event(ReachabilityResult.Failure("No network"))))
    }

    @Test
    fun `no sample yet reads as unknown`() {
        assertEquals("?", StatusBadge.format(null))
    }

    @Test
    fun `every badge stays short enough to draw at icon size`() {
        val badges = listOf(
            StatusBadge.format(null),
            StatusBadge.format(event(ReachabilityResult.Success(7, 204))),
            StatusBadge.format(event(ReachabilityResult.Success(999, 204))),
            StatusBadge.format(event(ReachabilityResult.Success(90_000, 204))),
            StatusBadge.format(event(ReachabilityResult.Timeout)),
            StatusBadge.format(event(ReachabilityResult.Failure("boom"))),
        )
        assertTrue(badges.all { it.length <= 4 })
    }
}
