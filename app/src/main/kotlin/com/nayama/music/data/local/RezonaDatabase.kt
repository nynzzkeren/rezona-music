package com.nayama.music.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        TrackEntity::class,
        PlaylistTrackCrossRef::class,
        LikedTrackEntity::class,
        PlaybackHistoryEntity::class,
        LyricsCacheEntity::class,
        PinnedSpeedDialEntity::class,
        OfflineDownloadEntity::class,
        SearchHistoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class RezonaDatabase : RoomDatabase() {
    abstract fun playlistDao(): RezonaPlaylistDao
    abstract fun trackDao(): RezonaTrackDao
    abstract fun likedTrackDao(): RezonaLikedTrackDao
    abstract fun historyDao(): RezonaHistoryDao
    abstract fun lyricsDao(): RezonaLyricsDao
    abstract fun speedDialDao(): RezonaSpeedDialDao
    abstract fun offlineDownloadDao(): RezonaOfflineDownloadDao
    abstract fun searchHistoryDao(): RezonaSearchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: RezonaDatabase? = null

        fun getInstance(context: Context): RezonaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RezonaDatabase::class.java,
                    "rezona_database.db"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
