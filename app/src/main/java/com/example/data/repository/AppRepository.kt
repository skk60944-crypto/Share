package com.example.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import com.example.data.model.InstalledAppInfo
import com.example.data.model.ItemCategory
import com.example.data.model.TransferItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AppRepository(private val context: Context) {

    private val iconCache = LruCache<String, Bitmap>(120)

    suspend fun loadInstalledApps(): Pair<List<InstalledAppInfo>, List<InstalledAppInfo>> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledPackages(0)
                }
            } catch (_: Exception) {
                emptyList()
            }

            val localList = mutableListOf<InstalledAppInfo>()
            val systemList = mutableListOf<InstalledAppInfo>()

            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue
                val sourcePath = appInfo.sourceDir ?: continue
                val sourceFile = File(sourcePath)
                if (!sourceFile.exists()) continue

                val splits = appInfo.splitSourceDirs?.filter { File(it).exists() } ?: emptyList()
                var totalBytes = sourceFile.length()
                for (splitPath in splits) {
                    totalBytes += File(splitPath).length()
                }

                val label = try {
                    appInfo.loadLabel(pm).toString()
                } catch (_: Exception) {
                    pkg.packageName
                }

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

                val entry = InstalledAppInfo(
                    packageName = pkg.packageName,
                    appName = label,
                    versionName = pkg.versionName ?: "1.0",
                    sourceApkPath = sourcePath,
                    splitApkPaths = splits,
                    totalSizeBytes = totalBytes,
                    isSystemApp = isSystem
                )

                if (isSystem) systemList.add(entry) else localList.add(entry)
            }

            localList.sortBy { it.appName.lowercase() }
            systemList.sortBy { it.appName.lowercase() }
            localList to systemList
        }

    fun getAppIconBitmap(packageName: String): Bitmap? {
        iconCache.get(packageName)?.let { return it }
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bmp = drawableToBitmap(drawable, 96)
            iconCache.put(packageName, bmp)
            bmp
        } catch (_: Exception) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable, targetPx: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, targetPx, targetPx, true)
        }
        val bitmap = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Resolves an APK TransferItem to an actual readable file on disk.
     * For Split APKs, bundles base.apk + all split APKs into a single `.apks` zip archive.
     */
    suspend fun prepareApkFileForTransfer(item: TransferItem): File = withContext(Dispatchers.IO) {
        if (!item.isSplitApk) {
            return@withContext File(item.path)
        }
        val cacheDir = File(context.cacheDir, "apks_bundle").apply { mkdirs() }
        val safeName = item.effectiveTransferName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val bundleFile = File(cacheDir, safeName)

        ZipOutputStream(BufferedOutputStream(FileOutputStream(bundleFile), 65536)).use { zos ->
            val allFiles = buildList {
                add(File(item.path))
                item.splitPaths.forEach { add(File(it)) }
            }
            val buffer = ByteArray(65536)
            for (apk in allFiles) {
                if (!apk.exists()) continue
                zos.putNextEntry(ZipEntry(apk.name))
                BufferedInputStream(FileInputStream(apk), 65536).use { bis ->
                    var read: Int
                    while (bis.read(buffer).also { read = it } != -1) {
                        zos.write(buffer, 0, read)
                    }
                }
                zos.closeEntry()
            }
        }
        bundleFile
    }

    /**
     * Extracts FlashShare's own base APK so it can be shared directly via Hotspot/Browser.
     */
    fun getSelfApkTransferItem(): TransferItem? {
        return try {
            val appInfo = context.applicationInfo
            val sourceFile = File(appInfo.sourceDir)
            if (!sourceFile.exists()) return null
            val splits = appInfo.splitSourceDirs?.filter { File(it).exists() } ?: emptyList()
            val totalSize = sourceFile.length() + splits.sumOf { File(it).length() }
            TransferItem(
                id = "self_apk",
                name = if (splits.isEmpty()) "FlashShare.apk" else "FlashShare.apks",
                path = sourceFile.absolutePath,
                sizeBytes = totalSize,
                mimeType = if (splits.isEmpty()) "application/vnd.android.package-archive" else "application/zip",
                category = ItemCategory.APP,
                splitPaths = splits,
                packageName = context.packageName
            )
        } catch (_: Exception) {
            null
        }
    }
}
