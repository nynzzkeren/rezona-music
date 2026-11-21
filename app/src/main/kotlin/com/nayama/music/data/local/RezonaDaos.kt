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
    @Query("SELECT * FROM playlists WHERE deleted = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylistsRaw(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    fun getPlaylistById(id: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistByIdSync(id: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity): Int

    @Query("UPDATE playlists SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renamePlaylist(id: String, title: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE playlists SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: String, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE playlists SET deleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeletePlaylistById(id: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity): Int

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: String): Int

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId AND pt.deleted = 0
        ORDER BY pt.orderIndex ASC
    """)
    fun getTracksForPlaylist(playlistId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getCrossRefsForPlaylistRaw(playlistId: String): List<PlaylistTrackCrossRef>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId AND deleted = 0 ORDER BY orderIndex ASC")
    suspend fun getActiveCrossRefsForPlaylist(playlistId: String): List<PlaylistTrackCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(crossRef: PlaylistTrackCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTracksToPlaylist(crossRefs: List<PlaylistTrackCrossRef>)

    @Query("UPDATE playlist_tracks SET deleted = 1, updatedAt = :updatedAt WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun softRemoveTrackFromPlaylist(playlistId: String, trackId: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Int

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearTracksFromPlaylist(playlistId: String): Int

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId AND deleted = 0")
    fun getTrackCount(playlistId: String): Flow<Int>

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists(): Int
}

@Dao
interface RezonaTrackDao {
    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks ORDER BY addedAt DESC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks")
    suspend fun getAllTracksRaw(): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrack(id: String): Int

    @Query("DELETE FROM tracks")
    suspend fun deleteAllTracks(): Int
}

@Dao
interface RezonaLikedTrackDao {
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN liked_tracks lt ON t.id = lt.trackId
        WHERE lt.deleted = 0
        ORDER BY lt.likedAt DESC
    """)
    fun getLikedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM liked_tracks")
    suspend fun getAllLikesRaw(): List<LikedTrackEntity>

    @Query("SELECT * FROM liked_tracks WHERE trackId = :trackId LIMIT 1")
    suspend fun getLikeRaw(trackId: String): LikedTrackEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM liked_tracks WHERE trackId = :trackId AND deleted = 0)")
    fun isLiked(trackId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun likeTrack(likedTrack: LikedTrackEntity)

    @Query("UPDATE liked_tracks SET deleted = 1, updatedAt = :updatedAt WHERE trackId = :trackId")
    suspend fun softUnlikeTrack(trackId: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM liked_tracks WHERE trackId = :trackId")
    suspend fun unlikeTrack(trackId: String): Int

    @Query("SELECT COUNT(*) FROM liked_tracks WHERE deleted = 0")
    fun getLikedCount(): Flow<Int>

    @Query("DELETE FROM liked_tracks")
    suspend fun deleteAllLikes(): Int
}

@Dao
interface RezonaHistoryDao {
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playback_history ph ON t.id = ph.trackId
        WHERE ph.deleted = 0
        ORDER BY ph.playedAt DESC
        LIMIT :limit
    """)
    fun getRecentHistory(limit: Int = 50): Flow<List<TrackEntity>>

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playback_history ph ON t.id = ph.trackId
        WHERE ph.deleted = 0
        GROUP BY t.id
        ORDER BY COUNT(ph.id) DESC, MAX(ph.playedAt) DESC
        LIMIT :limit
    """)
    fun getMostPlayedTracks(limit: Int = 50): Flow<List<TrackEntity>>

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playback_history ph ON t.id = ph.trackId
        WHERE ph.playedAt >= :sinceMs AND ph.deleted = 0
        GROUP BY t.id
        ORDER BY COUNT(ph.id) DESC, MAX(ph.playedAt) DESC
        LIMIT :limit
    """)
    fun getHistorySince(sinceMs: Long, limit: Int = 50): Flow<List<TrackEntity>>

    @Query("SELECT * FROM playback_history WHERE deleted = 0 ORDER BY playedAt DESC LIMIT :limit")
    fun getRecentEntries(limit: Int = 200): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun getAllEntriesRaw(limit: Int = 500): List<PlaybackHistoryEntity>

    @Query("SELECT * FROM playback_history WHERE entryId = :entryId LIMIT 1")
    suspend fun getByEntryId(entryId: String): PlaybackHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordPlayback(history: PlaybackHistoryEntity)

    @Query("UPDATE playback_history SET deleted = 1, updatedAt = :updatedAt WHERE deleted = 0")
    suspend fun softClearHistory(updatedAt: Long = System.currentTimeMillis()): Int

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
        WHERE p.deleted = 0
        ORDER BY p.pinnedAt DESC
    """)
    fun getPinnedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM pinned_speed_dial")
    suspend fun getAllPinsRaw(): List<PinnedSpeedDialEntity>

    @Query("SELECT * FROM pinned_speed_dial WHERE trackId = :trackId LIMIT 1")
    suspend fun getPinRaw(trackId: String): PinnedSpeedDialEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM pinned_speed_dial WHERE trackId = :trackId AND deleted = 0)")
    fun isPinned(trackId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun pinTrack(entity: PinnedSpeedDialEntity)

    @Query("UPDATE pinned_speed_dial SET deleted = 1, updatedAt = :updatedAt WHERE trackId = :trackId")
    suspend fun softUnpinTrack(trackId: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM pinned_speed_dial WHERE trackId = :trackId")
    suspend fun unpinTrack(trackId: String): Int

    @Query("DELETE FROM pinned_speed_dial")
    suspend fun deleteAllPins(): Int
}

@Dao
interface RezonaLibraryItemDao {
    @Query("SELECT * FROM library_items WHERE deleted = 0 ORDER BY addedAt DESC")
    fun getAllItems(): Flow<List<LibraryItemEntity>>

    @Query("SELECT * FROM library_items")
    suspend fun getAllItemsRaw(): List<LibraryItemEntity>

    @Query("SELECT * FROM library_items WHERE itemId = :itemId LIMIT 1")
    suspend fun getItemRaw(itemId: String): LibraryItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: LibraryItemEntity)

    @Query("UPDATE library_items SET deleted = 1, updatedAt = :updatedAt WHERE itemId = :itemId")
    suspend fun softDeleteItem(itemId: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM library_items")
    suspend fun deleteAllItems(): Int
}

@Dao
interface RezonaSyncDao {
    @Query("SELECT * FROM sync_operations WHERE uid = :uid ORDER BY updatedAt ASC")
    suspend fun getPendingForUid(uid: String): List<SyncOperationEntity>

    @Query("SELECT COUNT(*) FROM sync_operations WHERE uid = :uid")
    fun observePendingCount(uid: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_operations")
    fun observeTotalPendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOperation(op: SyncOperationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOperations(ops: List<SyncOperationEntity>)

    @Query("DELETE FROM sync_operations WHERE opKey IN (:opKeys)")
    suspend fun deleteByKeys(opKeys: List<String>): Int

    @Query("UPDATE sync_operations SET retryCount = retryCount + 1, lastError = :error WHERE opKey IN (:opKeys)")
    suspend fun markRetryError(opKeys: List<String>, error: String): Int

    @Query("DELETE FROM sync_operations WHERE uid = :uid")
    suspend fun clearForUid(uid: String): Int

    @Query("DELETE FROM sync_operations")
    suspend fun clearAll(): Int
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

    @Query("DELETE FROM offline_downloads")
    suspend fun clearAllDownloads(): Int
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
