package com.nayama.music.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RezonaPlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    fun getPlaylistById(id: String): Flow<PlaylistEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity): Int

    @Query("UPDATE playlists SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renamePlaylist(id: String, title: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE playlists SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: String, isPinned: Boolean): Int

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity): Int

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: String): Int

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.orderIndex ASC
    """)
    fun getTracksForPlaylist(playlistId: String): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(crossRef: PlaylistTrackCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTracksToPlaylist(crossRefs: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Int

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearTracksFromPlaylist(playlistId: String): Int

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    fun getTrackCount(playlistId: String): Flow<Int>
}

@Dao
interface RezonaTrackDao {
    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks ORDER BY addedAt DESC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrack(id: String): Int
}

@Dao
interface RezonaLikedTrackDao {
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN liked_tracks lt ON t.id = lt.trackId
        ORDER BY lt.likedAt DESC
    """)
    fun getLikedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_tracks WHERE trackId = :trackId)")
    fun isLiked(trackId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun likeTrack(likedTrack: LikedTrackEntity)

    @Query("DELETE FROM liked_tracks WHERE trackId = :trackId")
    suspend fun unlikeTrack(trackId: String): Int

    @Query("SELECT COUNT(*) FROM liked_tracks")
    fun getLikedCount(): Flow<Int>
}

@Dao
interface RezonaHistoryDao {
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playback_history ph ON t.id = ph.trackId
        ORDER BY ph.playedAt DESC
        LIMIT :limit
    """)
    fun getRecentHistory(limit: Int = 50): Flow<List<TrackEntity>>

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playback_history ph ON t.id = ph.trackId
        GROUP BY t.id
        ORDER BY COUNT(ph.id) DESC, MAX(ph.playedAt) DESC
        LIMIT :limit
    """)
    fun getMostPlayedTracks(limit: Int = 50): Flow<List<TrackEntity>>

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playback_history ph ON t.id = ph.trackId
        WHERE ph.playedAt >= :sinceMs
        GROUP BY t.id
        ORDER BY COUNT(ph.id) DESC, MAX(ph.playedAt) DESC
        LIMIT :limit
    """)
    fun getHistorySince(sinceMs: Long, limit: Int = 50): Flow<List<TrackEntity>>

    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    fun getRecentEntries(limit: Int = 200): Flow<List<PlaybackHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordPlayback(history: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory(): Int
}

@Dao
interface RezonaLyricsDao {
    @Query("SELECT * FROM lyrics_cache WHERE trackKey = :key LIMIT 1")
    suspend fun getLyrics(key: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyrics(lyrics: LyricsCacheEntity)
}

@Dao
interface RezonaSpeedDialDao {
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN pinned_speed_dial p ON t.id = p.trackId
        ORDER BY p.pinnedAt DESC
    """)
    fun getPinnedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM pinned_speed_dial WHERE trackId = :trackId)")
    fun isPinned(trackId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun pinTrack(entity: PinnedSpeedDialEntity)

    @Query("DELETE FROM pinned_speed_dial WHERE trackId = :trackId")
    suspend fun unpinTrack(trackId: String): Int
}

@Dao
interface RezonaOfflineDownloadDao {
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN offline_downloads od ON t.id = od.trackId
        ORDER BY od.downloadedAt DESC
    """)
    fun getDownloadedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM offline_downloads")
    fun getAllDownloadRecords(): Flow<List<OfflineDownloadEntity>>

    @Query("SELECT * FROM offline_downloads WHERE trackId = :trackId LIMIT 1")
    suspend fun getDownloadRecord(trackId: String): OfflineDownloadEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM offline_downloads WHERE trackId = :trackId)")
    fun isDownloaded(trackId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(entity: OfflineDownloadEntity)

    @Query("DELETE FROM offline_downloads WHERE trackId = :trackId")
    suspend fun deleteDownload(trackId: String): Int
}

@Dao
interface RezonaSearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun getRecentQueries(limit: Int = 20): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveQuery(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteQuery(query: String): Int

    @Query("DELETE FROM search_history")
    suspend fun clearAll(): Int
}
