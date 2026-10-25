package com.snowballons.tukiwatch

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.snowballons.tukiwatch.liveness.DeviceConnectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * NetInfo replacement: true only when the OS positively reports no internet.
 *
 * Deliberately optimistic on an unknown state — a transient callback miss must
 * not blank the user's list. Mirrors StreamContext's `isConnected === false ||
 * isInternetReachable === false` (strict equality, so `null` is not offline).
 * Requires ACCESS_NETWORK_STATE.
 */
fun deviceOfflineFlow(context: Context): Flow<Boolean> = callbackFlow {
    val cm = context.getSystemService(ConnectivityManager::class.java)
        ?: return@callbackFlow close()

    fun probe(network: Network?): Boolean {
        val caps = network?.let { cm.getNetworkCapabilities(it) }
            ?: cm.getNetworkCapabilities(cm.activeNetwork)
            ?: return true
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    trySend(probe(null))
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = trySend(probe(network))

        override fun onLost(network: Network) = trySend(probe(network))

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            trySend(
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                    !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            )
    }
    val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()
    cm.registerNetworkCallback(request, callback)
    awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
}.conflate().distinctUntilChanged()

/** Starts offline=false, as StreamContext's `useState(false)` did. */
class AndroidDeviceConnectivity(context: Context) : DeviceConnectivity {
    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)

    override val isOffline = deviceOfflineFlow(context.applicationContext)
        .stateIn(scope, SharingStarted.Eagerly, false)
}
