package com.nayama.music.network

import com.nayama.music.model.Artist
import com.nayama.music.model.MusicSource
import com.nayama.music.model.Playlist
import com.nayama.music.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object YouTubeMusicProvider {

    private const val YTM_API = "https://music.youtube.com/youtubei/v1"
    private const val CLIENT_VERSION = "1.20240415.01.00"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val invidiousMirrors = listOf(
        "https://yt.chocolatemoo53.com",
        "https://invidious.nerdvpn.de",
        "https://inv.nadeko.net",
        "https://yewtu.be",
        "https://invidious.tiekoetter.com"
    )

    private val pipedMirrors = listOf(
        "https://pipedapi.ducks.party",
        "https://api.piped.private.coffee"
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    suspend fun searchTracks(query: String, limit: Int = 20): List<Track> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        // Search Songs first, and also general search to capture DJ remixes / unofficial mixes
        val songResults = searchWithParams(trimmed, params = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
        val generalResults = if (songResults.size < limit) {
            searchWithParams(trimmed, params = null)
        } else {
            emptyList()
        }

        (songResults + generalResults)
            .distinctBy { it.id }
            .take(limit)
    }

    suspend fun searchPlaylists(query: String, limit: Int = 10): List<Playlist> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        try {
            val payload = JSONObject().apply {
                put("context", buildContext())
                put("query", trimmed)
                put("params", "Eg-KAQwIABAAGAAgACgBMAVKChAFEAMQBBAJEAo%3D")
            }
            val body = postYtm("search", payload) ?: return@withContext emptyList()
            val root = JSONObject(body)
            val renderers = mutableListOf<JSONObject>()
            collectRenderers(root, "musicResponsiveListItemRenderer", renderers)

            val playlists = mutableListOf<Playlist>()
            for (r in renderers) {
                val browseId = r.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    ?.ifBlank { null } ?: continue
                val cols = r.optJSONArray("flexColumns") ?: continue
                val title = extractRunsText(cols.optJSONObject(0)).ifBlank { continue }
                val sub = extractRunsText(cols.optJSONObject(1)).ifBlank { "YouTube Music Playlist" }
                val artwork = extractBestThumbnail(r)

                playlists.add(
                    Playlist(
                        id = "ytm_pl_$browseId",
                        title = title,
                        description = sub,
                        artworkUrl = artwork,
                        trackCount = 0
                    )
                )
                if (playlists.size >= limit) break
            }
            playlists
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun searchArtists(query: String, limit: Int = 10): List<Artist> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        try {
            val payload = JSONObject().apply {
                put("context", buildContext())
                put("query", trimmed)
                put("params", "EgWKAQIgAWoKEAkQBRAKEAMQBA%3D%3D")
            }
            val body = postYtm("search", payload) ?: return@withContext emptyList()
            val root = JSONObject(body)
            val renderers = mutableListOf<JSONObject>()
            collectRenderers(root, "musicResponsiveListItemRenderer", renderers)

            val artists = mutableListOf<Artist>()
            for (r in renderers) {
                val browseId = r.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    ?.ifBlank { null } ?: continue
                val cols = r.optJSONArray("flexColumns") ?: continue
                val name = extractRunsText(cols.optJSONObject(0)).ifBlank { continue }
                val sub = extractRunsText(cols.optJSONObject(1)).ifBlank { "YouTube Music Artist" }
                val avatar = extractBestThumbnail(r)

                artists.add(
                    Artist(
                        id = "ytm_artist_$browseId",
                        name = name,
                        genre = "YouTube Music",
                        monthlyListeners = sub,
                        avatarUrl = avatar
                    )
                )
                if (artists.size >= limit) break
            }
            artists
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchLyricsText(videoId: String): String? = withContext(Dispatchers.IO) {
        val cleanId = videoId.removePrefix("ytm_").removePrefix("yt_")
        if (cleanId.isBlank()) return@withContext null

        try {
            val nextPayload = JSONObject().apply {
                put("context", buildContext())
                put("videoId", cleanId)
            }
            val nextBody = postYtm("next", nextPayload) ?: return@withContext null
            val browseRegex = Regex("\"browseId\":\"(MPLYt_[^\"]+)\"")
            val lyricsBrowseId = browseRegex.find(nextBody)?.groupValues?.getOrNull(1)
                ?: return@withContext null

            val browsePayload = JSONObject().apply {
                put("context", buildContext())
                put("browseId", lyricsBrowseId)
            }
            val browseBody = postYtm("browse", browsePayload) ?: return@withContext null
            val browseRoot = JSONObject(browseBody)

            val shelves = mutableListOf<JSONObject>()
            collectRenderers(browseRoot, "musicDescriptionShelfRenderer", shelves)
            for (shelf in shelves) {
                val runs = shelf.optJSONObject("description")?.optJSONArray("runs") ?: continue
                val text = buildString {
                    for (i in 0 until runs.length()) {
                        append(runs.optJSONObject(i)?.optString("text") ?: "")
                    }
                }.trim()
                if (text.isNotBlank()) return@withContext text
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun resolveAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val cleanId = videoId.removePrefix("ytm_").removePrefix("yt_")
        if (cleanId.isBlank()) return@withContext null

        // 1. Try Invidious adaptive audio streams
        for (mirror in invidiousMirrors) {
            try {
                val req = Request.Builder()
                    .url("$mirror/api/v1/videos/$cleanId?local=true")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) NayamaMusic/1.0")
                    .build()
                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val formats = json.optJSONArray("adaptiveFormats")
                        if (formats != null) {
                            var bestUrl: String? = null
                            var bestBitrate = -1
                            for (i in 0 until formats.length()) {
                                val fmt = formats.optJSONObject(i) ?: continue
                                val type = fmt.optString("type")
                                if (type.startsWith("audio/mp4") || type.startsWith("audio/webm") || type.startsWith("audio/")) {
                                    val br = fmt.optString("bitrate").toIntOrNull() ?: 0
                                    val url = fmt.optString("url").ifBlank { null } ?: continue
                                    if (br > bestBitrate) {
                                        bestBitrate = br
                                        bestUrl = url
                                    }
                                }
                            }
                            if (!bestUrl.isNullOrBlank()) return@withContext bestUrl
                        }
                    }
                }
            } catch (_: Exception) {
                // Try next mirror
            }
        }

        // 2. Try Piped API audioStreams
        for (mirror in pipedMirrors) {
            try {
                val req = Request.Builder()
                    .url("$mirror/streams/$cleanId")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) NayamaMusic/1.0")
                    .build()
                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val streams = json.optJSONArray("audioStreams")
                        if (streams != null && streams.length() > 0) {
                            var bestUrl: String? = null
                            var bestBitrate = -1
                            for (i in 0 until streams.length()) {
                                val s = streams.optJSONObject(i) ?: continue
                                val br = s.optInt("bitrate", 0)
                                val url = s.optString("url").ifBlank { null } ?: continue
                                if (br > bestBitrate) {
                                    bestBitrate = br
                                    bestUrl = url
                                }
                            }
                            if (!bestUrl.isNullOrBlank()) return@withContext bestUrl
                        }
                    }
                }
            } catch (_: Exception) {
                // Try next mirror
            }
        }

        null
    }

    private fun searchWithParams(query: String, params: String?): List<Track> {
        return try {
            val payload = JSONObject().apply {
                put("context", buildContext())
                put("query", query)
                if (!params.isNullOrBlank()) put("params", params)
            }
            val body = postYtm("search", payload) ?: return emptyList()
            val root = JSONObject(body)
            val renderers = mutableListOf<JSONObject>()
            collectRenderers(root, "musicResponsiveListItemRenderer", renderers)

            val tracks = mutableListOf<Track>()
            for (r in renderers) {
                val videoId = r.optJSONObject("playlistItemData")
                    ?.optString("videoId")
                    ?.ifBlank { null }
                    ?: r.optJSONObject("overlay")
                        ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("musicPlayButtonRenderer")
                        ?.optJSONObject("playNavigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                        ?.optString("videoId")
                        ?.ifBlank { null }
                    ?: continue

                val cols = r.optJSONArray("flexColumns") ?: continue
                val title = extractRunsText(cols.optJSONObject(0)).ifBlank { continue }

                val secondColRuns = cols.optJSONObject(1)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs")

                val metaParts = parseMetadataRuns(secondColRuns)
                val artist = metaParts.artist.ifBlank { "YouTube Music" }
                val album = metaParts.album.ifBlank { "Single" }
                val durationSec = metaParts.durationSeconds.coerceAtLeast(30)
                val artwork = extractBestThumbnail(r)
                    ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                tracks.add(
                    Track(
                        id = "ytm_$videoId",
                        title = title,
                        artist = artist,
                        album = album,
                        durationSeconds = durationSec,
                        artworkUrl = artwork,
                        streamUrl = null, // Resolved dynamically at playback time
                        genre = "YouTube Music",
                        isPlayable = true,
                        sourceAttribution = "YouTube Music",
                        source = MusicSource.YOUTUBE_MUSIC,
                        audioQualityLabel = "OPUS / M4A"
                    )
                )
            }
            tracks
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun postYtm(endpoint: String, payload: JSONObject): String? {
        val req = Request.Builder()
            .url("$YTM_API/$endpoint?prettyPrint=false")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .header("Content-Type", "application/json")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            )
            .build()
        val resp = httpClient.newCall(req).execute()
        return if (resp.isSuccessful) resp.body?.string() else null
    }

    private fun buildContext(): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB_REMIX")
                put("clientVersion", CLIENT_VERSION)
                put("hl", "en")
                put("gl", "US")
            })
        }
    }

    private fun collectRenderers(node: Any?, keyName: String, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                if (node.has(keyName)) {
                    node.optJSONObject(keyName)?.let { out.add(it) }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    collectRenderers(node.opt(k), keyName, out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectRenderers(node.opt(i), keyName, out)
                }
            }
        }
    }

    private fun extractRunsText(colObj: JSONObject?): String {
        val runs = colObj?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs") ?: return ""
        return buildString {
            for (i in 0 until runs.length()) {
                append(runs.optJSONObject(i)?.optString("text") ?: "")
            }
        }.trim()
    }

    private fun extractBestThumbnail(renderer: JSONObject): String? {
        val thumbs = renderer.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails") ?: return null
        if (thumbs.length() == 0) return null
        val raw = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")?.ifBlank { null }
        return raw?.replace(Regex("=w\\d+-h\\d+[^\"&]*"), "=w544-h544-l90-rj")
    }

    private data class ParsedMeta(
        val artist: String = "",
        val album: String = "",
        val durationSeconds: Int = 180
    )

    private fun parseMetadataRuns(runs: JSONArray?): ParsedMeta {
        if (runs == null) return ParsedMeta()
        val segments = mutableListOf<String>()
        for (i in 0 until runs.length()) {
            val txt = runs.optJSONObject(i)?.optString("text")?.trim() ?: ""
            if (txt.isNotBlank() && txt != "•" && txt != "·") {
                segments.add(txt)
            }
        }
        var artist = ""
        var album = ""
        var duration = 180

        val durationRegex = Regex("^(\\d{1,2}:)?\\d{1,2}:\\d{2}$")
        val nonMetaLabels = setOf("Song", "Video", "Single", "EP", "Album", "Playlist")

        for (seg in segments) {
            if (durationRegex.matches(seg)) {
                duration = parseDuration(seg)
            } else if (seg in nonMetaLabels || seg.endsWith("plays") || seg.endsWith("views")) {
                continue
            } else if (artist.isBlank()) {
                artist = seg
            } else if (album.isBlank()) {
                album = seg
            }
        }
        return ParsedMeta(artist = artist, album = album, durationSeconds = duration)
    }

    private fun parseDuration(text: String): Int {
        val parts = text.split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 180
        }
    }
}

