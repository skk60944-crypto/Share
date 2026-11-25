package com.example.ui.screens

import android.os.Build
import android.os.Environment
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.UserPreferences
import com.example.data.model.MediaItem
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainerDark
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.EmeraldPrimary
import com.example.util.FileOpenUtils
import com.example.util.FormatUtils
import com.example.util.LocaleHelper
import com.example.viewmodel.ExtractionUiState

@Composable
fun ToolsScreen(
    videos: List<MediaItem>,
    extractionState: ExtractionUiState,
    onExtractAudio: (MediaItem) -> Unit,
    onBack: () -> Unit
) {
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
                IconButton(onClick = onBack, modifier = Modifier.testTag("tools_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.tools_video_to_mp3),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }
        }

        if (extractionState.activeVideoName != null || extractionState.lastMessage != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(14.dp),
                color = EmeraldContainerDark
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = extractionState.lastMessage ?: "Extracting audio...",
                        style = MaterialTheme.typography.titleSmall,
                        color = EmeraldGlow
                    )
                    if (extractionState.activeVideoName != null) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { extractionState.progress },
                            color = EmeraldBright,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (videos.isEmpty()) {
            EmptyTabPlaceholder(
                icon = Icons.Default.VideoLibrary,
                title = "No local videos found",
                subtitle = "Record or save a video on your phone to extract lossless offline audio tracks."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .navigationBarsPadding()
            ) {
                items(videos, key = { it.id }) { video ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = video.uri,
                                contentDescription = video.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 76.dp, height = 52.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = video.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${FormatUtils.formatDuration(video.durationMs)} • ${FormatUtils.formatSize(video.sizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { onExtractAudio(video) },
                                enabled = extractionState.activeVideoName == null,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldBright),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Extract", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    prefs: UserPreferences,
    onToggleNightMode: (Boolean) -> Unit,
    onToggle5Ghz: (Boolean) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onUpdateDownloadPath: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showPathDialog by remember { mutableStateOf(false) }

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
                IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Text("Me & Settings", style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Privacy Banner
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = EmeraldContainerDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldBright.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldGlow, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("100% Offline & Private", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            "Zero ads, zero cloud tracking, zero mobile data consumed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldGlow
                        )
                    }
                }
            }

            // Night Mode
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DarkMode, contentDescription = null, tint = EmeraldBright)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.drawer_night_mode), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (prefs.isDarkTheme) "Obsidian Dark Theme" else "Clean Light Theme",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.isDarkTheme,
                        onCheckedChange = onToggleNightMode,
                        colors = SwitchDefaults.colors(checkedTrackColor = EmeraldBright)
                    )
                }
            }

            // High-Speed 5GHz
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = EmeraldBright)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.drawer_high_speed), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.drawer_high_speed_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.prefer5Ghz,
                        onCheckedChange = onToggle5Ghz,
                        colors = SwitchDefaults.colors(checkedTrackColor = EmeraldBright)
                    )
                }
            }

            // Language Selector Chips
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = EmeraldBright)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.drawer_language), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LocaleHelper.supportedLanguages.forEach { lang ->
                            val selected = prefs.languageCode == lang.code
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelectLanguage(lang.code) }
                            ) {
                                Text(
                                    text = lang.nativeName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Storage Path
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPathDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = EmeraldBright)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.drawer_storage_path), style = MaterialTheme.typography.titleMedium)
                        Text(
                            prefs.downloadDirectoryPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldBright
                        )
                    }
                }
            }

            // All Files Access Shortcut
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val allFiles = Environment.isExternalStorageManager()
                OutlinedButton(
                    onClick = { FileOpenUtils.openAllFilesPermissionSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (allFiles) "All Files Access: Granted (Manage)" else "Grant All Files Access in Settings",
                        color = EmeraldBright
                    )
                }
            }
        }
    }

    if (showPathDialog) {
        StoragePathDialog(
            currentPath = prefs.downloadDirectoryPath,
            onDismiss = { showPathDialog = false },
            onSave = {
                onUpdateDownloadPath(it)
                showPathDialog = false
            }
        )
    }
}
