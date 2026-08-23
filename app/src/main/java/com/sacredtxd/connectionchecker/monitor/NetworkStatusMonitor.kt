package com.sacredtxd.connectionchecker.monitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import com.sacredtxd.connectionchecker.data.NetworkStatus
import com.sacredtxd.connectionchecker.data.Transport
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Emits the current [NetworkStatus] whenever the platform's view of connectivity
 * changes. The flow is cold: the callback is registered on collection and unregistered
 * when collection stops.
 */
class NetworkStatusMonitor(private val context: Context) : ConnectivitySource {

    private val connectivityManager: ConnectivityManager?
        get() = context.getSystemService()

    override fun statusFlow(): Flow<NetworkStatus> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            trySend(NetworkStatus.Offline)
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentStatus())
            }

            override fun onLost(network: Network) {
                trySend(currentStatus())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                trySend(currentStatus())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        manager.registerNetworkCallback(request, callback)
        trySend(currentStatus())

        awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    override fun currentStatus(): NetworkStatus {
        val manager = connectivityManager ?: return NetworkStatus.Offline
        val network = manager.activeNetwork ?: return NetworkStatus.Offline
        val capabilities = manager.getNetworkCapabilities(network) ?: return NetworkStatus.Offline
        return capabilities.toNetworkStatus()
    }

    private fun NetworkCapabilities.toNetworkStatus(): NetworkStatus {
        val hasInternet = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return NetworkStatus(
            connected = hasInternet,
            validated = validated,
            transport = primaryTransport(),
            metered = !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        )
    }

    private fun NetworkCapabilities.primaryTransport(): Transport = when {
        // VPN wraps another transport, so it is checked first to avoid reporting the
        // underlying bearer as the one in use.
        hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> Transport.VPN
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.ETHERNET
        hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> Transport.BLUETOOTH
        else -> Transport.OTHER
    }
}
