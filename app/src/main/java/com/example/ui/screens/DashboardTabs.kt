package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.TransferHistoryEntity
import com.example.data.model.FileEntry
import com.example.data.model.InstalledAppInfo
import com.example.data.model.TransferItem
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainerDark
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.EmeraldPrimary
import com.example.util.FileOpenUtils
import com.example.util.FormatUtils

@Composable
fun HistoryTabContent(
    received: List<TransferHistoryEntity>,
    sent: List<TransferHistoryEntity>,
    searchQuery: String,
    onDeleteRecord: (Long) -> Unit,
    onClearDirection: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedSegment by remember { mutableIntStateOf(0) } // 0 = Received, 1 = Sent
    val rawList = if (selectedSegment == 0) received else sent
    val filtered = remember(rawList, searchQuery) {
        if (searchQuery.isBlank()) rawList
        else rawList.filter { it.fileName.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Segmented toggle row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    SegmentButton(
                        title = "${stringResource(R.string.received)} (${received.size})",
                        selected = selectedSegment == 0,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("history_received_tab"),
                        onClick = { selectedSegment = 0 }
                    )
                    SegmentButton(
                        title = "${stringResource(R.string.sent)} (${sent.size})",
                        selected = selectedSegment == 1,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("history_sent_tab"),
                        onClick = { selectedSegment = 1 }
                    )
                }
            }
            if (filtered.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { onClearDirection(if (selectedSegment == 0) "RECEIVED" else "SENT") }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (filtered.isEmpty()) {
            EmptyTabPlaceholder(
                icon = Icons.Default.History,
                title = if (selectedSegment == 0) "No received files yet" else "No sent files yet",
                subtitle = "Tap SEND or RECEIVE below to transfer files offline at full Wi-Fi speed."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { entry ->
                    val isApk = entry.fileName.endsWith(".apk", ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(EmeraldContainerDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isApk) Icons.Default.Android else Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = EmeraldGlow
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.fileName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${FormatUtils.formatSize(entry.fileSizeBytes)} • ${FormatUtils.formatSpeed(entry.speedBytesPerSec)} • ${entry.peerName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldBright
                                )
                                Text(
                                    text = FormatUtils.formatTimestamp(entry.timestampMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = { FileOpenUtils.openOrInstallFile(context, entry.filePath) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(if (isApk) "Install" else "Open", style = MaterialTheme.typography.labelLarge)
                            }
                            IconButton(onClick = { onDeleteRecord(entry.id) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Remove history item",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentButton(
    title: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) EmeraldPrimary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DownloadTabContent(
    downloads: List<FileEntry>,
    selectedIds: Set<String>,
    searchQuery: String,
    onToggleSelect: (TransferItem) -> Unit
) {
    val context = LocalContext.current
    val filtered = remember(downloads, searchQuery) {
        if (searchQuery.isBlank()) downloads
        else downloads.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    if (filtered.isEmpty()) {
        EmptyTabPlaceholder(
            icon = Icons.Default.Download,
            title = "Downloads folder is empty",
            subtitle = "Files received over FlashShare or PC/KaiOS Web Portal appear here instantly."
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filtered, key = { it.path }) { entry ->
            val transferItem = entry.toTransferItem()
            val isSelected = transferItem.id in selectedIds
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) EmeraldContainerDark else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleSelect(transferItem) }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect(transferItem) },
                        colors = CheckboxDefaults.colors(checkedColor = EmeraldBright)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${entry.category.label} • ${FormatUtils.formatSize(entry.sizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { FileOpenUtils.openOrInstallFile(context, entry.path) }) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open file",
                            tint = EmeraldBright
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppTabContent(
    localApps: List<InstalledAppInfo>,
    systemApps: List<InstalledAppInfo>,
    isLoading: Boolean,
    selectedIds: Set<String>,
    searchQuery: String,
    getIcon: (String) -> Bitmap?,
    onToggleSelect: (TransferItem) -> Unit
) {
    var localExpanded by remember { mutableStateOf(true) }
    var systemExpanded by remember { mutableStateOf(false) }

    val filteredLocal = remember(localApps, searchQuery) {
        if (searchQuery.isBlank()) localApps
        else localApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }
    val filteredSystem = remember(systemApps, searchQuery) {
        if (searchQuery.isBlank()) systemApps
        else systemApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    if (isLoading && localApps.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EmeraldBright)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "header_local") {
            CollapsibleSectionHeader(
                title = "${stringResource(R.string.local_apps)} (${filteredLocal.size})",
                expanded = localExpanded,
                onClick = { localExpanded = !localExpanded }
            )
        }
        if (localExpanded) {
            val rows = filteredLocal.chunked(4)
            items(rows, key = { "local_row_${it.first().packageName}" }) { rowApps ->
                AppGridRow(
                    apps = rowApps,
                    selectedIds = selectedIds,
                    getIcon = getIcon,
                    onToggleSelect = onToggleSelect
                )
            }
        }

        item(key = "header_system") {
            Spacer(Modifier.height(4.dp))
            CollapsibleSectionHeader(
                title = "${stringResource(R.string.system_apps)} (${filteredSystem.size})",
                expanded = systemExpanded,
                onClick = { systemExpanded = !systemExpanded }
            )
        }
        if (systemExpanded) {
            val rows = filteredSystem.chunked(4)
            items(rows, key = { "sys_row_${it.first().packageName}" }) { rowApps ->
                AppGridRow(
                    apps = rowApps,
                    selectedIds = selectedIds,
                    getIcon = getIcon,
                    onToggleSelect = onToggleSelect
                )
            }
        }
    }
}

@Composable
private fun CollapsibleSectionHeader(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = EmeraldBright
            )
        }
    }
}

@Composable
private fun AppGridRow(
    apps: List<InstalledAppInfo>,
    selectedIds: Set<String>,
    getIcon: (String) -> Bitmap?,
    onToggleSelect: (TransferItem) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (app in apps) {
            val transferItem = app.toTransferItem()
            val selected = transferItem.id in selectedIds
            val iconBitmap = remember(app.packageName) { getIcon(app.packageName) }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        width = if (selected) 2.dp else 0.dp,
                        color = if (selected) EmeraldBright else Color.Transparent,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onToggleSelect(transferItem) },
                color = if (selected) EmeraldContainerDark else MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap.asImageBitmap(),
                                contentDescription = app.appName,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = app.appName,
                                tint = EmeraldBright,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = EmeraldBright,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(20.dp)
                                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                            )
                        }
                        if (app.isSplitApk) {
                            Surface(
                                color = EmeraldPrimary,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.align(Alignment.BottomCenter)
                            ) {
                                Text(
                                    text = "SPLIT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = FormatUtils.formatSize(app.totalSizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
        repeat(4 - apps.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun EmptyTabPlaceholder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(EmeraldContainerDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = EmeraldBright, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
