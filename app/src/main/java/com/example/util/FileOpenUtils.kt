package com.example.util

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

object LocaleHelper {
    data class AppLanguage(
        val code: String,
        val nativeName: String,
        val englishName: String,
        val isRtl: Boolean = false
    )

    val supportedLanguages = listOf(
        AppLanguage("en", "English", "English", false),
        AppLanguage("hi", "हिन्दी", "Hindi", false),
        AppLanguage("bn", "বাংলা", "Bengali", false),
        AppLanguage("ur", "اردو", "Urdu", true)
    )

    fun createLocalizedConfiguration(base: Context, languageCode: String): Configuration {
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        return Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }

    fun wrapContextWithLocale(base: Context, languageCode: String): Context {
        val config = createLocalizedConfiguration(base, languageCode)
        val configContext = base.createConfigurationContext(config)
        return object : ContextWrapper(base) {
            override fun getResources(): Resources = configContext.resources
        }
    }

    fun isRtl(languageCode: String): Boolean = languageCode == "ur"
}

object FileOpenUtils {
    fun openOrInstallFile(context: Context, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "File not found: ${file.name}", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val ext = file.extension.lowercase(Locale.ROOT)
            val mime = when (ext) {
                "apk" -> "application/vnd.android.package-archive"
                "apks" -> "application/zip"
                else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open ${file.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "No app available to open ${file.name}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAllFilesPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            }
        }
    }

    fun openWifiSettingsPanel(context: Context) {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Settings.Panel.ACTION_WIFI
        } else {
            Settings.ACTION_WIFI_SETTINGS
        }
        try {
            context.startActivity(Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun openBluetoothSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            // Ignored
        }
    }
}
