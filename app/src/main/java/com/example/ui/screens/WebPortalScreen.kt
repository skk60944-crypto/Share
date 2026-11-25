package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.WebPortalMode
import com.example.data.model.WebServerState
import com.example.transfer.QrCodec
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainerDark
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun WebPortalScreen(
    mode: WebPortalMode,
    serverState: WebServerState,
    sharedItemCount: Int,
    onStartServer: () -> Unit,
    onStopServerAndBack: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(mode) {
        onStartServer()
    }

    DisposableEffect(Unit) {
        onDispose { }
    }

    BackHandler {
        onStopServerAndBack()
    }

    val title = when (mode) {
        WebPortalMode.PC_DASHBOARD -> "Connect to PC"
        WebPortalMode.KAIOS_BROWSER -> "Connect to KaiOS / Browser"
        WebPortalMode.SHARE_SELF_APK -> "Share This App"
    }
    val subtitle = when (mode) {
        WebPortalMode.PC_DASHBOARD -> "Open the URL below in Chrome, Edge, Firefox, or Safari on any PC on the same Wi-Fi or Hotspot."
        WebPortalMode.KAIOS_BROWSER -> "Zero-JavaScript lightweight portal optimized for KaiOS, JioPhone, and feature phone browsers."
        WebPortalMode.SHARE_SELF_APK -> "Scan the QR code or open the link on any nearby device to download FlashShare.apk offline."
    }
    val icon = when (mode) {
        WebPortalMode.PC_DASHBOARD -> Icons.Default.Computer
        WebPortalMode.KAIOS_BROWSER -> Icons.Default.Public
        WebPortalMode.SHARE_SELF_APK -> Icons.Default.Share
    }

    val qrBitmap = remember(serverState.url) {
        if (serverState.url.isNotBlank()) QrCodec.generateQrBitmap(serverState.url, 520) else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(color = EmeraldPrimary) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onStopServerAndBack, modifier = Modifier.testTag("web_portal_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            // White QR Code Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 10.dp
            ) {
                Box(
                    modifier = Modifier.padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Local Web Portal QR Code",
                            modifier = Modifier
                                .size(220.dp)
                                .testTag("web_portal_qr_image")
                        )
                    } else {
                        Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = EmeraldPrimary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // URL Pill with Copy Action
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = EmeraldContainerDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldBright.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LOCAL ADDRESS (NO INTERNET)",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldGlow
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = serverState.url.ifBlank { "Starting local HTTP server..." },
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = {
                            if (serverState.url.isNotBlank()) {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("FlashShare URL", serverState.url))
                                Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("copy_web_url_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", tint = EmeraldGlow)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Live Portal Counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WebStatTile(
                    modifier = Modifier.weight(1f),
                    label = "Shared",
                    value = if (mode == WebPortalMode.SHARE_SELF_APK) "1 APK" else "$sharedItemCount"
                )
                WebStatTile(
                    modifier = Modifier.weight(1f),
                    label = "Uploaded",
                    value = "${serverState.uploadedCount}"
                )
                WebStatTile(
                    modifier = Modifier.weight(1f),
                    label = "Downloads",
                    value = "${serverState.downloadedCount}"
                )
            }

            if (serverState.errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = serverState.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = CoralError
                )
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = onStopServerAndBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("stop_web_portal_button"),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CoralError)
                ) {
                    Text("Stop Local Server", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WebStatTile(
    modifier: Modifier,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = EmeraldBright, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
