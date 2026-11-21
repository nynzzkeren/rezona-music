package com.nayama.music.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nayama.music.data.local.ListeningStatsCalculator
import com.nayama.music.data.local.StreamingQuality
import com.nayama.music.data.local.UserListeningStats
import com.nayama.music.data.local.UserPreferences
import com.nayama.music.data.local.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val statsCalculator = ListeningStatsCalculator(application)
    private val prefsRepo = UserPreferencesRepository.getInstance(application)

    private val _stats = MutableStateFlow(UserListeningStats())
    val stats: StateFlow<UserListeningStats> = _stats.asStateFlow()

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferences

    init {
        refreshStats()
    }

    fun refreshStats() {
        viewModelScope.launch {
            _stats.value = statsCalculator.computeStats()
            prefsRepo.refreshCacheSize()
        }
    }

    fun setQuality(quality: StreamingQuality) {
        prefsRepo.setStreamingQuality(quality)
    }

    fun setWifiOnly(enabled: Boolean) {
        prefsRepo.setWifiOnlyStreaming(enabled)
    }

    fun setAutoRetry(enabled: Boolean) {
        prefsRepo.setAutoRetryOnNetworkLoss(enabled)
    }

    fun clearCache(): Long {
        return prefsRepo.clearCache()
    }

    fun setSpotifyCredentials(clientId: String, clientSecret: String) {
        prefsRepo.setSpotifyCredentials(clientId, clientSecret)
    }

    fun setSoundCloudClientId(clientId: String) {
        prefsRepo.setSoundCloudClientId(clientId)
    }
}

