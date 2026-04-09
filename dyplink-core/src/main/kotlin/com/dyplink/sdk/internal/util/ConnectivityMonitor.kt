package com.dyplink.sdk.internal.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Observes device network connectivity via [ConnectivityManager]
 * and notifies registered listeners whenever the network becomes
 * available.
 *
 * Uses [ConnectivityManager.NetworkCallback] (API 21+).
 */
internal class ConnectivityMonitor {

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * `true` when the device currently has an active network
     * connection with internet capability.
     */
    @Volatile
    var isConnected: Boolean = false
        private set

    /**
     * Register a callback that fires whenever the network
     * transitions to an *available* state.
     */
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    /**
     * Remove a previously registered listener.
     */
    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Begin monitoring connectivity changes.  Must be called with
     * an application [Context].
     */
    fun start(context: Context) {
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        connectivityManager = cm

        // Seed the initial state.
        isConnected = currentlyConnected(cm)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isConnected = true
                notifyListeners()
            }

            override fun onLost(network: Network) {
                isConnected = currentlyConnected(cm)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                val hasInternet =
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                if (hasInternet && !isConnected) {
                    isConnected = true
                    notifyListeners()
                } else if (!hasInternet) {
                    isConnected = false
                }
            }
        }
        networkCallback = callback

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, callback)
    }

    /**
     * Stop monitoring and release resources.
     */
    fun stop() {
        networkCallback?.let { callback ->
            try {
                connectivityManager?.unregisterNetworkCallback(callback)
            } catch (_: IllegalArgumentException) {
                // Already unregistered; ignore.
            }
        }
        networkCallback = null
        connectivityManager = null
        listeners.clear()
    }

    // ── Helpers ─────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun currentlyConnected(cm: ConnectivityManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    private fun notifyListeners() {
        for (listener in listeners) {
            listener()
        }
    }
}
