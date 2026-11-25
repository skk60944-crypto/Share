package com.example.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.TransferItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

enum class ShareLaunchResult {
    LAUNCHED_DIRECT,
    LAUNCHED_CHOOSER_FALLBACK,
    NEEDS_BLUETOOTH_ENABLE,
    FAILED
}

object ShareAppHelper {

    private const val APK_MIME = "application/vnd.android.package-archive"
    private const val OCTET_MIME = "application/octet-stream"

    /**
     * Extracts FlashShare's own installable APK from /data/app/.../base.apk into
     * cacheDir/shared_apks/FlashShare.apk so FileProvider can grant read URIs to
     * Bluetooth OPP, WhatsApp, Messages, and any system share target.
     */
    suspend fun extractSelfApk(context: Context): File? = withContext(Dispatchers.IO) {
        try {
            val sourcePath = context.applicationInfo.sourceDir ?: return@withContext null
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists() || !sourceFile.canRead()) return@withContext null

            val shareDir = File(context.cacheDir, "shared_apks").apply { mkdirs() }
            val destFile = File(shareDir, "FlashShare.apk")

            if (!destFile.exists() || destFile.length() != sourceFile.length()) {
                copyFileBuffered(sourceFile, destFile)
            }
            destFile
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Copies any selected TransferItem (APK or regular file) into FileProvider-accessible cache
     * if it lives outside accessible storage (e.g. /data/app/ for installed apps).
     */
    suspend fun prepareShareableFile(context: Context, item: TransferItem): File? =
        withContext(Dispatchers.IO) {
            try {
                val src = File(item.path)
                val shareDir = File(context.cacheDir, "shared_apks").apply { mkdirs() }
                val safeName = item.effectiveTransferName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val dest = File(shareDir, safeName)

                if (src.exists() && src.canRead()) {
                    if (!dest.exists() || dest.length() != src.length()) {
                        copyFileBuffered(src, dest)
                    }
                    return@withContext dest
                }
                if (item.uri != null) {
                    context.contentResolver.openInputStream(item.uri)?.use { input ->
                        BufferedOutputStream(FileOutputStream(dest), 65536).use { out ->
                            input.copyTo(out, 65536)
                        }
                    }
                    if (dest.exists() && dest.length() > 0L) return@withContext dest
                }
                null
            } catch (_: Exception) {
                null
            }
        }

    fun isBluetoothEnabled(context: Context): Boolean {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter: BluetoothAdapter? = bm?.adapter
            adapter != null && adapter.isEnabled
        } catch (_: Exception) {
            true
        }
    }

