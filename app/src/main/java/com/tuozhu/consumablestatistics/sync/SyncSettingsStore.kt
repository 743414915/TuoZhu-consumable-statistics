package com.tuozhu.consumablestatistics.sync

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncSettings(
    val desktopBaseUrl: String = "",
)

class SyncSettingsStore(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val settingsFlow = MutableStateFlow(readSettings())

    fun observeSettings(): StateFlow<SyncSettings> = settingsFlow.asStateFlow()

    fun currentSettings(): SyncSettings = settingsFlow.value

    fun updateDesktopBaseUrl(rawValue: String) {
        val normalized = normalizeBaseUrl(rawValue)
        preferences.edit()
            .putString(KEY_DESKTOP_BASE_URL, normalized)
            .apply()
        settingsFlow.value = SyncSettings(desktopBaseUrl = normalized)
    }

    private fun readSettings(): SyncSettings {
        return SyncSettings(
            desktopBaseUrl = preferences.getString(KEY_DESKTOP_BASE_URL, "").orEmpty(),
        )
    }

    private fun normalizeBaseUrl(rawValue: String): String {
        val trimmed = rawValue.trim().trimEnd('/')
        if (trimmed.isEmpty()) {
            return ""
        }
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    private companion object {
        const val PREFS_NAME = "desktop_sync_settings"
        const val KEY_DESKTOP_BASE_URL = "desktop_base_url"
    }
}
