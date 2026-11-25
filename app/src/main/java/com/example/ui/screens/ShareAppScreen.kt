package com.example.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.QrConnectionPayload
import com.example.data.model.WebServerState
import com.example.transfer.QrCodec
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainerDark
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.FormatUtils
import com.example.util.ShareAppHelper
import com.example.util.ShareLaunchResult
import kotlinx.coroutines.launch
import java.io.File

private val HotspotGold = Color(0xFF9E6200)
private val HotspotGoldActive = Color(0xFFC27A00)
private val WhatsAppGreen = Color(0xFF25D366)
private val MessagesBlue = Color(0xFF1A8CFF)

@Composable
fun ShareAppScreen(
    serverState: WebServerState,
    hotspotPayload: QrConnectionPayload?,
    isStartingHotspot: Boolean,
    onStartSelfApkPortal: () -> Unit,
    onStartHotspotApkPortal: () -> Unit,
    onStopAllAndBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var extractedApk by remember { mutableStateOf<File?>(null) }
    var isExtractingApk by remember { mutableStateOf(true) }
    var pendingBluetoothAfterEnable by remember { mutableStateOf(false) }
    var statusNotice by remember { mutableStateOf<String?>(null) }

    // Extract FlashShare.apk + start local HTTP APK server immediately so QR works on open
    LaunchedEffect(Unit) {
        onStartSelfApkPortal()
        extractedApk = ShareAppHelper.extractSelfApk(context)
        isExtractingApk = false
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (pendingBluetoothAfterEnable) {
            pendingBluetoothAfterEnable = false
            val apk = extractedApk
            if (apk != null && ShareAppHelper.isBluetoothEnabled(context)) {
                ShareAppHelper.shareFileViaBluetooth(context, apk)
            } else {
                statusNotice = "Turn on Bluetooth to send FlashShare.apk nearby."
            }
        }
    }

    val btConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val apk = extractedApk ?: return@rememberLauncherForActivityResult
            val result = ShareAppHelper.shareFileViaBluetooth(context, apk)
            if (result == ShareLaunchResult.NEEDS_BLUETOOTH_ENABLE) {
                pendingBluetoothAfterEnable = true
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        } else {
            // Even without BLUETOOTH_CONNECT, ACTION_SEND chooser still works
            extractedApk?.let { ShareAppHelper.shareFileViaBluetooth(context, it) }
        }
    }

    val hotspotPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onStartHotspotApkPortal()
    }

    fun triggerBluetoothShare() {
        scope.launch {
            val apk = extractedApk ?: ShareAppHelper.extractSelfApk(context).also { extractedApk = it }
            if (apk == null) {
                Toast.makeText(context, "Could not extract FlashShare.apk", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                btConnectPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                return@launch
            }
            val result = ShareAppHelper.shareFileViaBluetooth(context, apk)
            when (result) {
                ShareLaunchResult.NEEDS_BLUETOOTH_ENABLE -> {
                    pendingBluetoothAfterEnable = true
                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
                ShareLaunchResult.LAUNCHED_DIRECT -> {
                    statusNotice = "Select a nearby Bluetooth device to send FlashShare.apk"
                }
                ShareLaunchResult.LAUNCHED_CHOOSER_FALLBACK -> {
                    statusNotice = "Choose Bluetooth or Quick Share to send FlashShare.apk"
                }
                ShareLaunchResult.FAILED -> {
                    statusNotice = "Unable to launch Bluetooth share on this device"
                }
            }
        }
    }

    fun triggerHotspotShare() {
        val permsToRequest = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        if (permsToRequest.isNotEmpty()) {
            hotspotPermLauncher.launch(permsToRequest.toTypedArray())
        } else {
            onStartHotspotApkPortal()
        }
    }

    BackHandler { onStopAllAndBack() }

    val qrBitmap = remember(serverState.url) {
        if (serverState.url.isNotBlank()) QrCodec.generateQrBitmap(serverState.url, 520) else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Emerald Top App Bar (matches reference design)
        Surface(color = EmeraldPrimary) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onStopAllAndBack,
                    modifier = Modifier.testTag("share_app_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Share FlashShare to other phone",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            // Top QR Code Hero Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 20.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    shadowElevation = 8.dp
                ) {
                    Box(
                        modifier = Modifier.padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Scan QR code to download FlashShare",
                                modifier = Modifier
                                    .size(188.dp)
                                    .testTag("share_app_qr_image")
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(188.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = EmeraldPrimary)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Scan QR code to download FlashShare",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                val apkInfoText = when {
                    isExtractingApk -> "Preparing FlashShare.apk..."
                    extractedApk != null -> "FlashShare.apk • ${FormatUtils.formatSize(extractedApk!!.length())}"
                    else -> "FlashShare.apk ready"
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = apkInfoText,
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldBright
                )
            }

            // Section Divider: Invite (Nearby)
            ShareSectionBand(title = "Invite (Nearby)")

            // BLUETOOTH & HOT SPOT Action Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Emerald BLUETOOTH Button
                Button(
                    onClick = { triggerBluetoothShare() },
                    enabled = !isExtractingApk,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("share_app_bluetooth_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "BLUETOOTH",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Warm Gold HOT SPOT Button
                Button(
                    onClick = { triggerHotspotShare() },
                    enabled = !isStartingHotspot,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("share_app_hotspot_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hotspotPayload != null) HotspotGoldActive else HotspotGold
                    )
                ) {
                    if (isStartingHotspot) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.WifiTethering,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (hotspotPayload != null) "HOT SPOT ACTIVE" else "HOT SPOT",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Live Hotspot & Download URL Details Card
                AnimatedVisibility(visible = serverState.isRunning && serverState.url.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = EmeraldContainerDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldBright.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            if (hotspotPayload != null && hotspotPayload.ssid.isNotBlank()) {
                                Text(
                                    text = "Hotspot: ${hotspotPayload.ssid}" +
                                        if (hotspotPayload.passphrase.isNotBlank()) "  •  Key: ${hotspotPayload.passphrase}" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldGlow,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = serverState.url,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Downloads: ${serverState.downloadedCount}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldGlow
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("FlashShare APK URL", serverState.url))
                                        Toast.makeText(context, "Download link copied", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy link", tint = EmeraldGlow)
                                }
                                if (hotspotPayload != null) {
                                    IconButton(onClick = onStopAllAndBack) {
                                        Icon(Icons.Default.StopCircle, contentDescription = "Stop hotspot", tint = Color(0xFFFF7043))
                                    }
                                }
                            }
                        }
                    }
                }

                if (statusNotice != null) {
                    Text(
                        text = statusNotice!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldBright,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Section Divider: Invite (Remote) — ONLY real working options, ZERO fake links!
            ShareSectionBand(title = "Invite (Remote)")

            RemoteShareRow(
                icon = Icons.AutoMirrored.Filled.Chat,
                badgeColor = WhatsAppGreen,
                title = "Share via WhatsApp",
                subtitle = "Send FlashShare.apk directly to a WhatsApp chat",
                testTag = "share_app_whatsapp_row",
                onClick = {
                    scope.launch {
                        val apk = extractedApk ?: ShareAppHelper.extractSelfApk(context).also { extractedApk = it }
                        if (apk != null) {
                            ShareAppHelper.shareApkViaWhatsApp(
                                context = context,
                                file = apk,
                                localDownloadUrl = serverState.url.takeIf { it.isNotBlank() }
                            )
                        }
                    }
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            RemoteShareRow(
                icon = Icons.Default.Sms,
                badgeColor = MessagesBlue,
                title = "Share via Messages",
                subtitle = "Send invite & offline link via SMS / Messages",
                testTag = "share_app_messages_row",
                onClick = {
                    ShareAppHelper.shareInviteViaMessages(
                        context = context,
                        localDownloadUrl = serverState.url.takeIf { it.isNotBlank() },
                        apkFile = extractedApk
                    )
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            RemoteShareRow(
                icon = Icons.Default.Share,
                badgeColor = EmeraldPrimary,
                title = "Share APK via Other Apps",
                subtitle = "Quick Share, Telegram, Drive, Email & more",
                testTag = "share_app_system_chooser_row",
                onClick = {
                    scope.launch {
                        val apk = extractedApk ?: ShareAppHelper.extractSelfApk(context).also { extractedApk = it }
                        if (apk != null) {
                            ShareAppHelper.shareFileViaSystemChooser(
                                context = context,
                                file = apk,
                                chooserTitle = "Share FlashShare.apk",
                                extraText = "Install FlashShare — 100% offline, ad-free file sharing."
                            )
                        }
                    }
                }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ShareSectionBand(title: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun RemoteShareRow(
    icon: ImageVector,
    badgeColor: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(badgeColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
