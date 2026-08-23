package com.sacredtxd.connectionchecker.data

import com.sacredtxd.connectionchecker.monitor.NetworkStatusMonitor
import com.sacredtxd.connectionchecker.monitor.ReachabilityChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Single source of truth for connection state: platform status from
 * [NetworkStatusMonitor], endpoint latency from [ReachabilityChecker], and the
 * persisted history in [ConnectionEventStore].
 */
class ConnectionRepository(
    private val monitor: NetworkStatusMonitor,
    private val reachabilityChecker: ReachabilityChecker,
    private val store: ConnectionEventStore,
    scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val status: StateFlow<NetworkStatus> = monitor.statusFlow()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), NetworkStatus.Offline)

    val history: StateFlow<List<ConnectionEvent>> = store.events

    val summary: StateFlow<ConnectionSummary> = store.events
        .map(ConnectionSummary::from)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), ConnectionSummary.Empty)

    private val _lastEvent = MutableStateFlow<ConnectionEvent?>(null)
    val lastEvent: StateFlow<ConnectionEvent?> = _lastEvent.asStateFlow()

    private val _checkInProgress = MutableStateFlow(false)
    val checkInProgress: StateFlow<Boolean> = _checkInProgress.asStateFlow()

    suspend fun loadHistory() = store.load()

    /**
     * Samples connectivity once and records it. When the platform says there is no
     * network, the endpoint probe is skipped — it would only time out.
     */
    suspend fun runCheck(): ConnectionEvent {
        _checkInProgress.value = true
        try {
            val status = monitor.currentStatus()
            val reachability = if (status.connected) {
                reachabilityChecker.check()
            } else {
                ReachabilityResult.Failure("No network")
            }
            val event = ConnectionEvent(
                timestampMs = now(),
                status = status,
                reachability = reachability,
            )
            store.append(event)
            _lastEvent.value = event
            return event
        } finally {
            _checkInProgress.value = false
        }
    }

    suspend fun clearHistory() {
        store.clear()
        _lastEvent.value = null
    }
}
