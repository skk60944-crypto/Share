package com.example

import android.app.Application
import com.example.data.local.HistoryDatabase
import com.example.data.local.HistoryRepository
import com.example.data.local.PreferencesStore
import com.example.data.repository.AppRepository
import com.example.data.repository.FileRepository
import com.example.data.repository.MediaRepository
import com.example.transfer.LocalWebServer
import com.example.transfer.MiniAudioPlayer
import com.example.transfer.PhoneCopyManager
import com.example.transfer.SocketTransferEngine
import com.example.transfer.VideoAudioExtractor
import com.example.transfer.WiFiDirectManager

class FlashShareApp : Application() {

    lateinit var preferencesStore: PreferencesStore
        private set
    lateinit var historyDatabase: HistoryDatabase
        private set
    lateinit var historyRepository: HistoryRepository
        private set
    lateinit var appRepository: AppRepository
        private set
    lateinit var mediaRepository: MediaRepository
        private set
    lateinit var fileRepository: FileRepository
        private set
    lateinit var wifiDirectManager: WiFiDirectManager
        private set
    lateinit var socketTransferEngine: SocketTransferEngine
        private set
    lateinit var localWebServer: LocalWebServer
        private set
    lateinit var phoneCopyManager: PhoneCopyManager
        private set
    lateinit var videoAudioExtractor: VideoAudioExtractor
        private set
    lateinit var miniAudioPlayer: MiniAudioPlayer
        private set

    override fun onCreate() {
        super.onCreate()
        preferencesStore = PreferencesStore(this)
        historyDatabase = HistoryDatabase.getInstance(this)
        historyRepository = HistoryRepository(historyDatabase)
        appRepository = AppRepository(this)
        mediaRepository = MediaRepository(this)
        fileRepository = FileRepository(this, preferencesStore)
        wifiDirectManager = WiFiDirectManager(this)
        socketTransferEngine = SocketTransferEngine(this, appRepository, historyRepository)
        localWebServer = LocalWebServer(this, appRepository, historyRepository)
        phoneCopyManager = PhoneCopyManager(this, appRepository, mediaRepository, fileRepository)
        videoAudioExtractor = VideoAudioExtractor(this)
        miniAudioPlayer = MiniAudioPlayer(this)
    }
}
