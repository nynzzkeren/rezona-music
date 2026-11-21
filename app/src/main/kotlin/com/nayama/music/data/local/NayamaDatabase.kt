package com.nayama.music.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class NayamaDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        const val DATABASE_NAME = "nayama_music.db"
        const val DATABASE_VERSION = 1

        @Volatile
        private var instance: NayamaDatabase? = null

        fun getInstance(context: Context): NayamaDatabase {
            return instance ?: synchronized(this) {
                instance ?: NayamaDatabase(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Liked Songs table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS liked_songs (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT NOT NULL,
                duration_seconds INTEGER NOT NULL,
                artwork_url TEXT,
                stream_url TEXT,
                genre TEXT,
                added_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        // Playlists table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlists (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                artwork_url TEXT,
                is_user_created INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        // Playlist Tracks junction table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlist_tracks (
                playlist_id TEXT NOT NULL,
                track_id TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT NOT NULL,
                duration_seconds INTEGER NOT NULL,
                artwork_url TEXT,
                stream_url TEXT,
                genre TEXT,
                sort_order INTEGER NOT NULL,
                added_at INTEGER NOT NULL,
                PRIMARY KEY (playlist_id, track_id)
            );
            """.trimIndent()
        )

        // Saved Albums table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS saved_albums (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                release_year TEXT NOT NULL,
                artwork_url TEXT,
                track_count INTEGER NOT NULL,
                saved_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        // Listening History table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS listening_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                track_id TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT NOT NULL,
                duration_seconds INTEGER NOT NULL,
                artwork_url TEXT,
                stream_url TEXT,
                genre TEXT,
                played_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        // Create initial default playlist
        val now = System.currentTimeMillis()
        db.execSQL(
            """
            INSERT OR IGNORE INTO playlists (id, title, description, artwork_url, is_user_created, created_at, updated_at)
            VALUES ('pl_favorites', 'My Top Picks', 'Curated tracks by you', null, 1, $now, $now);
            """.trimIndent()
        )

        createIndexes(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        createIndexes(db)
    }

    private fun createIndexes(db: SQLiteDatabase) {
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_track_id ON listening_history(track_id);")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_artist ON listening_history(artist);")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_genre ON listening_history(genre);")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_played_at ON listening_history(played_at DESC);")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_playlist_tracks_pid ON playlist_tracks(playlist_id, sort_order);")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_liked_songs_added ON liked_songs(added_at DESC);")
        } catch (_: Exception) {}
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle future schema migrations cleanly
    }
}

