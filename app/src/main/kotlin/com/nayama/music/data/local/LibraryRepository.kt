package com.nayama.music.data.local

import android.content.Context
import com.nayama.music.model.Album
import com.nayama.music.model.Playlist
import com.nayama.music.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryRepository private constructor(context: Context) {

    private val db = NayamaDatabase.getInstance(context)
    private val likedSongsDao = LikedSongsDao(db)
    private val playlistDao = PlaylistDao(db)
    private val savedAlbumDao = SavedAlbumDao(db)
    private val historyDao = HistoryDao(db)

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _likedSongs = MutableStateFlow<List<Track>>(emptyList())
    val likedSongs: StateFlow<List<Track>> = _likedSongs.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _savedAlbums = MutableStateFlow<List<Album>>(emptyList())
    val savedAlbums: StateFlow<List<Album>> = _savedAlbums.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<Track>>(emptyList())
    val recentlyPlayed: StateFlow<List<Track>> = _recentlyPlayed.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        repositoryScope.launch {
            val liked = likedSongsDao.getAll()
            val pls = playlistDao.getAllPlaylists()
            val albums = savedAlbumDao.getAllSavedAlbums()
            val history = historyDao.getRecentlyPlayed(30)

            _likedSongs.value = liked
            _playlists.value = pls
            _savedAlbums.value = albums
            _recentlyPlayed.value = history
        }
    }

    suspend fun isLiked(trackId: String): Boolean = withContext(Dispatchers.IO) {
        likedSongsDao.isLiked(trackId)
    }

    fun toggleLike(track: Track) {
        repositoryScope.launch {
            if (likedSongsDao.isLiked(track.id)) {
                likedSongsDao.delete(track.id)
            } else {
                likedSongsDao.insert(track)
            }
            _likedSongs.value = likedSongsDao.getAll()
        }
    }

    fun createPlaylist(title: String, description: String = "") {
        repositoryScope.launch {
            playlistDao.createPlaylist(title.trim(), description.trim())
            _playlists.value = playlistDao.getAllPlaylists()
        }
    }

    suspend fun createAndGetPlaylist(title: String, description: String = ""): Playlist = withContext(Dispatchers.IO) {
        val created = playlistDao.createPlaylist(title.trim(), description.trim())
        _playlists.value = playlistDao.getAllPlaylists()
        created
    }

    fun renamePlaylist(id: String, newTitle: String, newDescription: String = "") {
        repositoryScope.launch {
            playlistDao.updatePlaylist(id, newTitle.trim(), newDescription.trim())
            _playlists.value = playlistDao.getAllPlaylists()
        }
    }

    fun deletePlaylist(id: String) {
        repositoryScope.launch {
            playlistDao.deletePlaylist(id)
            _playlists.value = playlistDao.getAllPlaylists()
        }
    }

    suspend fun getPlaylistTracks(playlistId: String): List<Track> = withContext(Dispatchers.IO) {
        playlistDao.getPlaylistTracks(playlistId)
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        repositoryScope.launch {
            playlistDao.addTrackToPlaylist(playlistId, track)
            _playlists.value = playlistDao.getAllPlaylists()
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        repositoryScope.launch {
            playlistDao.removeTrackFromPlaylist(playlistId, trackId)
            _playlists.value = playlistDao.getAllPlaylists()
        }
    }

    fun reorderPlaylist(playlistId: String, trackIdsInOrder: List<String>) {
        repositoryScope.launch {
            playlistDao.reorderTracks(playlistId, trackIdsInOrder)
            _playlists.value = playlistDao.getAllPlaylists()
        }
    }

    fun recordHistory(track: Track) {
        repositoryScope.launch {
            historyDao.recordTrack(track)
            _recentlyPlayed.value = historyDao.getRecentlyPlayed(30)
        }
    }

    fun clearHistory() {
        repositoryScope.launch {
            historyDao.clearHistory()
            _recentlyPlayed.value = emptyList()
        }
    }

    fun toggleSaveAlbum(album: Album) {
        repositoryScope.launch {
            if (savedAlbumDao.isAlbumSaved(album.id)) {
                savedAlbumDao.removeAlbum(album.id)
            } else {
                savedAlbumDao.saveAlbum(album)
            }
            _savedAlbums.value = savedAlbumDao.getAllSavedAlbums()
        }
    }

    companion object {
        @Volatile
        private var instance: LibraryRepository? = null

        fun getInstance(context: Context): LibraryRepository {
            return instance ?: synchronized(this) {
                instance ?: LibraryRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

