package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FlashShareApp
import com.example.data.local.TransferHistoryEntity
import com.example.data.local.UserPreferences
import com.example.data.model.FileEntry
import com.example.data.model.InstalledAppInfo
import com.example.data.model.MediaItem
import com.example.data.model.SmartBucketSummary
import com.example.data.model.SmartBucketType
import com.example.data.model.StorageInfo
import com.example.data.model.TransferItem
import com.example.transfer.AudioPlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExtractionUiState(
    val activeVideoName: String? = null,
    val progress: Float = 0f,
    val lastMessage: String? = null
)

data class DashboardUiState(
    val isLoadingApps: Boolean = false,
    val isLoadingMedia: Boolean = false,
    val isLoadingFiles: Boolean = false,
    val localApps: List<InstalledAppInfo> = emptyList(),
    val systemApps: List<InstalledAppInfo> = emptyList(),
    val photos: List<MediaItem> = emptyList(),
    val selectedPhotoFolder: String = "All",
    val music: List<MediaItem> = emptyList(),
    val videos: List<MediaItem> = emptyList(),
    val downloads: List<FileEntry> = emptyList(),
    val storageInfo: StorageInfo = StorageInfo(),
    val smartBuckets: Map<SmartBucketType, SmartBucketSummary> = emptyMap(),
    val activeBucket: SmartBucketType? = null,
    val explorerPath: String? = null,
    val explorerEntries: List<FileEntry> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val selectedItems: Map<String, TransferItem> = emptyMap(),
    val extractionState: ExtractionUiState = ExtractionUiState()
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FlashShareApp

    val userPreferences: StateFlow<UserPreferences> = app.preferencesStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val receivedHistory: StateFlow<List<TransferHistoryEntity>> = app.historyRepository.receivedHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sentHistory: StateFlow<List<TransferHistoryEntity>> = app.historyRepository.sentHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val audioPlayerState: StateFlow<AudioPlayerState> = app.miniAudioPlayer.state

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refreshAllData()
    }

    fun refreshAllData() {
        loadApps()
        loadMedia()
        loadFilesAndStorage()
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val (local, system) = app.appRepository.loadInstalledApps()
            _uiState.update {
                it.copy(
                    isLoadingApps = false,
                    localApps = local,
                    systemApps = system
                )
            }
        }
    }

    fun loadMedia() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMedia = true) }
            val photos = app.mediaRepository.loadPhotos()
            val music = app.mediaRepository.loadMusic()
            val videos = app.mediaRepository.loadVideos()
            _uiState.update {
                it.copy(
                    isLoadingMedia = false,
                    photos = photos,
                    music = music,
                    videos = videos
                )
            }
        }
    }

    fun loadFilesAndStorage() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFiles = true) }
            val storage = app.fileRepository.getStorageInfo()
            val downloads = app.fileRepository.loadDownloadedFiles(userPreferences.value.downloadDirectoryPath)
            val buckets = app.fileRepository.scanSmartBuckets()
            _uiState.update {
                it.copy(
                    isLoadingFiles = false,
                    storageInfo = storage,
                    downloads = downloads,
                    smartBuckets = buckets
                )
            }
        }
    }

    fun getAppIcon(packageName: String): Bitmap? = app.appRepository.getAppIconBitmap(packageName)

    fun toggleSelect(item: TransferItem) {
        _uiState.update { state ->
            val updated = state.selectedItems.toMutableMap()
            if (updated.containsKey(item.id)) {
                updated.remove(item.id)
            } else {
                updated[item.id] = item
            }
            state.copy(selectedItems = updated)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedItems = emptyMap()) }
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update {
            it.copy(
                isSearchActive = active,
                searchQuery = if (!active) "" else it.searchQuery
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setPhotoFolder(folder: String) {
        _uiState.update { it.copy(selectedPhotoFolder = folder) }
    }

    fun openStorageExplorer(path: String = app.fileRepository.rootStoragePath) {
        viewModelScope.launch {
            val entries = app.fileRepository.listDirectory(path)
            _uiState.update {
                it.copy(
                    explorerPath = path,
                    explorerEntries = entries,
                    activeBucket = null
                )
            }
        }
    }

    fun navigateUpExplorer(): Boolean {
        val current = _uiState.value.explorerPath ?: return false
        val root = app.fileRepository.rootStoragePath
        if (current == root || current == "/") {
            _uiState.update { it.copy(explorerPath = null, explorerEntries = emptyList()) }
            return true
        }
        val parent = java.io.File(current).parent ?: root
        if (parent.length < root.length) {
            _uiState.update { it.copy(explorerPath = null, explorerEntries = emptyList()) }
        } else {
            openStorageExplorer(parent)
        }
        return true
    }

    fun openSmartBucket(type: SmartBucketType?) {
        _uiState.update { it.copy(activeBucket = type, explorerPath = null) }
    }

    fun playOrToggleAudio(item: MediaItem) {
        app.miniAudioPlayer.playOrToggle(item)
    }

    fun toggleAudioPause() = app.miniAudioPlayer.togglePlayPause()

    fun seekAudio(fraction: Float) = app.miniAudioPlayer.seekTo(fraction)

    fun stopAudio() = app.miniAudioPlayer.stop()

    fun extractAudioFromVideo(video: MediaItem) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    extractionState = ExtractionUiState(
                        activeVideoName = video.name,
                        progress = 0.05f,
                        lastMessage = "Extracting audio from ${video.name}..."
                    )
                )
            }
            val outDir = app.preferencesStore.resolveWritableDownloadDir(
                userPreferences.value.downloadDirectoryPath
            )
            val result = app.videoAudioExtractor.extractAudio(
                videoUri = video.uri,
                videoDisplayName = video.name,
                outputDir = outDir,
                onProgress = { p ->
                    _uiState.update {
                        it.copy(extractionState = it.extractionState.copy(progress = p))
                    }
                }
            )
            _uiState.update {
                it.copy(
                    extractionState = ExtractionUiState(
                        activeVideoName = null,
                        progress = if (result.success) 1f else 0f,
                        lastMessage = result.message
                    )
                )
            }
            if (result.success) {
                loadMedia()
                loadFilesAndStorage()
            }
        }
    }

    fun clearExtractionMessage() {
        _uiState.update { it.copy(extractionState = it.extractionState.copy(lastMessage = null)) }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            app.historyRepository.deleteRecord(id)
        }
    }

    fun clearHistory(direction: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.historyRepository.clearDirection(direction)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            app.preferencesStore.setOnboardingCompleted(true)
            refreshAllData()
        }
    }

    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch { app.preferencesStore.setDarkTheme(dark) }
    }

    fun setLanguage(code: String) {
        viewModelScope.launch { app.preferencesStore.setLanguageCode(code) }
    }

    fun setPrefer5Ghz(prefer: Boolean) {
        viewModelScope.launch { app.preferencesStore.setPrefer5Ghz(prefer) }
    }

    fun setDownloadPath(path: String) {
        viewModelScope.launch {
            app.preferencesStore.setDownloadDirectoryPath(path)
            loadFilesAndStorage()
        }
    }

    override fun onCleared() {
        super.onCleared()
        app.miniAudioPlayer.stop()
    }
}
