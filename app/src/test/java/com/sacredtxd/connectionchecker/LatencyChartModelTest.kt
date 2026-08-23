package com.sacredtxd.connectionchecker

import com.sacredtxd.connectionchecker.data.ConnectionEvent
import com.sacredtxd.connectionchecker.data.LatencyChartModel
import com.sacredtxd.connectionchecker.data.NetworkStatus
import com.sacredtxd.connectionchecker.data.ReachabilityResult
import com.sacredtxd.connectionchecker.data.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LatencyChartModelTest {

    private val online = NetworkStatus(
        connected = true,
        validated = true,
        transport = Transport.WIFI,
        metered = false,
    )

    private fun event(timestamp: Long, latencyMs: Long?) = ConnectionEvent(
        timestampMs = timestamp,
        status = if (latencyMs == null) NetworkStatus.Offline else online,
        reachability = latencyMs
            ?.let { ReachabilityResult.Success(latencyMs = it, httpStatus = 204) }
            ?: ReachabilityResult.Failure("No network"),
    )

    @Test
    fun `points are plotted oldest first`() {
        // The store hands over newest-first; the chart reads left to right in time.
        val model = LatencyChartModel.from(listOf(event(3, 30), event(2, 20), event(1, 10)))
        assertEquals(listOf(1L, 2L, 3L), model.points.map { it.timestampMs })
        assertEquals(listOf(10L, 20L, 30L), model.points.map { it.latencyMs })
    }

    @Test
    fun `only the most recent samples are plotted`() {
        val events = (1..100L).map { event(it, it) }.asReversed()
        val model = LatencyChartModel.from(events, maxPoints = 10)

        assertEquals(10, model.points.size)
        assertEquals(91L, model.points.first().timestampMs)
        assertEquals(100L, model.points.last().timestampMs)
    }

    @Test
    fun `offline samples are plotted as gaps`() {
        val model = LatencyChartModel.from(listOf(event(2, null), event(1, 10)))
        val offline = model.points.single { !it.online }

        assertNull(offline.latencyMs)
        assertEquals(1, model.outageCount)
    }

    @Test
    fun `an all-offline history has no plottable data`() {
        val model = LatencyChartModel.from(listOf(event(2, null), event(1, null)))
        assertFalse(model.hasData)
        assertEquals(100L, model.yMaxMs)
    }

    @Test
    fun `an empty history falls back to a default axis`() {
        val model = LatencyChartModel.from(emptyList())
        assertTrue(model.points.isEmpty())
        assertEquals(100L, model.yMaxMs)
    }

    @Test
    fun `the axis rounds up to a readable ceiling`() {
        assertEquals(10L, LatencyChartModel.niceCeiling(7))
        assertEquals(20L, LatencyChartModel.niceCeiling(11))
        assertEquals(50L, LatencyChartModel.niceCeiling(42))
        assertEquals(100L, LatencyChartModel.niceCeiling(51))
        assertEquals(100L, LatencyChartModel.niceCeiling(100))
        assertEquals(200L, LatencyChartModel.niceCeiling(101))
        assertEquals(2000L, LatencyChartModel.niceCeiling(1500))
    }

    @Test
    fun `the axis covers the slowest sample`() {
        val model = LatencyChartModel.from(listOf(event(2, 340), event(1, 12)))
        assertEquals(500L, model.yMaxMs)
        assertTrue(model.yMaxMs >= 340L)
    }

    @Test
    fun `grid lines divide the axis evenly and end at the top`() {
        val model = LatencyChartModel.from(listOf(event(1, 90)))
        assertEquals(listOf(25L, 50L, 75L, 100L), model.gridLinesMs)
        assertEquals(model.yMaxMs, model.gridLinesMs.last())
    }
}
