package com.example.transfer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.data.model.QrConnectionPayload
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.coroutines.resume

class WiFiDirectManager(private val context: Context) {

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val p2pManager: WifiP2pManager? =
        context.applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var p2pChannel: WifiP2pManager.Channel? = null

    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null

    fun isWifiEnabled(): Boolean = try {
        wifiManager.isWifiEnabled
    } catch (_: Exception) {
        false
    }

    fun is5GhzSupported(): Boolean = try {
        wifiManager.is5GHzBandSupported
    } catch (_: Exception) {
        false
    }

    fun isBluetoothOff(): Boolean {
        return try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter: BluetoothAdapter? = btManager?.adapter
            adapter == null || !adapter.isEnabled
        } catch (_: SecurityException) {
            true
        } catch (_: Exception) {
            true
        }
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNearbyWifiPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun getDeviceName(): String {
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"
        val manufacturer = Build.MANUFACTURER?.takeIf { it.isNotBlank() } ?: ""
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model".trim()
    }

    /**
     * Starts a LocalOnlyHotspot (preferred for high-speed QR pairing) or falls back to
     * Wi-Fi Direct Group creation / current LAN Wi-Fi address when restricted by OEM/emulator.
     */
    @SuppressLint("MissingPermission")
    suspend fun startSenderHost(
        port: Int,
        prefer5Ghz: Boolean
    ): QrConnectionPayload {
        // 1. Attempt Android 8+ LocalOnlyHotspot
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && (hasLocationPermission() || hasNearbyWifiPermission())) {
            val hotspotPayload = tryStartLocalOnlyHotspot(port, prefer5Ghz)
            if (hotspotPayload != null) return hotspotPayload
        }

        // 2. Attempt Wi-Fi Direct Group (WifiP2pManager)
        val p2pPayload = tryStartWifiP2pGroup(port, prefer5Ghz)
        if (p2pPayload != null) return p2pPayload

        // 3. Fallback to active local Wi-Fi LAN IP (works when both phones share a router/hotspot)
        val lanIp = getLocalIpv4Address() ?: "192.168.43.1"
        return QrConnectionPayload(
            ssid = "FlashShare-LAN",
            passphrase = "",
            hostIp = lanIp,
            port = port,
            deviceName = getDeviceName(),
            mode = "LAN",
            is5Ghz = prefer5Ghz && is5GhzSupported()
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun tryStartLocalOnlyHotspot(
        port: Int,
        prefer5Ghz: Boolean
    ): QrConnectionPayload? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        try {
            stopHotspotOnly()
            wifiManager.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                        hotspotReservation = reservation
                        var ssid = "FlashShare-${(1000..9999).random()}"
                        var pass = ""
                        if (reservation != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val softAp = reservation.softApConfiguration
                                ssid = softAp.wifiSsid?.toString()?.trim('"') ?: ssid
                                pass = softAp.passphrase ?: ""
                            } else {
                                @Suppress("DEPRECATION")
                                val cfg = reservation.wifiConfiguration
                                ssid = cfg?.SSID?.trim('"') ?: ssid
                                pass = cfg?.preSharedKey ?: ""
                            }
                        }
                        val hostIp = getHotspotOrLanIpv4Address()
                        if (cont.isActive) {
                            cont.resume(
                                QrConnectionPayload(
                                    ssid = ssid,
                                    passphrase = pass,
                                    hostIp = hostIp,
                                    port = port,
                                    deviceName = getDeviceName(),
                                    mode = "HOTSPOT",
                                    is5Ghz = prefer5Ghz && is5GhzSupported()
                                )
                            )
                        }
                    }

