package com.example.data.model

import android.net.Uri

enum class ItemCategory(val label: String) {
    APP("App"),
    PHOTO("Photo"),
    MUSIC("Music"),
    VIDEO("Video"),
    DOCUMENT("Document"),
    EBOOK("Ebook"),
    APK("APK"),
    ARCHIVE("Archive"),
    BACKUP("Backup"),
    OTHER("File")
}

enum class TransferDirection {
    SENT,
    RECEIVED
}

enum class FileTransferStatus {
    QUEUED,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class TransferItem(
    val id: String,
    val name: String,
    val path: String,
    val uri: Uri? = null,
    val sizeBytes: Long,
    val mimeType: String = "application/octet-stream",
    val category: ItemCategory = ItemCategory.OTHER,
    val splitPaths: List<String> = emptyList(),
    val packageName: String? = null
) {
    val isSplitApk: Boolean get() = splitPaths.isNotEmpty()
    val effectiveTransferName: String
        get() = when {
            isSplitApk && !name.endsWith(".apks", ignoreCase = true) -> "$name.apks"
            category == ItemCategory.APP && !name.endsWith(".apk", ignoreCase = true) && !isSplitApk -> "$name.apk"
            else -> name
        }
}

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val sourceApkPath: String,
    val splitApkPaths: List<String>,
    val totalSizeBytes: Long,
    val isSystemApp: Boolean
) {
    val isSplitApk: Boolean get() = splitApkPaths.isNotEmpty()

    fun toTransferItem(): TransferItem = TransferItem(
        id = "app:$packageName",
        name = "${appName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}_$versionName" +
            if (isSplitApk) ".apks" else ".apk",
        path = sourceApkPath,
        sizeBytes = totalSizeBytes,
        mimeType = if (isSplitApk) "application/zip" else "application/vnd.android.package-archive",
        category = ItemCategory.APP,
        splitPaths = splitApkPaths,
        packageName = packageName
    )
}

data class MediaItem(
    val id: Long,
    val name: String,
    val path: String,
    val uri: Uri,
    val sizeBytes: Long,
    val durationMs: Long = 0L,
    val dateAddedSec: Long = 0L,
    val bucketName: String = "Device",
    val mimeType: String = "",
    val category: ItemCategory
) {
    fun toTransferItem(): TransferItem = TransferItem(
        id = "${category.name}:$id",
        name = name,
        path = path,
        uri = uri,
        sizeBytes = sizeBytes,
        mimeType = mimeType.ifBlank { "application/octet-stream" },
        category = category
    )
}

data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val childCount: Int = 0,
    val category: ItemCategory = ItemCategory.OTHER
) {
    fun toTransferItem(): TransferItem = TransferItem(
        id = "file:$path",
        name = name,
        path = path,
        sizeBytes = sizeBytes,
        category = category
    )
}

data class StorageInfo(
    val totalBytes: Long = 0L,
    val availableBytes: Long = 0L
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0L)
    val usedFraction: Float
        get() = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

enum class SmartBucketType {
    DOCUMENTS,
    EBOOKS,
    APKS,
    ARCHIVES,
    BIG_FILES
}

data class SmartBucketSummary(
    val type: SmartBucketType,
    val count: Int,
    val totalBytes: Long,
    val files: List<FileEntry>
)

data class FileTransferProgress(
    val itemId: String,
    val fileName: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val status: FileTransferStatus,
    val resumedFromBytes: Long = 0L,
    val savedPath: String? = null
) {
    val progress: Float
        get() = if (totalBytes > 0L) (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        else if (status == FileTransferStatus.COMPLETED) 1f else 0f
}

enum class ConnectionPhase {
    IDLE,
    PREPARING_HOTSPOT,
    WAITING_FOR_RECEIVER,
    CONNECTING_TO_SENDER,
    TRANSFERRING,
    COMPLETED,
    ERROR
}

data class QrConnectionPayload(
    val ssid: String,
    val passphrase: String,
    val hostIp: String,
    val port: Int,
    val deviceName: String,
    val mode: String = "P2P",
    val is5Ghz: Boolean = false
)

data class TransferSessionState(
    val phase: ConnectionPhase = ConnectionPhase.IDLE,
    val direction: TransferDirection = TransferDirection.SENT,
    val qrPayload: QrConnectionPayload? = null,
    val peerName: String = "",
    val files: List<FileTransferProgress> = emptyList(),
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val statusMessage: String = "",
    val errorMessage: String? = null,
    val isMinimized: Boolean = false
) {
    val totalBytes: Long get() = files.sumOf { it.totalBytes }
    val transferredBytes: Long get() = files.sumOf { it.transferredBytes }
    val overallProgress: Float
        get() = if (totalBytes > 0L) (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    val completedCount: Int get() = files.count { it.status == FileTransferStatus.COMPLETED }
}

enum class WebPortalMode {
    PC_DASHBOARD,
    KAIOS_BROWSER,
    SHARE_SELF_APK
}

data class WebServerState(
    val isRunning: Boolean = false,
    val mode: WebPortalMode = WebPortalMode.PC_DASHBOARD,
    val url: String = "",
    val ipAddress: String = "",
    val port: Int = 8080,
    val connectedClients: Int = 0,
    val uploadedCount: Int = 0,
    val downloadedCount: Int = 0,
    val errorMessage: String? = null
)

enum class PhoneCopyRole {
    NONE,
    OLD_PHONE,
    NEW_PHONE
}

enum class PhoneCopyCategoryType {
    APPS,
    PHOTOS,
    VIDEOS,
    MUSIC,
    DOCUMENTS,
    CONTACTS,
    CALL_LOGS,
    SMS
}

data class PhoneCopyCategoryItem(
    val type: PhoneCopyCategoryType,
    val count: Int,
    val totalBytes: Long,
    val selected: Boolean = true,
    val permissionGranted: Boolean = true
)
