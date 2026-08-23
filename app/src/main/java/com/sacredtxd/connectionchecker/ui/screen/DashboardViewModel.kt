package com.sacredtxd.connectionchecker.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sacredtxd.connectionchecker.data.ConnectionEvent
import com.sacredtxd.connectionchecker.data.ConnectionRepository
import com.sacredtxd.connectionchecker.data.ConnectionSummary
import com.sacredtxd.connectionchecker.data.LatencyChartModel
import com.sacredtxd.connectionchecker.data.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val status: NetworkStatus = NetworkStatus.Offline,
    val lastEvent: ConnectionEvent? = null,
    val history: List<ConnectionEvent> = emptyList(),
    val summary: ConnectionSummary = ConnectionSummary.Empty,
    val checkInProgress: Boolean = false,
    val monitoring: Boolean = false,
)

class DashboardViewModel(private val repository: ConnectionRepository) : ViewModel() {

    val status: StateFlow<NetworkStatus> = repository.status
    val lastEvent: StateFlow<ConnectionEvent?> = repository.lastEvent
    val history: StateFlow<List<ConnectionEvent>> = repository.history
    val summary: StateFlow<ConnectionSummary> = repository.summary
    val checkInProgress: StateFlow<Boolean> = repository.checkInProgress

    val chartModel: StateFlow<LatencyChartModel> = repository.history
        .map(LatencyChartModel::from)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LatencyChartModel.from(emptyList()),
        )

    private val _monitoring = MutableStateFlow(false)
    val monitoring: StateFlow<Boolean> = _monitoring.asStateFlow()

    fun runCheck() {
        viewModelScope.launch { repository.runCheck() }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    fun setMonitoring(enabled: Boolean) {
        _monitoring.value = enabled
    }

    class Factory(private val repository: ConnectionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DashboardViewModel(repository) as T
    }
}
