package com.nayama.music.data.local

import android.content.Context
import com.nayama.music.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UserListeningStats(
    val totalTracksPlayed: Int = 0,
    val totalListeningMinutes: Long = 0L,
    val topArtist: String = "None yet",
    val topGenre: String = "Various",
    val totalLikedSongs: Int = 0,
    val listenerPersona: String = "Nayama Music Explorer",
    val topPlayedTracks: List<Track> = emptyList()
)

class ListeningStatsCalculator(private val context: Context) {

    private val dbHelper = NayamaDatabase.getInstance(context)

    suspend fun computeStats(): UserListeningStats = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase

        var totalPlayed = 0
        var totalSeconds = 0L
        var topArtist = "Belum Ada"
        var topGenre = "Indie / Ambient"
        var totalLiked = 0
        val topTracks = mutableListOf<Track>()

        // 1. Total tracks and seconds
        val countCursor = db.rawQuery("SELECT COUNT(*), SUM(duration_seconds) FROM listening_history", null)
        countCursor.use {
            if (it.moveToFirst()) {
                totalPlayed = it.getInt(0)
                totalSeconds = it.getLong(1)
            }
        }

        // 2. Liked songs count
        val likedCursor = db.rawQuery("SELECT COUNT(*) FROM liked_songs", null)
        likedCursor.use {
            if (it.moveToFirst()) {
                totalLiked = it.getInt(0)
            }
        }

        // 3. Top artist
        val artistCursor = db.rawQuery(
            "SELECT artist, COUNT(*) as cnt FROM listening_history GROUP BY artist ORDER BY cnt DESC LIMIT 1",
            null
        )
        artistCursor.use {
            if (it.moveToFirst() && it.getString(0) != null) {
                topArtist = it.getString(0)
            }
        }

        // 4. Top genre
        val genreCursor = db.rawQuery(
            "SELECT genre, COUNT(*) as cnt FROM listening_history WHERE genre IS NOT NULL AND genre != '' GROUP BY genre ORDER BY cnt DESC LIMIT 1",
            null
        )
        genreCursor.use {
            if (it.moveToFirst() && it.getString(0) != null) {
                topGenre = it.getString(0)
            }
        }

        // 5. Top 5 tracks
        val topTracksCursor = db.rawQuery(
            """
            SELECT track_id, title, artist, album, duration_seconds, artwork_url, stream_url, genre, COUNT(*) as plays
            FROM listening_history
            GROUP BY track_id
            ORDER BY plays DESC
            LIMIT 5
            """.trimIndent(),
            null
        )
        topTracksCursor.use {
            while (it.moveToNext()) {
                topTracks.add(
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
                        sourceAttribution = "Top Played History"
                    )
                )
            }
        }

        // Compute persona
        val persona = when {
            totalPlayed == 0 -> "Pendengar Pemula"
            topGenre.contains("Ambient", ignoreCase = true) || topGenre.contains("Lo-Fi", ignoreCase = true) -> "Chill & Ambient Explorer"
            topGenre.contains("Electronic", ignoreCase = true) || topGenre.contains("House", ignoreCase = true) -> "Synthesizer & Beat Enthusiast"
            topGenre.contains("Hip-Hop", ignoreCase = true) || topGenre.contains("Rap", ignoreCase = true) -> "Urban Rhythm Pioneer"
            topGenre.contains("Acoustic", ignoreCase = true) -> "Organic Melody Curator"
            else -> "Monochrome Sound Explorer"
        }

        UserListeningStats(
            totalTracksPlayed = totalPlayed,
            totalListeningMinutes = totalSeconds / 60,
            topArtist = topArtist,
            topGenre = topGenre,
            totalLikedSongs = totalLiked,
            listenerPersona = persona,
            topPlayedTracks = topTracks
        )
    }
}

