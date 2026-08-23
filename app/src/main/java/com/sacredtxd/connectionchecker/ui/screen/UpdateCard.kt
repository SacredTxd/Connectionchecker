package com.sacredtxd.connectionchecker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sacredtxd.connectionchecker.data.UpdateStatus

/**
 * The in-app updater. Every state says what it is waiting on, so a stalled check or
 * a failed download never leaves the card looking idle.
 */
@Composable
fun UpdateCard(
    status: UpdateStatus,
    installedVersionName: String,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "App version",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Installed: $installedVersionName",
                style = MaterialTheme.typography.bodyMedium,
            )

            when (status) {
                is UpdateStatus.Idle -> {
                    OutlinedButton(onClick = onCheck) { Text("Check for updates") }
                }

                is UpdateStatus.Checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Checking…", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                is UpdateStatus.UpToDate -> {
                    Text(
                        "You are on the latest build.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onCheck) { Text("Check again") }
                }

                is UpdateStatus.Available -> {
                    Text(
                        "Version ${status.manifest.versionName} is available.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (status.manifest.notes.isNotBlank()) {
                        Text(
                            status.manifest.notes,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onDownload) { Text("Download") }
                        TextButton(onClick = onDismiss) { Text("Not now") }
                    }
                }

                is UpdateStatus.Downloading -> {
                    Text(
                        "Downloading ${status.manifest.versionName}… ${status.percent}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(
                        progress = { status.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is UpdateStatus.ReadyToInstall -> {
                    Text(
                        "Version ${status.manifest.versionName} is ready to install.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onInstall) { Text("Install") }
                        TextButton(onClick = onDismiss) { Text("Later") }
                    }
                }

                is UpdateStatus.Failed -> {
                    Text(
                        "Update check failed: ${status.reason}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = onCheck) { Text("Try again") }
                }
            }
        }
    }
}
