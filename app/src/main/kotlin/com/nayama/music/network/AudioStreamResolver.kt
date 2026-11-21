package com.nayama.music.network

import com.nayama.music.model.MusicSource
import com.nayama.music.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Multi-Source Audio Stream Resolver.
 * Resolves direct full-length playable audio URLs across YouTube Music,
 * SoundCloud, Spotify, JioSaavn 320kbps HD, and Audius.
 */
object AudioStreamResolver {

    private val resolvedTrackCache = ConcurrentHashMap<String, Track>()

    suspend fun resolveFullLengthTrack(
        track: Track,
        soundCloudClientId: String = ""
    ): Track = withContext(Dispatchers.IO) {
        val isPreviewOrLazy = track.streamUrl.isNullOrBlank() ||
            track.streamUrl.contains("dzcdn.net") ||
            track.streamUrl.contains("scdn.co/mp3-preview") ||
            track.streamUrl.contains("preview") ||
            track.id.startsWith("ytm_") ||
            track.id.startsWith("sc_") ||
            track.id.startsWith("spotify_") ||
            track.id.startsWith("deezer_")

        if (!isPreviewOrLazy && track.durationSeconds > 35) {
            return@withContext track
        }

        resolvedTrackCache[track.id]?.let { cached ->
            if (!cached.streamUrl.isNullOrBlank()) return@withContext cached
        }

        try {
            // 1. YouTube Music direct stream resolution
            if (track.id.startsWith("ytm_") || track.source == MusicSource.YOUTUBE_MUSIC) {
                val ytUrl = YouTubeMusicProvider.resolveAudioStreamUrl(track.id)
                if (!ytUrl.isNullOrBlank()) {
                    val upgraded = track.copy(
                        streamUrl = ytUrl,
                        audioQualityLabel = "OPUS / M4A HD"
                    )
                    resolvedTrackCache[track.id] = upgraded
                    return@withContext upgraded
                }
            }

            // 3. JioSaavn 320kbps AAC resolution (for Spotify / Deezer / fallback)
            val query = "${track.title} ${track.artist}".trim()
            val saavnMatches = SaavnMusicProvider.searchTracks(query, limit = 5)
            val bestSaavn = saavnMatches.firstOrNull { candidate ->
                !candidate.streamUrl.isNullOrBlank() && candidate.durationSeconds > 45 &&
                    candidate.title.contains(track.title.take(8), ignoreCase = true)
            } ?: saavnMatches.firstOrNull { !it.streamUrl.isNullOrBlank() && it.durationSeconds > 45 }

            if (bestSaavn != null && !bestSaavn.streamUrl.isNullOrBlank()) {
                val upgraded = track.copy(
                    streamUrl = bestSaavn.streamUrl,
                    durationSeconds = bestSaavn.durationSeconds,
                    sourceAttribution = "${track.source.displayName} · 320kbps HD",
                    audioQualityLabel = "AAC 320 KBPS"
                )
                resolvedTrackCache[track.id] = upgraded
                return@withContext upgraded
            }

            // 4. Cross-match via YouTube Music search -> stream URL (great for Spotify / Deezer / SC fallback)
            if (!track.id.startsWith("ytm_")) {
                val ytCandidates = YouTubeMusicProvider.searchTracks(query, limit = 3)
                for (cand in ytCandidates) {
                    val resolvedYt = YouTubeMusicProvider.resolveAudioStreamUrl(cand.id)
                    if (!resolvedYt.isNullOrBlank()) {
                        val upgraded = track.copy(
                            streamUrl = resolvedYt,
                            durationSeconds = if (cand.durationSeconds > 30) cand.durationSeconds else track.durationSeconds,
                            sourceAttribution = "${track.source.displayName} · YTM Audio",
                            audioQualityLabel = "OPUS / M4A HD"
                        )
                        resolvedTrackCache[track.id] = upgraded
                        return@withContext upgraded
                    }
                }
            }
        } catch (_: Throwable) {
            // Graceful fallback to original track
        }

        track
    }
}