    fun hasBluetoothAdapter(context: Context): Boolean {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bm?.adapter != null
        } catch (_: Exception) {
            false
        }
    }

    fun getFileProviderUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Launches the real Android Bluetooth OPP device picker directly with the extracted APK file.
     * Supports AOSP (com.android.bluetooth), Android 13+ Mainline (com.google.android.bluetooth),
     * MediaTek, Samsung, Xiaomi, and OEM Bluetooth stacks, with MIME fallback when OEM OPP blocks
     * application/vnd.android.package-archive.
     */
    fun shareFileViaBluetooth(context: Context, file: File): ShareLaunchResult {
        if (!file.exists()) {
            Toast.makeText(context, "Unable to locate APK file", Toast.LENGTH_SHORT).show()
            return ShareLaunchResult.FAILED
        }
        if (hasBluetoothAdapter(context) && !isBluetoothEnabled(context)) {
            return ShareLaunchResult.NEEDS_BLUETOOTH_ENABLE
        }

        val uri = getFileProviderUri(context, file)
        val candidateMimes = listOf(APK_MIME, OCTET_MIME, "*/*")

        for (mime in candidateMimes) {
            val probeIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val activities = queryActivities(context, probeIntent)
            val btMatch = activities.firstOrNull { info ->
                val pkg = info.activityInfo.packageName.lowercase()
                val cls = info.activityInfo.name.lowercase()
                pkg.contains("bluetooth") || cls.contains("bluetooth")
            }

            if (btMatch != null) {
                val targetPkg = btMatch.activityInfo.packageName
                val targetCls = btMatch.activityInfo.name
                try {
                    context.grantUriPermission(
                        targetPkg,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    val directIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri(file.name, uri)
                        component = ComponentName(targetPkg, targetCls)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(directIntent)
                    return ShareLaunchResult.LAUNCHED_DIRECT
                } catch (_: Exception) {
                    // Try next MIME or fallback chooser
                }
            }
        }

        // Fallback to native Android Share Sheet (allows user to pick Bluetooth / Quick Share)
        return shareFileViaSystemChooser(
            context = context,
            file = file,
            chooserTitle = "Share ${file.name} via Bluetooth",
            extraText = null
        )
    }

    /**
     * Shares multiple files/APKs via Android Bluetooth OPP (ACTION_SEND_MULTIPLE) or single file.
     */
    fun shareMultipleFilesViaBluetooth(context: Context, files: List<File>): ShareLaunchResult {
        val valid = files.filter { it.exists() }
        if (valid.isEmpty()) return ShareLaunchResult.FAILED
        if (valid.size == 1) return shareFileViaBluetooth(context, valid.first())

        if (hasBluetoothAdapter(context) && !isBluetoothEnabled(context)) {
            return ShareLaunchResult.NEEDS_BLUETOOTH_ENABLE
        }

        val uris = ArrayList<Uri>(valid.map { getFileProviderUri(context, it) })
        val probe = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val btMatch = queryActivities(context, probe).firstOrNull {
            it.activityInfo.packageName.lowercase().contains("bluetooth")
        }
        if (btMatch != null) {
            val pkg = btMatch.activityInfo.packageName
            uris.forEach { u ->
                context.grantUriPermission(pkg, u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                component = ComponentName(pkg, btMatch.activityInfo.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(intent)
                ShareLaunchResult.LAUNCHED_DIRECT
            } catch (_: Exception) {
                ShareLaunchResult.FAILED
            }
        }

        val chooser = Intent.createChooser(probe, "Share ${valid.size} files via Bluetooth").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(chooser)
            ShareLaunchResult.LAUNCHED_CHOOSER_FALLBACK
        } catch (_: Exception) {
            ShareLaunchResult.FAILED
        }
    }

    /**
     * Shares the real extracted FlashShare.apk directly into WhatsApp or WhatsApp Business.
     * Falls back cleanly to Android's native Share Sheet if WhatsApp is not installed.
     */
    fun shareApkViaWhatsApp(
        context: Context,
        file: File,
        localDownloadUrl: String? = null
    ): ShareLaunchResult {
        if (!file.exists()) {
            Toast.makeText(context, "Unable to extract FlashShare.apk", Toast.LENGTH_SHORT).show()
            return ShareLaunchResult.FAILED
        }

        val uri = getFileProviderUri(context, file)
        val caption = buildString {
            append("Install FlashShare — 100% offline, ad-free file & app sharing!")
            if (!localDownloadUrl.isNullOrBlank()) {
                append("\nLocal Wi-Fi download: $localDownloadUrl")
            }
        }

        val whatsAppPackages = listOf("com.whatsapp", "com.whatsapp.w4b")
        val installedWa = whatsAppPackages.firstOrNull { isPackageInstalled(context, it) }

        if (installedWa != null) {
            try {
                context.grantUriPermission(installedWa, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val waIntent = Intent(Intent.ACTION_SEND).apply {
                    type = OCTET_MIME
                    setPackage(installedWa)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, caption)
                    clipData = ClipData.newRawUri(file.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(waIntent)
                return ShareLaunchResult.LAUNCHED_DIRECT
            } catch (_: Exception) {
                // Fall back to system chooser below
            }
        }

        Toast.makeText(
            context,
            "WhatsApp not found — choose an app to send ${file.name}",
            Toast.LENGTH_SHORT
        ).show()
        return shareFileViaSystemChooser(
            context = context,
            file = file,
            chooserTitle = "Share ${file.name}",
            extraText = caption
        )
    }

    /**
     * Shares via the user's real SMS / Messages app (with local download URL & invite message).
     */
    fun shareInviteViaMessages(
        context: Context,
        localDownloadUrl: String?,
        apkFile: File?
    ): ShareLaunchResult {
        val messageBody = buildString {
            append("Get FlashShare — 100% offline, ad-free file, APK & phone sharing!")
            if (!localDownloadUrl.isNullOrBlank()) {
                append("\nConnect to my Wi-Fi/Hotspot and open: $localDownloadUrl")
            } else {
                append("\nTap Share This App -> Hotspot or Bluetooth to receive FlashShare.apk offline.")
            }
        }

        // Prefer real default SMS app via ACTION_SENDTO smsto:
        try {
            val defaultSmsPkg = Telephony.Sms.getDefaultSmsPackage(context)
            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
                putExtra("sms_body", messageBody)
                if (!defaultSmsPkg.isNullOrBlank()) {
                    setPackage(defaultSmsPkg)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(smsIntent)
            return ShareLaunchResult.LAUNCHED_DIRECT
        } catch (_: Exception) {
            // Try generic SMS chooser without explicit package
            try {
                val genericSms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
                    putExtra("sms_body", messageBody)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(genericSms)
                return ShareLaunchResult.LAUNCHED_DIRECT
            } catch (_: Exception) {
                // Final fallback: system share sheet with APK if available
                if (apkFile != null && apkFile.exists()) {
                    return shareFileViaSystemChooser(context, apkFile, "Share via Messages", messageBody)
                }
                return ShareLaunchResult.FAILED
            }
        }
    }

    fun shareFileViaSystemChooser(
        context: Context,
        file: File,
        chooserTitle: String = "Share ${file.name}",
        extraText: String? = null
    ): ShareLaunchResult {
        return try {
            val uri = getFileProviderUri(context, file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (file.extension.equals("apk", ignoreCase = true)) APK_MIME else "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                if (!extraText.isNullOrBlank()) {
                    putExtra(Intent.EXTRA_TEXT, extraText)
                }
                clipData = ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            ShareLaunchResult.LAUNCHED_CHOOSER_FALLBACK
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to open share dialog", Toast.LENGTH_SHORT).show()
            ShareLaunchResult.FAILED
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun queryActivities(context: Context, intent: Intent) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }

    private fun copyFileBuffered(src: File, dest: File) {
        val buffer = ByteArray(65536)
        BufferedInputStream(FileInputStream(src), 65536).use { input ->
            BufferedOutputStream(FileOutputStream(dest), 65536).use { output ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
    }
}
