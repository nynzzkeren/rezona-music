package com.nayama.music.data.local

import android.content.ContentValues
import android.database.Cursor
import com.nayama.music.model.Track

class HistoryDao(private val databaseHelper: NayamaDatabase) {

    fun recordTrack(track: Track) {
        val db = databaseHelper.writableDatabase
        // Delete previous occurrences of this track to keep list distinct and move to top
        db.delete("listening_history", "track_id = ?", arrayOf(track.id))

        val values = ContentValues().apply {
            put("track_id", track.id)
            put("title", track.title)
            put("artist", track.artist)
            put("album", track.album)
            put("duration_seconds", track.durationSeconds)
            put("artwork_url", track.artworkUrl)
            put("stream_url", track.streamUrl)
            put("genre", track.genre)
            put("played_at", System.currentTimeMillis())
        }
        db.insert("listening_history", null, values)

        // Keep last 100 entries to prevent unbounded table growth
        db.execSQL(
            """
            DELETE FROM listening_history
            WHERE id NOT IN (
                SELECT id FROM listening_history ORDER BY played_at DESC LIMIT 100
            )
            """.trimIndent()
        )
    }

    fun getRecentlyPlayed(limit: Int = 30): List<Track> {
        val db = databaseHelper.readableDatabase
        val history = mutableListOf<Track>()
        val cursor: Cursor = db.rawQuery(
            """
            SELECT track_id, title, artist, album, duration_seconds, artwork_url, stream_url, genre
            FROM listening_history
            ORDER BY played_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                history.add(
                    Track(
                        id = it.getString(it.getColumnIndexOrThrow("track_id")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        artist = it.getString(it.getColumnIndexOrThrow("artist")),
                        album = it.getString(it.getColumnIndexOrThrow("album")),
                        durationSeconds = it.getInt(it.getColumnIndexOrThrow("duration_seconds")),
                        artworkUrl = it.getString(it.getColumnIndexOrThrow("artwork_url")),
                        streamUrl = it.getString(it.getColumnIndexOrThrow("stream_url")),
                        genre = it.getString(it.getColumnIndexOrThrow("genre")),
                        isPlayable = true,
                        sourceAttribution = "Recently Played"
                    )
                )
            }
        }
        return history
    }

    fun clearHistory() {
        val db = databaseHelper.writableDatabase
        db.delete("listening_history", null, null)
    }
}

