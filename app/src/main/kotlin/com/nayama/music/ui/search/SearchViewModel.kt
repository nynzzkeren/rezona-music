package com.nayama.music.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nayama.music.data.AudiusMusicRepository
import com.nayama.music.data.MusicRepository
import com.nayama.music.data.Resource
import com.nayama.music.data.SearchResult
import com.nayama.music.model.MusicSource
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class SearchCategoryFilter {
    ALL, TRACKS, ARTISTS, PLAYLISTS
}

data class SearchUiState(
    val query: String = "",
    val activeFilter: SearchCategoryFilter = SearchCategoryFilter.ALL,
    val activeSource: MusicSource = MusicSource.ALL,
    val searchResultState: Resource<SearchResult> = Resource.Success(SearchResult()),
    val recentSearches: List<String> = listOf("Snowfall Oneheart", "DJ Remix Indo", "Komang Raim Laode", "Ambient Techno")
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val repository: MusicRepository = AudiusMusicRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private var searchJob: Job? = null

    private var spotifyClientId: String = ""
    private var spotifyClientSecret: String = ""
    private var soundCloudClientId: String = ""

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(380)
                .distinctUntilChanged()
                .collectLatest { query ->
                    executeSearch(query, _uiState.value.activeSource)
                }
        }
    }

    fun updateCredentials(spotifyId: String, spotifySecret: String, scId: String) {
        spotifyClientId = spotifyId
        spotifyClientSecret = spotifySecret
        soundCloudClientId = scId
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
        queryFlow.value = newQuery
    }

    fun setFilter(filter: SearchCategoryFilter) {
        _uiState.value = _uiState.value.copy(activeFilter = filter)
    }

    fun setSourceFilter(source: MusicSource) {
        if (_uiState.value.activeSource == source) return
        _uiState.value = _uiState.value.copy(activeSource = source)
        val q = _uiState.value.query.trim()
        if (q.isNotBlank()) {
            executeSearch(q, source)
        }
    }

    fun selectRecentSearch(search: String) {
        onQueryChanged(search)
    }

    fun removeRecentSearch(search: String) {
        val updated = _uiState.value.recentSearches.filterNot { it.equals(search, ignoreCase = true) }
        _uiState.value = _uiState.value.copy(recentSearches = updated)
    }

    fun clearSearchHistory() {
        _uiState.value = _uiState.value.copy(recentSearches = emptyList())
    }

    private fun executeSearch(query: String, source: MusicSource) {
        val trimmed = query.trim()
        searchJob?.cancel()
        if (trimmed.isBlank()) {
            _uiState.value = _uiState.value.copy(
                searchResultState = Resource.Success(SearchResult())
            )
            return
        }

        searchJob = viewModelScope.launch {
            repository.searchAllBySource(
                query = trimmed,
                source = source,
                spotifyClientId = spotifyClientId,
                spotifyClientSecret = spotifyClientSecret,
                soundCloudClientId = soundCloudClientId
            ).collect { result ->
                _uiState.value = _uiState.value.copy(searchResultState = result)
                if (result is Resource.Success && (result.data.tracks.isNotEmpty() || result.data.artists.isNotEmpty() || result.data.playlists.isNotEmpty())) {
                    saveToRecentSearches(trimmed)
                }
            }
        }
    }

    private fun saveToRecentSearches(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        val current = _uiState.value.recentSearches.toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        val limited = current.take(8)
        _uiState.value = _uiState.value.copy(recentSearches = limited)
    }

    fun retry() {
        executeSearch(_uiState.value.query, _uiState.value.activeSource)
    }
}
