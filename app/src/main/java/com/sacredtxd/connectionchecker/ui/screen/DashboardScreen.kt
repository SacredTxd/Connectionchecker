package com.sacredtxd.connectionchecker.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValueCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sacredtxd.connectionchecker.data.ConnectionEvent
import com.sacredtxd.connectionchecker.data.ConnectionSummary
import com.sacredtxd.connectionchecker.data.NetworkStatus
import com.sacredtxd.connectionchecker.data.ReachabilityResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    updateViewModel: UpdateViewModel,
    onMonitoringChanged: (Boolean) -> Unit,
    onInstallPermissionNeeded: () -> Unit,
    onQuit: () -> Unit,
) {
    val status by viewModel.status.collectAsState()
    val lastEvent by viewModel.lastEvent.collectAsState()
    val history by viewModel.history.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val checkInProgress by viewModel.checkInProgress.collectAsState()
    val monitoring by viewModel.monitoring.collectAsState()
    val chartModel by viewModel.chartModel.collectAsState()
    val updateStatus by updateViewModel.status.collectAsState()
    val context = LocalContext.current

    var confirmQuit by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("Quit Connection Checker?") },
            text = {
                Text(
                    if (monitoring) {
                        "Background monitoring will stop, so no further samples are " +
                            "recorded until you open the app again. Your history is kept."
                    } else {
                        "The app will close. Your history is kept."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmQuit = false
                    onQuit()
                }) { Text("Quit") }
            },
            dismissButton = {
                TextButton(onClick = { confirmQuit = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Connection Checker") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.size(4.dp))
            StatusCard(status = status, lastEvent = lastEvent)
            ChartCard(
                model = chartModel,
                onSaved = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
            )
            SummaryCard(summary = summary)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = viewModel::runCheck,
                    enabled = !checkInProgress,
                ) {
                    if (checkInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Check now")
                }
                OutlinedButton(onClick = viewModel::clearHistory) { Text("Clear") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Background monitoring", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = monitoring,
                    onCheckedChange = {
                        viewModel.setMonitoring(it)
                        onMonitoringChanged(it)
                    },
                )
            }

            HorizontalDivider()

            UpdateCard(
                status = updateStatus,
                installedVersionName = updateViewModel.installedVersionName,
                onCheck = updateViewModel::check,
                onDownload = { updateViewModel.download(context) },
                onInstall = {
                    if (!updateViewModel.install(context)) onInstallPermissionNeeded()
                },
                onDismiss = updateViewModel::dismiss,
            )

            HorizontalDivider()

            Text(
                "History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (history.isEmpty()) {
                Text(
                    "No samples yet. Run a check to start the log.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    history.forEach { event -> HistoryRow(event) }
                }
            }
            HorizontalDivider()

            OutlinedButton(
                onClick = { confirmQuit = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Quit app")
            }

            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun StatusCard(status: NetworkStatus, lastEvent: ConnectionEvent?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(online = lastEvent?.isOnline ?: status.connected)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (status.connected) "Connected" else "No network",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Text("Transport: ${status.transport.name.lowercase()}")
            Text("Validated by system: ${if (status.validated) "yes" else "no"}")
            Text("Metered: ${if (status.metered) "yes" else "no"}")
            lastEvent?.let { Text("Last probe: ${it.reachability.describe()}") }
        }
    }
}

@Composable
private fun SummaryCard(summary: ConnectionSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Last ${summary.sampleCount} samples", fontWeight = FontWeight.SemiBold)
            Text("Uptime: ${(summary.uptimeFraction * 100).toInt()}%")
            Text("Average latency: ${summary.averageLatencyMs?.let { "$it ms" } ?: "—"}")
            Text("Worst latency: ${summary.worstLatencyMs?.let { "$it ms" } ?: "—"}")
            Text("Outages: ${summary.outageCount}")
        }
    }
}

@Composable
private fun HistoryRow(event: ConnectionEvent) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusDot(online = event.isOnline, size = 8.dp)
        Text(
            timeFormat.format(Date(event.timestampMs)),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            event.status.transport.name.lowercase(),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            event.reachability.describe(),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StatusDot(online: Boolean, size: Dp = 14.dp) {
    val color = if (online) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    Spacer(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color, CircleShape)
    )
}

private fun ReachabilityResult?.describe(): String = when (this) {
    null -> "not probed"
    is ReachabilityResult.Success -> "${latencyMs} ms (HTTP $httpStatus)"
    is ReachabilityResult.Timeout -> "timeout"
    is ReachabilityResult.Failure -> reason
}
