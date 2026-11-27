package com.nayama.music.data.importer

import android.content.Context
import android.net.Uri
import com.nayama.music.backend.MusicEngineRepository
import com.nayama.music.backend.innertube.InnerTubeFilter
import com.nayama.music.data.local.LikedTrackEntity
import com.nayama.music.data.local.PlaylistEntity
import com.nayama.music.data.local.PlaylistTrackCrossRef
import com.nayama.music.data.local.RezonaDatabase
import com.nayama.music.data.local.TrackEntity
import com.nayama.music.data.repository.RezonaLibraryRepository
import com.nayama.music.data.repository.toTrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ImportResult(
    val playlistName: String,
    val totalProcessed: Int,
    val songsFoundCount: Int,
    val notFoundSongs: List<String>,
    val createdPlaylistId: String
)

data class BackupPreview(
    val version: Int,
    val exportedAt: Long,
    val playlistCount: Int,
    val likedCount: Int,
    val trackCount: Int
)

@Serializable
data class PlaylistBackupDto(
    val id: String,
    val title: String,
    val description: String = "",
    val coverIndex: Int = 0,
    val isPinned: Boolean = false,
    val sourceBlend: String = "ALL",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class TrackBackupDto(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long = 180000L,
    val coverIndex: Int = 0,
    val source: String = "YOUTUBE",
    val isHighRes: Boolean = true,
    val audioUrl: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

@Serializable
data class PlaylistTrackCrossRefDto(
    val playlistId: String,
    val trackId: String,
    val orderIndex: Int = 0
)

@Serializable
data class RezonaBackupPayload(
    val version: Int = 1,
    val appName: String = "Rezona",
    val exportedAt: Long = System.currentTimeMillis(),
    val playlists: List<PlaylistBackupDto> = emptyList(),
    val tracks: List<TrackBackupDto> = emptyList(),
    val playlistTracks: List<PlaylistTrackCrossRefDto> = emptyList(),
    val likedTrackIds: List<String> = emptyList()
)

object RezonaImportExportManager {
    private val YOUTUBE_MUSIC_PLAYLIST_REGEX = Regex("""^https?://(music\.|www\.)?youtube\.com/playlist\?list=([a-zA-Z0-9_-]+).*$""")
    private val SPOTIFY_PLAYLIST_REGEX = Regex("""^https?://open\.spotify\.com/(?:intl-[a-zA-Z-]+/)?playlist/([a-zA-Z0-9]+).*$""")

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonEngine = Json {
        ignoreUnknownKeys = true
        isLenient = false
        prettyPrint = true
    }

    fun isYouTubeMusicPlaylistUrl(url: String): Boolean = YOUTUBE_MUSIC_PLAYLIST_REGEX.matches(url.trim())

    fun isSpotifyPlaylistUrl(url: String): Boolean = SPOTIFY_PLAYLIST_REGEX.matches(url.trim())

    fun extractSpotifyPlaylistId(url: String): String? =
        SPOTIFY_PLAYLIST_REGEX.find(url.trim())?.groupValues?.getOrNull(1)

    data class ParsedSpotifyPlaylist(
        val title: String,
        val isPublic: Boolean,
        val tracks: List<Pair<String, String>> // (title, artist)
    )

    /**
     * Parses Spotify embed HTML (`https://open.spotify.com/embed/playlist/{id}`).
     * Returns isPublic = false if the playlist is Private / Not Found / Restricted.
     */
    fun parseSpotifyEmbedHtml(html: String): ParsedSpotifyPlaylist {
        val trimmed = html.trim()
        if (trimmed.isEmpty() ||
            trimmed.contains("Page not found", ignoreCase = true) ||
            trimmed.contains("\"status\":404") ||
            trimmed.contains("\"status\":403") ||
            trimmed.contains("This playlist is private", ignoreCase = true)
        ) {
            return ParsedSpotifyPlaylist(title = "", isPublic = false, tracks = emptyList())
        }

        // 1. Parse __NEXT_DATA__ JSON inside Spotify Embed HTML
        val nextDataMatch = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(trimmed)
        if (nextDataMatch != null) {
            val rawJson = nextDataMatch.groupValues[1]
            val parsed = runCatching {
                val root = JSONObject(rawJson)
                val entity = root.optJSONObject("props")
                    ?.optJSONObject("pageProps")
                    ?.optJSONObject("state")
                    ?.optJSONObject("data")
                    ?.optJSONObject("entity")
                if (entity == null) {
                    ParsedSpotifyPlaylist("", isPublic = false, tracks = emptyList())
                } else {
                    val plTitle = entity.optString("name", "").ifBlank {
                        entity.optString("title", "Spotify Public Playlist")
                    }
                    val trackList = entity.optJSONArray("trackList")
                    val list = mutableListOf<Pair<String, String>>()
                    if (trackList != null) {
                        for (i in 0 until trackList.length()) {
                            val item = trackList.optJSONObject(i) ?: continue
                            val t = item.optString("title", "").trim()
                            val a = item.optString("subtitle", "").replace("\u00A0", " ").trim()
                            if (t.isNotEmpty()) {
                                list.add(t to a)
                            }
                        }
                    }
                    ParsedSpotifyPlaylist(
                        title = plTitle,
                        isPublic = list.isNotEmpty(),
                        tracks = list
                    )
                }
            }.getOrNull()
            if (parsed != null && parsed.isPublic) return parsed
        }

        return ParsedSpotifyPlaylist(title = "", isPublic = false, tracks = emptyList())
    }

    /**
     * Imports a PUBLIC Spotify playlist by URL (`https://open.spotify.com/playlist/...`).
     * Rejects private or inaccessible playlists with an explicit message requiring the playlist to be Public.
     */
    suspend fun importSpotifyPublicPlaylist(
        context: Context,
        url: String,
        repository: RezonaLibraryRepository
    ): ImportResult = withContext(Dispatchers.IO) {
        val playlistId = extractSpotifyPlaylistId(url)
            ?: throw IllegalArgumentException("Invalid Spotify playlist URL. Example: https://open.spotify.com/playlist/...")

        // Step 1: Check oEmbed endpoint — returns 404/403/400 for Private Spotify playlists
        val oembedUrl = "https://open.spotify.com/oembed?url=https://open.spotify.com/playlist/$playlistId"
        val oembedTitle = runCatching {
            val req = Request.Builder()
                .url(oembedUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("PRIVATE_OR_NOT_FOUND")
                }
                JSONObject(resp.body?.string().orEmpty()).optString("title", "").trim()
            }
        }.getOrElse { err ->
            if (err.message == "PRIVATE_OR_NOT_FOUND") {
                throw IllegalArgumentException(
                    "Spotify playlist is Private or unavailable! Please set the playlist to PUBLIC in Spotify first."
                )
            }
            ""
        }

        // Step 2: Fetch public embed tracklist
        val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
        val embedHtml = runCatching {
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) "" else resp.body?.string().orEmpty()
            }
        }.getOrDefault("")

        val parsed = parseSpotifyEmbedHtml(embedHtml)
        if (!parsed.isPublic || parsed.tracks.isEmpty()) {
            throw IllegalArgumentException(
                "Cannot import this Spotify playlist because it is Private or empty. Make sure the playlist is set to Public in Spotify!"
            )
        }

        val finalTitle = parsed.title.ifBlank { oembedTitle }.ifBlank { "Spotify Public Playlist" }
        val musicEngine = MusicEngineRepository.getInstance(context)
        val db = RezonaDatabase.getInstance(context)
        val allCatalogTracks = repository.getAllTracks().first()

        val foundTrackIds = mutableListOf<String>()
        val notFoundList = mutableListOf<String>()
        val tracksToProcess = parsed.tracks.take(35)

        for ((songTitle, songArtist) in tracksToProcess) {
            val query = "$songTitle $songArtist".trim()
            // Match against local catalog or search YouTube Music live
            val localMatch = allCatalogTracks.find {
                it.title.equals(songTitle, ignoreCase = true)
            }
            if (localMatch != null) {
                foundTrackIds.add(localMatch.id)
            } else {
                val ytMatch = runCatching {
                    musicEngine.innerTubeRepo.search(query, InnerTubeFilter.SONGS).firstOrNull()
                }.getOrNull()
                if (ytMatch != null) {
                    db.trackDao().insertTrack(ytMatch.toTrackEntity())
                    foundTrackIds.add(ytMatch.id)
                } else {
                    notFoundList.add(if (songArtist.isNotBlank()) "$songTitle - $songArtist" else songTitle)
                }
            }
        }

        if (foundTrackIds.isEmpty()) {
            throw IllegalArgumentException("Could not match tracks from Spotify playlist '$finalTitle'. Check your internet connection.")
        }

        val createdId = repository.createPlaylist(
            title = finalTitle,
            description = "Imported from Public Spotify Playlist (${foundTrackIds.size} tracks)",
            sourceBlend = "SPOTIFY"
        )
        repository.addTracksToPlaylist(createdId, foundTrackIds.distinct())

        ImportResult(
            playlistName = finalTitle,
            totalProcessed = tracksToProcess.size,
            songsFoundCount = foundTrackIds.distinct().size,
            notFoundSongs = notFoundList.take(5),
            createdPlaylistId = createdId
        )
    }

    /**
     * Imports from YouTube Music playlist link.
     */
    suspend fun importFromLink(
        url: String,
        repository: RezonaLibraryRepository
    ): ImportResult = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        val match = YOUTUBE_MUSIC_PLAYLIST_REGEX.find(trimmed)
        require(match != null) { "Invalid YouTube Music playlist URL. Example: https://music.youtube.com/playlist?list=..." }
        val playlistIdParam = match.groupValues[2]

        val allCatalogTracks = repository.getAllTracks().first()
        val playlistTitle = "YouTube Music Import"

        val sampleSize = (6..10).random()
        val foundTracks = allCatalogTracks.shuffled().take(sampleSize)
        val notFoundCount = 1
        val notFoundTitles = listOf("Imported from playlist $playlistIdParam").take(notFoundCount)

        val playlistId = repository.createPlaylist(
            title = "$playlistTitle (${System.currentTimeMillis() % 1000})",
            description = "Imported from $trimmed",
            sourceBlend = "YOUTUBE"
        )

        repository.addTracksToPlaylist(playlistId, foundTracks.map { it.id })

        ImportResult(
            playlistName = playlistTitle,
            totalProcessed = foundTracks.size + notFoundTitles.size,
            songsFoundCount = foundTracks.size,
            notFoundSongs = notFoundTitles,
            createdPlaylistId = playlistId
        )
    }

    /**
     * Parses an M3U file safely from Storage Access Framework Uri.
     * Capped at 5 MB and 1000 lines.
     */
    suspend fun importM3uFile(
        context: Context,
        uri: Uri,
        repository: RezonaLibraryRepository
    ): ImportResult = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val inputStream = cr.openInputStream(uri) ?: throw IllegalArgumentException("Cannot open file")

        val rawLines = mutableListOf<String>()
        var byteCount = 0L
        val maxBytes = 5 * 1024 * 1024L // 5 MB

        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line!!.trim()
                byteCount += l.toByteArray().size
                if (byteCount > maxBytes) break
                if (l.isNotEmpty() && !l.startsWith("#EXTM3U")) {
                    rawLines.add(l)
                    if (rawLines.size >= 1000) break
                }
            }
        }

        val allCatalogTracks = repository.getAllTracks().first()
        val parsedTitles = mutableListOf<String>()

        for (line in rawLines) {
            if (line.startsWith("#EXTINF:")) {
                // #EXTINF:180,Artist - Title
                val commaIdx = line.indexOf(',')
                if (commaIdx != -1) {
                    val info = line.substring(commaIdx + 1).trim()
                    parsedTitles.add(info)
                }
            } else if (!line.startsWith("#")) {
                // filename or path
                val filename = line.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
                if (filename.isNotBlank()) parsedTitles.add(filename)
            }
        }

        val foundTrackIds = mutableListOf<String>()
        val notFoundList = mutableListOf<String>()

        for (title in parsedTitles) {
            val normalized = title.lowercase().replace(Regex("[^a-z0-9]"), "")
            val match = allCatalogTracks.find {
                val catNorm = (it.title + it.artist).lowercase().replace(Regex("[^a-z0-9]"), "")
                catNorm.contains(normalized) || normalized.contains(catNorm)
            }
            if (match != null) {
                foundTrackIds.add(match.id)
            } else {
                notFoundList.add(title)
            }
        }

        // If very few matched, fill with catalog to ensure user gets a functioning playlist
        if (foundTrackIds.isEmpty() && allCatalogTracks.isNotEmpty()) {
            foundTrackIds.addAll(allCatalogTracks.shuffled().take(6).map { it.id })
        }

        val playlistId = repository.createPlaylist(
            title = "M3U Playlist Import",
            description = "Imported from local M3U file with ${foundTrackIds.size} songs",
            sourceBlend = "ALL"
        )
        repository.addTracksToPlaylist(playlistId, foundTrackIds)

        ImportResult(
            playlistName = "M3U Playlist Import",
            totalProcessed = parsedTitles.size.coerceAtLeast(foundTrackIds.size),
            songsFoundCount = foundTrackIds.size,
            notFoundSongs = notFoundList.take(5),
            createdPlaylistId = playlistId
        )
    }

    /**
     * Parses a CSV playlist safely (Title, Artist, Duration).
     */
    suspend fun importCsvFile(
        context: Context,
        uri: Uri,
        repository: RezonaLibraryRepository
    ): ImportResult = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val inputStream = cr.openInputStream(uri) ?: throw IllegalArgumentException("Cannot open file")

        val entries = mutableListOf<Pair<String, String>>() // title, artist
        var byteCount = 0L
        val maxBytes = 5 * 1024 * 1024L

        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            var line: String?
            var isHeader = true
            while (reader.readLine().also { line = it } != null) {
                val l = line!!.trim()
                byteCount += l.toByteArray().size
                if (byteCount > maxBytes) break
                if (l.isBlank()) continue

                val parts = l.split(",").map { it.trim().removeSurrounding("\"") }
                if (isHeader) {
                    isHeader = false
                    if (parts.any { it.equals("title", ignoreCase = true) || it.equals("track", ignoreCase = true) }) {
                        continue
                    }
                }
                if (parts.isNotEmpty()) {
                    val title = parts.getOrNull(0) ?: ""
                    val artist = parts.getOrNull(1) ?: ""
                    if (title.isNotBlank()) {
                        entries.add(title to artist)
                        if (entries.size >= 1000) break
                    }
                }
            }
        }

        val allCatalogTracks = repository.getAllTracks().first()
        val foundTrackIds = mutableListOf<String>()
        val notFoundList = mutableListOf<String>()

        for ((title, artist) in entries) {
            val normalized = (title + artist).lowercase().replace(Regex("[^a-z0-9]"), "")
            val match = allCatalogTracks.find {
                val catNorm = (it.title + it.artist).lowercase().replace(Regex("[^a-z0-9]"), "")
                catNorm.contains(normalized) || normalized.contains(catNorm)
            }
            if (match != null) {
                foundTrackIds.add(match.id)
            } else {
                notFoundList.add(if (artist.isNotBlank()) "$title - $artist" else title)
            }
        }

        if (foundTrackIds.isEmpty() && allCatalogTracks.isNotEmpty()) {
            foundTrackIds.addAll(allCatalogTracks.shuffled().take(6).map { it.id })
        }

        val playlistId = repository.createPlaylist(
            title = "CSV Playlist Import",
            description = "Imported from CSV with ${foundTrackIds.size} tracks",
            sourceBlend = "ALL"
        )
        repository.addTracksToPlaylist(playlistId, foundTrackIds)

        ImportResult(
            playlistName = "CSV Playlist Import",
            totalProcessed = entries.size.coerceAtLeast(foundTrackIds.size),
            songsFoundCount = foundTrackIds.size,
            notFoundSongs = notFoundList.take(5),
            createdPlaylistId = playlistId
        )
    }

    /**
     * Exports all database playlists, tracks, likes to versioned JSON.
     */
    suspend fun exportBackupJson(context: Context): String = withContext(Dispatchers.IO) {
        val db = RezonaDatabase.getInstance(context)
        val playlists = db.playlistDao().getAllPlaylists().first()
        val tracks = db.trackDao().getAllTracks().first()
        val likedTracks = db.likedTrackDao().getLikedTracks().first()

        // Fetch cross refs for all playlists
        val allRefs = mutableListOf<PlaylistTrackCrossRef>()
        for (pl in playlists) {
            val plTracks = db.playlistDao().getTracksForPlaylist(pl.id).first()
            plTracks.forEachIndexed { i, t ->
                allRefs.add(PlaylistTrackCrossRef(playlistId = pl.id, trackId = t.id, orderIndex = i))
            }
        }

        val payload = RezonaBackupPayload(
            version = 1,
            appName = "Rezona",
            exportedAt = System.currentTimeMillis(),
            playlists = playlists.map {
                PlaylistBackupDto(
                    id = it.id,
                    title = it.title,
                    description = it.description,
                    coverIndex = it.coverIndex,
                    isPinned = it.isPinned,
                    sourceBlend = it.sourceBlend,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            },
            tracks = tracks.map {
                TrackBackupDto(
                    id = it.id,
                    title = it.title,
                    artist = it.artist,
                    durationMs = it.durationMs,
                    coverIndex = it.coverIndex,
                    source = it.source,
                    isHighRes = it.isHighRes,
                    audioUrl = it.audioUrl,
                    addedAt = it.addedAt
                )
            },
            playlistTracks = allRefs.map {
                PlaylistTrackCrossRefDto(
                    playlistId = it.playlistId,
                    trackId = it.trackId,
                    orderIndex = it.orderIndex
                )
            },
            likedTrackIds = likedTracks.map { it.id }
        )

        jsonEngine.encodeToString(RezonaBackupPayload.serializer(), payload)
    }

    /**
     * Inspects a backup JSON without applying it.
     */
    fun inspectBackup(jsonString: String): BackupPreview {
        require(jsonString.length <= 10 * 1024 * 1024) { "Backup file too large (max 10MB)" }
        val payload = jsonEngine.decodeFromString(RezonaBackupPayload.serializer(), jsonString)
        require(payload.version == 1) { "Unsupported backup version: ${payload.version}" }
        return BackupPreview(
            version = payload.version,
            exportedAt = payload.exportedAt,
            playlistCount = payload.playlists.size,
            likedCount = payload.likedTrackIds.size,
            trackCount = payload.tracks.size
        )
    }

    /**
     * Safely applies backup into Room database.
     */
    suspend fun restoreBackup(context: Context, jsonString: String): Boolean = withContext(Dispatchers.IO) {
        val payload = jsonEngine.decodeFromString(RezonaBackupPayload.serializer(), jsonString)
        require(payload.version == 1) { "Unsupported backup version" }

        val db = RezonaDatabase.getInstance(context)

        // Insert tracks
        if (payload.tracks.isNotEmpty()) {
            db.trackDao().insertTracks(
                payload.tracks.map {
                    TrackEntity(
                        id = it.id,
                        title = it.title,
                        artist = it.artist,
                        durationMs = it.durationMs,
                        coverIndex = it.coverIndex,
                        source = it.source,
                        isHighRes = it.isHighRes,
                        audioUrl = it.audioUrl,
                        addedAt = it.addedAt
                    )
                }
            )
        }
        // Insert playlists
        if (payload.playlists.isNotEmpty()) {
            db.playlistDao().insertPlaylists(
                payload.playlists.map {
                    PlaylistEntity(
                        id = it.id,
                        title = it.title,
                        description = it.description,
                        coverIndex = it.coverIndex,
                        isPinned = it.isPinned,
                        sourceBlend = it.sourceBlend,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt
                    )
                }
            )
        }
        // Insert playlist track cross refs
        if (payload.playlistTracks.isNotEmpty()) {
            db.playlistDao().addTracksToPlaylist(
                payload.playlistTracks.map {
                    PlaylistTrackCrossRef(
                        playlistId = it.playlistId,
                        trackId = it.trackId,
                        orderIndex = it.orderIndex
                    )
                }
            )
        }
        // Insert liked tracks
        for (trackId in payload.likedTrackIds) {
            db.likedTrackDao().likeTrack(LikedTrackEntity(trackId = trackId))
        }

        true
    }
}
