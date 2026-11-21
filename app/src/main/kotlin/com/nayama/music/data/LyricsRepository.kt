package com.nayama.music.data

import com.nayama.music.model.LyricLine
import com.nayama.music.model.Lyrics
import com.nayama.music.model.Track
import com.nayama.music.model.WordToken
import com.nayama.music.network.YouTubeMusicProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class LyricsRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val cache = ConcurrentHashMap<String, Lyrics>()
    private val noLyricsCache = ConcurrentHashMap.newKeySet<String>()

    private fun safelyCache(trackId: String, lyrics: Lyrics?) {
        if (trackId.isNotBlank() && lyrics != null) {
            cache[trackId] = lyrics
        }
    }

    private fun safelyMarkNoLyrics(trackId: String) {
        if (trackId.isNotBlank()) {
            noLyricsCache.add(trackId)
        }
    }

    private val lrcPattern = Pattern.compile("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{1,3}))?\\](.*)")
    private val wordTagRegex = Regex("<(\\d{2}):(\\d{2})(?:\\.(\\d{1,3}))?>\\s*([^<]*)")

    suspend fun getLyrics(track: Track?): Lyrics? = withContext(Dispatchers.IO) {
        val trackId = track?.id?.trim()?.ifBlank { null } ?: return@withContext null
        try {
            if (noLyricsCache.contains(trackId)) return@withContext null
            val cached = cache[trackId]
            if (cached != null) return@withContext cached

            val cleanTitle = cleanTrackTitle(track.title.orEmpty())
            val cleanArtist = cleanArtistName(track.artist.orEmpty())
            val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
            val encodedArtist = URLEncoder.encode(cleanArtist, "UTF-8")

            // 1. LRCLIB Direct match endpoint
            val directUrl = "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist&duration=${track.durationSeconds}"
            val request = Request.Builder()
                .url(directUrl)
                .header("User-Agent", "OReverie/1.0 (Android Native; open-source)")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    if (json.optBoolean("instrumental", false)) {
                        safelyMarkNoLyrics(trackId)
                        return@withContext null
                    }
                    val lyrics = parseLyricsJson(trackId, json)
                    if (lyrics != null) {
                        safelyCache(trackId, lyrics)
                        return@withContext lyrics
                    }
                }
            }

            // 2. LRCLIB Search endpoint fallback
            val query = URLEncoder.encode("$cleanTitle $cleanArtist".trim(), "UTF-8")
            val searchUrl = "https://lrclib.net/api/search?q=$query"
            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "OReverie/1.0 (Android Native; open-source)")
                .build()

            val searchResponse = client.newCall(searchRequest).execute()
            if (searchResponse.isSuccessful) {
                val searchBody = searchResponse.body?.string()
                if (!searchBody.isNullOrBlank()) {
                    val array = JSONArray(searchBody)
                    // Prefer entries that actually have synced lyrics and are not marked instrumental
                    var fallbackMatch: JSONObject? = null
                    for (i in 0 until array.length().coerceAtMost(5)) {
                        val candidate = array.optJSONObject(i) ?: continue
                        if (candidate.optBoolean("instrumental", false)) continue
                        if (candidate.optString("syncedLyrics", "").isNotBlank()) {
                            val syncedLyrics = parseLyricsJson(trackId, candidate)
                            if (syncedLyrics != null) {
                                safelyCache(trackId, syncedLyrics)
                                return@withContext syncedLyrics
                            }
                        } else if (fallbackMatch == null && candidate.optString("plainLyrics", "").isNotBlank()) {
                            fallbackMatch = candidate
                        }
                    }
                    if (fallbackMatch != null) {
                        val plainOnly = parseLyricsJson(trackId, fallbackMatch)
                        if (plainOnly != null) {
                            safelyCache(trackId, plainOnly)
                            return@withContext plainOnly
                        }
                    }
                }
            }

            // 3. YouTube Music InnerTube Lyrics fallback (for YT Music or cross-matched tracks)
            val ytmVideoId = if (trackId.startsWith("ytm_")) {
                trackId
            } else {
                YouTubeMusicProvider.searchTracks("$cleanTitle $cleanArtist", limit = 1)
                    .firstOrNull()?.id
            }
            if (!ytmVideoId.isNullOrBlank()) {
                val ytText = YouTubeMusicProvider.fetchLyricsText(ytmVideoId)
                if (!ytText.isNullOrBlank()) {
                    val lines = ytText.lines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .map { LyricLine(timeMs = -1L, text = it) }
                    if (lines.isNotEmpty()) {
                        val ytLyrics = Lyrics(
                            trackId = trackId,
                            trackName = cleanTitle,
                            artistName = cleanArtist,
                            isSynced = false,
                            lines = lines,
                            plainLyrics = ytText,
                            sourceAttribution = "YouTube Music Lyrics"
                        )
                        safelyCache(trackId, ytLyrics)
                        return@withContext ytLyrics
                    }
                }
            }

            safelyMarkNoLyrics(trackId)
        } catch (_: Throwable) {
            // Graceful fallback on any network, parsing, or threading failure — never crash
        }

        null
    }

    private fun parseLyricsJson(trackId: String, json: JSONObject): Lyrics? {
        val synced = json.optString("syncedLyrics", "").trim()
        val plain = json.optString("plainLyrics", "").trim()
        val trackName = json.optString("trackName", "")
        val artistName = json.optString("artistName", "")

        if (synced.isNotBlank()) {
            val (lines, hasWords) = parseSyncedLyrics(synced)
            if (lines.isNotEmpty()) {
                return Lyrics(
                    trackId = trackId,
                    trackName = trackName,
                    artistName = artistName,
                    isSynced = true,
                    lines = lines,
                    plainLyrics = plain.ifBlank { null },
                    sourceAttribution = if (hasWords) "LRCLIB Word-Sync" else "LRCLIB Time-Synced",
                    hasWordSync = hasWords
                )
            }
        }

        if (plain.isNotBlank()) {
            val lines = plain.lines().map { line ->
                LyricLine(timeMs = -1L, text = line.trim())
            }.filter { it.text.isNotBlank() }

            if (lines.isNotEmpty()) {
                return Lyrics(
                    trackId = trackId,
                    trackName = trackName,
                    artistName = artistName,
                    isSynced = false,
                    lines = lines,
                    plainLyrics = plain,
                    sourceAttribution = "LRCLIB Plain"
                )
            }
        }

        return null
    }

    internal fun parseSyncedLyrics(lrcContent: String): Pair<List<LyricLine>, Boolean> {
        val lines = mutableListOf<LyricLine>()
        var anyWordSync = false

        for (rawLine in lrcContent.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isBlank()) continue

            val matcher = lrcPattern.matcher(trimmed)
            if (matcher.matches()) {
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                val millisPart = matcher.group(3)
                val millis = parseMillis(millisPart)
                val totalMs = (minutes * 60 * 1000) + (seconds * 1000) + millis
                val rawText = matcher.group(4)?.trim() ?: ""

                // Parse Enhanced LRC word-by-word tokens (<mm:ss.xx> word)
                val wordMatches = wordTagRegex.findAll(rawText).toList()
                if (wordMatches.isNotEmpty()) {
                    anyWordSync = true
                    val words = wordMatches.mapNotNull { m ->
                        val wm = m.groupValues[1].toLongOrNull() ?: 0L
                        val ws = m.groupValues[2].toLongOrNull() ?: 0L
                        val wms = parseMillis(m.groupValues[3].ifBlank { null })
                        val tokenText = m.groupValues[4].trim()
                        if (tokenText.isBlank()) null
                        else WordToken(timeMs = (wm * 60 * 1000) + (ws * 1000) + wms, text = tokenText)
                    }
                    val cleanText = words.joinToString(" ") { it.text }.ifBlank { rawText.replace(Regex("<[^>]+>"), "").trim() }
                    lines.add(LyricLine(timeMs = totalMs, text = cleanText, words = words))
                } else {
                    lines.add(LyricLine(timeMs = totalMs, text = rawText))
                }
            }
        }
        return lines.sortedBy { it.timeMs } to anyWordSync
    }

    private fun parseMillis(millisPart: String?): Long {
        return when {
            millisPart == null -> 0L
            millisPart.length == 1 -> millisPart.toLong() * 100
            millisPart.length == 2 -> millisPart.toLong() * 10
            else -> millisPart.take(3).toLongOrNull() ?: 0L
        }
    }

    private fun cleanTrackTitle(title: String): String {
        return title
            .replace(Regex("\\(.*?feat.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(.*?official.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("feat\\..*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("ft\\..*", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun cleanArtistName(artist: String): String {
        return artist
            .replace(Regex(",.*"), "")
            .replace(Regex("&.*"), "")
            .trim()
    }

    companion object {
        @Volatile
        private var instance: LyricsRepository? = null

        fun getInstance(): LyricsRepository {
            return instance ?: synchronized(this) {
                instance ?: LyricsRepository().also { instance = it }
            }
        }
    }
}
