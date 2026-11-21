package com.nayama.music.backend

import android.content.Context
import android.util.Log
import com.nayama.music.backend.innertube.InnerTubeFilter
import com.nayama.music.backend.innertube.InnerTubeRepository
import com.nayama.music.model.RezonaTrack
import com.nayama.music.model.SyncedLyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class ProviderSearchState {
    IDLE,
    SEARCHING,
    SUCCESS,
    TIMEOUT,
    ERROR
}

data class ProviderStatusInfo(
    val name: String,
    val state: ProviderSearchState = ProviderSearchState.IDLE,
    val resultCount: Int = 0,
    val error: String? = null
)

data class ProgressiveSearchUpdate(
    val tracks: List<RezonaTrack>,
    val isSearching: Boolean,
    val providers: Map<String, ProviderStatusInfo>,
    val continuation: String? = null
)

data class ResolvedLyricsBundle(
    val lines: List<SyncedLyricLine>,
    val sourceLabel: String,
    val sourceId: LyricSourceId = LyricSourceId.AUTO,
    val isSynced: Boolean = true
)

/**
 * Unified Music Engine Repository.
 * Powered exclusively by YouTube Music (InnerTube data layer + NewPipe stream resolution)
 * and LRCLIB for synced lyrics (+ YouTube Music Lyrics tab fallback).
 */
class MusicEngineRepository(private val context: Context) {

