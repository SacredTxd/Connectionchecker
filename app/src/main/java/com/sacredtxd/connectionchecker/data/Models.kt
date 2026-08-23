package com.sacredtxd.connectionchecker.data

import kotlinx.serialization.Serializable

/** The physical bearer carrying the active connection. */
enum class Transport {
    WIFI,
    CELLULAR,
    ETHERNET,
    VPN,
    BLUETOOTH,
    OTHER,
    NONE,
}

/**
 * What the platform reports about connectivity. This says a network exists and is
 * validated by the system; it does not say our own endpoint is reachable — that is
 * what [ReachabilityResult] measures.
 */
@Serializable
data class NetworkStatus(
    val connected: Boolean,
    val validated: Boolean,
    val transport: Transport,
    val metered: Boolean,
    val networkName: String? = null,
) {
    companion object {
        val Offline = NetworkStatus(
            connected = false,
            validated = false,
            transport = Transport.NONE,
            metered = false,
        )
    }
}

/** Outcome of one probe against a reachability endpoint. */
@Serializable
sealed interface ReachabilityResult {
    @Serializable
    data class Success(val latencyMs: Long, val httpStatus: Int) : ReachabilityResult

    @Serializable
    data class Failure(val reason: String) : ReachabilityResult

    @Serializable
    data object Timeout : ReachabilityResult
}

/** A point-in-time sample written to the history log. */
@Serializable
data class ConnectionEvent(
    val timestampMs: Long,
    val status: NetworkStatus,
    val reachability: ReachabilityResult? = null,
) {
    val isOnline: Boolean
        get() = status.connected && reachability is ReachabilityResult.Success

    val latencyMs: Long?
        get() = (reachability as? ReachabilityResult.Success)?.latencyMs
}

/** Rolled-up view of the history log, shown on the dashboard. */
data class ConnectionSummary(
    val sampleCount: Int,
    val onlineCount: Int,
    val averageLatencyMs: Long?,
    val worstLatencyMs: Long?,
    val outageCount: Int,
) {
    val uptimeFraction: Double
        get() = if (sampleCount == 0) 0.0 else onlineCount.toDouble() / sampleCount

    companion object {
        val Empty = ConnectionSummary(0, 0, null, null, 0)

        fun from(events: List<ConnectionEvent>): ConnectionSummary {
            if (events.isEmpty()) return Empty
            val latencies = events.mapNotNull { it.latencyMs }
            // An outage is a transition into an offline sample, not every offline sample,
            // so a long outage counts once.
            var outages = 0
            var previousOnline = true
            for (event in events) {
                if (previousOnline && !event.isOnline) outages++
                previousOnline = event.isOnline
            }
            return ConnectionSummary(
                sampleCount = events.size,
                onlineCount = events.count { it.isOnline },
                averageLatencyMs = latencies.takeIf { it.isNotEmpty() }?.average()?.toLong(),
                worstLatencyMs = latencies.maxOrNull(),
                outageCount = outages,
            )
        }
    }
}
