package com.nayama.music.data.repository

import android.content.Context
import com.nayama.music.data.local.LibraryItemEntity
import com.nayama.music.data.local.LikedTrackEntity
import com.nayama.music.data.local.OfflineDownloadEntity
import com.nayama.music.data.local.PinnedSpeedDialEntity
import com.nayama.music.data.local.PlaybackHistoryEntity
import com.nayama.music.data.local.PlaylistEntity
import com.nayama.music.data.local.PlaylistTrackCrossRef
import com.nayama.music.data.local.RezonaDatabase
import com.nayama.music.data.local.RezonaHistoryDao
import com.nayama.music.data.local.RezonaLibraryItemDao
import com.nayama.music.data.local.RezonaLikedTrackDao
import com.nayama.music.data.local.RezonaOfflineDownloadDao
import com.nayama.music.data.local.RezonaPlaylistDao
import com.nayama.music.data.local.RezonaSearchHistoryDao
import com.nayama.music.data.local.RezonaSpeedDialDao
import com.nayama.music.data.local.RezonaTrackDao
import com.nayama.music.data.local.SearchHistoryEntity
import com.nayama.music.data.local.TrackEntity
import com.nayama.music.model.RezonaTrack
import com.nayama.music.sync.RezonaCloudSyncManager
import com.nayama.music.sync.RezonaSyncLogic
import com.nayama.music.ui.home.REAL_POPULAR_SEEDS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Converts a Room TrackEntity into a playable RezonaTrack with real YouTube Music or local cover art & stream URI.
 */
fun TrackEntity.toRezonaTrack(): RezonaTrack {
    val videoId = when {
        audioUrl.contains("v=") -> audioUrl.substringAfter("v=").substringBefore("&")
        id.startsWith("yt_") -> id.removePrefix("yt_")
        else -> id
    }
    val coverUrl = when {
        thumbnailUrl.isNotBlank() -> thumbnailUrl
        videoId.length == 11 -> "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        else -> ""
    }
    val stream = if (audioUrl.isNotBlank()) {
        audioUrl
    } else if (videoId.length == 11) {
        "https://www.youtube.com/watch?v=$videoId"
    } else ""
    val durFormatted = String.format(
        Locale.ENGLISH,
        "%d:%02d",
        (durationMs / 60000L).coerceAtLeast(0L),
        ((durationMs % 60000L) / 1000L).coerceAtLeast(0L)
    )
    return RezonaTrack(
        id = id,
        index = coverIndex,
        title = title,
        artist = artist,
        album = if (source == "On device") "Local Audio" else "YouTube Music",
        duration = durFormatted,
        source = source.ifBlank { "YouTube Music" },
        coverArtUrl = coverUrl,
        streamUrl = stream
    )
}

fun RezonaTrack.toTrackEntity(): TrackEntity {
    val parts = duration.split(":")
    val durMs = ((parts.getOrNull(0)?.toLongOrNull() ?: 3L) * 60L +
        (parts.getOrNull(1)?.toLongOrNull() ?: 30L)) * 1000L
    return TrackEntity(
        id = id,
        title = title,
        artist = artist,
        durationMs = durMs,
        coverIndex = index,
        source = source.ifBlank { "YouTube Music" },
        isHighRes = true,
        audioUrl = streamUrl,
        album = album,
        thumbnailUrl = coverArtUrl,
        updatedAt = System.currentTimeMillis()
    )
}

class RezonaLibraryRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val db = RezonaDatabase.getInstance(appContext)
    private val playlistDao: RezonaPlaylistDao = db.playlistDao()
    private val trackDao: RezonaTrackDao = db.trackDao()
    private val likedDao: RezonaLikedTrackDao = db.likedTrackDao()
    private val historyDao: RezonaHistoryDao = db.historyDao()
    private val speedDialDao: RezonaSpeedDialDao = db.speedDialDao()
    private val downloadDao: RezonaOfflineDownloadDao = db.offlineDownloadDao()
    private val searchHistoryDao: RezonaSearchHistoryDao = db.searchHistoryDao()
    private val libraryItemDao: RezonaLibraryItemDao = db.libraryItemDao()
    private val preferences = com.nayama.music.data.preferences.RezonaPreferences(appContext)
    private val syncManager by lazy { RezonaCloudSyncManager.getInstance(appContext) }
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            cleanupAutoSeededClutterOnce()
        }
    }

    /**
     * Previously the app auto-seeded 11 demo tracks, 3 playlists, and 5 liked songs on cold start,
     * causing a brand-new install to unexpectedly show "Cached 11" and "Liked 5".
     * On first run after upgrade (v2), remove only unplayed default seeds while preserving any user history/likes.
     */
    private suspend fun cleanupAutoSeededClutterOnce() {
        val alreadyCleaned = preferences.cleanupDefaultSeedsV2DoneFlow.first()
        if (alreadyCleaned) return

        val playedIds = historyDao.getRecentEntries(500).first().map { it.trackId }.toSet()
        val seedIds = REAL_POPULAR_SEEDS.map { it.id }.toSet()
        val legacySeedPlaylists = setOf("pl_obsidian_resonance", "pl_late_night_ambient", "pl_milled_glass")

        val playlists = playlistDao.getAllPlaylists().first()
        playlists.forEach { pl ->
            if (pl.id in legacySeedPlaylists) {
                playlistDao.clearTracksFromPlaylist(pl.id)
                playlistDao.deletePlaylistById(pl.id)
            }
        }

        val allTracks = trackDao.getAllTracks().first()
        allTracks.forEach { t ->
            if ((t.id in seedIds || t.id.startsWith("track_")) && t.id !in playedIds) {
                likedDao.unlikeTrack(t.id)
                trackDao.deleteTrack(t.id)
            }
        }

        preferences.markDefaultSeedsV2Cleaned()
    }

    // ── Playlists ─────────────────────────────────────────────────────────────
    fun getAllPlaylists(): Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    fun getPlaylist(id: String): Flow<PlaylistEntity?> = playlistDao.getPlaylistById(id)

    fun getPlaylistTracks(playlistId: String): Flow<List<TrackEntity>> =
        playlistDao.getTracksForPlaylist(playlistId)

    suspend fun createPlaylist(
        title: String,
        description: String = "",
        sourceBlend: String = "YouTube Music",
        coverIndex: Int = (0..9).random()
    ): String {
        val id = "pl_" + UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis().coerceAtLeast(1L)
        val playlist = PlaylistEntity(
            id = id,
            title = title.ifBlank { "New Resonance" },
            description = description,
            coverIndex = coverIndex,
            isPinned = false,
            sourceBlend = sourceBlend.ifBlank { "YouTube Music" },
            createdAt = now,
            updatedAt = now,
            deleted = false
        )
        playlistDao.insertPlaylist(playlist)
        syncManager.onPlaylistChanged(id)
        return id
    }

    suspend fun renamePlaylist(id: String, title: String) {
        val now = System.currentTimeMillis().coerceAtLeast(1L)
        playlistDao.renamePlaylist(id, title.trim(), updatedAt = now)
        syncManager.onPlaylistChanged(id)
    }

    suspend fun togglePinPlaylist(id: String, currentPinned: Boolean) {
        val now = System.currentTimeMillis().coerceAtLeast(1L)
        playlistDao.setPinned(id, !currentPinned, updatedAt = now)
        syncManager.onPlaylistChanged(id)
    }

    suspend fun deletePlaylist(id: String) {
        val now = System.currentTimeMillis().coerceAtLeast(1L)
        playlistDao.softDeletePlaylistById(id, updatedAt = now)
        syncManager.onPlaylistChanged(id)
    }

    suspend fun addTrackToPlaylist(playlistId: String, track: RezonaTrack) {
        val now = System.currentTimeMillis().coerceAtLeast(1L)
        val entity = track.toTrackEntity()
        trackDao.insertTrack(entity)
        val currentTracks = playlistDao.getTracksForPlaylist(playlistId).first()
        val orderIdx = currentTracks.size
        playlistDao.addTrackToPlaylist(
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = track.id,
                orderIndex = orderIdx,
                addedAt = now,
                updatedAt = now,
                deleted = false
            )
        )
        playlistDao.getPlaylistByIdSync(playlistId)?.let { pl ->
            playlistDao.updatePlaylist(pl.copy(updatedAt = now))
        }
        syncManager.onPlaylistTrackChanged(playlistId, entity, orderIdx, deleted = false, updatedAt = now)
    }

    suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<String>) {
        val now = System.currentTimeMillis().coerceAtLeast(1L)
        val currentTracks = playlistDao.getTracksForPlaylist(playlistId).first()
        val startIndex = currentTracks.size
        val crossRefs = trackIds.mapIndexed { idx, tId ->
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = tId,
                orderIndex = startIndex + idx,
                addedAt = now,
                updatedAt = now,
                deleted = false
            )
        }
        playlistDao.addTracksToPlaylist(crossRefs)
        playlistDao.getPlaylistByIdSync(playlistId)?.let { pl ->
            playlistDao.updatePlaylist(pl.copy(updatedAt = now))
        }
        crossRefs.forEach { ref ->
            trackDao.getTrackById(ref.trackId)?.let { entity ->
                syncManager.onPlaylistTrackChanged(playlistId, entity, ref.orderIndex, deleted = false, updatedAt = now)
            }
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val now = System.currentTimeMillis().coerceAtLeast(1L)
        playlistDao.softRemoveTrackFromPlaylist(playlistId, trackId, updatedAt = now)
        playlistDao.getPlaylistByIdSync(playlistId)?.let { pl ->
            playlistDao.updatePlaylist(pl.copy(updatedAt = now))
        }
        trackDao.getTrackById(trackId)?.let { entity ->
            syncManager.onPlaylistTrackChanged(playlistId, entity, 0, deleted = true, updatedAt = now)
        }
    }

    // ── Library & Liked Songs ─────────────────────────────────────────────────
    fun getLikedTracks(): Flow<List<TrackEntity>> = likedDao.getLikedTracks()

    fun getLikedCount(): Flow<Int> = likedDao.getLikedCount()

    fun isLiked(trackId: String): Flow<Boolean> = likedDao.isLiked(trackId)

    suspend fun toggleLike(trackId: String) {
        val now = System.currentTimeMillis().coerceAtLeast(1L)
        val liked = likedDao.isLiked(trackId).first()
        if (liked) {
            likedDao.softUnlikeTrack(trackId, updatedAt = now)
        } else {
            val existing = likedDao.getLikeRaw(trackId)
            likedDao.likeTrack(
                LikedTrackEntity(
                    trackId = trackId,
                    likedAt = existing?.likedAt ?: now,
                    updatedAt = now,
                    deleted = false
                )
            )
        }
        trackDao.getTrackById(trackId)?.let { entity ->
            syncManager.onLikeChanged(entity, liked = !liked, updatedAt = now)
        }
    }

    suspend fun toggleLikeTrack(track: RezonaTrack) {
        trackDao.insertTrack(track.toTrackEntity())
        toggleLike(track.id)
    }

    // ── Library Items (Saved albums/artists/playlists) ─────────────────────────
    fun getSavedLibraryItems(): Flow<List<LibraryItemEntity>> = libraryItemDao.getAllItems()

    suspend fun toggleSaveLibraryItem(
        itemId: String,
        itemType: String,
        title: String,
        subtitle: String = "",
        thumbnailUrl: String = ""
    ): Boolean {
        val now = System.currentTimeMillis().coerceAtLeast(1L)
        val existing = libraryItemDao.getItemRaw(itemId)
        val currentlySaved = existing != null && !existing.deleted
        val next = LibraryItemEntity(
            itemId = itemId,
            itemType = itemType,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnailUrl,
            addedAt = existing?.addedAt ?: now,
            updatedAt = now,
            deleted = currentlySaved
        )
        libraryItemDao.upsertItem(next)
        syncManager.onLibraryItemChanged(next)
        return !currentlySaved
    }

    // ── Speed Dial Pins ───────────────────────────────────────────────────────
    fun getPinnedSpeedDial(): Flow<List<TrackEntity>> = speedDialDao.getPinnedTracks()

    fun isPinnedSpeedDial(trackId: String): Flow<Boolean> = speedDialDao.isPinned(trackId)

    suspend fun togglePinSpeedDial(track: RezonaTrack) {
        val now = System.currentTimeMillis().coerceAtLeast(1L)
        val entity = track.toTrackEntity()
        trackDao.insertTrack(entity)
        val currentlyPinned = speedDialDao.isPinned(track.id).first()
        if (currentlyPinned) {
            speedDialDao.softUnpinTrack(track.id, updatedAt = now)
        } else {
            val existing = speedDialDao.getPinRaw(track.id)
            speedDialDao.pinTrack(
                PinnedSpeedDialEntity(
                    trackId = track.id,
                    pinnedAt = existing?.pinnedAt ?: now,
                    updatedAt = now,
                    deleted = false
                )
            )
        }
        syncManager.onPinChanged(entity, pinned = !currentlyPinned, updatedAt = now)
    }

    // ── Offline Downloads ─────────────────────────────────────────────────────
    fun getDownloadedTracks(): Flow<List<TrackEntity>> = downloadDao.getDownloadedTracks()

    fun getAllDownloadRecords(): Flow<List<OfflineDownloadEntity>> = downloadDao.getAllDownloadRecords()

    fun isDownloaded(trackId: String): Flow<Boolean> = downloadDao.isDownloaded(trackId)

    // ── History & Stats ───────────────────────────────────────────────────────
    fun getRecentHistory(limit: Int = 30): Flow<List<TrackEntity>> = historyDao.getRecentHistory(limit)

    fun getMostPlayed(limit: Int = 50): Flow<List<TrackEntity>> = historyDao.getMostPlayedTracks(limit)

    fun getTopThisWeek(limit: Int = 50): Flow<List<TrackEntity>> {
        val sevenDaysAgo = System.currentTimeMillis() - (7L * 24L * 60L * 60L * 1000L)
        return historyDao.getHistorySince(sevenDaysAgo, limit)
    }

    fun getTopThisMonth(limit: Int = 50): Flow<List<TrackEntity>> {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L)
        return historyDao.getHistorySince(thirtyDaysAgo, limit)
    }

    suspend fun recordPlayback(track: RezonaTrack) {
        val now = System.currentTimeMillis().coerceAtLeast(1L)
        val entity = track.toTrackEntity()
        trackDao.insertTrack(entity)
        val eid = "h_${now}_${UUID.randomUUID().toString().take(6)}"
        val history = PlaybackHistoryEntity(
            trackId = track.id,
            playedAt = now,
            entryId = eid,
            updatedAt = now,
            deleted = false
        )
        historyDao.recordPlayback(history)
        syncManager.onHistoryRecorded(history, entity)
    }

    suspend fun clearPlaybackHistory() {
        val now = System.currentTimeMillis().coerceAtLeast(1L)
        syncManager.onHistoryCleared(now)
        historyDao.softClearHistory(updatedAt = now)
    }

    // ── Search History ────────────────────────────────────────────────────────
    fun getRecentSearchQueries(limit: Int = 15): Flow<List<SearchHistoryEntity>> =
        searchHistoryDao.getRecentQueries(limit)

    suspend fun saveSearchQuery(query: String) {
        val clean = query.trim()
        if (clean.isNotEmpty()) {
            searchHistoryDao.saveQuery(SearchHistoryEntity(query = clean))
        }
    }

    suspend fun deleteSearchQuery(query: String) {
        searchHistoryDao.deleteQuery(query)
    }

    suspend fun clearSearchHistory() {
        searchHistoryDao.clearAll()
    }

    // ── Stored Tracks ─────────────────────────────────────────────────────────
    fun getAllTracks(): Flow<List<TrackEntity>> = trackDao.getAllTracks()

    suspend fun upsertTrack(track: RezonaTrack) {
        trackDao.insertTrack(track.toTrackEntity())
    }

    companion object {
        @Volatile
        private var INSTANCE: RezonaLibraryRepository? = null

        fun getInstance(context: Context): RezonaLibraryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RezonaLibraryRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
