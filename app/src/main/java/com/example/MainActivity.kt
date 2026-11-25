package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.ConnectionPhase
import com.example.data.model.WebPortalMode
import com.example.ui.screens.ConnectQrScreen
import com.example.ui.screens.MainDashboardScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PhoneCopyScreen
import com.example.ui.screens.PreparationsScreen
import com.example.ui.screens.QrScannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ShareAppScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.screens.TransferProgressScreen
import com.example.ui.screens.WebPortalScreen
import com.example.ui.theme.FlashShareTheme
import com.example.util.LocaleHelper
import com.example.viewmodel.DashboardViewModel
import com.example.viewmodel.PhoneCopyViewModel
import com.example.viewmodel.TransferViewModel

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val PREPARATIONS = "preparations"
    const val CONNECT_QR = "connect_qr"
    const val QR_SCANNER = "qr_scanner"
    const val TRANSFER_PROGRESS = "transfer_progress"
    const val PHONE_COPY = "phone_copy"
    const val SHARE_APP = "share_app"
    const val WEB_PORTAL = "web_portal/{mode}"
    const val TOOLS = "tools"
    const val SETTINGS = "settings"

    fun webPortal(mode: WebPortalMode): String = "web_portal/${mode.name}"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlashShareRoot()
        }
    }
}

