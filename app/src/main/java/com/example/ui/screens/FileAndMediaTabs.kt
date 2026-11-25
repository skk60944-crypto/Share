package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.FileEntry
import com.example.data.model.MediaItem
import com.example.data.model.SmartBucketSummary
import com.example.data.model.SmartBucketType
import com.example.data.model.StorageInfo
import com.example.data.model.TransferItem
import com.example.transfer.AudioPlayerState
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainerDark
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.VioletAccent
import com.example.util.FormatUtils
import com.example.viewmodel.ExtractionUiState

@Composable
fun PhotoTabContent(
    photos: List<MediaItem>,
    selectedFolder: String,
    selectedIds: Set<String>,
    searchQuery: String,
    onSelectFolder: (String) -> Unit,
    onToggleSelect: (TransferItem) -> Unit
) {
    val folders = remember(photos) {
        listOf("All") + photos.map { it.bucketName }.distinct().sorted()
    }
    val filtered = remember(photos, selectedFolder, searchQuery) {
        photos.filter { item ->
            (selectedFolder == "All" || item.bucketName == selectedFolder) &&
                (searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true))
        }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { FormatUtils.formatDateGroup(it.dateAddedSec) }
    }

    if (photos.isEmpty()) {
        EmptyTabPlaceholder(
            icon = Icons.Default.PhotoLibrary,
            title = "No photos found",
            subtitle = "Grant media access or take photos to share them offline at full original resolution."
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(folders) { folder ->
                FilterChip(
                    selected = folder == selectedFolder,
                    onClick = { onSelectFolder(folder) },
                    label = { Text(folder) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldPrimary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            grouped.forEach { (dateLabel, itemsInDate) ->
                item(key = "date_$dateLabel") {
                    Text(
                        text = "$dateLabel (${itemsInDate.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                val rows = itemsInDate.chunked(3)
                items(rows, key = { "photo_row_${it.first().id}" }) { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (photo in rowItems) {
                            val tItem = photo.toTransferItem()
                            val selected = tItem.id in selectedIds
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (selected) 2.5.dp else 0.dp,
                                        color = if (selected) EmeraldBright else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onToggleSelect(tItem) }
                            ) {
                                AsyncImage(
                                    model = photo.uri,
                                    contentDescription = photo.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (selected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(EmeraldPrimary.copy(alpha = 0.25f))
                                    )
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = EmeraldGlow,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(22.dp)
                                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    )
                                }
                            }
                        }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
fun MusicTabContent(
    music: List<MediaItem>,
    selectedIds: Set<String>,
    searchQuery: String,
    playerState: AudioPlayerState,
    onPlayOrToggle: (MediaItem) -> Unit,
    onTogglePause: () -> Unit,
    onSeek: (Float) -> Unit,
    onStopPlayer: () -> Unit,
    onToggleSelect: (TransferItem) -> Unit
) {
    val filtered = remember(music, searchQuery) {
        if (searchQuery.isBlank()) music
        else music.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    if (filtered.isEmpty()) {
        EmptyTabPlaceholder(
            icon = Icons.Default.MusicNote,
            title = "No audio files found",
            subtitle = "Local MP3, M4A, and extracted audio tracks will show up here with an offline mini player."
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (playerState.currentTrack != null) {
            MiniAudioPlayerCard(
                state = playerState,
                onTogglePause = onTogglePause,
                onSeek = onSeek,
                onClose = onStopPlayer
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.id }) { track ->
                val tItem = track.toTransferItem()
                val selected = tItem.id in selectedIds
                val isCurrent = playerState.currentTrack?.id == track.id
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) EmeraldContainerDark else MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleSelect(tItem) }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onPlayOrToggle(track) }) {
                            Icon(
                                imageVector = if (isCurrent && playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play or pause track",
                                tint = EmeraldBright
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isCurrent) EmeraldGlow else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${FormatUtils.formatDuration(track.durationMs)} • ${FormatUtils.formatSize(track.sizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { onToggleSelect(tItem) },
                            colors = CheckboxDefaults.colors(checkedColor = EmeraldBright)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniAudioPlayerCard(
    state: AudioPlayerState,
    onTogglePause: () -> Unit,
    onSeek: (Float) -> Unit,
    onClose: () -> Unit
) {
    val track = state.currentTrack ?: return
    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = EmeraldContainerDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldBright.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = EmeraldGlow)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${FormatUtils.formatDuration(state.positionMs)} / ${FormatUtils.formatDuration(state.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldGlow
                    )
                }
                IconButton(onClick = onTogglePause) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close player", tint = Color.White)
                }
            }
            Slider(
                value = progress,
                onValueChange = onSeek,
                colors = SliderDefaults.colors(
                    thumbColor = EmeraldGlow,
                    activeTrackColor = EmeraldBright
                ),
                modifier = Modifier.height(24.dp)
            )
        }
    }
}

@Composable
fun VideoTabContent(
    videos: List<MediaItem>,
    selectedIds: Set<String>,
    searchQuery: String,
    extractionState: ExtractionUiState,
    onExtractAudio: (MediaItem) -> Unit,
    onToggleSelect: (TransferItem) -> Unit
) {
    val filtered = remember(videos, searchQuery) {
        if (searchQuery.isBlank()) videos
        else videos.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    if (filtered.isEmpty()) {
        EmptyTabPlaceholder(
            icon = Icons.Default.VideoLibrary,
            title = "No videos found",
            subtitle = "Local videos appear here with direct offline sharing and built-in audio extraction."
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (extractionState.activeVideoName != null || extractionState.lastMessage != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = EmeraldContainerDark
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = extractionState.lastMessage ?: "Extracting audio...",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldGlow
                    )
                    if (extractionState.activeVideoName != null) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { extractionState.progress },
                            color = EmeraldBright,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered, key = { it.id }) { video ->
                val tItem = video.toTransferItem()
                val selected = tItem.id in selectedIds
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) EmeraldContainerDark else MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleSelect(tItem) }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 86.dp, height = 56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = video.uri,
                                contentDescription = video.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.72f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = FormatUtils.formatDuration(video.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = FormatUtils.formatSize(video.sizeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { onExtractAudio(video) },
                                enabled = extractionState.activeVideoName == null,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AudioFile,
                                    contentDescription = null,
                                    tint = EmeraldBright,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.extract_audio),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldBright
                                )
                            }
                        }
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { onToggleSelect(tItem) },
                            colors = CheckboxDefaults.colors(checkedColor = EmeraldBright)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileTabContent(
    storageInfo: StorageInfo,
    smartBuckets: Map<SmartBucketType, SmartBucketSummary>,
    activeBucket: SmartBucketType?,
    explorerPath: String?,
    explorerEntries: List<FileEntry>,
    selectedIds: Set<String>,
    searchQuery: String,
    onOpenStorageExplorer: () -> Unit,
    onEnterDirectory: (String) -> Unit,
    onNavigateUpExplorer: () -> Unit,
    onSelectBucket: (SmartBucketType?) -> Unit,
    onToggleSelect: (TransferItem) -> Unit
) {
    // Mode 1: Directory Explorer open
    if (explorerPath != null) {
        val filtered = remember(explorerEntries, searchQuery) {
            if (searchQuery.isBlank()) explorerEntries
            else explorerEntries.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateUpExplorer) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back up folder")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("File Explorer", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = explorerPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldBright,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                EmptyTabPlaceholder(
                    icon = Icons.Default.Folder,
                    title = "Empty folder",
                    subtitle = "No visible files or subfolders in this directory."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.path }) { entry ->
                        FileEntryRow(
                            entry = entry,
                            selected = !entry.isDirectory && entry.toTransferItem().id in selectedIds,
                            onClick = {
                                if (entry.isDirectory) onEnterDirectory(entry.path)
                                else onToggleSelect(entry.toTransferItem())
                            }
                        )
                    }
                }
            }
        }
        return
    }

    // Mode 2: Smart Bucket Detail List
    if (activeBucket != null) {
        val bucketFiles = smartBuckets[activeBucket]?.files.orEmpty()
        val filtered = remember(bucketFiles, searchQuery) {
            if (searchQuery.isBlank()) bucketFiles
            else bucketFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onSelectBucket(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to File Manager")
                    }
                    Text(
                        text = "${bucketTitle(activeBucket)} (${filtered.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            if (filtered.isEmpty()) {
                EmptyTabPlaceholder(
                    icon = Icons.Default.InsertDriveFile,
                    title = "No matching files",
                    subtitle = "Nothing found in this category yet."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.path }) { entry ->
                        FileEntryRow(
                            entry = entry,
                            selected = entry.toTransferItem().id in selectedIds,
                            onClick = { onToggleSelect(entry.toTransferItem()) }
                        )
                    }
                }
            }
        }
        return
    }

    // Mode 3: Main File Manager Overview (Storage bar + Smart Buckets)
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "phone_storage_card") {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldBright.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenStorageExplorer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(EmeraldContainerDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SdStorage, contentDescription = null, tint = EmeraldGlow)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.phone_storage),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Available ${FormatUtils.formatSize(storageInfo.availableBytes)} / Total ${FormatUtils.formatSize(storageInfo.totalBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldBright
                            )
                        }
                        Text(
                            text = "Explore →",
                            style = MaterialTheme.typography.labelLarge,
                            color = EmeraldBright
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { storageInfo.usedFraction },
                        color = EmeraldBright,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                    )
                }
            }
        }

        item(key = "buckets_title") {
            Text(
                text = "Smart Categories",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        val bucketOrder = listOf(
            Triple(SmartBucketType.DOCUMENTS, Icons.Default.Description, CyanAccent),
            Triple(SmartBucketType.EBOOKS, Icons.Default.MenuBook, AmberAccent),
            Triple(SmartBucketType.APKS, Icons.Default.Android, EmeraldBright),
            Triple(SmartBucketType.ARCHIVES, Icons.Default.Archive, VioletAccent),
            Triple(SmartBucketType.BIG_FILES, Icons.Default.Storage, Color(0xFFFF7043))
        )

        items(bucketOrder, key = { it.first.name }) { (type, icon, tint) ->
            val summary = smartBuckets[type]
            SmartBucketCard(
                title = bucketTitle(type),
                subtitle = bucketExtensionsHint(type),
                count = summary?.count ?: 0,
                totalBytes = summary?.totalBytes ?: 0L,
                icon = icon,
                accentTint = tint,
                onClick = { onSelectBucket(type) }
            )
        }
    }
}