    companion object {
        private const val TAG = "MusicEngineRepository"

        @Volatile
        private var instance: MusicEngineRepository? = null

        fun getInstance(context: Context): MusicEngineRepository {
            return instance ?: synchronized(this) {
                instance ?: MusicEngineRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    val ytProvider = YouTubeMusicProvider()
    val innerTubeRepo = InnerTubeRepository.getInstance()
    val lyricsProvider = LyricsProvider(context)

    suspend fun searchTracks(query: String, filter: String = "ALL"): List<RezonaTrack> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val innerFilter = when (filter.uppercase()) {
            "SONGS" -> InnerTubeFilter.SONGS
            "VIDEOS" -> InnerTubeFilter.VIDEOS
            "ALBUMS" -> InnerTubeFilter.ALBUMS
            "ARTISTS" -> InnerTubeFilter.ARTISTS
            "PLAYLISTS" -> InnerTubeFilter.PLAYLISTS
            else -> InnerTubeFilter.ALL
        }
        val primary = runCatching { innerTubeRepo.search(trimmed, innerFilter) }.getOrNull().orEmpty()
        if (innerFilter == InnerTubeFilter.ALL && primary.size < 10) {
            val songs = runCatching { innerTubeRepo.search(trimmed, InnerTubeFilter.SONGS) }.getOrNull().orEmpty()
            val merged = (primary + songs).distinctBy { it.id }.mapIndexed { idx, t -> t.copy(index = idx) }
            if (merged.isNotEmpty()) return@withContext merged
        } else if (primary.isNotEmpty()) {
            return@withContext primary
        }

        ytProvider.searchSongs(trimmed, maxResults = 20)
    }

    fun searchTracksProgressive(
        query: String,
        sourceFilter: String = "ALL"
    ): Flow<ProgressiveSearchUpdate> = channelFlow {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            send(ProgressiveSearchUpdate(emptyList(), false, emptyMap(), null))
            return@channelFlow
        }

        val mutex = Mutex()
        val aggregated = LinkedHashMap<String, RezonaTrack>()
        var latestContinuation: String? = null
        val statuses = mutableMapOf(
            "YOUTUBE" to ProviderStatusInfo("YouTube Music", ProviderSearchState.SEARCHING)
        )

        send(ProgressiveSearchUpdate(emptyList(), true, statuses.toMap(), null))

        val filter = when (sourceFilter.uppercase()) {
            "SONGS" -> InnerTubeFilter.SONGS
            "VIDEOS" -> InnerTubeFilter.VIDEOS
            "ALBUMS" -> InnerTubeFilter.ALBUMS
            "ARTISTS" -> InnerTubeFilter.ARTISTS
            "PLAYLISTS" -> InnerTubeFilter.PLAYLISTS
            else -> InnerTubeFilter.ALL
        }

        coroutineScope {
            if (filter == InnerTubeFilter.ALL) {
                val catalogJob = launch {
                    val page = withTimeoutOrNull(6000L) {
                        runCatching { innerTubeRepo.searchPage(trimmed, InnerTubeFilter.ALL) }.getOrNull()
                    }
                    if (page != null && page.items.isNotEmpty()) {
                        mutex.withLock {
                            page.items.forEach { if (!aggregated.containsKey(it.id)) aggregated[it.id] = it }
                            if (latestContinuation == null) latestContinuation = page.continuation
                            val list = aggregated.values.mapIndexed { idx, t -> t.copy(index = idx) }
                            statuses["YOUTUBE"] = ProviderStatusInfo("YouTube Music", ProviderSearchState.SEARCHING, list.size)
                            send(ProgressiveSearchUpdate(list, true, statuses.toMap(), latestContinuation))
                        }
                    }
                }
                val songsJob = launch {
                    val page = withTimeoutOrNull(6000L) {
                        runCatching { innerTubeRepo.searchPage(trimmed, InnerTubeFilter.SONGS) }.getOrNull()
                    }
                    if (page != null && page.items.isNotEmpty()) {
                        mutex.withLock {
                            page.items.forEach { if (!aggregated.containsKey(it.id)) aggregated[it.id] = it }
                            latestContinuation = page.continuation ?: latestContinuation
                            val list = aggregated.values.mapIndexed { idx, t -> t.copy(index = idx) }
                            statuses["YOUTUBE"] = ProviderStatusInfo("YouTube Music", ProviderSearchState.SEARCHING, list.size)
                            send(ProgressiveSearchUpdate(list, true, statuses.toMap(), latestContinuation))
                        }
                    }
                }
                catalogJob.join()
                songsJob.join()
            } else {
                val page = withTimeoutOrNull(6000L) {
                    runCatching { innerTubeRepo.searchPage(trimmed, filter) }.getOrNull()
                }
                if (page != null && page.items.isNotEmpty()) {
                    mutex.withLock {
                        page.items.forEach { if (!aggregated.containsKey(it.id)) aggregated[it.id] = it }
                        latestContinuation = page.continuation
                    }
                }
            }
        }

        val currentList = aggregated.values.mapIndexed { idx, t -> t.copy(index = idx) }
        if (currentList.isNotEmpty()) {
            statuses["YOUTUBE"] = ProviderStatusInfo("YouTube Music", ProviderSearchState.SUCCESS, currentList.size)
            send(ProgressiveSearchUpdate(currentList, false, statuses.toMap(), latestContinuation))
            return@channelFlow
        }

        try {
            val newPipeResults = withTimeoutOrNull(6000L) {
                ytProvider.searchSongs(trimmed, maxResults = 25)
            }
            if (newPipeResults != null && newPipeResults.isNotEmpty()) {
                statuses["YOUTUBE"] = ProviderStatusInfo("YouTube Music", ProviderSearchState.SUCCESS, newPipeResults.size)
                send(ProgressiveSearchUpdate(newPipeResults, false, statuses.toMap(), null))
            } else if (newPipeResults != null) {
                statuses["YOUTUBE"] = ProviderStatusInfo("YouTube Music", ProviderSearchState.SUCCESS, 0)
                send(ProgressiveSearchUpdate(emptyList(), false, statuses.toMap(), null))
            } else {
                statuses["YOUTUBE"] = ProviderStatusInfo("YouTube Music", ProviderSearchState.TIMEOUT, 0, "Timed out (6s)")
                send(ProgressiveSearchUpdate(emptyList(), false, statuses.toMap(), null))
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Search error", e)
            statuses["YOUTUBE"] = ProviderStatusInfo("YouTube Music", ProviderSearchState.ERROR, 0, e.message ?: "Search failed")
            send(ProgressiveSearchUpdate(emptyList(), false, statuses.toMap(), null))
        }
    }

    suspend fun resolveStreamWithFallback(track: RezonaTrack, bypassCache: Boolean = false): ResolvedStream? = withContext(Dispatchers.IO) {
        // 1. Check local/on-device URI or app-private offline file first
        if (track.streamUrl.startsWith("content://") || track.streamUrl.startsWith("file://")) {
            return@withContext ResolvedStream(
                streamUrl = track.streamUrl,
                bitrateKbps = 320,
                format = "Local Audio",
                expiresAtMs = Long.MAX_VALUE
            )
        }
        val offlineUri = OfflineDownloadManager.getInstance(context).getLocalUriIfDownloaded(track.id)
        if (offlineUri != null) {
            return@withContext ResolvedStream(
                streamUrl = offlineUri,
                bitrateKbps = 192,
                format = "Offline Cache",
                expiresAtMs = Long.MAX_VALUE
            )
        }

        // 2. Online extraction (skip playlist URLs like list=VL.../OLAK.../RD... without v=)
        val target = if (track.streamUrl.isNotBlank()) track.streamUrl else track.id
        val isPlayableVideoTarget = target.contains("watch?v=") ||
            target.contains("youtu.be/") ||
            (!target.contains("playlist?list=") && !target.startsWith("pl_") && !target.startsWith("VL") && !target.startsWith("OL"))

        if (isPlayableVideoTarget) {
            val stream = ytProvider.resolveAudioStream(target, bypassCache = bypassCache)
            if (stream != null) return@withContext stream
        }

        Log.w(TAG, "Primary stream failed for ${track.title}. Searching YouTube Music for playable alternate candidates...")
        val queryWithArtist = "${track.title} ${track.artist}".trim()
        val candidates = innerTubeRepo.search(queryWithArtist, InnerTubeFilter.SONGS)
            .ifEmpty { innerTubeRepo.search(track.title, InnerTubeFilter.SONGS) }

        for (candidate in candidates.take(4)) {
            val candTarget = candidate.streamUrl.ifBlank { candidate.id }
            if (candTarget == target && isPlayableVideoTarget) continue
            val fallback = ytProvider.resolveAudioStream(candTarget, bypassCache = bypassCache)
            if (fallback != null) {
                BackendDebugTracker.recordResolution(
                    trackTitle = track.title,
                    artist = track.artist,
                    provider = "YouTube Music (Fallback)",
                    bitrateKbps = fallback.bitrateKbps,
                    format = fallback.format,
                    fallbackReason = "Resolved via alternate candidate '${candidate.title}'",
                    durationMs = 0L,
                    isSuccess = true
                )
                return@withContext fallback
            }
        }

        null
    }

    suspend fun getLyrics(track: RezonaTrack, forceReload: Boolean = false): List<SyncedLyricLine> {
        return getLyricsWithSource(track, forceReload).lines
    }

    suspend fun getLyricsWithSource(
        track: RezonaTrack,
        forceReload: Boolean = false,
        preferredSource: LyricSourceId = LyricSourceId.AUTO
    ): ResolvedLyricsBundle {
        val startMs = System.currentTimeMillis()
        val parts = track.duration.split(":")
        val sec = (parts.getOrNull(0)?.toIntOrNull() ?: 3) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 30)

        if (preferredSource != LyricSourceId.YOUTUBE_MUSIC) {
            val res = if (preferredSource == LyricSourceId.AUTO) {
                lyricsProvider.getLyricsFromAuto(track.title, track.artist, sec, forceReload = forceReload)
            } else {
                lyricsProvider.fetchFromSource(preferredSource, track.title, track.artist, sec)
            }
            if (res.lines.isNotEmpty() && (preferredSource != LyricSourceId.AUTO || res.isSynced)) {
                val elapsed = System.currentTimeMillis() - startMs
                BackendDebugTracker.updateEndpointStatus(
                    key = "lrclib_lyrics",
                    endpointName = "Multi-Source Lyrics",
                    detail = "'${track.title}' via ${res.sourceId.displayName} -> ${res.lines.size} lines (${elapsed}ms)",
                    latencyMs = elapsed,
                    isHealthy = true
                )
                return ResolvedLyricsBundle(
                    lines = res.lines,
                    sourceLabel = res.sourceLabel,
                    sourceId = res.sourceId,
                    isSynced = res.isSynced
                )
            }
            if (preferredSource != LyricSourceId.AUTO) {
                return ResolvedLyricsBundle(
                    lines = emptyList(),
                    sourceLabel = "Not found on ${preferredSource.displayName}",
                    sourceId = preferredSource,
                    isSynced = false
                )
            }
        }

        // Fallback to YouTube Music lyrics tab (or explicit YouTube Music selection)
        val vid = when {
            track.streamUrl.contains("v=") -> track.streamUrl.substringAfter("v=").substringBefore("&")
            else -> track.id.removePrefix("yt_")
        }
        val ytLines = innerTubeRepo.getYouTubeLyrics(vid, sec)
        val elapsed = System.currentTimeMillis() - startMs
        BackendDebugTracker.updateEndpointStatus(
            key = "lrclib_lyrics",
            endpointName = "Lyrics (Multi-Source + YTM)",
            detail = if (ytLines.isNotEmpty()) "YTM '${track.title}' -> ${ytLines.size} lines (${elapsed}ms)"
            else "No lyrics for '${track.title}' (${elapsed}ms)",
            latencyMs = elapsed,
            isHealthy = ytLines.isNotEmpty()
        )
        if (ytLines.isNotEmpty()) {
            return ResolvedLyricsBundle(
                lines = ytLines,
                sourceLabel = "Lyrics from YouTube Music",
                sourceId = LyricSourceId.YOUTUBE_MUSIC,
                isSynced = false
            )
        }

        // Last resort in AUTO mode: return plain LRCLIB if any
        val autoPlain = lyricsProvider.getLyricsFromAuto(track.title, track.artist, sec, forceReload = false)
        return ResolvedLyricsBundle(
            lines = autoPlain.lines,
            sourceLabel = if (autoPlain.lines.isNotEmpty()) autoPlain.sourceLabel else "No synchronized lyrics",
            sourceId = autoPlain.sourceId,
            isSynced = autoPlain.isSynced
        )
    }

    suspend fun probeLyricsSource(track: RezonaTrack, sourceId: LyricSourceId): LyricProviderResult {
        val parts = track.duration.split(":")
        val sec = (parts.getOrNull(0)?.toIntOrNull() ?: 3) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 30)
        if (sourceId == LyricSourceId.YOUTUBE_MUSIC) {
            val vid = when {
                track.streamUrl.contains("v=") -> track.streamUrl.substringAfter("v=").substringBefore("&")
                else -> track.id.removePrefix("yt_")
            }
            val ytLines = innerTubeRepo.getYouTubeLyrics(vid, sec)
            return LyricProviderResult(
                lines = ytLines,
                sourceId = LyricSourceId.YOUTUBE_MUSIC,
                sourceLabel = "Lyrics from YouTube Music",
                statusNote = if (ytLines.isNotEmpty()) "Unsynced" else "Not found",
                isSynced = false
            )
        }
        return lyricsProvider.fetchFromSource(sourceId, track.title, track.artist, sec)
    }

    suspend fun searchLyricsQuery(query: String, durationSeconds: Int? = null): List<SyncedLyricLine> {
        return lyricsProvider.searchLyrics(query, durationSeconds)
    }

    suspend fun getRadioSeeds(seedTrack: RezonaTrack): List<RezonaTrack> {
        val queue = innerTubeRepo.getRadioQueue(seedTrack.id)
        if (queue.isNotEmpty()) return queue

        val related = ytProvider.getRelatedSongs(seedTrack.streamUrl, maxCount = 15)
        if (related.isNotEmpty()) return related

        return innerTubeRepo.search("${seedTrack.artist} songs", InnerTubeFilter.SONGS)
    }
}
