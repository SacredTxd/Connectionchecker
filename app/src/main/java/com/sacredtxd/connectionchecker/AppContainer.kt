package com.sacredtxd.connectionchecker

import android.content.Context
import com.sacredtxd.connectionchecker.data.ConnectionEventStore
import com.sacredtxd.connectionchecker.data.ConnectionRepository
import com.sacredtxd.connectionchecker.monitor.NetworkStatusMonitor
import com.sacredtxd.connectionchecker.monitor.ReachabilityChecker
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/**
 * Hand-rolled dependency container. The app is small enough that a DI framework would
 * cost more than it saves; everything here lives for the process lifetime.
 */
class AppContainer(context: Context) {

    private val applicationContext = context.applicationContext

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val monitor = NetworkStatusMonitor(applicationContext)

    private val reachabilityChecker = ReachabilityChecker()

    private val store = ConnectionEventStore(
        File(applicationContext.filesDir, ConnectionEventStore.FILE_NAME)
    )

    val repository: ConnectionRepository = ConnectionRepository(
        monitor = monitor,
        reachabilityChecker = reachabilityChecker,
        store = store,
        scope = applicationScope,
    )
}
