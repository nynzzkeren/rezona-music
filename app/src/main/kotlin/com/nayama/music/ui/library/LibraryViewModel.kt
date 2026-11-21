package com.nayama.music.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nayama.music.data.local.LibraryRepository
import com.nayama.music.model.Album
import com.nayama.music.model.Playlist
import com.nayama.music.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LibraryRepository.getInstance(application)

    val likedSongs: StateFlow<List<Track>> = repository.likedSongs
    val playlists: StateFlow<List<Playlist>> = repository.playlists
    val savedAlbums: StateFlow<List<Album>> = repository.savedAlbums
    val recentlyPlayed: StateFlow<List<Track>> = repository.recentlyPlayed

    private val _selectedFilter = MutableStateFlow("All")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    private val _activePlaylist = MutableStateFlow<Playlist?>(null)
    val activePlaylist: StateFlow<Playlist?> = _activePlaylist.asStateFlow()

    private val _activePlaylistTracks = MutableStateFlow<List<Track>>(emptyList())
    val activePlaylistTracks: StateFlow<List<Track>> = _activePlaylistTracks.asStateFlow()

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun createPlaylist(title: String, description: String = "") {
        if (title.isBlank()) return
        repository.createPlaylist(title, description)
    }

    fun renamePlaylist(id: String, newTitle: String, newDescription: String = "") {
        if (newTitle.isBlank()) return
        repository.renamePlaylist(id, newTitle, newDescription)
    }

    fun deletePlaylist(id: String) {
        repository.deletePlaylist(id)
        if (_activePlaylist.value?.id == id) {
            _activePlaylist.value = null
            _activePlaylistTracks.value = emptyList()
        }
    }

    fun toggleLike(track: Track) {
        repository.toggleLike(track)
    }

    fun openPlaylist(playlist: Playlist) {
        _activePlaylist.value = playlist
        loadPlaylistTracks(playlist.id)
    }

    fun closePlaylist() {
        _activePlaylist.value = null
        _activePlaylistTracks.value = emptyList()
    }

    fun loadPlaylistTracks(playlistId: String) {
        viewModelScope.launch {
            _activePlaylistTracks.value = repository.getPlaylistTracks(playlistId)
        }
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        repository.addTrackToPlaylist(playlistId, track)
        if (_activePlaylist.value?.id == playlistId) {
            loadPlaylistTracks(playlistId)
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        repository.removeTrackFromPlaylist(playlistId, trackId)
        if (_activePlaylist.value?.id == playlistId) {
            loadPlaylistTracks(playlistId)
        }
    }

    fun reorderPlaylist(playlistId: String, trackIdsInOrder: List<String>) {
        repository.reorderPlaylist(playlistId, trackIdsInOrder)
        if (_activePlaylist.value?.id == playlistId) {
            loadPlaylistTracks(playlistId)
        }
    }

    fun clearHistory() {
        repository.clearHistory()
    }

    fun toggleSaveAlbum(album: Album) {
        repository.toggleSaveAlbum(album)
    }
}

