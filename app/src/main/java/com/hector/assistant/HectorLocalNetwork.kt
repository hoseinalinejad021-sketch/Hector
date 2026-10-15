package com.hector.assistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Phase 1 of Hector's local-network roadmap.
 * Status and opt-in only: no scanning, sockets, pairing, sync, or remote commands.
 */
internal class HectorLocalNetwork(private val context: Context) {
    private val preferences = context.getSharedPreferences("hector_local_network", Context.MODE_PRIVATE)

    fun isOptedIn(): Boolean = preferences.getBoolean("opt_in", false)

    fun setOptedIn(enabled: Boolean): Boolean =
        preferences.edit().putBoolean("opt_in", enabled).commit()

    fun status(): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork
        val capabilities = network?.let { manager.getNetworkCapabilities(it) }
        val connection = when {
            capabilities == null -> "Offline / no active network"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi connected"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data connected"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet connected"
            else -> "Connected using another transport"
        }
        val mode = if (isOptedIn()) "Local-network features: opted in" else "Local-network features: disabled"
        return "$connection\n$mode\nDiscovery, pairing, sync, and remote relay are not implemented yet."
    }
}
