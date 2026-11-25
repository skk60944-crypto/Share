package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ConnectionPhase
import com.example.data.model.TransferSessionState
import com.example.transfer.QrCodec
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.EmeraldPrimary

@Composable
fun ConnectQrScreen(
    sessionState: TransferSessionState,
    prefer5Ghz: Boolean,
    supports5Ghz: Boolean,
    onToggle5Ghz: (Boolean) -> Unit,
    onStartHost: (Boolean) -> Unit,
    onStopAndExit: () -> Unit,
    onMinimize: () -> Unit,
    onConnectedNavigateToProgress: () -> Unit
) {
    var showStopDialog by remember { mutableStateOf(false) }
    var show5GhzTooltip by remember { mutableStateOf(false) }

    // Start hosting hotspot + TCP socket immediately on launch
    LaunchedEffect(Unit) {
        if (sessionState.phase == ConnectionPhase.IDLE) {
            onStartHost(prefer5Ghz)
        }
    }

    // Automatically open TransferProgressScreen when a receiver connects
    LaunchedEffect(sessionState.phase) {
        if (sessionState.phase == ConnectionPhase.TRANSFERRING || sessionState.phase == ConnectionPhase.COMPLETED) {
            onConnectedNavigateToProgress()
        }
    }

    BackHandler {
        showStopDialog = true
    }

    val qrBitmap = remember(sessionState.qrPayload) {
        sessionState.qrPayload?.let { payload ->
            QrCodec.generateQrBitmap(QrCodec.encodePayload(payload), 560)
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "radarPulse")
    val ring1 by pulseTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "ring1"
    )
    val ring2 by pulseTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(2400, delayMillis = 800, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "ring2"
    )
    val ring3 by pulseTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(2400, delayMillis = 1600, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "ring3"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Green Top Bar
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
                    onClick = { showStopDialog = true },
                    modifier = Modifier.testTag("connect_qr_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.connect_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }
        }

        // Radar Pulse + Centered Crisp White QR Card
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Animated Emerald Radar Rings behind card
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxRadius = minOf(size.width, size.height) * 0.48f
                listOf(ring1, ring2, ring3).forEach { progress ->
                    val alpha = (1f - progress).coerceIn(0f, 0.5f)
                    drawCircle(
                        color = EmeraldBright.copy(alpha = alpha),
                        radius = maxRadius * progress,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 28.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    shadowElevation = 12.dp,
                    modifier = Modifier.width(280.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Sender Wi-Fi QR Code",
                                modifier = Modifier
                                    .size(224.dp)
                                    .testTag("sender_qr_image")
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(224.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = EmeraldPrimary)
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        val payload = sessionState.qrPayload
                        Text(
                            text = payload?.ssid?.ifBlank { "FlashShare Local LAN" } ?: "Starting hotspot...",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF121212),
                            fontWeight = FontWeight.Bold
                        )
                        if (payload != null) {
                            Text(
                                text = "${payload.hostIp}:${payload.port}" +
                                    if (payload.passphrase.isNotBlank()) " • Key: ${payload.passphrase}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4F5D56),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                Text(
                    text = stringResource(R.string.waiting_for_receiver),
                    style = MaterialTheme.typography.titleMedium,
                    color = EmeraldGlow,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Open FlashShare → RECEIVE on the other phone and scan this QR code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Bottom 5GHz High-Speed Mode Toggle Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = EmeraldBright)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.drawer_high_speed),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (supports5Ghz) stringResource(R.string.supports_5ghz)
                            else stringResource(R.string.only_24ghz),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { show5GhzTooltip = !show5GhzTooltip }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "5GHz Mode info",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefer5Ghz && supports5Ghz,
                        enabled = supports5Ghz,
                        onCheckedChange = { enabled ->
                            onToggle5Ghz(enabled)
                            onStartHost(enabled)
                        },
                        modifier = Modifier.testTag("connect_5ghz_switch"),
                        colors = SwitchDefaults.colors(checkedTrackColor = EmeraldBright)
                    )
                }

                if (show5GhzTooltip) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "5GHz Wi-Fi Direct/Hotspot boosts transfer speed up to 40+ MB/s with less interference than 2.4GHz. Both phones must support 5GHz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldBright
                    )
                }
            }
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text(stringResource(R.string.stop_sending_title)) },
            text = { Text(stringResource(R.string.stop_sending_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStopDialog = false
                        onStopAndExit()
                    },
                    modifier = Modifier.testTag("confirm_stop_sending_button")
                ) {
                    Text(stringResource(R.string.stop), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        showStopDialog = false
                        onMinimize()
                    }) {
                        Text(stringResource(R.string.minimize_banner), color = EmeraldBright)
                    }
                    TextButton(onClick = { showStopDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }
}
