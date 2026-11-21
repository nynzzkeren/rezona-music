package com.nayama.music.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nayama.music.data.AudiusMusicRepository
import com.nayama.music.data.MusicRepository
import com.nayama.music.data.Resource
import com.nayama.music.model.GenreCategory
import com.nayama.music.model.Playlist
import com.nayama.music.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExploreUiState(
    val categories: List<GenreCategory> = emptyList(),
    val selectedCategory: GenreCategory? = null,
    val categoryTracksState: Resource<List<Track>> = Resource.Loading,
    val playlistsState: Resource<List<Playlist>> = Resource.Loading
)

class ExploreViewModel(
    private val repository: MusicRepository = AudiusMusicRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    init {
        val genres = repository.getCuratedGenres()
        val initialCategory = genres.firstOrNull()
        _uiState.value = _uiState.value.copy(
            categories = genres,
            selectedCategory = initialCategory
        )
        loadData(initialCategory?.title)
    }

    fun selectCategory(category: GenreCategory) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        loadGenreTracks(category.title)
    }

    private fun loadData(genre: String?) {
        loadGenreTracks(genre)
        loadPlaylists()
    }

    private fun loadGenreTracks(genre: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(categoryTracksState = Resource.Loading)
            repository.getTrendingTracks(genre = genre, limit = 20).collect { result ->
                _uiState.value = _uiState.value.copy(categoryTracksState = result)
            }
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            repository.getTrendingPlaylists(limit = 10).collect { result ->
                _uiState.value = _uiState.value.copy(playlistsState = result)
            }
        }
    }

    fun retry() {
        loadData(_uiState.value.selectedCategory?.title)
    }
}

