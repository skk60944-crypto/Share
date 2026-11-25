package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ConnectionPhase
import com.example.data.model.FileTransferStatus
import com.example.data.model.TransferDirection
import com.example.data.model.TransferSessionState
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainerDark
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.EmeraldPrimary
import com.example.util.FileOpenUtils
import com.example.util.FormatUtils

@Composable
fun TransferProgressScreen(
    sessionState: TransferSessionState,
    onAddMoreFiles: () -> Unit,
    onMinimize: () -> Unit,
    onCancelOrFinish: () -> Unit
) {
    val context = LocalContext.current
    val isFinished = sessionState.phase == ConnectionPhase.COMPLETED ||
        sessionState.phase == ConnectionPhase.ERROR ||
        sessionState.phase == ConnectionPhase.IDLE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
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
                    onClick = { if (isFinished) onCancelOrFinish() else onMinimize() },
                    modifier = Modifier.testTag("progress_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (sessionState.direction == TransferDirection.SENT) "Sending Files" else "Receiving Files",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = sessionState.peerName.ifBlank { sessionState.statusMessage },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Telemetry Dashboard Card (Live MB/s, ETA, Overall Progress)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = EmeraldContainerDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldBright.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = EmeraldGlow, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("LIVE SPEED", style = MaterialTheme.typography.labelSmall, color = EmeraldGlow)
                        }
                        Text(
                            text = FormatUtils.formatSpeed(sessionState.speedBytesPerSec),
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "ETA ${FormatUtils.formatEta(sessionState.etaSeconds)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "${sessionState.completedCount}/${sessionState.files.size} files",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldGlow
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { sessionState.overallProgress },
                    color = EmeraldGlow,
                    trackColor = Color.White.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${FormatUtils.formatSize(sessionState.transferredBytes)} / ${FormatUtils.formatSize(sessionState.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Text(
                        text = "${(sessionState.overallProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldGlow,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (sessionState.errorMessage != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = sessionState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = CoralError
                    )
                } else if (sessionState.statusMessage.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = sessionState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
            }
        }

        // Per-file Progress List
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(sessionState.files, key = { it.itemId }) { item ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.fileName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${FormatUtils.formatSize(item.transferredBytes)} / ${FormatUtils.formatSize(item.totalBytes)}" +
                                        if (item.resumedFromBytes > 0L) " • Resumed" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            when (item.status) {
                                FileTransferStatus.COMPLETED -> {
                                    if (item.savedPath != null) {
                                        IconButton(onClick = { FileOpenUtils.openOrInstallFile(context, item.savedPath) }) {
                                            Icon(Icons.Default.OpenInNew, contentDescription = "Open received file", tint = EmeraldBright)
                                        }
                                    }
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = EmeraldBright)
                                }
                                FileTransferStatus.FAILED -> {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = "Failed", tint = CoralError)
                                }
                                else -> {
                                    Text(
                                        text = "${(item.progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldBright
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { item.progress },
                            color = if (item.status == FileTransferStatus.FAILED) CoralError else EmeraldBright,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )
                    }
                }
            }
        }

        // Bottom Controls: Add More Files + Cancel/Done
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (sessionState.direction == TransferDirection.SENT && !isFinished) {
                    OutlinedButton(
                        onClick = onAddMoreFiles,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("add_more_files_button"),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = EmeraldBright)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Files", color = EmeraldBright)
                    }
                }
                Button(
                    onClick = onCancelOrFinish,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("finish_or_cancel_transfer_button"),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFinished) EmeraldBright else CoralError
                    )
                ) {
                    Text(
                        text = if (isFinished) "Done" else "Cancel Transfer",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
