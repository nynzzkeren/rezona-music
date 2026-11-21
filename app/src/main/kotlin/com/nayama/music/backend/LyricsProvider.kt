package com.nayama.music.backend

import android.content.Context
import android.util.Base64
import android.util.Log
import com.nayama.music.data.local.LyricsCacheEntity
import com.nayama.music.data.local.RezonaDatabase
import com.nayama.music.model.LyricWord
import com.nayama.music.model.SyncedLyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.abs

enum class LyricSourceId(
    val id: String,
    val displayName: String,
    val description: String
) {
    AUTO("auto", "Automatic", "Picks the best synced match"),
    LRC_RED("lrc.red", "lrc.red", "Word-by-word synced"),
    LYRICS_PLUS("lyricsplus", "LyricsPlus", "Karaoke syllable sync"),
    LRCLIB("lrclib", "LRCLIB", "Community synced LRC"),
    KUGOU("kugou", "KuGou", "Synced global & Asian catalog"),
    YOUTUBE_MUSIC("ytmusic", "YouTube Music", "Track transcript (unsynced)")
}

data class LyricProviderResult(
    val lines: List<SyncedLyricLine>,
    val sourceId: LyricSourceId,
    val sourceLabel: String,
    val statusNote: String,
    val isSynced: Boolean
)

/**
 * Multi-Source Lyrics Provider:
 * - Automatic priority: LRCLIB synced (duration-scored) -> lrc.red -> LyricsPlus -> KuGou -> LRCLIB plain -> YouTube Music
 * - Supports explicit source selection and live probing for the Lyrics Source sheet
 * - Parses standard [mm:ss.xx] LRC AND Enhanced word-level <mm:ss.xx>word LRC + LyricsPlus syllabus JSON
 */
class LyricsProvider(context: Context) {

    companion object {
        private const val TAG = "LyricsProvider"
        private const val USER_AGENT = "RezonaMusic/1.0.1 (Android Music Player)"
        private const val LRCLIB_BASE_URL = "https://lrclib.net/api/get"
        private const val LRCLIB_SEARCH_URL = "https://lrclib.net/api/search"
        private const val LRC_RED_SEARCH_URL = "https://lrc.red/search.json"
        private val LYRICSPLUS_MIRRORS = listOf(
            "https://lyricsplus.prjktla.my.id/v2/lyrics/get",
            "https://lyricsplus.binimum.org/v2/lyrics/get",
            "https://lyricsplus.atomix.one/v2/lyrics/get"
        )
        private const val KUGOU_SEARCH_URL = "https://lyrics.kugou.com/search"
        private const val KUGOU_DOWNLOAD_URL = "https://lyrics.kugou.com/download"

        private val LRC_LINE_PATTERN = Pattern.compile("^\\[(\\d{1,3}):(\\d{2})(?:\\.(\\d{1,3}))?\\](.*)$")
        private val WORD_TAG_REGEX = Regex("<(\\d{1,3}):(\\d{2})(?:\\.(\\d{1,3}))?>\\s*([^<]*)")
        private val FEAT_REGEX = Regex("\\s*[(\\[]?(?:feat\\.?|ft\\.?|with)\\s+[^)\\]]+[)\\]]?", RegexOption.IGNORE_CASE)
        private val BRACKET_NOISE_REGEX = Regex("\\s*[(\\[](?:official\\s*(?:music\\s*)?(?:video|audio|lyric\\s*video)?|lyrics?|audio|hd|4k|explicit|remastered[^)\\]]*)[)\\]]", RegexOption.IGNORE_CASE)
    }

    private val db = RezonaDatabase.getInstance(context)
    private val lyricsDao = db.lyricsDao()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonEngine = Json { ignoreUnknownKeys = true }

    private fun cleanTitle(raw: String): String {
        return raw
            .replace(BRACKET_NOISE_REGEX, "")
            .replace(FEAT_REGEX, "")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .ifBlank { raw.trim() }
    }

    private fun cleanArtist(raw: String): String {
        val noFeat = raw.replace(FEAT_REGEX, "").trim()
        val primary = noFeat.substringBefore(",").substringBefore("&").trim()
        return primary.ifBlank { raw.trim() }
    }

