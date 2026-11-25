package com.example.ui.screens

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.UserPreferences
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldPrimary
import com.example.util.LocaleHelper
import java.io.File

@Composable
fun TopRightOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onConnectToPc: () -> Unit,
    onConnectToKaiOs: () -> Unit,
    onConnectToAndroid: () -> Unit,
    onShareThisApp: () -> Unit,
    onPhoneCopy: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_connect_pc)) },
            leadingIcon = { Icon(Icons.Default.Computer, contentDescription = null, tint = EmeraldBright) },
            onClick = { onDismiss(); onConnectToPc() },
            modifier = Modifier.testTag("menu_connect_pc")
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_connect_kaios)) },
            leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, tint = EmeraldBright) },
            onClick = { onDismiss(); onConnectToKaiOs() },
            modifier = Modifier.testTag("menu_connect_kaios")
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_connect_android)) },
            leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = EmeraldBright) },
            onClick = { onDismiss(); onConnectToAndroid() },
            modifier = Modifier.testTag("menu_connect_android")
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_share_app)) },
            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = EmeraldBright) },
            onClick = { onDismiss(); onShareThisApp() },
            modifier = Modifier.testTag("menu_share_app")
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_phone_copy), fontWeight = FontWeight.SemiBold) },
            leadingIcon = { Icon(Icons.Default.SyncAlt, contentDescription = null, tint = EmeraldBright) },
            onClick = { onDismiss(); onPhoneCopy() },
            modifier = Modifier.testTag("menu_phone_copy")
        )
    }
}

@Composable
fun FlashShareDrawerSheet(
    prefs: UserPreferences,
    onSelectLanguage: (String) -> Unit,
    onToggleHighSpeed: (Boolean) -> Unit,
    onUpdateDownloadPath: (String) -> Unit,
    onToggleNightMode: (Boolean) -> Unit,
    onShareThisApp: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showPathDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val currentLanguageName = LocaleHelper.supportedLanguages
        .firstOrNull { it.code == prefs.languageCode }
        ?.let { "${it.nativeName} (${it.englishName})" } ?: "English"

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(310.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            // Emerald Brand Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(EmeraldDeep, EmeraldPrimary, EmeraldBright))
                    )
                    .statusBarsPadding()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "FlashShare Logo",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.drawer_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Language Switcher
            DrawerClickableItem(
                icon = Icons.Default.Language,
                title = stringResource(R.string.drawer_language),
                subtitle = currentLanguageName,
                testTag = "drawer_language_row",
                onClick = { showLanguageDialog = true }
            )

            // High-Speed Mode (5GHz)
            DrawerSwitchItem(
                icon = Icons.Default.Speed,
                title = stringResource(R.string.drawer_high_speed),
                subtitle = stringResource(R.string.drawer_high_speed_desc),
                checked = prefs.prefer5Ghz,
                testTag = "drawer_5ghz_switch",
                onCheckedChange = onToggleHighSpeed
            )

            // Storage Download Path
            DrawerClickableItem(
                icon = Icons.Default.Folder,
                title = stringResource(R.string.drawer_storage_path),
                subtitle = prefs.downloadDirectoryPath,
                testTag = "drawer_storage_path_row",
                onClick = { showPathDialog = true }
            )

            // Night Mode Toggle
            DrawerSwitchItem(
                icon = Icons.Default.DarkMode,
                title = stringResource(R.string.drawer_night_mode),
                subtitle = if (prefs.isDarkTheme) "Dark Mode Active" else "Light Mode Active",
                checked = prefs.isDarkTheme,
                testTag = "drawer_night_mode_switch",
                onCheckedChange = onToggleNightMode
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))

            // Share FlashShare to Other Phone (Bluetooth, Hotspot, WhatsApp, Messages)
            DrawerClickableItem(
                icon = Icons.Default.Share,
                title = stringResource(R.string.menu_share_app),
                subtitle = "Bluetooth, Hotspot, QR & WhatsApp APK share",
                testTag = "drawer_share_app_row",
                onClick = {
                    onCloseDrawer()
                    onShareThisApp()
                }
            )

            // About Section
            DrawerClickableItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.drawer_about),
                subtitle = stringResource(R.string.drawer_about_desc),
                testTag = "drawer_about_row",
                onClick = { showAboutDialog = true }
            )
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.drawer_language)) },
            text = {
                Column {
                    LocaleHelper.supportedLanguages.forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectLanguage(lang.code)
                                    showLanguageDialog = false
                                    onCloseDrawer()
                                }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = prefs.languageCode == lang.code,
                                onClick = {
                                    onSelectLanguage(lang.code)
                                    showLanguageDialog = false
                                    onCloseDrawer()
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(lang.nativeName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    lang.englishName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (prefs.languageCode == lang.code) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldBright)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("FlashShare v1.0") },
            text = {
                Text(
                    "FlashShare is a 100% offline, ad-free, open-architecture file, APK, and phone data transfer utility.\n\n" +
                        "• Zero cloud servers or analytics\n" +
                        "• High-speed 5GHz Wi-Fi Direct & Local Hotspot\n" +
                        "• Multi-threaded chunked TCP with resume\n" +
                        "• Embedded PC & KaiOS local HTTP portal"
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun DrawerClickableItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = EmeraldBright, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun DrawerSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = EmeraldBright, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(checkedTrackColor = EmeraldBright)
        )
    }
}

@Composable
fun StoragePathDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val presets = remember {
        val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        listOf(
            File(dl, "FlashShare").absolutePath,
            File(dl, "FlashShare/Received").absolutePath,
            File(docs, "FlashShare").absolutePath
        )
    }
    var customPath by remember { mutableStateOf(currentPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drawer_storage_path)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { customPath = preset }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = customPath == preset, onClick = { customPath = preset })
                        Text(
                            text = preset,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = customPath,
                    onValueChange = { customPath = it },
                    label = { Text("Custom Folder Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(customPath.trim()) }) {
                Text("Save", color = EmeraldBright)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
