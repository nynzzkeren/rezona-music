package com.nayama.music.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class StreamingQuality(val label: String, val bitrateLabel: String) {
    AUTO("Otomatis (Adaptif)", "96 - 320 kbps"),
    STANDARD("Standar (Hemat Data)", "128 kbps"),
    HIGH("High Fidelity", "320 kbps")
}

data class UserPreferences(
    val streamingQuality: StreamingQuality = StreamingQuality.HIGH,
    val wifiOnlyStreaming: Boolean = false,
    val autoRetryOnNetworkLoss: Boolean = true,
    val cacheSizeBytes: Long = 0L,
    val spotifyClientId: String = "",
    val spotifyClientSecret: String = "",
    val soundCloudClientId: String = ""
)

class UserPreferencesRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("nayama_preferences", Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(loadPreferences())
    val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    private fun loadPreferences(): UserPreferences {
        val qualityName = prefs.getString("streaming_quality", StreamingQuality.HIGH.name) ?: StreamingQuality.HIGH.name
        val quality = try {
            StreamingQuality.valueOf(qualityName)
        } catch (e: Exception) {
            StreamingQuality.HIGH
        }

        val wifiOnly = prefs.getBoolean("wifi_only", false)
        val autoRetry = prefs.getBoolean("auto_retry", true)
        val spotifyId = prefs.getString("spotify_client_id", "") ?: ""
        val spotifySecret = prefs.getString("spotify_client_secret", "") ?: ""
        val scId = prefs.getString("soundcloud_client_id", "") ?: ""

        return UserPreferences(
            streamingQuality = quality,
            wifiOnlyStreaming = wifiOnly,
            autoRetryOnNetworkLoss = autoRetry,
            cacheSizeBytes = calculateCacheSize(),
            spotifyClientId = spotifyId,
            spotifyClientSecret = spotifySecret,
            soundCloudClientId = scId
        )
    }

    fun setSpotifyCredentials(clientId: String, clientSecret: String) {
        val cleanId = clientId.trim()
        val cleanSecret = clientSecret.trim()
        prefs.edit()
            .putString("spotify_client_id", cleanId)
            .putString("spotify_client_secret", cleanSecret)
            .apply()
        _preferences.value = _preferences.value.copy(
            spotifyClientId = cleanId,
            spotifyClientSecret = cleanSecret
        )
    }

    fun setSoundCloudClientId(clientId: String) {
        val cleanId = clientId.trim()
        prefs.edit().putString("soundcloud_client_id", cleanId).apply()
        _preferences.value = _preferences.value.copy(soundCloudClientId = cleanId)
    }

    fun setStreamingQuality(quality: StreamingQuality) {
        prefs.edit().putString("streaming_quality", quality.name).apply()
        _preferences.value = _preferences.value.copy(streamingQuality = quality)
    }

    fun setWifiOnlyStreaming(enabled: Boolean) {
        prefs.edit().putBoolean("wifi_only", enabled).apply()
        _preferences.value = _preferences.value.copy(wifiOnlyStreaming = enabled)
    }

    fun setAutoRetryOnNetworkLoss(enabled: Boolean) {
        prefs.edit().putBoolean("auto_retry", enabled).apply()
        _preferences.value = _preferences.value.copy(autoRetryOnNetworkLoss = enabled)
    }

    fun calculateCacheSize(): Long {
        var size = 0L
        fun dirSize(dir: File?) {
            if (dir == null || !dir.exists()) return
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) dirSize(file) else size += file.length()
            }
        }
        dirSize(context.cacheDir)
        dirSize(context.externalCacheDir)
        return size
    }

    fun clearCache(): Long {
        val before = calculateCacheSize()
        fun deleteDir(dir: File?) {
            if (dir == null || !dir.exists()) return
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) deleteDir(file)
                file.delete()
            }
        }
        deleteDir(context.cacheDir)
        deleteDir(context.externalCacheDir)
        _preferences.value = _preferences.value.copy(cacheSizeBytes = calculateCacheSize())
        return before
    }

    fun refreshCacheSize() {
        _preferences.value = _preferences.value.copy(cacheSizeBytes = calculateCacheSize())
    }

    companion object {
        @Volatile
        private var instance: UserPreferencesRepository? = null

        fun getInstance(context: Context): UserPreferencesRepository {
            return instance ?: synchronized(this) {
                instance ?: UserPreferencesRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

