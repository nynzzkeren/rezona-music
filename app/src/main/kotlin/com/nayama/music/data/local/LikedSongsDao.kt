package com.nayama.music.data.local

import android.content.ContentValues
import android.database.Cursor
import com.nayama.music.model.Track

class LikedSongsDao(private val databaseHelper: NayamaDatabase) {

    fun insert(track: Track) {
        val db = databaseHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", track.id)
            put("title", track.title)
            put("artist", track.artist)
            put("album", track.album)
            put("duration_seconds", track.durationSeconds)
            put("artwork_url", track.artworkUrl)
            put("stream_url", track.streamUrl)
            put("genre", track.genre)
            put("added_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict(
            "liked_songs",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun delete(trackId: String) {
        val db = databaseHelper.writableDatabase
        db.delete("liked_songs", "id = ?", arrayOf(trackId))
    }

    fun isLiked(trackId: String): Boolean {
        val db = databaseHelper.readableDatabase
        val cursor: Cursor = db.rawQuery(
            "SELECT 1 FROM liked_songs WHERE id = ? LIMIT 1",
            arrayOf(trackId)
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    fun getAll(): List<Track> {
        val db = databaseHelper.readableDatabase
        val list = mutableListOf<Track>()
        val cursor: Cursor = db.rawQuery(
            "SELECT id, title, artist, album, duration_seconds, artwork_url, stream_url, genre FROM liked_songs ORDER BY added_at DESC",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    Track(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        artist = it.getString(it.getColumnIndexOrThrow("artist")),
                        album = it.getString(it.getColumnIndexOrThrow("album")),
                        durationSeconds = it.getInt(it.getColumnIndexOrThrow("duration_seconds")),
                        artworkUrl = it.getString(it.getColumnIndexOrThrow("artwork_url")),
                        streamUrl = it.getString(it.getColumnIndexOrThrow("stream_url")),
                        genre = it.getString(it.getColumnIndexOrThrow("genre")),
                        isPlayable = true,
                        sourceAttribution = "Library Liked"
                    )
                )
            }
        }
        return list
    }

    fun count(): Int {
        val db = databaseHelper.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM liked_songs", null)
        var count = 0
        cursor.use {
            if (it.moveToFirst()) {
                count = it.getInt(0)
            }
        }
        return count
    }
}

