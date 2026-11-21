package com.nayama.music.backend

import android.util.Log
import com.nayama.music.backend.innertube.InnerTubeRepository
import com.nayama.music.model.RezonaTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap

data class ResolvedStream(
    val streamUrl: String,
    val bitrateKbps: Int,
    val format: String,
    val expiresAtMs: Long
)

/**
 * YouTube Music Audio & Search Provider powered by NewPipeExtractor.
 * Implements lazy stream resolution, audio-only stream extraction (best bitrate),
 * query filtering, related tracks / radio seeding, and stream URL expiry caching.
 */
class YouTubeMusicProvider {

    companion object {
        private const val TAG = "YouTubeMusicProvider"
    }

    // In-memory stream URL cache: videoId -> ResolvedStream
    private val streamCache = ConcurrentHashMap<String, ResolvedStream>()

    /**
     * Searches YouTube Music for songs, tracks, and audio streams.
     */
    suspend fun searchSongs(query: String, maxResults: Int = 20): List<RezonaTrack> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (!BackendConfig.ENABLE_UNOFFICIAL_PROVIDERS || !BackendConfig.ENABLE_YOUTUBE_MUSIC || trimmed.isEmpty()) {
            return@withContext emptyList()
        }

        try {
            withTimeoutOrNull(10000L) {
                val service = ServiceList.YouTube
                val musicQuery = if (trimmed.length <= 3) {
                    trimmed
                } else if (trimmed.contains("music", ignoreCase = true) || trimmed.contains("song", ignoreCase = true)) {
                    trimmed
                } else {
                    "$trimmed song"
                }

                val searchExtractor = service.getSearchExtractor(
                    musicQuery,
                    listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
                    ""
                )
                searchExtractor.fetchPage()

                val items = searchExtractor.initialPage?.items ?: emptyList()
                val tracks = mutableListOf<RezonaTrack>()

                for ((index, item) in items.withIndex()) {
                    if (tracks.size >= maxResults) break
                    if (item is StreamInfoItem) {
                        val durationSec = item.duration.coerceAtLeast(0)
                        val minutes = durationSec / 60
                        val seconds = durationSec % 60
                        val durationStr = String.format("%d:%02d", minutes, seconds)

                        val cleanTitle = cleanSongTitle(item.name)

                        tracks.add(
                            RezonaTrack(
                                id = "yt_${item.url.hashCode().toString().replace("-", "0")}",
                                index = index,
                                title = cleanTitle,
                                artist = item.uploaderName.orEmpty().ifBlank { "YouTube Music" },
                                album = "YouTube Music Audio",
                                duration = durationStr,
                                source = "YouTube Music",
                                coverArtUrl = item.thumbnails.lastOrNull()?.url.orEmpty(),
                                streamUrl = item.url
                            )
                        )
                    }
                }
                tracks
            } ?: emptyList()
        } catch (e: Throwable) {
            Log.w(TAG, "Search failed for query: $query", e)
            emptyList()
        }
    }

    fun invalidateStreamCache(trackUrlOrId: String) {
        val fullUrl = normalizeWatchUrl(trackUrlOrId)
        streamCache.remove(fullUrl)
        streamCache.remove(trackUrlOrId)
    }

    private fun normalizeWatchUrl(trackUrlOrId: String): String {
        val raw = trackUrlOrId.trim()
        return when {
            raw.startsWith("http") -> raw
            raw.startsWith("yt_") -> "https://www.youtube.com/watch?v=${raw.removePrefix("yt_")}"
            else -> "https://www.youtube.com/watch?v=$raw"
        }
    }

    private fun extractVideoId(trackUrlOrId: String): String {
        val raw = trackUrlOrId.trim()
        return when {
            raw.contains("v=") -> raw.substringAfter("v=").substringBefore("&")
            raw.contains("youtu.be/") -> raw.substringAfter("youtu.be/").substringBefore("?")
            raw.startsWith("yt_") -> raw.removePrefix("yt_")
            else -> raw
        }.trim()
    }

    /**
     * Resolves a direct progressive audio-only stream URL for the given YouTube video/music URL.
     * Prefers progressive M4A/Opus (no DASH segmented manifests), with automatic InnerTube ANDROID_VR/IOS fallback.
     */
    suspend fun resolveAudioStream(trackUrlOrId: String, bypassCache: Boolean = false): ResolvedStream? = withContext(Dispatchers.IO) {
        if (!BackendConfig.ENABLE_UNOFFICIAL_PROVIDERS || !BackendConfig.ENABLE_YOUTUBE_MUSIC) {
            return@withContext null
        }

        val fullUrl = normalizeWatchUrl(trackUrlOrId)
        val videoId = extractVideoId(trackUrlOrId)

        if (!bypassCache) {
            val cached = streamCache[fullUrl]
            if (cached != null && System.currentTimeMillis() < cached.expiresAtMs) {
                return@withContext cached
            }
        } else {
            streamCache.remove(fullUrl)
        }

        val startTime = System.currentTimeMillis()
        try {
            val streamInfo = withTimeoutOrNull(14000L) {
                StreamInfo.getInfo(ServiceList.YouTube, fullUrl)
            }
            val audioStreams = streamInfo?.audioStreams.orEmpty()
                .filter { it.isUrl && it.content.isNotBlank() }

            // Prefer progressive HTTP streams (M4A or WEBM_OPUS) that ExoPlayer plays directly without DASH failures
            val progressive = audioStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            val pool = progressive.ifEmpty { audioStreams }

            if (pool.isNotEmpty()) {
                val bestAudio: AudioStream = pool.sortedWith(
                    compareByDescending<AudioStream> {
                        val fmt = it.format?.name?.lowercase().orEmpty()
                        fmt.contains("m4a") || fmt.contains("mp4")
                    }.thenByDescending { it.averageBitrate }
                ).first()

                val resolved = ResolvedStream(
                    streamUrl = bestAudio.content,
                    bitrateKbps = bestAudio.averageBitrate.coerceAtLeast(64),
                    format = bestAudio.format?.name ?: "M4A/Opus",
                    expiresAtMs = System.currentTimeMillis() + BackendConfig.STREAM_URL_EXPIRY_MS
                )

                streamCache[fullUrl] = resolved

                BackendDebugTracker.recordResolution(
                    trackTitle = streamInfo?.name.orEmpty(),
                    artist = streamInfo?.uploaderName.orEmpty(),
                    provider = "YouTube Music",
                    bitrateKbps = resolved.bitrateKbps,
                    format = resolved.format,
                    fallbackReason = null,
                    durationMs = System.currentTimeMillis() - startTime,
                    isSuccess = true
                )

                return@withContext resolved
            }
        } catch (e: Throwable) {
            Log.w(TAG, "NewPipe extraction failed for $fullUrl: ${e.message}")
        }

        // Secondary Engine: Direct InnerTube ANDROID_VR / iOS player format extraction
        if (videoId.length == 11) {
            val innerTubePair = InnerTubeRepository.getInstance().resolveDirectAudioStream(videoId)
            if (innerTubePair != null) {
                val resolved = ResolvedStream(
                    streamUrl = innerTubePair.first,
                    bitrateKbps = innerTubePair.second,
                    format = "InnerTube AAC/Opus",
                    expiresAtMs = System.currentTimeMillis() + BackendConfig.STREAM_URL_EXPIRY_MS
                )
                streamCache[fullUrl] = resolved
                BackendDebugTracker.recordResolution(
                    trackTitle = videoId,
                    artist = "YouTube Music",
                    provider = "InnerTube Player Fallback",
                    bitrateKbps = resolved.bitrateKbps,
                    format = resolved.format,
                    fallbackReason = "NewPipe empty/failed",
                    durationMs = System.currentTimeMillis() - startTime,
                    isSuccess = true
                )
                return@withContext resolved
            }
        }

        BackendDebugTracker.recordResolution(
            trackTitle = fullUrl,
            artist = "Unknown",
            provider = "YouTube Music",
            bitrateKbps = null,
            format = null,
            fallbackReason = "Both NewPipe and InnerTube player failed",
            durationMs = System.currentTimeMillis() - startTime,
            isSuccess = false
        )
        null
    }

    /**
     * Fetches related songs / radio recommendations for a seed track.
     */
    suspend fun getRelatedSongs(seedUrlOrId: String, maxCount: Int = 15): List<RezonaTrack> = withContext(Dispatchers.IO) {
        val fullUrl = if (seedUrlOrId.startsWith("http")) seedUrlOrId else "https://www.youtube.com/watch?v=$seedUrlOrId"
        try {
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, fullUrl)
            val relatedItems = streamInfo.relatedItems.orEmpty()

            val tracks = mutableListOf<RezonaTrack>()
            for ((index, item) in relatedItems.withIndex()) {
                if (tracks.size >= maxCount) break
                if (item is StreamInfoItem) {
                    val durationSec = item.duration.coerceAtLeast(0)
                    val minutes = durationSec / 60
                    val seconds = durationSec % 60

                    tracks.add(
                        RezonaTrack(
                            id = "yt_rel_${item.url.hashCode().toString().replace("-", "0")}",
                            index = index,
                            title = cleanSongTitle(item.name),
                            artist = item.uploaderName.orEmpty().ifBlank { "Radio Artist" },
                            album = "Algorithmic Radio",
                            duration = String.format("%d:%02d", minutes, seconds),
                            source = "YouTube Music Radio",
                            coverArtUrl = item.thumbnails.lastOrNull()?.url.orEmpty(),
                            streamUrl = item.url
                        )
                    )
                }
            }
            tracks
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to fetch related tracks for $seedUrlOrId", e)
            emptyList()
        }
    }

    private fun cleanSongTitle(raw: String): String {
        return raw.replace(Regex("(?i)\\[official audio\\]"), "")
            .replace(Regex("(?i)\\(official audio\\)"), "")
            .replace(Regex("(?i)\\[official video\\]"), "")
            .replace(Regex("(?i)\\(official music video\\)"), "")
            .replace(Regex("(?i)\\[lyrics\\]"), "")
            .replace(Regex("(?i)\\(lyrics\\)"), "")
            .trim()
    }
}
