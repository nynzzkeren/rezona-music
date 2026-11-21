package com.nayama.music.backend.innertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * YouTube Music InnerTube HTTP client.
 * Connects directly to the WEB_REMIX endpoint used by YouTube Music Web.
 * Architecture references: InnerTune / Metrolist / SimpMusic (GPL-3.0).
 */
class InnerTubeClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "InnerTubeClient"
        private const val BASE_URL = "https://music.youtube.com/youtubei/v1/"
        private const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-KLET5YdCE"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private const val CLIENT_CONTEXT_JSON = """{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20240101.01.00","hl":"en","gl":"US"}}"""

        private fun createClientContext(): JSONObject {
            return JSONObject(CLIENT_CONTEXT_JSON)
        }
    }

    private suspend fun post(
        endpoint: String,
        payload: JSONObject,
        continuation: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val contQuery = if (!continuation.isNullOrBlank()) {
            val enc = URLEncoder.encode(continuation, "UTF-8")
            "&ctoken=$enc&continuation=$enc"
        } else ""
        val url = "$BASE_URL$endpoint?key=$API_KEY$contQuery"
        payload.put("context", createClientContext())

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0")
            .header("Referer", "https://music.youtube.com/")
            .header("Origin", "https://music.youtube.com")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", "1.20240101.01.00")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    SafeLog.w(TAG, "Request to $endpoint failed with code ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            SafeLog.e(TAG, "Exception calling $endpoint", e)
            null
        }
    }

    suspend fun search(query: String, filter: InnerTubeFilter = InnerTubeFilter.ALL): String? {
        val payload = JSONObject().apply {
            put("query", query)
            if (filter.param != null) {
                put("params", filter.param)
            }
        }
        return post("search", payload)
    }

    suspend fun searchWithContinuation(continuation: String): String? {
        val payload = JSONObject().apply {
            put("continuation", continuation)
        }
        return post("search", payload, continuation = continuation)
    }

    suspend fun getSearchSuggestions(query: String): String? {
        val payload = JSONObject().apply {
            put("input", query)
        }
        return post("music/get_search_suggestions", payload)
    }

    suspend fun browse(browseId: String, params: String? = null): String? {
        val payload = JSONObject().apply {
            put("browseId", browseId)
            if (params != null) {
                put("params", params)
            }
        }
        return post("browse", payload)
    }

    suspend fun browseWithContinuation(continuation: String): String? {
        val payload = JSONObject().apply {
            put("continuation", continuation)
        }
        return post("browse", payload, continuation = continuation)
    }

    suspend fun next(videoId: String, playlistId: String? = null): String? {
        val payload = JSONObject().apply {
            put("videoId", videoId)
            if (playlistId != null) {
                put("playlistId", playlistId)
            }
            put("isAudioOnly", true)
            put("enablePersistentPlaylistPanel", true)
        }
        return post("next", payload)
    }

    /**
     * Direct InnerTube Player API fallback (ANDROID_VR & IOS clients) used when NewPipe fails
     * to extract a progressive audio stream for restricted/ciphered YouTube Music tracks.
     */
    suspend fun getPlayerFormats(videoId: String): String? = withContext(Dispatchers.IO) {
        val cleanId = videoId.removePrefix("yt_").substringAfter("v=").substringBefore("&").trim()
        if (cleanId.isBlank()) return@withContext null

        val clients = listOf(
            Triple(
                "ANDROID_VR",
                "1.60.19",
                "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12; Quest 3 Build/SQ3A.220605.009.A1) gzip"
            ),
            Triple(
                "IOS",
                "20.10.4",
                "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
            )
        )

        for ((clientName, clientVersion, ua) in clients) {
            try {
                val contextObj = JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", clientName)
                        put("clientVersion", clientVersion)
                        put("hl", "en")
                        put("gl", "US")
                    })
                }
                val payload = JSONObject().apply {
                    put("context", contextObj)
                    put("videoId", cleanId)
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                }
                val req = Request.Builder()
                    .url("https://youtubei.googleapis.com/youtubei/v1/player?key=$API_KEY")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .header("User-Agent", ua)
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank() && body.contains("\"streamingData\"")) {
                            return@withContext body
                        }
                    }
                }
            } catch (e: Exception) {
                SafeLog.w(TAG, "InnerTube player fallback ($clientName) failed for $cleanId: ${e.message}")
            }
        }
        null
    }
}
