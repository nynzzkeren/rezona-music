package com.nayama.music.network

import com.nayama.music.model.Artist
import com.nayama.music.model.MusicSource
import com.nayama.music.model.Playlist
import com.nayama.music.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object DeezerMusicProvider {

    private const val BASE_URL = "https://api.deezer.com"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun searchTracks(query: String, limit: Int = 25): List<Track> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "$BASE_URL/search?q=$encoded&limit=$limit"
            val jsonStr = executeGet(url) ?: return@withContext emptyList()
            parseTracks(jsonStr)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun searchArtists(query: String, limit: Int = 10): List<Artist> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "$BASE_URL/search/artist?q=$encoded&limit=$limit"
            val jsonStr = executeGet(url) ?: return@withContext emptyList()
            parseArtists(jsonStr)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun searchPlaylists(query: String, limit: Int = 10): List<Playlist> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "$BASE_URL/search/playlist?q=$encoded&limit=$limit"
            val jsonStr = executeGet(url) ?: return@withContext emptyList()
            parsePlaylists(jsonStr)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getTopCharts(limit: Int = 25): List<Track> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/chart/0/tracks?limit=$limit"
            val jsonStr = executeGet(url) ?: return@withContext emptyList()
            parseTracks(jsonStr)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getGenreChart(genreKey: String, limit: Int = 20): List<Track> = withContext(Dispatchers.IO) {
        val genreId = mapGenreKeyToId(genreKey)
        try {
            val url = "$BASE_URL/chart/$genreId/tracks?limit=$limit"
            val jsonStr = executeGet(url) ?: return@withContext emptyList()
            parseTracks(jsonStr)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mapGenreKeyToId(genreKey: String): Int {
        val key = genreKey.lowercase()
        return when {
            key.contains("electro") || key.contains("dance") || key.contains("edm") -> 106
            key.contains("hip-hop") || key.contains("rap") || key.contains("urban") -> 116
            key.contains("rock") || key.contains("metal") || key.contains("alternative") -> 152
            key.contains("r&b") || key.contains("soul") -> 165
            key.contains("pop") -> 132
            key.contains("ambient") || key.contains("classical") || key.contains("acoustic") -> 98
            else -> 0
        }
    }

    private fun executeGet(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) NayamaMusic/1.0")
            .header("Accept", "application/json")
            .build()
        val response = httpClient.newCall(request).execute()
        return if (response.isSuccessful) response.body?.string() else null
    }

    private fun parseTracks(jsonStr: String): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            val root = JSONObject(jsonStr)
            val data = root.optJSONArray("data") ?: return emptyList()

            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optLong("id", -1L)
                if (id <= 0) continue

                val title = item.optString("title").trim()
                if (title.isBlank()) continue

                val duration = item.optInt("duration", 0)
                val preview = item.optString("preview").trim()
                if (preview.isBlank()) continue

                val artistObj = item.optJSONObject("artist")
                val artistName = artistObj?.optString("name")?.trim() ?: "Unknown Artist"

                val albumObj = item.optJSONObject("album")
                val albumTitle = albumObj?.optString("title")?.trim() ?: "Single"

                val artworkUrl = albumObj?.optString("cover_xl")?.ifBlank { null }
                    ?: albumObj?.optString("cover_big")?.ifBlank { null }
                    ?: albumObj?.optString("cover_medium")?.ifBlank { null }

                tracks.add(
                    Track(
                        id = "deezer_$id",
                        title = title,
                        artist = artistName,
                        album = albumTitle,
                        durationSeconds = duration,
                        artworkUrl = artworkUrl,
                        streamUrl = preview,
                        genre = null,
                        playCount = null,
                        isPlayable = true,
                        sourceAttribution = "Deezer Master",
                        source = MusicSource.DEEZER,
                        audioQualityLabel = "MP3 320 KBPS"
                    )
                )
            }
        } catch (_: Exception) {
            // Ignore parse errors
        }
        return tracks
    }

    private fun parseArtists(jsonStr: String): List<Artist> {
        val artists = mutableListOf<Artist>()
        try {
            val root = JSONObject(jsonStr)
            val data = root.optJSONArray("data") ?: return emptyList()

            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optLong("id", -1L)
                if (id <= 0) continue

                val name = item.optString("name").trim()
                if (name.isBlank()) continue

                val fans = item.optInt("nb_fan", 0)
                val formattedFans = if (fans >= 1000) "${fans / 1000}k followers" else "$fans followers"

                val artworkUrl = item.optString("picture_big").ifBlank { null }
                    ?: item.optString("picture_medium").ifBlank { null }

                artists.add(
                    Artist(
                        id = "deezer_artist_$id",
                        name = name,
                        genre = "Verified Artist",
                        monthlyListeners = formattedFans,
                        avatarUrl = artworkUrl
                    )
                )
            }
        } catch (_: Exception) {
            // Ignore parse errors
        }
        return artists
    }

    private fun parsePlaylists(jsonStr: String): List<Playlist> {
        val playlists = mutableListOf<Playlist>()
        try {
            val root = JSONObject(jsonStr)
            val data = root.optJSONArray("data") ?: return emptyList()

            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optLong("id", -1L)
                if (id <= 0) continue

                val title = item.optString("title").trim()
                if (title.isBlank()) continue

                val nbTracks = item.optInt("nb_tracks", 0)
                val creatorName = item.optJSONObject("user")?.optString("name") ?: "Nayama Curation"

                val artworkUrl = item.optString("picture_big").ifBlank { null }
                    ?: item.optString("picture_medium").ifBlank { null }

                playlists.add(
                    Playlist(
                        id = "deezer_playlist_$id",
                        title = title,
                        description = "Curated by $creatorName • $nbTracks songs",
                        artworkUrl = artworkUrl,
                        trackCount = nbTracks
                    )
                )
            }
        } catch (_: Exception) {
            // Ignore parse errors
        }
        return playlists
    }
}
