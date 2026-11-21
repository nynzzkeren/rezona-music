package com.nayama.music.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nayama.music.data.AudiusMusicRepository
import com.nayama.music.data.MusicRepository
import com.nayama.music.data.RecommendationEngine
import com.nayama.music.data.RecommendationResult
import com.nayama.music.data.Resource
import com.nayama.music.model.Playlist
import com.nayama.music.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val trendingTracksState: Resource<List<Track>> = Resource.Loading,
    val playlistsState: Resource<List<Playlist>> = Resource.Loading,
    val recommendations: RecommendationResult? = null,
    val isRefreshing: Boolean = false
)

class HomeViewModel(
    private val repository: MusicRepository = AudiusMusicRepository(),
    private var recommendationEngine: RecommendationEngine? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeFeed()
    }

    fun initRecommendationEngine(context: Context) {
        if (recommendationEngine == null) {
            recommendationEngine = RecommendationEngine(context.applicationContext, repository)
            loadRecommendations()
        }
    }

    fun loadHomeFeed() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                trendingTracksState = Resource.Loading,
                playlistsState = Resource.Loading
            )

            launch {
                repository.getTrendingTracks(limit = 40).collect { result ->
                    _uiState.value = _uiState.value.copy(trendingTracksState = result)
                }
            }

            launch {
                repository.getTrendingPlaylists(limit = 12).collect { result ->
                    _uiState.value = _uiState.value.copy(playlistsState = result)
                }
            }

            loadRecommendations()
        }
    }

    fun loadRecommendations() {
        val engine = recommendationEngine ?: return
        viewModelScope.launch {
            val recs = engine.getPersonalizedRecommendations()
            _uiState.value = _uiState.value.copy(recommendations = recs)
        }
    }

    fun retry() {
        loadHomeFeed()
    }
}
