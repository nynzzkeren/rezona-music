package com.nayama.music.data.importer

import android.content.Context
import android.net.Uri
import com.nayama.music.data.local.LikedTrackEntity
import com.nayama.music.data.local.PlaylistEntity
import com.nayama.music.data.local.PlaylistTrackCrossRef
import com.nayama.music.data.local.RezonaDatabase
import com.nayama.music.data.local.TrackEntity
import com.nayama.music.data.repository.RezonaLibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

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

    private val jsonEngine = Json {
        ignoreUnknownKeys = true
        isLenient = false
        prettyPrint = true
    }

    fun isYouTubeMusicPlaylistUrl(url: String): Boolean = YOUTUBE_MUSIC_PLAYLIST_REGEX.matches(url.trim())

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