@Composable
fun FlashShareRoot() {
    val dashboardVm: DashboardViewModel = viewModel()
    val transferVm: TransferViewModel = viewModel()
    val phoneCopyVm: PhoneCopyViewModel = viewModel()

    val prefs by dashboardVm.userPreferences.collectAsStateWithLifecycle()
    val dashboardState by dashboardVm.uiState.collectAsStateWithLifecycle()
    val receivedHistory by dashboardVm.receivedHistory.collectAsStateWithLifecycle()
    val sentHistory by dashboardVm.sentHistory.collectAsStateWithLifecycle()
    val audioPlayerState by dashboardVm.audioPlayerState.collectAsStateWithLifecycle()

    val checklist by transferVm.checklist.collectAsStateWithLifecycle()
    val queuedItems by transferVm.queuedItems.collectAsStateWithLifecycle()
    val transferSession by transferVm.sessionState.collectAsStateWithLifecycle()
    val webServerState by transferVm.webServerState.collectAsStateWithLifecycle()
    val shareHotspotPayload by transferVm.shareAppHotspotPayload.collectAsStateWithLifecycle()
    val isStartingShareHotspot by transferVm.isStartingShareHotspot.collectAsStateWithLifecycle()

    val phoneCopyState by phoneCopyVm.uiState.collectAsStateWithLifecycle()

    val baseContext = LocalContext.current
    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current
    val backPressedDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
    val localizedConfiguration = remember(baseContext, prefs.languageCode) {
        LocaleHelper.createLocalizedConfiguration(baseContext, prefs.languageCode)
    }
    val localizedContext = remember(baseContext, prefs.languageCode) {
        LocaleHelper.wrapContextWithLocale(baseContext, prefs.languageCode)
    }
    val layoutDirection = if (LocaleHelper.isRtl(prefs.languageCode)) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    val providedLocals = buildList {
        add(LocalContext provides localizedContext)
        add(LocalConfiguration provides localizedConfiguration)
        add(LocalLayoutDirection provides layoutDirection)
        if (activityResultRegistryOwner != null) {
            add(LocalActivityResultRegistryOwner provides activityResultRegistryOwner)
        }
        if (backPressedDispatcherOwner != null) {
            add(LocalOnBackPressedDispatcherOwner provides backPressedDispatcherOwner)
        }
    }.toTypedArray()

    CompositionLocalProvider(*providedLocals) {
        FlashShareTheme(darkTheme = prefs.isDarkTheme) {
            val navController = rememberNavController()
            val startDestination = if (prefs.hasCompletedOnboarding) Routes.DASHBOARD else Routes.ONBOARDING

            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(
                        onFinished = {
                            dashboardVm.completeOnboarding()
                            navController.navigate(Routes.DASHBOARD) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.DASHBOARD) {
                    MainDashboardScreen(
                        dashboardState = dashboardState,
                        prefs = prefs,
                        receivedHistory = receivedHistory,
                        sentHistory = sentHistory,
                        audioPlayerState = audioPlayerState,
                        transferSession = transferSession,
                        dashboardViewModel = dashboardVm,
                        onSendClicked = { selected ->
                            if (transferSession.phase == ConnectionPhase.TRANSFERRING && selected.isNotEmpty()) {
                                transferVm.addMoreFilesToActiveTransfer(selected)
                                dashboardVm.clearSelection()
                                transferVm.restoreTransfer()
                                navController.navigate(Routes.TRANSFER_PROGRESS)
                            } else {
                                transferVm.stageItemsForSend(selected)
                                transferVm.refreshHardwareChecklist()
                                navController.navigate(Routes.PREPARATIONS)
                            }
                        },
                        onReceiveClicked = {
                            transferVm.refreshHardwareChecklist()
                            navController.navigate(Routes.QR_SCANNER)
                        },
                        onOpenWebPortal = { mode ->
                            navController.navigate(Routes.webPortal(mode))
                        },
                        onShareThisApp = {
                            navController.navigate(Routes.SHARE_APP)
                        },
                        onOpenPhoneCopy = {
                            phoneCopyVm.resetRole()
                            navController.navigate(Routes.PHONE_COPY)
                        },
                        onOpenTools = {
                            navController.navigate(Routes.TOOLS)
                        },
                        onOpenSettings = {
                            navController.navigate(Routes.SETTINGS)
                        },
                        onRestoreTransfer = {
                            transferVm.restoreTransfer()
                            if (transferSession.phase == ConnectionPhase.WAITING_FOR_RECEIVER) {
                                navController.navigate(Routes.CONNECT_QR)
                            } else {
                                navController.navigate(Routes.TRANSFER_PROGRESS)
                            }
                        }
                    )
                }

                composable(Routes.PREPARATIONS) {
                    PreparationsScreen(
                        checklist = checklist,
                        queuedCount = queuedItems.size,
                        onRefreshChecklist = transferVm::refreshHardwareChecklist,
                        onBack = { navController.popBackStack() },
                        onNext = {
                            navController.navigate(Routes.CONNECT_QR)
                        }
                    )
                }

                composable(Routes.CONNECT_QR) {
                    ConnectQrScreen(
                        sessionState = transferSession,
                        prefer5Ghz = prefs.prefer5Ghz,
                        supports5Ghz = checklist.supports5Ghz,
                        onToggle5Ghz = dashboardVm::setPrefer5Ghz,
                        onStartHost = transferVm::startSenderHost,
                        onStopAndExit = {
                            transferVm.stopTransferSession()
                            navController.popBackStack(Routes.DASHBOARD, inclusive = false)
                        },
                        onMinimize = {
                            transferVm.minimizeTransfer()
                            navController.popBackStack(Routes.DASHBOARD, inclusive = false)
                        },
                        onConnectedNavigateToProgress = {
                            navController.navigate(Routes.TRANSFER_PROGRESS) {
                                popUpTo(Routes.CONNECT_QR) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.QR_SCANNER) {
                    QrScannerScreen(
                        supports5Ghz = checklist.supports5Ghz,
                        onBack = { navController.popBackStack() },
                        onQrScanned = { payload ->
                            transferVm.connectAndReceive(payload)
                            navController.navigate(Routes.TRANSFER_PROGRESS) {
                                popUpTo(Routes.QR_SCANNER) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.TRANSFER_PROGRESS) {
                    TransferProgressScreen(
                        sessionState = transferSession,
                        onAddMoreFiles = {
                            transferVm.minimizeTransfer()
                            navController.popBackStack(Routes.DASHBOARD, inclusive = false)
                        },
                        onMinimize = {
                            transferVm.minimizeTransfer()
                            navController.popBackStack(Routes.DASHBOARD, inclusive = false)
                        },
                        onCancelOrFinish = {
                            transferVm.stopTransferSession()
                            dashboardVm.clearSelection()
                            dashboardVm.refreshAllData()
                            navController.popBackStack(Routes.DASHBOARD, inclusive = false)
                        }
                    )
                }

                composable(Routes.PHONE_COPY) {
                    PhoneCopyScreen(
                        uiState = phoneCopyState,
                        onSelectRole = phoneCopyVm::selectRole,
                        onToggleCategory = phoneCopyVm::toggleCategory,
                        onRescan = phoneCopyVm::scanDeviceData,
                        onStartOldPhoneMigration = { batch ->
                            transferVm.stageItemsForSend(batch)
                            transferVm.refreshHardwareChecklist()
                            navController.navigate(Routes.PREPARATIONS)
                        },
                        onPrepareBatch = phoneCopyVm::buildBatchAndProceed,
                        onStartNewPhoneReceiver = {
                            transferVm.refreshHardwareChecklist()
                            navController.navigate(Routes.QR_SCANNER)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Routes.WEB_PORTAL,
                    arguments = listOf(navArgument("mode") { type = NavType.StringType })
                ) { backStackEntry ->
                    val modeName = backStackEntry.arguments?.getString("mode") ?: WebPortalMode.PC_DASHBOARD.name
                    val mode = runCatching { WebPortalMode.valueOf(modeName) }
                        .getOrDefault(WebPortalMode.PC_DASHBOARD)
                    val selectedItems = dashboardState.selectedItems.values.toList()
                    WebPortalScreen(
                        mode = mode,
                        serverState = webServerState,
                        sharedItemCount = selectedItems.size,
                        onStartServer = { transferVm.startWebPortal(mode, selectedItems) },
                        onStopServerAndBack = {
                            transferVm.stopWebPortal()
                            dashboardVm.refreshAllData()
                            navController.popBackStack()
                        }
                    )
                }

                composable(Routes.SHARE_APP) {
                    ShareAppScreen(
                        serverState = webServerState,
                        hotspotPayload = shareHotspotPayload,
                        isStartingHotspot = isStartingShareHotspot,
                        onStartSelfApkPortal = {
                            transferVm.startWebPortal(WebPortalMode.SHARE_SELF_APK, emptyList())
                        },
                        onStartHotspotApkPortal = {
                            transferVm.startShareAppHotspot(prefs.prefer5Ghz)
                        },
                        onStopAllAndBack = {
                            transferVm.stopShareAppHotspot()
                            navController.popBackStack()
                        }
                    )
                }

                composable(Routes.TOOLS) {
                    ToolsScreen(
                        videos = dashboardState.videos,
                        extractionState = dashboardState.extractionState,
                        onExtractAudio = dashboardVm::extractAudioFromVideo,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        prefs = prefs,
                        onToggleNightMode = dashboardVm::setDarkTheme,
                        onToggle5Ghz = dashboardVm::setPrefer5Ghz,
                        onSelectLanguage = dashboardVm::setLanguage,
                        onUpdateDownloadPath = dashboardVm::setDownloadPath,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
