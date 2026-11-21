package com.nayama.music.data.local

import android.content.ContentValues
import android.database.Cursor
import com.nayama.music.model.Album

class SavedAlbumDao(private val databaseHelper: NayamaDatabase) {

    fun saveAlbum(album: Album) {
        val db = databaseHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", album.id)
            put("title", album.title)
            put("artist", album.artist)
            put("release_year", album.releaseYear)
            put("artwork_url", album.artworkUrl)
            put("track_count", album.trackCount)
            put("saved_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict(
            "saved_albums",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun removeAlbum(albumId: String) {
        val db = databaseHelper.writableDatabase
        db.delete("saved_albums", "id = ?", arrayOf(albumId))
    }

    fun isAlbumSaved(albumId: String): Boolean {
        val db = databaseHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT 1 FROM saved_albums WHERE id = ? LIMIT 1",
            arrayOf(albumId)
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    fun getAllSavedAlbums(): List<Album> {
        val db = databaseHelper.readableDatabase
        val albums = mutableListOf<Album>()
        val cursor: Cursor = db.rawQuery(
            "SELECT id, title, artist, release_year, artwork_url, track_count FROM saved_albums ORDER BY saved_at DESC",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                albums.add(
                    Album(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        artist = it.getString(it.getColumnIndexOrThrow("artist")),
                        releaseYear = it.getString(it.getColumnIndexOrThrow("release_year")),
                        artworkUrl = it.getString(it.getColumnIndexOrThrow("artwork_url")),
                        trackCount = it.getInt(it.getColumnIndexOrThrow("track_count"))
                    )
                )
            }
        }
        return albums
    }
}