@Composable
private fun SmartBucketCard(
    title: String,
    subtitle: String,
    count: Int,
    totalBytes: Long,
    icon: ImageVector,
    accentTint: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentTint)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.titleMedium,
                    color = EmeraldBright,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = FormatUtils.formatSize(totalBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FileEntryRow(
    entry: FileEntry,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) EmeraldContainerDark else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (entry.isDirectory) AmberAccent else EmeraldBright,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (entry.isDirectory) "${entry.childCount} items" else FormatUtils.formatSize(entry.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!entry.isDirectory) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(checkedColor = EmeraldBright)
                )
            }
        }
    }
}

@Composable
private fun bucketTitle(type: SmartBucketType): String = when (type) {
    SmartBucketType.DOCUMENTS -> stringResource(R.string.bucket_documents)
    SmartBucketType.EBOOKS -> stringResource(R.string.bucket_ebooks)
    SmartBucketType.APKS -> stringResource(R.string.bucket_apks)
    SmartBucketType.ARCHIVES -> stringResource(R.string.bucket_archives)
    SmartBucketType.BIG_FILES -> stringResource(R.string.bucket_big_files)
}

private fun bucketExtensionsHint(type: SmartBucketType): String = when (type) {
    SmartBucketType.DOCUMENTS -> ".pdf, .docx, .xlsx, .pptx"
    SmartBucketType.EBOOKS -> ".txt, .epub, .mobi"
    SmartBucketType.APKS -> ".apk, .apks"
    SmartBucketType.ARCHIVES -> ".zip, .rar, .7z"
    SmartBucketType.BIG_FILES -> "Files larger than 50 MB"
}
