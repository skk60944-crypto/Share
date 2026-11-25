package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.PhoneCopyCategoryType
import com.example.data.model.PhoneCopyRole
import com.example.data.model.TransferItem
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainerDark
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.EmeraldPrimary
import com.example.util.FormatUtils
import com.example.viewmodel.PhoneCopyUiState

@Composable
fun PhoneCopyScreen(
    uiState: PhoneCopyUiState,
    onSelectRole: (PhoneCopyRole) -> Unit,
    onToggleCategory: (PhoneCopyCategoryType) -> Unit,
    onRescan: () -> Unit,
    onStartOldPhoneMigration: (List<TransferItem>) -> Unit,
    onPrepareBatch: ((List<TransferItem>) -> Unit) -> Unit,
    onStartNewPhoneReceiver: () -> Unit,
    onBack: () -> Unit
) {
    val backupPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRescan()
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
                IconButton(
                    onClick = {
                        if (uiState.role != PhoneCopyRole.NONE) onSelectRole(PhoneCopyRole.NONE)
                        else onBack()
                    },
                    modifier = Modifier.testTag("phone_copy_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.menu_phone_copy),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }
        }

        when (uiState.role) {
            PhoneCopyRole.NONE -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Full Offline Phone Clone",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.phone_copy_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(26.dp))

                    RoleSelectionCard(
                        icon = Icons.Default.Upload,
                        title = stringResource(R.string.phone_copy_old),
                        subtitle = "Export Apps, Photos, Videos, Music, Documents, Contacts (.vcf), Call Logs & SMS and send in one batch.",
                        testTag = "phone_copy_old_card",
                        onClick = { onSelectRole(PhoneCopyRole.OLD_PHONE) }
                    )

                    Spacer(Modifier.height(16.dp))

                    RoleSelectionCard(
                        icon = Icons.Default.QrCodeScanner,
                        title = stringResource(R.string.phone_copy_new),
                        subtitle = "Scan the Old Phone's QR code to receive the complete backup over high-speed offline Wi-Fi.",
                        testTag = "phone_copy_new_card",
                        onClick = onStartNewPhoneReceiver
                    )
                }
            }

            PhoneCopyRole.OLD_PHONE -> {
                if (uiState.isScanning) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = EmeraldBright)
                            Spacer(Modifier.height(12.dp))
                            Text("Scanning apps, media, contacts, call logs & SMS...")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            val needsExtraPerms = uiState.categories.any { !it.permissionGranted }
                            if (needsExtraPerms) {
                                OutlinedButton(
                                    onClick = {
                                        backupPermLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.READ_CONTACTS,
                                                Manifest.permission.READ_CALL_LOG,
                                                Manifest.permission.READ_SMS
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Grant Contacts, Call Logs & SMS Access", color = EmeraldBright)
                                }
                            }
                        }
                        items(uiState.categories, key = { it.type.name }) { cat ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (cat.selected) EmeraldContainerDark else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleCategory(cat.type) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = categoryIcon(cat.type),
                                        contentDescription = null,
                                        tint = EmeraldGlow,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(categoryName(cat.type), style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = if (!cat.permissionGranted) {
                                                "Permission required to export"
                                            } else {
                                                "${cat.count} items • ${FormatUtils.formatSize(cat.totalBytes)}"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Checkbox(
                                        checked = cat.selected,
                                        onCheckedChange = { onToggleCategory(cat.type) },
                                        colors = CheckboxDefaults.colors(checkedColor = EmeraldBright)
                                    )
                                }
                            }
                        }
                    }

                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Selected: ${uiState.totalSelectedItems} items (${FormatUtils.formatSize(uiState.totalSelectedBytes)})",
                                style = MaterialTheme.typography.labelLarge,
                                color = EmeraldBright
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { onPrepareBatch(onStartOldPhoneMigration) },
                                enabled = !uiState.isPreparingBatch && uiState.totalSelectedItems > 0,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("start_phone_copy_button"),
                                shape = RoundedCornerShape(26.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldBright)
                            ) {
                                if (uiState.isPreparingBatch) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("Exporting VCF & Backups...")
                                } else {
                                    Text("Start Batch Migration", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            PhoneCopyRole.NEW_PHONE -> {
                onStartNewPhoneReceiver()
            }
        }
    }
}

@Composable
private fun RoleSelectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, EmeraldBright.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(EmeraldContainerDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = EmeraldGlow, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun categoryIcon(type: PhoneCopyCategoryType): ImageVector = when (type) {
    PhoneCopyCategoryType.APPS -> Icons.Default.Apps
    PhoneCopyCategoryType.PHOTOS -> Icons.Default.PhotoLibrary
    PhoneCopyCategoryType.VIDEOS -> Icons.Default.VideoLibrary
    PhoneCopyCategoryType.MUSIC -> Icons.Default.MusicNote
    PhoneCopyCategoryType.DOCUMENTS -> Icons.Default.Description
    PhoneCopyCategoryType.CONTACTS -> Icons.Default.Contacts
    PhoneCopyCategoryType.CALL_LOGS -> Icons.Default.Call
    PhoneCopyCategoryType.SMS -> Icons.Default.Sms
}

private fun categoryName(type: PhoneCopyCategoryType): String = when (type) {
    PhoneCopyCategoryType.APPS -> "Installed Apps (APK / Split APK)"
    PhoneCopyCategoryType.PHOTOS -> "Photos"
    PhoneCopyCategoryType.VIDEOS -> "Videos"
    PhoneCopyCategoryType.MUSIC -> "Music & Audio"
    PhoneCopyCategoryType.DOCUMENTS -> "Documents"
    PhoneCopyCategoryType.CONTACTS -> "Contacts (.vcf vCard)"
    PhoneCopyCategoryType.CALL_LOGS -> "Call Logs (.json backup)"
    PhoneCopyCategoryType.SMS -> "SMS Messages (.json backup)"
}
