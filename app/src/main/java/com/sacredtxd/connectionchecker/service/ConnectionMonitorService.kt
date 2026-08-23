package com.sacredtxd.connectionchecker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.sacredtxd.connectionchecker.ConnectionCheckerApp
import com.sacredtxd.connectionchecker.MainActivity
import com.sacredtxd.connectionchecker.R
import com.sacredtxd.connectionchecker.data.ConnectionEvent
import com.sacredtxd.connectionchecker.data.ReachabilityResult
import com.sacredtxd.connectionchecker.data.StatusBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs periodic checks while the app is in the background and keeps the latest result
 * on an ongoing notification. Started and stopped from the dashboard.
 */
class ConnectionMonitorService : LifecycleService() {

    private val repository by lazy {
        (application as ConnectionCheckerApp).container.repository
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground(buildNotification(null))

        val intervalMs = intent?.getLongExtra(EXTRA_INTERVAL_MS, DEFAULT_INTERVAL_MS)
            ?: DEFAULT_INTERVAL_MS

        lifecycleScope.launch {
            while (isActive) {
                val event = runCatching { repository.runCheck() }.getOrNull()
                notificationManager?.notify(NOTIFICATION_ID, buildNotification(event))
                delay(intervalMs.coerceAtLeast(MIN_INTERVAL_MS))
            }
        }

        return START_STICKY
    }

    private val notificationManager: NotificationManager?
        get() = getSystemService()

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(event: ConnectionEvent?): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = when (val reachability = event?.reachability) {
            null -> getString(R.string.monitoring_active)
            is ReachabilityResult.Success ->
                "Online via ${event.status.transport.name.lowercase()} — ${reachability.latencyMs} ms"
            is ReachabilityResult.Timeout -> "Timed out reaching the network"
            is ReachabilityResult.Failure -> "Offline — ${reachability.reason}"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            // The small icon carries the latency itself, so the current ping is
            // readable in the status bar without opening the shade.
            .setSmallIcon(StatusBadgeIcon.of(StatusBadge.format(event)))
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "connection_monitor"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_STOP = "com.sacredtxd.connectionchecker.action.STOP"
        const val EXTRA_INTERVAL_MS = "interval_ms"
        const val DEFAULT_INTERVAL_MS = 60_000L
        const val MIN_INTERVAL_MS = 5_000L

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
        }

        fun start(context: Context, intervalMs: Long = DEFAULT_INTERVAL_MS) {
            val intent = Intent(context, ConnectionMonitorService::class.java)
                .putExtra(EXTRA_INTERVAL_MS, intervalMs)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectionMonitorService::class.java))
        }
    }
}
