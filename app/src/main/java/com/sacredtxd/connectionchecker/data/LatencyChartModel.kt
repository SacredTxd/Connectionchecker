package com.sacredtxd.connectionchecker.data

/** One plotted sample. [latencyMs] is null when the sample was offline. */
data class ChartPoint(
    val timestampMs: Long,
    val latencyMs: Long?,
) {
    val online: Boolean get() = latencyMs != null
}

/**
 * The chart's data in plot order — oldest on the left, newest on the right — with
 * the y-axis already resolved. Keeping this separate from the drawing code means
 * the axis maths is testable without a device.
 */
data class LatencyChartModel(
    val points: List<ChartPoint>,
    val yMaxMs: Long,
    val gridLinesMs: List<Long>,
) {
    val hasData: Boolean get() = points.any { it.online }

    val outageCount: Int get() = points.count { !it.online }

    companion object {
        const val DEFAULT_MAX_POINTS = 60
        private const val FALLBACK_Y_MAX_MS = 100L
        private const val GRID_LINE_COUNT = 4

        /**
         * @param events newest-first, as the history store holds them.
         * @param maxPoints how many of the most recent samples to plot.
         */
        fun from(
            events: List<ConnectionEvent>,
            maxPoints: Int = DEFAULT_MAX_POINTS,
        ): LatencyChartModel {
            val points = events
                .take(maxPoints)
                .asReversed()
                .map { ChartPoint(timestampMs = it.timestampMs, latencyMs = it.latencyMs) }

            val peak = points.mapNotNull { it.latencyMs }.maxOrNull()
            val yMax = if (peak == null || peak <= 0L) FALLBACK_Y_MAX_MS else niceCeiling(peak)

            val gridLines = (1..GRID_LINE_COUNT).map { step ->
                yMax * step / GRID_LINE_COUNT
            }

            return LatencyChartModel(points = points, yMaxMs = yMax, gridLinesMs = gridLines)
        }

        /**
         * Rounds up to the next 1, 2 or 5 times a power of ten, so the axis lands on
         * numbers a reader can divide in their head rather than on the raw peak.
         */
        internal fun niceCeiling(value: Long): Long {
            require(value > 0) { "value must be positive" }
            var magnitude = 1L
            while (magnitude * 10 <= value) magnitude *= 10
            for (multiplier in longArrayOf(1, 2, 5)) {
                val candidate = magnitude * multiplier
                if (candidate >= value) return candidate
            }
            return magnitude * 10
        }
    }
}
