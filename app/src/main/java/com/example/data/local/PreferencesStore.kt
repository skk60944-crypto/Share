package com.example.data.local

import android.content.Context
import android.os.Environment
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

private val Context.flashShareDataStore by preferencesDataStore(name = "flashshare_prefs")

data class UserPreferences(
    val hasCompletedOnboarding: Boolean = false,
    val isDarkTheme: Boolean = true,
    val languageCode: String = "en",
    val prefer5Ghz: Boolean = true,
    val downloadDirectoryPath: String = ""
)

class PreferencesStore(private val context: Context) {

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val PREFER_5GHZ = booleanPreferencesKey("prefer_5ghz")
        val DOWNLOAD_PATH = stringPreferencesKey("download_path")
    }

    val defaultDownloadDir: String
        get() {
            val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(base, "FlashShare")
            return dir.absolutePath
        }

    val preferencesFlow: Flow<UserPreferences> = context.flashShareDataStore.data.map { prefs ->
        UserPreferences(
            hasCompletedOnboarding = prefs[Keys.ONBOARDING_DONE] ?: false,
            isDarkTheme = prefs[Keys.DARK_THEME] ?: true,
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: "en",
            prefer5Ghz = prefs[Keys.PREFER_5GHZ] ?: true,
            downloadDirectoryPath = prefs[Keys.DOWNLOAD_PATH]?.takeIf { it.isNotBlank() }
                ?: defaultDownloadDir
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.flashShareDataStore.edit { it[Keys.ONBOARDING_DONE] = completed }
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.flashShareDataStore.edit { it[Keys.DARK_THEME] = dark }
    }

    suspend fun setLanguageCode(code: String) {
        context.flashShareDataStore.edit { it[Keys.LANGUAGE_CODE] = code }
    }

    suspend fun setPrefer5Ghz(prefer: Boolean) {
        context.flashShareDataStore.edit { it[Keys.PREFER_5GHZ] = prefer }
    }

    suspend fun setDownloadDirectoryPath(path: String) {
        context.flashShareDataStore.edit { it[Keys.DOWNLOAD_PATH] = path }
    }

    fun resolveWritableDownloadDir(configuredPath: String): File {
        val candidate = File(configuredPath.ifBlank { defaultDownloadDir })
        if ((candidate.exists() || candidate.mkdirs()) && candidate.canWrite()) {
            return candidate
        }
        val appExternal = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
            "FlashShare"
        )
        appExternal.mkdirs()
        return appExternal
    }
}