                    override fun onFailed(reason: Int) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onStopped() {
                        hotspotReservation = null
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (_: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun tryStartWifiP2pGroup(
        port: Int,
        prefer5Ghz: Boolean
    ): QrConnectionPayload? = suspendCancellableCoroutine { cont ->
        val manager = p2pManager
        if (manager == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        try {
            val channel = p2pChannel ?: manager.initialize(context, Looper.getMainLooper(), null)
                .also { p2pChannel = it }

            val actionListener = object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    manager.requestGroupInfo(channel) { group: WifiP2pGroup? ->
                        if (group != null && cont.isActive) {
                            val ip = getHotspotOrLanIpv4Address("192.168.49.1")
                            cont.resume(
                                QrConnectionPayload(
                                    ssid = group.networkName ?: "DIRECT-FlashShare",
                                    passphrase = group.passphrase ?: "",
                                    hostIp = ip,
                                    port = port,
                                    deviceName = getDeviceName(),
                                    mode = "P2P",
                                    is5Ghz = prefer5Ghz && is5GhzSupported()
                                )
                            )
                        } else if (cont.isActive) {
                            cont.resume(null)
                        }
                    }
                }

                override fun onFailure(reason: Int) {
                    if (cont.isActive) cont.resume(null)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val band = if (prefer5Ghz && is5GhzSupported()) {
                    WifiP2pConfig.GROUP_OWNER_BAND_5GHZ
                } else {
                    WifiP2pConfig.GROUP_OWNER_BAND_2GHZ
                }
                val suffix = (1000..9999).random()
                val config = WifiP2pConfig.Builder()
                    .setNetworkName("DIRECT-FS-$suffix")
                    .setPassphrase("FS${(100000..999999).random()}")
                    .setGroupOperatingBand(band)
                    .build()
                manager.createGroup(channel, config, actionListener)
            } else {
                manager.createGroup(channel, actionListener)
            }
        } catch (_: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }

    /**
     * Receiver joins sender's Wi-Fi Hotspot / Wi-Fi Direct network on Android 10+
     * via WifiNetworkSpecifier and binds the process network for direct TCP streaming.
     */
    suspend fun connectToSenderNetwork(payload: QrConnectionPayload): Boolean {
        if (payload.mode == "LAN" || payload.ssid.isBlank() || payload.passphrase.isBlank()) {
            return true
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true
        }
        return suspendCancellableCoroutine { cont ->
            try {
                disconnectFromSenderNetwork()
                val specifier = WifiNetworkSpecifier.Builder()
                    .setSsid(payload.ssid)
                    .setWpa2Passphrase(payload.passphrase)
                    .build()

                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(specifier)
                    .build()

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        connectivityManager.bindProcessToNetwork(network)
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onUnavailable() {
                        if (cont.isActive) cont.resume(false)
                    }
                }
                activeNetworkCallback = callback
                connectivityManager.requestNetwork(request, callback, 20_000)

                cont.invokeOnCancellation {
                    disconnectFromSenderNetwork()
                }
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    fun disconnectFromSenderNetwork() {
        try {
            connectivityManager.bindProcessToNetwork(null)
            activeNetworkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        } catch (_: Exception) {
        }
        activeNetworkCallback = null
    }

    fun stopAll() {
        stopHotspotOnly()
        try {
            val manager = p2pManager
            val channel = p2pChannel
            if (manager != null && channel != null) {
                manager.removeGroup(channel, null)
            }
        } catch (_: Exception) {
        }
        disconnectFromSenderNetwork()
    }

    private fun stopHotspotOnly() {
        try {
            hotspotReservation?.close()
        } catch (_: Exception) {
        }
        hotspotReservation = null
    }

    fun getLocalIpv4Address(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            var fallback: String? = null
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val name = intf.name.lowercase()
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (name.contains("wlan") || name.contains("ap") || name.contains("p2p")) {
                            return ip
                        }
                        if (fallback == null) fallback = ip
                    }
                }
            }
            fallback
        } catch (_: Exception) {
            null
        }
    }

    private fun getHotspotOrLanIpv4Address(defaultIp: String = "192.168.43.1"): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                for (intf in interfaces) {
                    if (!intf.isUp || intf.isLoopback) continue
                    val name = intf.name.lowercase()
                    if (name.contains("ap") || name.contains("p2p") || name.contains("wlan")) {
                        for (addr in intf.inetAddresses) {
                            if (addr is Inet4Address && !addr.isLoopbackAddress) {
                                addr.hostAddress?.let { return it }
                            }
                        }
                    }
                }
            }
            getLocalIpv4Address() ?: defaultIp
        } catch (_: Exception) {
            defaultIp
        }
    }
}
