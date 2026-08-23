package com.sacredtxd.connectionchecker.monitor

import com.sacredtxd.connectionchecker.data.NetworkStatus
import com.sacredtxd.connectionchecker.data.ReachabilityResult
import kotlinx.coroutines.flow.Flow

/** Where the platform's view of connectivity comes from. */
interface ConnectivitySource {
    /** Emits on every change the platform reports. */
    fun statusFlow(): Flow<NetworkStatus>

    /** Reads connectivity synchronously, for one-shot checks outside the flow. */
    fun currentStatus(): NetworkStatus
}

/** A single probe against a reachability endpoint. */
interface ReachabilityProbe {
    suspend fun check(): ReachabilityResult
}