    private fun foldAscii(input: String): String {
        val nfd = Normalizer.normalize(input, Normalizer.Form.NFD)
        return nfd.replace("\\p{Mn}+".toRegex(), "")
            .replace("ł", "l")
            .replace("Ł", "L")
            .lowercase()
            .trim()
    }

    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSeconds: Int? = null,
        forceReload: Boolean = false
    ): List<SyncedLyricLine> = getLyricsFromAuto(title, artist, durationSeconds, forceReload).lines

    suspend fun getLyricsFromAuto(
        title: String,
        artist: String,
        durationSeconds: Int? = null,
        forceReload: Boolean = false
    ): LyricProviderResult = withContext(Dispatchers.IO) {
        if (!BackendConfig.ENABLE_UNOFFICIAL_PROVIDERS || !BackendConfig.ENABLE_LRCLIB_LYRICS) {
            return@withContext LyricProviderResult(emptyList(), LyricSourceId.AUTO, "Disabled", "Disabled", false)
        }

        val cleanT = cleanTitle(title)
        val cleanA = cleanArtist(artist)
        val cacheKey = "${foldAscii(cleanT)}_${foldAscii(cleanA)}"

        if (!forceReload) {
            val cached = lyricsDao.getLyrics(cacheKey)
            if (cached != null && cached.syncedLrc.isNotBlank()) {
                val parsed = parseLrc(cached.syncedLrc)
                if (parsed.isNotEmpty()) {
                    return@withContext LyricProviderResult(
                        lines = parsed,
                        sourceId = LyricSourceId.LRCLIB,
                        sourceLabel = "Lyrics from LRCLIB",
                        statusNote = "Synced per line",
                        isSynced = true
                    )
                }
            }
        }

        // 1. Prefer word-synced providers or duration-exact LRCLIB synced:
        val lrcRedRes = fetchFromLrcRed(title, artist, durationSeconds)
        if (lrcRedRes.isSynced && lrcRedRes.lines.isNotEmpty()) {
            return@withContext lrcRedRes
        }

        val lrcLibRes = fetchFromLrclib(title, artist, durationSeconds, cacheKey)
        if (lrcLibRes.isSynced && lrcLibRes.lines.isNotEmpty()) {
            return@withContext lrcLibRes
        }

        val lyricsPlusRes = fetchFromLyricsPlus(title, artist, durationSeconds)
        if (lyricsPlusRes.isSynced && lyricsPlusRes.lines.isNotEmpty()) {
            return@withContext lyricsPlusRes
        }

        val kugouRes = fetchFromKugou(title, artist, durationSeconds)
        if (kugouRes.isSynced && kugouRes.lines.isNotEmpty()) {
            return@withContext kugouRes
        }

        // Plain fallback if LRCLIB returned plain lyrics
        if (lrcLibRes.lines.isNotEmpty()) {
            return@withContext lrcLibRes
        }

        LyricProviderResult(emptyList(), LyricSourceId.AUTO, "Lyrics not found", "Not found", false)
    }

    suspend fun fetchFromSource(
        sourceId: LyricSourceId,
        title: String,
        artist: String,
        durationSeconds: Int? = null
    ): LyricProviderResult = withContext(Dispatchers.IO) {
        val cleanT = cleanTitle(title)
        val cleanA = cleanArtist(artist)
        val cacheKey = "${foldAscii(cleanT)}_${foldAscii(cleanA)}"
        when (sourceId) {
            LyricSourceId.AUTO -> getLyricsFromAuto(title, artist, durationSeconds, forceReload = true)
            LyricSourceId.LRC_RED -> fetchFromLrcRed(title, artist, durationSeconds)
            LyricSourceId.LYRICS_PLUS -> fetchFromLyricsPlus(title, artist, durationSeconds)
            LyricSourceId.LRCLIB -> fetchFromLrclib(title, artist, durationSeconds, cacheKey)
            LyricSourceId.KUGOU -> fetchFromKugou(title, artist, durationSeconds)
            LyricSourceId.YOUTUBE_MUSIC -> LyricProviderResult(emptyList(), LyricSourceId.YOUTUBE_MUSIC, "Lyrics from YouTube Music", "Delegated", false)
        }
    }

    private suspend fun fetchFromLrclib(
        rawTitle: String,
        rawArtist: String,
        durationSeconds: Int?,
        cacheKey: String
    ): LyricProviderResult {
        val cleanT = cleanTitle(rawTitle)
        val cleanA = cleanArtist(rawArtist)
        val dur = durationSeconds ?: 210

        // Attempt 1: /api/get with cleaned title + artist
        try {
            var url = "$LRCLIB_BASE_URL?track_name=${URLEncoder.encode(cleanT, "UTF-8")}&artist_name=${URLEncoder.encode(cleanA, "UTF-8")}"
            if (durationSeconds != null && durationSeconds > 0) url += "&duration=$durationSeconds"
            val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        val obj = jsonEngine.parseToJsonElement(body).jsonObject
                        val synced = obj["syncedLyrics"]?.jsonPrimitive?.content.orEmpty()
                        if (synced.isNotBlank()) {
                            val parsed = parseLrc(synced)
                            if (parsed.isNotEmpty()) {
                                saveLrcCache(cacheKey, synced, obj["plainLyrics"]?.jsonPrimitive?.content.orEmpty())
                                return LyricProviderResult(parsed, LyricSourceId.LRCLIB, "Lyrics from LRCLIB", "Line synced", true)
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "LRCLIB /api/get failed: ${e.message}")
        }

        // Attempt 2: /api/search?q=<title> <artist> with duration-proximity scoring
        try {
            val q = "$cleanT $cleanA"
            val url = "$LRCLIB_SEARCH_URL?q=${URLEncoder.encode(q, "UTF-8")}"
            val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return LyricProviderResult(emptyList(), LyricSourceId.LRCLIB, "LRCLIB", "Not found", false)
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return LyricProviderResult(emptyList(), LyricSourceId.LRCLIB, "LRCLIB", "Not found", false)

                val arr = jsonEngine.parseToJsonElement(body).jsonArray
                data class Candidate(val synced: String, val plain: String, val score: Double)
                val ranked = arr.mapNotNull { el ->
                    val obj = el.jsonObject
                    val synced = obj["syncedLyrics"]?.jsonPrimitive?.content.orEmpty()
                    val plain = obj["plainLyrics"]?.jsonPrimitive?.content.orEmpty()
                    if (synced.isBlank() && plain.isBlank()) return@mapNotNull null

                    val cDur = obj["duration"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt()
                    val cArtist = foldAscii(obj["artistName"]?.jsonPrimitive?.content.orEmpty())
                    val cTitle = foldAscii(obj["trackName"]?.jsonPrimitive?.content.orEmpty())
                    val durPenalty = if (cDur != null && dur > 0) abs(cDur - dur).coerceAtMost(300) else 80
                    val artistBonus = if (cArtist.contains(foldAscii(cleanA))) 150.0 else 0.0
                    val titleBonus = if (cTitle == foldAscii(cleanT)) 120.0 else 0.0
                    val syncBonus = if (synced.isNotBlank()) 1000.0 else 0.0
                    val lengthSanity = if (cDur != null && cDur < 30) -600.0 else 0.0
                    Candidate(synced, plain, syncBonus + artistBonus + titleBonus + lengthSanity - durPenalty * 2.0)
                }.sortedByDescending { it.score }

                val bestSynced = ranked.firstOrNull { it.synced.isNotBlank() }
                if (bestSynced != null) {
                    val parsed = parseLrc(bestSynced.synced)
                    if (parsed.isNotEmpty()) {
                        saveLrcCache(cacheKey, bestSynced.synced, bestSynced.plain)
                        return LyricProviderResult(parsed, LyricSourceId.LRCLIB, "Lyrics from LRCLIB", "Line synced", true)
                    }
                }
                val bestPlain = ranked.firstOrNull { it.plain.isNotBlank() }
                if (bestPlain != null) {
                    val lines = generatePlainLyricLines(bestPlain.plain, dur)
                    if (lines.isNotEmpty()) {
                        return LyricProviderResult(lines, LyricSourceId.LRCLIB, "Lyrics from LRCLIB (Unsynced)", "Unsynced", false)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "LRCLIB /api/search failed: ${e.message}")
        }

        return LyricProviderResult(emptyList(), LyricSourceId.LRCLIB, "LRCLIB", "Not found", false)
    }

    private fun fetchFromLrcRed(rawTitle: String, rawArtist: String, durationSeconds: Int?): LyricProviderResult {
        val cleanT = cleanTitle(rawTitle)
        val cleanA = cleanArtist(rawArtist)
        val targetDur = durationSeconds ?: 210
        return try {
            val q = "$cleanT $cleanA"
            val url = "$LRC_RED_SEARCH_URL?q=${URLEncoder.encode(q, "UTF-8")}"
            val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            val hitsJson = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return LyricProviderResult(emptyList(), LyricSourceId.LRC_RED, "lrc.red", "Not found", false)
                resp.body?.string().orEmpty()
            }
            if (hitsJson.isBlank()) return LyricProviderResult(emptyList(), LyricSourceId.LRC_RED, "lrc.red", "Not found", false)

            val root = jsonEngine.parseToJsonElement(hitsJson).jsonObject
            val hits = root["hits"]?.jsonArray ?: return LyricProviderResult(emptyList(), LyricSourceId.LRC_RED, "lrc.red", "Not found", false)
            if (hits.isEmpty()) return LyricProviderResult(emptyList(), LyricSourceId.LRC_RED, "lrc.red", "Not found", false)

            val bestHit = hits.mapNotNull { el ->
                val obj = el.jsonObject
                val isrc = obj["isrc"]?.jsonPrimitive?.content.orEmpty()
                if (isrc.isBlank()) return@mapNotNull null
                val hTitle = foldAscii(obj["title"]?.jsonPrimitive?.content.orEmpty())
                val hArtist = foldAscii(obj["artist"]?.jsonPrimitive?.content.orEmpty())
                val hDur = obj["duration"]?.jsonPrimitive?.intOrNull ?: targetDur
                var score = -abs(hDur - targetDur).toDouble()
                if (hTitle.contains(foldAscii(cleanT))) score += 200.0
                if (hArtist.contains(foldAscii(cleanA))) score += 200.0
                isrc to score
            }.maxByOrNull { it.second }?.first ?: return LyricProviderResult(emptyList(), LyricSourceId.LRC_RED, "lrc.red", "Not found", false)

            val lrcReq = Request.Builder().url("https://lrc.red/s/$bestHit.lrc").header("User-Agent", USER_AGENT).build()
            val lrcText = httpClient.newCall(lrcReq).execute().use { resp ->
                if (!resp.isSuccessful) "" else resp.body?.string().orEmpty()
            }
            val parsed = parseLrc(lrcText)
            if (parsed.isNotEmpty()) {
                val hasWord = parsed.any { !it.words.isNullOrEmpty() }
                LyricProviderResult(
                    lines = parsed,
                    sourceId = LyricSourceId.LRC_RED,
                    sourceLabel = "Lyrics from lrc.red",
                    statusNote = if (hasWord) "Word synced" else "Line synced",
                    isSynced = true
                )
            } else {
                LyricProviderResult(emptyList(), LyricSourceId.LRC_RED, "lrc.red", "Not found", false)
            }
        } catch (e: Throwable) {
            Log.d(TAG, "lrc.red failed: ${e.message}")
            LyricProviderResult(emptyList(), LyricSourceId.LRC_RED, "lrc.red", "Not found", false)
        }
    }

    private fun fetchFromLyricsPlus(rawTitle: String, rawArtist: String, durationSeconds: Int?): LyricProviderResult {
        val cleanT = cleanTitle(rawTitle)
        val cleanA = cleanArtist(rawArtist)
        for (mirror in LYRICSPLUS_MIRRORS) {
            try {
                var url = "$mirror?title=${URLEncoder.encode(cleanT, "UTF-8")}&artist=${URLEncoder.encode(cleanA, "UTF-8")}"
                if (durationSeconds != null && durationSeconds > 0) url += "&duration=$durationSeconds"
                val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                val body = httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) "" else resp.body?.string().orEmpty()
                }
                if (body.isBlank()) continue

                val root = jsonEngine.parseToJsonElement(body).jsonObject
                val arr = root["lyrics"]?.jsonArray ?: continue
                val lines = arr.mapNotNull { el ->
                    val obj = el.jsonObject
                    val timeMs = obj["time"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                    val text = obj["text"]?.jsonPrimitive?.content.orEmpty().trim()
                    if (text.isBlank()) return@mapNotNull null

                    val syllabus = obj["syllabus"]?.jsonArray
                    val words = if (syllabus != null && syllabus.isNotEmpty()) {
                        // Combine syllable fragments into word entries when separated by trailing spaces or treat directly
                        syllabus.mapNotNull { sylEl ->
                            val sObj = sylEl.jsonObject
                            val wTime = sObj["time"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                            val wDur = (sObj["duration"]?.jsonPrimitive?.longOrNull ?: 250L).coerceAtLeast(50L)
                            val wText = sObj["text"]?.jsonPrimitive?.content.orEmpty()
                            if (wText.isEmpty()) return@mapNotNull null
                            LyricWord(word = wText.trimEnd(), startMs = wTime, endMs = wTime + wDur)
                        }
                    } else emptyList()

                    SyncedLyricLine(timeMs = timeMs, text = text, words = words)
                }.sortedBy { it.timeMs }

                if (lines.isNotEmpty()) {
                    val isWord = lines.any { !it.words.isNullOrEmpty() }
                    return LyricProviderResult(
                        lines = lines,
                        sourceId = LyricSourceId.LYRICS_PLUS,
                        sourceLabel = "Lyrics from LyricsPlus",
                        statusNote = if (isWord) "Karaoke synced" else "Line synced",
                        isSynced = true
                    )
                }
            } catch (e: Throwable) {
                Log.d(TAG, "LyricsPlus mirror $mirror failed: ${e.message}")
            }
        }
        return LyricProviderResult(emptyList(), LyricSourceId.LYRICS_PLUS, "LyricsPlus", "Not found", false)
    }

    private fun fetchFromKugou(rawTitle: String, rawArtist: String, durationSeconds: Int?): LyricProviderResult {
        val cleanT = cleanTitle(rawTitle)
        val cleanA = cleanArtist(rawArtist)
        val durMs = ((durationSeconds ?: 210) * 1000L).coerceAtLeast(30_000L)
        return try {
            val kw = URLEncoder.encode("$cleanT $cleanA", "UTF-8")
            val searchUrl = "$KUGOU_SEARCH_URL?ver=1&man=yes&client=pc&keyword=$kw&duration=$durMs"
            val req = Request.Builder().url(searchUrl).header("User-Agent", USER_AGENT).build()
            val body = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) "" else resp.body?.string().orEmpty()
            }
            if (body.isBlank()) return LyricProviderResult(emptyList(), LyricSourceId.KUGOU, "KuGou", "Not found", false)

            val root = jsonEngine.parseToJsonElement(body).jsonObject
            val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: return LyricProviderResult(emptyList(), LyricSourceId.KUGOU, "KuGou", "Not found", false)
            val id = candidate["id"]?.jsonPrimitive?.content.orEmpty()
            val accessKey = candidate["accesskey"]?.jsonPrimitive?.content.orEmpty()
            if (id.isBlank() || accessKey.isBlank()) {
                return LyricProviderResult(emptyList(), LyricSourceId.KUGOU, "KuGou", "Not found", false)
            }

            val dlUrl = "$KUGOU_DOWNLOAD_URL?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=lrc&charset=utf8"
            val dlReq = Request.Builder().url(dlUrl).header("User-Agent", USER_AGENT).build()
            val dlBody = httpClient.newCall(dlReq).execute().use { resp ->
                if (!resp.isSuccessful) "" else resp.body?.string().orEmpty()
            }
            if (dlBody.isBlank()) return LyricProviderResult(emptyList(), LyricSourceId.KUGOU, "KuGou", "Not found", false)

            val dlJson = jsonEngine.parseToJsonElement(dlBody).jsonObject
            val b64 = dlJson["content"]?.jsonPrimitive?.content.orEmpty()
            if (b64.isBlank()) return LyricProviderResult(emptyList(), LyricSourceId.KUGOU, "KuGou", "Not found", false)

            val decodedLrc = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            val parsed = parseLrc(decodedLrc)
            if (parsed.isNotEmpty()) {
                LyricProviderResult(parsed, LyricSourceId.KUGOU, "Lyrics from KuGou", "Line synced", true)
            } else {
                LyricProviderResult(emptyList(), LyricSourceId.KUGOU, "KuGou", "Not found", false)
            }
        } catch (e: Throwable) {
            Log.d(TAG, "KuGou failed: ${e.message}")
            LyricProviderResult(emptyList(), LyricSourceId.KUGOU, "KuGou", "Not found", false)
        }
    }

    private suspend fun saveLrcCache(key: String, synced: String, plain: String) {
        runCatching {
            lyricsDao.insertLyrics(LyricsCacheEntity(trackKey = key, syncedLrc = synced, plainLyrics = plain))
        }
    }

    /**
     * Manual search fallback for Stage F "Search another lyric".
     */
    suspend fun searchLyrics(query: String, durationSeconds: Int? = null): List<SyncedLyricLine> =
        withContext(Dispatchers.IO) {
            val clean = query.trim()
            if (clean.isBlank()) return@withContext emptyList()
            try {
                val url = "$LRCLIB_SEARCH_URL?q=${URLEncoder.encode(clean, "UTF-8")}"
                val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank()) return@withContext emptyList()
                    val arr = jsonEngine.parseToJsonElement(body).jsonArray
                    for (el in arr) {
                        val obj = el.jsonObject
                        val synced = obj["syncedLyrics"]?.jsonPrimitive?.content.orEmpty()
                        if (synced.isNotBlank()) {
                            val parsed = parseLrc(synced)
                            if (parsed.isNotEmpty()) return@withContext parsed
                        }
                    }
                    val firstObj = arr.firstOrNull()?.jsonObject ?: return@withContext emptyList()
                    val plain = firstObj["plainLyrics"]?.jsonPrimitive?.content.orEmpty()
                    if (plain.isNotBlank()) {
                        return@withContext generatePlainLyricLines(plain, durationSeconds ?: 210)
                    }
                    emptyList()
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }

    fun parseLrc(lrcText: String): List<SyncedLyricLine> {
        val lines = lrcText.lines()
        val result = mutableListOf<SyncedLyricLine>()

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isBlank()) continue

            val matcher = LRC_LINE_PATTERN.matcher(trimmed)
            if (matcher.matches()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val fracStr = matcher.group(3).orEmpty()
                val frac = parseFractionMs(fracStr)

                val timeMs = (min * 60L * 1000L) + (sec * 1000L) + frac
                val rawContent = matcher.group(4).orEmpty().trim()
                if (rawContent.isBlank()) continue

                // Check for enhanced LRC word tags <mm:ss.xx>word
                val wordMatches = WORD_TAG_REGEX.findAll(rawContent).toList()
                if (wordMatches.isNotEmpty()) {
                    val parsedWords = mutableListOf<LyricWord>()
                    val cleanTextBuilder = StringBuilder()
                    for (i in wordMatches.indices) {
                        val wm = wordMatches[i]
                        val wMin = wm.groupValues[1].toLongOrNull() ?: min
                        val wSec = wm.groupValues[2].toLongOrNull() ?: sec
                        val wFrac = parseFractionMs(wm.groupValues[3])
                        val wStart = (wMin * 60L * 1000L) + (wSec * 1000L) + wFrac
                        val wWord = wm.groupValues[4].trim()
                        if (wWord.isNotBlank()) {
                            if (cleanTextBuilder.isNotEmpty()) cleanTextBuilder.append(' ')
                            cleanTextBuilder.append(wWord)
                            val nextStart = wordMatches.getOrNull(i + 1)?.let { nm ->
                                val nMin = nm.groupValues[1].toLongOrNull() ?: wMin
                                val nSec = nm.groupValues[2].toLongOrNull() ?: wSec
                                val nFrac = parseFractionMs(nm.groupValues[3])
                                (nMin * 60L * 1000L) + (nSec * 1000L) + nFrac
                            } ?: (wStart + 500L)
                            parsedWords.add(
                                LyricWord(
                                    word = wWord,
                                    startMs = wStart,
                                    endMs = nextStart.coerceAtLeast(wStart + 80L)
                                )
                            )
                        }
                    }
                    val finalText = cleanTextBuilder.toString().trim()
                    if (finalText.isNotBlank()) {
                        result.add(SyncedLyricLine(timeMs = timeMs, text = finalText, words = parsedWords))
                    }
                } else {
                    val words = rawContent.split(" ").filter { it.isNotBlank() }
                    val wordDuration = 2500L / words.size.coerceAtLeast(1)
                    val lyricWords = words.mapIndexed { idx, w ->
                        val start = timeMs + (idx * wordDuration)
                        LyricWord(word = w, startMs = start, endMs = start + (wordDuration * 0.85f).toLong())
                    }
                    result.add(SyncedLyricLine(timeMs = timeMs, text = rawContent, words = lyricWords))
                }
            }
        }
        return result.sortedBy { it.timeMs }
    }

    private fun parseFractionMs(fracStr: String): Long = when (fracStr.length) {
        1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
        2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
        3 -> fracStr.toLongOrNull() ?: 0L
        else -> 0L
    }

    private fun generatePlainLyricLines(plainText: String, durationSec: Int): List<SyncedLyricLine> {
        val lines = plainText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val intervalMs = ((durationSec * 1000L) / lines.size.coerceAtLeast(1)).coerceAtLeast(3000L)
        return lines.mapIndexed { index, line ->
            val timeMs = index * intervalMs
            SyncedLyricLine(timeMs = timeMs, text = line)
        }
    }
}
