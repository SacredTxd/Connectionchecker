package com.sacredtxd.connectionchecker

import com.sacredtxd.connectionchecker.data.ConnectionEvent
import com.sacredtxd.connectionchecker.data.ConnectionSummary
import com.sacredtxd.connectionchecker.data.NetworkStatus
import com.sacredtxd.connectionchecker.data.ReachabilityResult
import com.sacredtxd.connectionchecker.data.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionSummaryTest {

    private val online = NetworkStatus(
        connected = true,
        validated = true,
        transport = Transport.WIFI,
        metered = false,
    )

    private fun event(timestamp: Long, latencyMs: Long?): ConnectionEvent = ConnectionEvent(
        timestampMs = timestamp,
        status = if (latencyMs == null) NetworkStatus.Offline else online,
        reachability = latencyMs
            ?.let { ReachabilityResult.Success(latencyMs = it, httpStatus = 204) }
            ?: ReachabilityResult.Failure("No network"),
    )

    @Test
    fun `empty history summarises to zero`() {
        val summary = ConnectionSummary.from(emptyList())
        assertEquals(0, summary.sampleCount)
        assertEquals(0.0, summary.uptimeFraction, 0.0)
        assertNull(summary.averageLatencyMs)
    }

    @Test
    fun `latency stats ignore offline samples`() {
        val summary = ConnectionSummary.from(
            listOf(event(3, 100), event(2, null), event(1, 300))
        )
        assertEquals(3, summary.sampleCount)
        assertEquals(2, summary.onlineCount)
        assertEquals(200L, summary.averageLatencyMs)
        assertEquals(300L, summary.worstLatencyMs)
    }

    @Test
    fun `a run of offline samples counts as one outage`() {
        val summary = ConnectionSummary.from(
            listOf(event(4, 50), event(3, null), event(2, null), event(1, 50))
        )
        assertEquals(1, summary.outageCount)
    }

    @Test
    fun `separate offline runs count separately`() {
        val summary = ConnectionSummary.from(
            listOf(event(5, null), event(4, 50), event(3, null), event(2, null), event(1, 50))
        )
        assertEquals(2, summary.outageCount)
    }

    @Test
    fun `uptime is the online fraction`() {
        val summary = ConnectionSummary.from(
            listOf(event(4, 10), event(3, 10), event(2, null), event(1, 10))
        )
        assertEquals(0.75, summary.uptimeFraction, 0.0001)
    }
}
