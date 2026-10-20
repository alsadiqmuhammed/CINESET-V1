package com.madar.launcher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * A place is a Wi-Fi network: "Office" → focus, "Home" after 21:00 → sanctuary.
 * [from] == [to] means all day.
 */
@Serializable
data class Place(val ssid: String, val mode: Mode, val from: Int = 0, val to: Int = 0) {
    fun activeAt(minute: Int): Boolean = from == to || if (from < to) minute in from until to else minute >= from || minute < to
}

/**
 * Tracks the connected Wi-Fi name through a network callback: woken only when the network changes,
 * no GPS and no polling. Android hides SSIDs from apps without location permission (and with
 * location turned off), so [ssid] stays null until both are on.
 */
class PlacesRepository(private val context: Context) {

    private val _ssid = MutableStateFlow<String?>(null)
    val ssid: StateFlow<String?> = _ssid.asStateFlow()

    private var registered = false

    val hasPermission: Boolean
        get() = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (registered || !hasPermission) return
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val info = caps.transportInfo as? WifiInfo ?: return
                _ssid.value = info.ssid?.trim('"')?.takeUnless { it.isBlank() || it == WifiManagerUnknown }
            }

            override fun onLost(network: Network) {
                _ssid.value = null
            }
        }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        runCatching { cm.registerNetworkCallback(request, callback) }.onSuccess { registered = true }
    }

    private companion object {
        const val WifiManagerUnknown = "<unknown ssid>"
    }
}
