package com.nayama.music.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        SearchHistoryEntity::class,
        LibraryItemEntity::class,
        SyncOperationEntity::class
    ],
    version = 4,
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
    abstract fun libraryItemDao(): RezonaLibraryItemDao
    abstract fun syncDao(): RezonaSyncDao

    companion object {
        @Volatile
        private var INSTANCE: RezonaDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tracks ADD COLUMN album TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tracks ADD COLUMN thumbnailUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tracks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE liked_tracks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE liked_tracks ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE playback_history ADD COLUMN entryId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE playback_history ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE playback_history ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pinned_speed_dial ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pinned_speed_dial ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS library_items (
                        itemId TEXT NOT NULL PRIMARY KEY,
                        itemType TEXT NOT NULL,
                        title TEXT NOT NULL,
                        subtitle TEXT NOT NULL,
                        thumbnailUrl TEXT NOT NULL,
                        addedAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deleted INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_operations (
                        opKey TEXT NOT NULL PRIMARY KEY,
                        uid TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        parentId TEXT NOT NULL,
                        action TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        retryCount INTEGER NOT NULL,
                        lastError TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): RezonaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RezonaDatabase::class.java,
                    "rezona_database.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
