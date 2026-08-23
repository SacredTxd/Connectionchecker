package com.sacredtxd.connectionchecker

import android.app.Application
import com.sacredtxd.connectionchecker.service.ConnectionMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ConnectionCheckerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ConnectionMonitorService.createNotificationChannel(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            container.repository.loadHistory()
        }
    }
}
