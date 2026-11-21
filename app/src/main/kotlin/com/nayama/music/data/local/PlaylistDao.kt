package com.nayama.music.data.local

import android.content.ContentValues
import android.database.Cursor
import com.nayama.music.model.Playlist
import com.nayama.music.model.Track
import java.util.UUID

class PlaylistDao(private val databaseHelper: NayamaDatabase) {

    fun createPlaylist(title: String, description: String = "", artworkUrl: String? = null): Playlist {
        val db = databaseHelper.writableDatabase
        val id = "pl_" + UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("id", id)
            put("title", title)
            put("description", description)
            put("artwork_url", artworkUrl)
            put("is_user_created", 1)
            put("created_at", now)
            put("updated_at", now)
        }
        db.insert("playlists", null, values)
        return Playlist(
            id = id,
            title = title,
            description = description,
            trackCount = 0,
            artworkUrl = artworkUrl,
            isUserCreated = true
        )
    }

    fun updatePlaylist(id: String, title: String, description: String) {
        val db = databaseHelper.writableDatabase
        val values = ContentValues().apply {
            put("title", title)
            put("description", description)
            put("updated_at", System.currentTimeMillis())
        }
        db.update("playlists", values, "id = ?", arrayOf(id))
    }

    fun deletePlaylist(id: String) {
        val db = databaseHelper.writableDatabase
        db.delete("playlists", "id = ?", arrayOf(id))
        db.delete("playlist_tracks", "playlist_id = ?", arrayOf(id))
    }

    fun getAllPlaylists(): List<Playlist> {
        val db = databaseHelper.readableDatabase
        val playlists = mutableListOf<Playlist>()
        val sql = """
            SELECT p.id, p.title, p.description, p.artwork_url, p.is_user_created,
                   COUNT(pt.track_id) as track_count
            FROM playlists p
            LEFT JOIN playlist_tracks pt ON p.id = pt.playlist_id
            GROUP BY p.id
            ORDER BY p.updated_at DESC
        """.trimIndent()

        val cursor = db.rawQuery(sql, null)
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(it.getColumnIndexOrThrow("id"))
                val title = it.getString(it.getColumnIndexOrThrow("title"))
                val desc = it.getString(it.getColumnIndexOrThrow("description"))
                val artwork = it.getString(it.getColumnIndexOrThrow("artwork_url"))
                val isUserCreated = it.getInt(it.getColumnIndexOrThrow("is_user_created")) == 1
                val trackCount = it.getInt(it.getColumnIndexOrThrow("track_count"))

                playlists.add(
                    Playlist(
                        id = id,
                        title = title,
                        description = desc,
                        trackCount = trackCount,
                        artworkUrl = artwork,
                        isUserCreated = isUserCreated
                    )
                )
            }
        }
        return playlists
    }

    fun getPlaylistTracks(playlistId: String): List<Track> {
        val db = databaseHelper.readableDatabase
        val tracks = mutableListOf<Track>()
        val sql = """
            SELECT track_id, title, artist, album, duration_seconds, artwork_url, stream_url, genre
            FROM playlist_tracks
            WHERE playlist_id = ?
            ORDER BY sort_order ASC
        """.trimIndent()

        val cursor = db.rawQuery(sql, arrayOf(playlistId))
        cursor.use {
            while (it.moveToNext()) {
                tracks.add(
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
                        sourceAttribution = "Playlist Track"
                    )
                )
            }
        }
        return tracks
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        val db = databaseHelper.writableDatabase
        // find max sort order
        var nextOrder = 0
        val cursor = db.rawQuery(
            "SELECT MAX(sort_order) FROM playlist_tracks WHERE playlist_id = ?",
            arrayOf(playlistId)
        )
        cursor.use {
            if (it.moveToFirst() && !it.isNull(0)) {
                nextOrder = it.getInt(0) + 1
            }
        }

        val values = ContentValues().apply {
            put("playlist_id", playlistId)
            put("track_id", track.id)
            put("title", track.title)
            put("artist", track.artist)
            put("album", track.album)
            put("duration_seconds", track.durationSeconds)
            put("artwork_url", track.artworkUrl)
            put("stream_url", track.streamUrl)
            put("genre", track.genre)
            put("sort_order", nextOrder)
            put("added_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict(
            "playlist_tracks",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )

        // update playlist updated_at
        val pValues = ContentValues().apply {
            put("updated_at", System.currentTimeMillis())
            // If playlist artwork is empty, set track's artwork
            if (track.artworkUrl != null) {
                put("artwork_url", track.artworkUrl)
            }
        }
        db.update("playlists", pValues, "id = ?", arrayOf(playlistId))
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val db = databaseHelper.writableDatabase
        db.delete(
            "playlist_tracks",
            "playlist_id = ? AND track_id = ?",
            arrayOf(playlistId, trackId)
        )
    }

    fun reorderTracks(playlistId: String, trackIdsInOrder: List<String>) {
        val db = databaseHelper.writableDatabase
        db.beginTransaction()
        try {
            trackIdsInOrder.forEachIndexed { index, trackId ->
                val values = ContentValues().apply {
                    put("sort_order", index)
                }
                db.update(
                    "playlist_tracks",
                    values,
                    "playlist_id = ? AND track_id = ?",
                    arrayOf(playlistId, trackId)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

