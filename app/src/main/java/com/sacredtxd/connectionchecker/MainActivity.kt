package com.sacredtxd.connectionchecker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sacredtxd.connectionchecker.service.ConnectionMonitorService
import com.sacredtxd.connectionchecker.ui.screen.DashboardScreen
import com.sacredtxd.connectionchecker.ui.screen.DashboardViewModel
import com.sacredtxd.connectionchecker.ui.screen.UpdateViewModel
import com.sacredtxd.connectionchecker.util.ApkInstaller
import com.sacredtxd.connectionchecker.ui.theme.ConnectionCheckerTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // The service still runs without the permission; only the ongoing
            // notification is suppressed, so a denial is not fatal.
            if (granted) ConnectionMonitorService.start(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = (application as ConnectionCheckerApp).container.repository

        setContent {
            ConnectionCheckerTheme {
                val viewModel: DashboardViewModel = viewModel(
                    factory = DashboardViewModel.Factory(repository)
                )
                val updateViewModel: UpdateViewModel = viewModel(
                    factory = UpdateViewModel.Factory()
                )
                DashboardScreen(
                    viewModel = viewModel,
                    updateViewModel = updateViewModel,
                    onMonitoringChanged = { enabled ->
                        if (enabled) startMonitoring() else ConnectionMonitorService.stop(this)
                    },
                    onQuit = {
                        // Quitting stops the background service as well, otherwise the
                        // app would keep sampling after the user asked it to stop.
                        ConnectionMonitorService.stop(this)
                        viewModel.setMonitoring(false)
                        finishAndRemoveTask()
                    },
                    onInstallPermissionNeeded = {
                        // Android 8+ gates installing by source app; send the user to
                        // the settings screen that grants it, then they tap Install again.
                        startActivity(ApkInstaller.installPermissionIntent(this))
                    },
                )
            }
        }
    }

    private fun startMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        ConnectionMonitorService.start(this)
    }
}
