package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FlashShareApp
import com.example.data.model.ConnectionPhase
import com.example.data.model.QrConnectionPayload
import com.example.data.model.TransferItem
import com.example.data.model.TransferSessionState
import com.example.data.model.WebPortalMode
import com.example.data.model.WebServerState
import com.example.service.TransferForegroundService
import com.example.transfer.LocalWebServer
import com.example.transfer.SocketTransferEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HardwareChecklistState(
    val wifiEnabled: Boolean = false,
    val bluetoothOff: Boolean = true,
    val locationPermissionGranted: Boolean = false,
    val nearbyWifiPermissionGranted: Boolean = false,
    val supports5Ghz: Boolean = false
) {
    val canProceed: Boolean
        get() = wifiEnabled && locationPermissionGranted && nearbyWifiPermissionGranted
}

class TransferViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FlashShareApp

    val sessionState: StateFlow<TransferSessionState> = app.socketTransferEngine.sessionState
    val webServerState: StateFlow<WebServerState> = app.localWebServer.state

    private val _checklist = MutableStateFlow(HardwareChecklistState())
    val checklist: StateFlow<HardwareChecklistState> = _checklist.asStateFlow()

    private val _queuedItems = MutableStateFlow<List<TransferItem>>(emptyList())
    val queuedItems: StateFlow<List<TransferItem>> = _queuedItems.asStateFlow()

    private val _shareAppHotspotPayload = MutableStateFlow<QrConnectionPayload?>(null)
    val shareAppHotspotPayload: StateFlow<QrConnectionPayload?> = _shareAppHotspotPayload.asStateFlow()

    private val _isStartingShareHotspot = MutableStateFlow(false)
    val isStartingShareHotspot: StateFlow<Boolean> = _isStartingShareHotspot.asStateFlow()

    init {
        refreshHardwareChecklist()
    }

    fun refreshHardwareChecklist() {
        _checklist.value = HardwareChecklistState(
            wifiEnabled = app.wifiDirectManager.isWifiEnabled(),
            bluetoothOff = app.wifiDirectManager.isBluetoothOff(),
            locationPermissionGranted = app.wifiDirectManager.hasLocationPermission(),
            nearbyWifiPermissionGranted = app.wifiDirectManager.hasNearbyWifiPermission(),
            supports5Ghz = app.wifiDirectManager.is5GhzSupported()
        )
    }

    fun stageItemsForSend(items: List<TransferItem>) {
        _queuedItems.value = items
    }

    fun startSenderHost(prefer5Ghz: Boolean) {
        viewModelScope.launch {
            val items = _queuedItems.value
            val deviceName = app.wifiDirectManager.getDeviceName()
            TransferForegroundService.updateNotification(
                context = app,
                title = "FlashShare Hotspot Ready",
                detail = "Waiting for receiver to scan QR code",
                progressPct = -1
            )

            val payload = app.wifiDirectManager.startSenderHost(
                port = SocketTransferEngine.DEFAULT_PORT,
                prefer5Ghz = prefer5Ghz
            )

            app.socketTransferEngine.startSenderServer(
                items = items,
                deviceName = deviceName,
                qrPayload = payload,
                onProgressNotification = { title, detail, pct ->
                    TransferForegroundService.updateNotification(app, title, detail, pct)
                },
                onFinished = {
                    TransferForegroundService.stopService(app)
                }
            )
        }
    }

    fun connectAndReceive(payload: QrConnectionPayload) {
        viewModelScope.launch {
            val prefs = app.preferencesStore.preferencesFlow.first()
            val outDir = app.preferencesStore.resolveWritableDownloadDir(prefs.downloadDirectoryPath)
            val deviceName = app.wifiDirectManager.getDeviceName()

            TransferForegroundService.updateNotification(
                context = app,
                title = "Connecting to ${payload.deviceName}",
                detail = "Joining offline Wi-Fi channel...",
                progressPct = -1
            )

            val joined = app.wifiDirectManager.connectToSenderNetwork(payload)
            if (!joined) {
                TransferForegroundService.stopService(app)
            }

            app.socketTransferEngine.startReceiverClient(
                payload = payload,
                deviceName = deviceName,
                outputDir = outDir,
                onProgressNotification = { title, detail, pct ->
                    TransferForegroundService.updateNotification(app, title, detail, pct)
                },
                onFinished = {
                    app.wifiDirectManager.disconnectFromSenderNetwork()
                    TransferForegroundService.stopService(app)
                }
            )
        }
    }

    fun addMoreFilesToActiveTransfer(items: List<TransferItem>) {
        app.socketTransferEngine.addMoreFilesToQueue(items)
    }

    fun minimizeTransfer() {
        app.socketTransferEngine.setMinimized(true)
    }

    fun restoreTransfer() {
        app.socketTransferEngine.setMinimized(false)
    }

    fun stopTransferSession() {
        app.socketTransferEngine.cancelTransfer()
        app.wifiDirectManager.stopAll()
        TransferForegroundService.stopService(app)
    }

    fun startWebPortal(mode: WebPortalMode, items: List<TransferItem>) {
        viewModelScope.launch {
            val prefs = app.preferencesStore.preferencesFlow.first()
            val downloadDir = app.preferencesStore.resolveWritableDownloadDir(prefs.downloadDirectoryPath)
            val ip = app.wifiDirectManager.getLocalIpv4Address() ?: "192.168.43.1"

            app.localWebServer.start(
                mode = mode,
                ipAddress = ip,
                items = items,
                downloadDir = downloadDir,
                port = LocalWebServer.DEFAULT_HTTP_PORT
            )

            val modeLabel = when (mode) {
                WebPortalMode.PC_DASHBOARD -> "PC Web Portal"
                WebPortalMode.KAIOS_BROWSER -> "KaiOS / Browser Portal"
                WebPortalMode.SHARE_SELF_APK -> "Share FlashShare APK"
            }
            TransferForegroundService.updateNotification(
                context = app,
                title = modeLabel,
                detail = "Serving offline at http://$ip:${LocalWebServer.DEFAULT_HTTP_PORT}",
                progressPct = -1
            )
        }
    }

    fun stopWebPortal() {
        app.localWebServer.stop()
        if (sessionState.value.phase == ConnectionPhase.IDLE) {
            TransferForegroundService.stopService(app)
        }
    }

    fun startShareAppHotspot(prefer5Ghz: Boolean) {
        viewModelScope.launch {
            _isStartingShareHotspot.value = true
            try {
                val prefs = app.preferencesStore.preferencesFlow.first()
                val downloadDir = app.preferencesStore.resolveWritableDownloadDir(prefs.downloadDirectoryPath)
                val payload = app.wifiDirectManager.startSenderHost(
                    port = LocalWebServer.DEFAULT_HTTP_PORT,
                    prefer5Ghz = prefer5Ghz
                )
                _shareAppHotspotPayload.value = payload
                app.localWebServer.start(
                    mode = WebPortalMode.SHARE_SELF_APK,
                    ipAddress = payload.hostIp,
                    items = emptyList(),
                    downloadDir = downloadDir,
                    port = LocalWebServer.DEFAULT_HTTP_PORT
                )
                TransferForegroundService.updateNotification(
                    context = app,
                    title = "FlashShare Hotspot Active",
                    detail = "SSID: ${payload.ssid} • http://${payload.hostIp}:${LocalWebServer.DEFAULT_HTTP_PORT}/apk",
                    progressPct = -1
                )
            } finally {
                _isStartingShareHotspot.value = false
            }
        }
    }

    fun stopShareAppHotspot() {
        _shareAppHotspotPayload.value = null
        _isStartingShareHotspot.value = false
        app.localWebServer.stop()
        app.wifiDirectManager.stopAll()
        if (sessionState.value.phase == ConnectionPhase.IDLE) {
            TransferForegroundService.stopService(app)
        }
    }
}
