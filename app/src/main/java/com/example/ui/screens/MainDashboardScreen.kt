package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.TransferHistoryEntity
import com.example.data.local.UserPreferences
import com.example.data.model.ConnectionPhase
import com.example.data.model.TransferItem
import com.example.data.model.TransferSessionState
import com.example.data.model.WebPortalMode
import com.example.transfer.AudioPlayerState
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainerDark
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.EmeraldPrimary
import androidx.compose.ui.platform.LocalContext
import com.example.util.FormatUtils
import com.example.util.ShareAppHelper
import com.example.util.ShareLaunchResult
import com.example.viewmodel.DashboardUiState
import com.example.viewmodel.DashboardViewModel
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.launch

@Composable
fun MainDashboardScreen(
    dashboardState: DashboardUiState,
    prefs: UserPreferences,
    receivedHistory: List<TransferHistoryEntity>,
    sentHistory: List<TransferHistoryEntity>,
    audioPlayerState: AudioPlayerState,
    transferSession: TransferSessionState,
    dashboardViewModel: DashboardViewModel,
    onSendClicked: (List<TransferItem>) -> Unit,
    onReceiveClicked: () -> Unit,
    onOpenWebPortal: (WebPortalMode) -> Unit,
    onShareThisApp: () -> Unit,
    onOpenPhoneCopy: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSettings: () -> Unit,
    onRestoreTransfer: () -> Unit
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showOverflowMenu by remember { mutableStateOf(false) }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(2) } // Default to APP tab like Xender
    var showFloatingTransferPills by rememberSaveable { mutableStateOf(true) }

    val tabTitles = listOf(
        stringResource(R.string.tab_history),
        stringResource(R.string.tab_download),
        stringResource(R.string.tab_app),
        stringResource(R.string.tab_photo),
        stringResource(R.string.tab_music),
        stringResource(R.string.tab_video),
        stringResource(R.string.tab_file)
    )

    val selectedIds = remember(dashboardState.selectedItems) {
        dashboardState.selectedItems.keys
    }
    val selectedList = remember(dashboardState.selectedItems) {
        dashboardState.selectedItems.values.toList()
    }
    val selectedTotalBytes = remember(selectedList) {
        selectedList.sumOf { it.sizeBytes }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FlashShareDrawerSheet(
                prefs = prefs,
                onSelectLanguage = dashboardViewModel::setLanguage,
                onToggleHighSpeed = dashboardViewModel::setPrefer5Ghz,
                onUpdateDownloadPath = dashboardViewModel::setDownloadPath,
                onToggleNightMode = dashboardViewModel::setDarkTheme,
                onShareThisApp = onShareThisApp,
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Emerald Green Top App Bar
                Surface(
                    color = EmeraldPrimary,
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left App Logo Button (opens Navigation Drawer)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { scope.launch { drawerState.open() } }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("drawer_logo_button"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.22f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = "Open navigation drawer",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(Modifier.weight(1f))

                            // Search Local Files Toggle
                            IconButton(
                                onClick = { dashboardViewModel.setSearchActive(!dashboardState.isSearchActive) },
                                modifier = Modifier.testTag("search_toggle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search Local Files",
                                    tint = Color.White
                                )
                            }

                            // Quick-Connect QR Icon
                            IconButton(
                                onClick = onReceiveClicked,
                                modifier = Modifier.testTag("quick_connect_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Quick Connect QR Scanner",
                                    tint = Color.White
                                )
                            }

                            // Top-Right Hamburger Menu (≡)
                            Box {
                                IconButton(
                                    onClick = { showOverflowMenu = true },
                                    modifier = Modifier.testTag("top_right_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Connection modes menu",
                                        tint = Color.White
                                    )
                                }
                                TopRightOverflowMenu(
                                    expanded = showOverflowMenu,
                                    onDismiss = { showOverflowMenu = false },
                                    onConnectToPc = { onOpenWebPortal(WebPortalMode.PC_DASHBOARD) },
                                    onConnectToKaiOs = { onOpenWebPortal(WebPortalMode.KAIOS_BROWSER) },
                                    onConnectToAndroid = { onSendClicked(selectedList) },
                                    onShareThisApp = onShareThisApp,
                                    onPhoneCopy = onOpenPhoneCopy
                                )
                            }
                        }

                        // Expandable Search Bar
                        AnimatedVisibility(visible = dashboardState.isSearchActive) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 8.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = dashboardState.searchQuery,
                                    onValueChange = dashboardViewModel::setSearchQuery,
                                    placeholder = {
                                        Text(
                                            stringResource(R.string.search_local_files),
                                            color = Color.White.copy(alpha = 0.72f)
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.45f),
                                        focusedContainerColor = Color.Black.copy(alpha = 0.16f),
                                        unfocusedContainerColor = Color.Black.copy(alpha = 0.12f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("search_local_files_input")
                                )
                                TextButton(
                                    onClick = { dashboardViewModel.setSearchActive(false) },
                                    modifier = Modifier.testTag("search_cancel_button")
                                ) {
                                    Text(stringResource(R.string.cancel), color = Color.White)
                                }
                            }
                        }

                        // Scrollable 7 Category Tabs
                        ScrollableTabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = EmeraldPrimary,
                            contentColor = Color.White,
                            edgePadding = 8.dp,
                            indicator = { tabPositions ->
                                if (selectedTabIndex < tabPositions.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                        height = 3.dp,
                                        color = Color.White
                                    )
                                }
                            },
                            divider = {}
                        ) {
                            tabTitles.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTabIndex == index,
                                    onClick = { selectedTabIndex = index },
                                    modifier = Modifier.testTag("category_tab_$index"),
                                    text = {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selectedTabIndex == index) Color.White else Color.White.copy(alpha = 0.72f)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // Active Selection Bar (shows count + direct Bluetooth share + clear button)
                AnimatedVisibility(visible = selectedList.isNotEmpty()) {
                    Surface(
                        color = EmeraldContainerDark,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedList.size} selected • ${FormatUtils.formatSize(selectedTotalBytes)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = EmeraldGlow,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val files = selectedList.mapNotNull { item ->
                                            ShareAppHelper.prepareShareableFile(context, item)
                                        }
                                        if (files.isEmpty()) {
                                            Toast.makeText(context, "Unable to prepare selected file(s)", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val res = ShareAppHelper.shareMultipleFilesViaBluetooth(context, files)
                                            if (res == ShareLaunchResult.NEEDS_BLUETOOTH_ENABLE) {
                                                try {
                                                    context.startActivity(
                                                        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                    )
                                                } catch (_: Exception) {
                                                    Toast.makeText(context, "Please turn on Bluetooth first", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("selection_bluetooth_share_button")
                            ) {
                                Icon(Icons.Default.Bluetooth, contentDescription = "Share via Bluetooth", tint = EmeraldGlow, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Bluetooth", color = EmeraldGlow)
                            }
                            TextButton(onClick = dashboardViewModel::clearSelection) {
                                Icon(Icons.Default.Close, contentDescription = "Clear selection", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Clear", color = Color.White)
                            }
                        }
                    }
                }

                // Tab Content Body
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTabIndex) {
                        0 -> HistoryTabContent(
                            received = receivedHistory,
                            sent = sentHistory,
                            searchQuery = dashboardState.searchQuery,
                            onDeleteRecord = dashboardViewModel::deleteHistoryItem,
                            onClearDirection = dashboardViewModel::clearHistory
                        )
                        1 -> DownloadTabContent(
                            downloads = dashboardState.downloads,
                            selectedIds = selectedIds,
                            searchQuery = dashboardState.searchQuery,
                            onToggleSelect = dashboardViewModel::toggleSelect
                        )
                        2 -> AppTabContent(
                            localApps = dashboardState.localApps,
                            systemApps = dashboardState.systemApps,
                            isLoading = dashboardState.isLoadingApps,
                            selectedIds = selectedIds,
                            searchQuery = dashboardState.searchQuery,
                            getIcon = dashboardViewModel::getAppIcon,
                            onToggleSelect = dashboardViewModel::toggleSelect
                        )
                        3 -> PhotoTabContent(
                            photos = dashboardState.photos,
                            selectedFolder = dashboardState.selectedPhotoFolder,
                            selectedIds = selectedIds,
                            searchQuery = dashboardState.searchQuery,
                            onSelectFolder = dashboardViewModel::setPhotoFolder,
                            onToggleSelect = dashboardViewModel::toggleSelect
                        )
                        4 -> MusicTabContent(
                            music = dashboardState.music,
                            selectedIds = selectedIds,
                            searchQuery = dashboardState.searchQuery,
                            playerState = audioPlayerState,
                            onPlayOrToggle = dashboardViewModel::playOrToggleAudio,
                            onTogglePause = dashboardViewModel::toggleAudioPause,
                            onSeek = dashboardViewModel::seekAudio,
                            onStopPlayer = dashboardViewModel::stopAudio,
                            onToggleSelect = dashboardViewModel::toggleSelect
                        )
                        5 -> VideoTabContent(
                            videos = dashboardState.videos,
                            selectedIds = selectedIds,
                            searchQuery = dashboardState.searchQuery,
                            extractionState = dashboardState.extractionState,
                            onExtractAudio = dashboardViewModel::extractAudioFromVideo,
                            onToggleSelect = dashboardViewModel::toggleSelect
                        )
                        6 -> FileTabContent(
                            storageInfo = dashboardState.storageInfo,
                            smartBuckets = dashboardState.smartBuckets,
                            activeBucket = dashboardState.activeBucket,
                            explorerPath = dashboardState.explorerPath,
                            explorerEntries = dashboardState.explorerEntries,
                            selectedIds = selectedIds,
                            searchQuery = dashboardState.searchQuery,
                            onOpenStorageExplorer = { dashboardViewModel.openStorageExplorer() },
                            onEnterDirectory = dashboardViewModel::openStorageExplorer,
                            onNavigateUpExplorer = { dashboardViewModel.navigateUpExplorer() },
                            onSelectBucket = dashboardViewModel::openSmartBucket,
                            onToggleSelect = dashboardViewModel::toggleSelect
                        )
                    }
                }
            }

            // Bottom Overlay: Minimized Transfer Banner + Floating SEND/RECEIVE Pills + Compact Bottom Bar
            Column(
                modifier = Modifier.align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Minimized Active Session Status Banner
                if (transferSession.isMinimized && transferSession.phase != ConnectionPhase.IDLE) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable(onClick = onRestoreTransfer)
                            .testTag("minimized_transfer_banner"),
                        shape = RoundedCornerShape(16.dp),
                        color = EmeraldPrimary,
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = transferSession.statusMessage.ifBlank { "Active offline session" },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Tap to return to transfer",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                            Text(
                                text = FormatUtils.formatSpeed(transferSession.speedBytesPerSec),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                }

                // Floating SEND & RECEIVE Emerald Pills
                AnimatedVisibility(
                    visible = showFloatingTransferPills,
                    enter = fadeIn() + scaleIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInVertically { it / 2 },
                    exit = fadeOut() + scaleOut() + slideOutVertically { it / 2 }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // SEND Pill
                        BadgedBox(
                            badge = {
                                if (selectedList.isNotEmpty()) {
                                    Badge(containerColor = Color(0xFFFFB020), contentColor = Color.Black) {
                                        Text("${selectedList.size}", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = EmeraldBright,
                                shadowElevation = 8.dp,
                                modifier = Modifier
                                    .height(48.dp)
                                    .clickable { onSendClicked(selectedList) }
                                    .testTag("floating_send_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send files",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.action_send),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // RECEIVE Pill
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = EmeraldPrimary,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .height(48.dp)
                                .clickable(onClick = onReceiveClicked)
                                .testTag("floating_receive_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 22.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Receive files",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.action_receive),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Compact Bottom Navigation Bar (respects navigationBarsPadding)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .height(64.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        BottomNavSlot(
                            icon = Icons.Default.History,
                            label = stringResource(R.string.nav_history),
                            selected = selectedTabIndex == 0,
                            testTag = "bottom_nav_history",
                            onClick = { selectedTabIndex = 0 }
                        )
                        BottomNavSlot(
                            icon = Icons.Default.AudioFile,
                            label = stringResource(R.string.nav_tools),
                            selected = false,
                            testTag = "bottom_nav_tools",
                            onClick = onOpenTools
                        )

                        // Center Circular Transfer (⇄) Toggle Button
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .shadow(6.dp, CircleShape)
                                .clip(CircleShape)
                                .background(if (showFloatingTransferPills) EmeraldBright else EmeraldPrimary)
                                .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                .clickable { showFloatingTransferPills = !showFloatingTransferPills }
                                .testTag("center_transfer_toggle_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Toggle Send and Receive pills",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        BottomNavSlot(
                            icon = Icons.Default.SyncAlt,
                            label = stringResource(R.string.nav_phone_copy),
                            selected = false,
                            testTag = "bottom_nav_phone_copy",
                            onClick = onOpenPhoneCopy
                        )
                        BottomNavSlot(
                            icon = Icons.Default.Person,
                            label = stringResource(R.string.nav_settings),
                            selected = false,
                            testTag = "bottom_nav_settings",
                            onClick = onOpenSettings
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavSlot(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val tint = if (selected) EmeraldBright else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1
        )
    }
}
