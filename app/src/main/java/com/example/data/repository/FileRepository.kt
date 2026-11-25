package com.example.data.repository

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.example.data.local.PreferencesStore
import com.example.data.model.FileEntry
import com.example.data.model.ItemCategory
import com.example.data.model.SmartBucketSummary
import com.example.data.model.SmartBucketType
import com.example.data.model.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class FileRepository(
    private val context: Context,
    private val preferencesStore: PreferencesStore
) {

    val rootStoragePath: String
        get() = Environment.getExternalStorageDirectory().absolutePath

    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val available = stat.availableBlocksLong * stat.blockSizeLong
            StorageInfo(totalBytes = total, availableBytes = available)
        } catch (_: Exception) {
            StorageInfo()
        }
    }

    suspend fun listDirectory(path: String): List<FileEntry> = withContext(Dispatchers.IO) {
        val dir = File(path)
        val children = dir.listFiles() ?: return@withContext emptyList()
        children
            .filter { !it.name.startsWith(".") }
            .map { file ->
                val isDir = file.isDirectory
                FileEntry(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = isDir,
                    sizeBytes = if (isDir) 0L else file.length(),
                    lastModifiedMs = file.lastModified(),
                    childCount = if (isDir) (file.listFiles()?.size ?: 0) else 0,
                    category = if (isDir) ItemCategory.OTHER else classifyExtension(file.extension)
                )
            }
            .sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    suspend fun loadDownloadedFiles(configuredPath: String): List<FileEntry> =
        withContext(Dispatchers.IO) {
            val dirs = buildList {
                add(preferencesStore.resolveWritableDownloadDir(configuredPath))
                val publicDownload = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (publicDownload.exists()) add(publicDownload)
            }.distinctBy { it.absolutePath }

            val collected = mutableListOf<FileEntry>()
            val seenPaths = HashSet<String>()

            for (dir in dirs) {
                val files = dir.listFiles() ?: continue
                for (f in files) {
                    if (f.isFile && !f.name.startsWith(".") && seenPaths.add(f.absolutePath)) {
                        collected.add(
                            FileEntry(
                                name = f.name,
                                path = f.absolutePath,
                                isDirectory = false,
                                sizeBytes = f.length(),
                                lastModifiedMs = f.lastModified(),
                                category = classifyExtension(f.extension)
                            )
                        )
                    }
                }
            }
            collected.sortedByDescending { it.lastModifiedMs }
        }

    suspend fun scanSmartBuckets(): Map<SmartBucketType, SmartBucketSummary> =
        withContext(Dispatchers.IO) {
            val docs = mutableListOf<FileEntry>()
            val ebooks = mutableListOf<FileEntry>()
            val apks = mutableListOf<FileEntry>()
            val archives = mutableListOf<FileEntry>()
            val bigFiles = mutableListOf<FileEntry>()

            val root = Environment.getExternalStorageDirectory()
            val bigThresholdBytes = 50L * 1024L * 1024L // > 50 MB

            val queue = ArrayDeque<Pair<File, Int>>()
            if (root.exists() && root.canRead()) {
                queue.add(root to 0)
            }
            // Also scan app external files dir so sandboxed environments still show received files
            context.getExternalFilesDir(null)?.let { appDir ->
                if (appDir.exists()) queue.add(appDir to 0)
            }

            var visitedFiles = 0
            val maxFilesToScan = 6000
            val maxDepth = 5
            val skippedDirs = setOf("Android", ".thumbnails", ".cache", "cache")

            while (queue.isNotEmpty() && visitedFiles < maxFilesToScan) {
                val (current, depth) = queue.removeFirst()
                val children = current.listFiles() ?: continue
                for (child in children) {
                    if (visitedFiles >= maxFilesToScan) break
                    if (child.name.startsWith(".")) continue
                    if (child.isDirectory) {
                        if (depth < maxDepth && child.name !in skippedDirs) {
                            queue.add(child to (depth + 1))
                        }
                    } else {
                        visitedFiles++
                        val ext = child.extension.lowercase(Locale.ROOT)
                        val size = child.length()
                        val entry = FileEntry(
                            name = child.name,
                            path = child.absolutePath,
                            isDirectory = false,
                            sizeBytes = size,
                            lastModifiedMs = child.lastModified(),
                            category = classifyExtension(ext)
                        )
                        when (ext) {
                            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv" -> docs.add(entry)
                            "txt", "epub", "mobi", "azw3", "fb2" -> ebooks.add(entry)
                            "apk", "apks", "xapk" -> apks.add(entry)
                            "zip", "rar", "7z", "tar", "gz", "bz2" -> archives.add(entry)
                        }
                        if (size >= bigThresholdBytes) {
                            bigFiles.add(entry)
                        }
                    }
                }
            }

            mapOf(
                SmartBucketType.DOCUMENTS to SmartBucketSummary(
                    SmartBucketType.DOCUMENTS, docs.size, docs.sumOf { it.sizeBytes },
                    docs.sortedByDescending { it.lastModifiedMs }
                ),
                SmartBucketType.EBOOKS to SmartBucketSummary(
                    SmartBucketType.EBOOKS, ebooks.size, ebooks.sumOf { it.sizeBytes },
                    ebooks.sortedByDescending { it.lastModifiedMs }
                ),
                SmartBucketType.APKS to SmartBucketSummary(
                    SmartBucketType.APKS, apks.size, apks.sumOf { it.sizeBytes },
                    apks.sortedByDescending { it.lastModifiedMs }
                ),
                SmartBucketType.ARCHIVES to SmartBucketSummary(
                    SmartBucketType.ARCHIVES, archives.size, archives.sumOf { it.sizeBytes },
                    archives.sortedByDescending { it.lastModifiedMs }
                ),
                SmartBucketType.BIG_FILES to SmartBucketSummary(
                    SmartBucketType.BIG_FILES, bigFiles.size, bigFiles.sumOf { it.sizeBytes },
                    bigFiles.sortedByDescending { it.sizeBytes }
                )
            )
        }

    fun classifyExtension(extension: String): ItemCategory {
        return when (extension.lowercase(Locale.ROOT)) {
            "apk", "apks", "xapk" -> ItemCategory.APK
            "jpg", "jpeg", "png", "webp", "gif", "heic" -> ItemCategory.PHOTO
            "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus" -> ItemCategory.MUSIC
            "mp4", "mkv", "webm", "avi", "mov", "3gp" -> ItemCategory.VIDEO
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv" -> ItemCategory.DOCUMENT
            "txt", "epub", "mobi" -> ItemCategory.EBOOK
            "zip", "rar", "7z", "tar", "gz" -> ItemCategory.ARCHIVE
            "vcf", "json" -> ItemCategory.BACKUP
            else -> ItemCategory.OTHER
        }
    }
}
