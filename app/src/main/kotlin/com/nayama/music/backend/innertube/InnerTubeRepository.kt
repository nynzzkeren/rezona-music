package com.nayama.music.backend.innertube

import com.nayama.music.backend.BackendDebugTracker
import com.nayama.music.model.RezonaTrack
import com.nayama.music.model.SyncedLyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * High-level repository providing access to YouTube Music data via InnerTube.
 * Supports filtered search + continuation pagination, home feed, moods & genres,
 * charts, artist/album/playlist pages, radio queues, and YouTube lyrics fallback.
 */
class InnerTubeRepository(
    private val client: InnerTubeClient = InnerTubeClient()
) {

    companion object {
        private const val TAG = "InnerTubeRepository"
        private const val HOME_CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes

        @Volatile
        private var instance: InnerTubeRepository? = null

        fun getInstance(): InnerTubeRepository {
            return instance ?: synchronized(this) {
                instance ?: InnerTubeRepository().also { instance = it }
            }
        }
    }

    private var cachedHomeSections: List<InnerTubeSection>? = null
    private var homeCacheTimestamp: Long = 0L

    private val radioQueueCache = ConcurrentHashMap<String, List<RezonaTrack>>()

    suspend fun search(
        query: String,
        filter: InnerTubeFilter = InnerTubeFilter.ALL
    ): List<RezonaTrack> = searchPage(query, filter).items

    suspend fun searchPage(
        query: String,
        filter: InnerTubeFilter = InnerTubeFilter.ALL
    ): InnerTubeSearchPage = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext InnerTubeSearchPage(emptyList())

        val startMs = System.currentTimeMillis()
        val reqParams = "query=$trimmed, filter=${filter.label}, params=${filter.param ?: "<null>"}"
        try {
            val responseJson = client.search(trimmed, filter)
            val page = InnerTubeParser.parseSearchPage(responseJson, reqParams)
            val elapsed = System.currentTimeMillis() - startMs
            SafeLog.d(TAG, "InnerTube search for '$trimmed' (filter=${filter.label}) returned ${page.items.size} tracks in ${elapsed}ms")
            BackendDebugTracker.recordSearchTelemetry(page.sectionCounts, reqParams)
            BackendDebugTracker.updateEndpointStatus(
                key = "innertube_search",
                endpointName = "InnerTube Search",
                detail = "'$trimmed' [${filter.label}] -> ${page.items.size} items (${elapsed}ms)",
                latencyMs = elapsed,
                isHealthy = page.items.isNotEmpty()
            )
            page
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startMs
            SafeLog.e(TAG, "Search failed for '$query'", e)
            BackendDebugTracker.updateEndpointStatus(
                key = "innertube_search",
                endpointName = "InnerTube Search",
                detail = "Error: ${e.message ?: "Failed"}",
                latencyMs = elapsed,
                isHealthy = false
            )
            InnerTubeSearchPage(emptyList(), null, emptyMap(), reqParams)
        }
    }

    suspend fun searchContinuation(continuation: String): InnerTubeSearchPage = withContext(Dispatchers.IO) {
        if (continuation.isBlank()) return@withContext InnerTubeSearchPage(emptyList())
        try {
            val json = client.searchWithContinuation(continuation)
            InnerTubeParser.parseSearchPage(json, "continuation=${continuation.take(24)}...")
        } catch (e: Exception) {
            SafeLog.e(TAG, "Search continuation failed", e)
            InnerTubeSearchPage(emptyList())
        }
    }

    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val startMs = System.currentTimeMillis()
        try {
            val responseJson = client.getSearchSuggestions(trimmed)
            val list = InnerTubeParser.parseSearchSuggestions(responseJson)
            val elapsed = System.currentTimeMillis() - startMs
            BackendDebugTracker.updateEndpointStatus(
                key = "innertube_suggestions",
                endpointName = "InnerTube Suggestions",
                detail = "'$trimmed' -> ${list.size} suggestions (${elapsed}ms)",
                latencyMs = elapsed,
                isHealthy = true
            )
            list
        } catch (e: Exception) {
            SafeLog.e(TAG, "Failed to get suggestions for '$query'", e)
            emptyList()
        }
    }

    suspend fun getHomeSections(forceRefresh: Boolean = false): List<InnerTubeSection> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedHomeSections != null && (now - homeCacheTimestamp) < HOME_CACHE_TTL_MS) {
            return@withContext cachedHomeSections!!
        }

        val startMs = System.currentTimeMillis()
        try {
            val homeJson = client.browse("FEmusic_home")
            val homeSections = InnerTubeParser.parseHomeSections(homeJson).toMutableList()

            // Enrich with real Charts & Explore shelves if anonymous FEmusic_home returned few shelves
            if (homeSections.size < 3) {
                runCatching {
                    val chartsJson = client.browse("FEmusic_charts")
                    InnerTubeParser.parseHomeSections(chartsJson).forEach { sec ->
                        if (homeSections.none { it.title.equals(sec.title, ignoreCase = true) }) {
                            homeSections.add(sec)
                        }
                    }
                }
            }

            val elapsed = System.currentTimeMillis() - startMs
            if (homeSections.isNotEmpty()) {
                cachedHomeSections = homeSections
                homeCacheTimestamp = now
                SafeLog.d(TAG, "Fetched ${homeSections.size} home sections from InnerTube")
            }
            BackendDebugTracker.updateEndpointStatus(
                key = "innertube_home",
                endpointName = "InnerTube Home Browse",
                detail = "${homeSections.size} sections loaded (${elapsed}ms)",
                latencyMs = elapsed,
                isHealthy = homeSections.isNotEmpty()
            )
            homeSections
        } catch (e: Exception) {
            SafeLog.e(TAG, "Failed to load home sections", e)
            cachedHomeSections ?: emptyList()
        }
    }

    suspend fun getMoodsAndGenres(): List<InnerTubeMoodCategory> = withContext(Dispatchers.IO) {
        runCatching {
            val json = client.browse("FEmusic_moods_and_genres")
            InnerTubeParser.parseMoodsAndGenres(json)
        }.getOrDefault(emptyList())
    }

    suspend fun getAlbumOrPlaylistPage(browseId: String, params: String? = null): InnerTubeAlbumPage? =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = client.browse(browseId, params)
                InnerTubeParser.parseAlbumOrPlaylistPage(json)
            }.getOrNull()
        }

    suspend fun getArtistPage(browseId: String): InnerTubeArtistPage? = withContext(Dispatchers.IO) {
        runCatching {
            val json = client.browse(browseId)
            InnerTubeParser.parseArtistPage(json)
        }.getOrNull()
    }

    suspend fun getRadioQueue(videoId: String): List<RezonaTrack> = withContext(Dispatchers.IO) {
        val cleanId = videoId.removePrefix("yt_").take(11)
        if (cleanId.length < 11) return@withContext emptyList()

        radioQueueCache[cleanId]?.let { return@withContext it }

        val startMs = System.currentTimeMillis()
        try {
            val playlistId = "RDAMVM$cleanId"
            val responseJson = client.next(cleanId, playlistId)
            val queue = InnerTubeParser.parseNextQueue(responseJson)
            val elapsed = System.currentTimeMillis() - startMs
            if (queue.isNotEmpty()) {
                radioQueueCache[cleanId] = queue
                SafeLog.d(TAG, "Radio queue for '$cleanId' returned ${queue.size} songs")
            }
            BackendDebugTracker.updateEndpointStatus(
                key = "innertube_next",
                endpointName = "InnerTube Radio Queue",
                detail = "RDAMVM$cleanId -> ${queue.size} tracks (${elapsed}ms)",
                latencyMs = elapsed,
                isHealthy = queue.isNotEmpty()
            )
            queue
        } catch (e: Exception) {
            SafeLog.e(TAG, "Failed to get radio queue for '$videoId'", e)
            emptyList()
        }
    }

    /**
     * Fetches plain lyrics from YouTube Music's own Lyrics tab (fallback when LRCLIB misses).
     */
    suspend fun getYouTubeLyrics(videoId: String, durationSec: Int = 210): List<SyncedLyricLine> =
        withContext(Dispatchers.IO) {
            val cleanId = videoId.removePrefix("yt_").take(11)
            if (cleanId.length < 11) return@withContext emptyList()
            runCatching {
                val nextJson = client.next(cleanId)
                val lyricsBrowseId = InnerTubeParser.parseLyricsBrowseId(nextJson) ?: return@runCatching emptyList()
                val browseJson = client.browse(lyricsBrowseId)
                val plainText = InnerTubeParser.parseLyricsText(browseJson) ?: return@runCatching emptyList()
                val lines = plainText.lines().map { it.trim() }.filter { it.isNotBlank() }
                if (lines.isEmpty()) return@runCatching emptyList()
                val intervalMs = ((durationSec.coerceAtLeast(60) * 1000L) / lines.size).coerceAtLeast(3000L)
                lines.mapIndexed { idx, line ->
                    SyncedLyricLine(timeMs = idx * intervalMs, text = line)
                }
            }.getOrDefault(emptyList())
        }

    /**
     * Resolves a direct audio stream URL directly via InnerTube Player API (ANDROID_VR / IOS)
     * when NewPipe fails on ciphered or restricted streams.
     */
    suspend fun resolveDirectAudioStream(videoId: String): Pair<String, Int>? = withContext(Dispatchers.IO) {
        val cleanId = videoId.removePrefix("yt_").substringAfter("v=").substringBefore("&").trim()
        if (cleanId.length < 11) return@withContext null

        val rawJson = client.getPlayerFormats(cleanId) ?: return@withContext null
        runCatching {
            val root = org.json.JSONObject(rawJson)
            val streamingData = root.optJSONObject("streamingData") ?: return@runCatching null

            val candidates = mutableListOf<Triple<String, Int, Boolean>>() // url, bitrateKbps, isMp4
            val adaptive = streamingData.optJSONArray("adaptiveFormats")
            if (adaptive != null) {
                for (i in 0 until adaptive.length()) {
                    val fmt = adaptive.optJSONObject(i) ?: continue
                    val mime = fmt.optString("mimeType").lowercase()
                    val url = fmt.optString("url")
                    if (mime.startsWith("audio/") && url.isNotBlank()) {
                        val bitrateKbps = (fmt.optInt("bitrate", 128000) / 1000).coerceAtLeast(64)
                        val isMp4 = mime.contains("mp4")
                        candidates.add(Triple(url, bitrateKbps, isMp4))
                    }
                }
            }
            if (candidates.isEmpty()) {
                val progressive = streamingData.optJSONArray("formats")
                if (progressive != null) {
                    for (i in 0 until progressive.length()) {
                        val fmt = progressive.optJSONObject(i) ?: continue
                        val url = fmt.optString("url")
                        if (url.isNotBlank()) {
                            val bitrateKbps = (fmt.optInt("bitrate", 128000) / 1000).coerceAtLeast(64)
                            candidates.add(Triple(url, bitrateKbps, true))
                        }
                    }
                }
            }
            val best = candidates.sortedWith(
                compareByDescending<Triple<String, Int, Boolean>> { it.third }
                    .thenByDescending { it.second }
            ).firstOrNull() ?: return@runCatching null
            best.first to best.second
        }.getOrNull()
    }
}
