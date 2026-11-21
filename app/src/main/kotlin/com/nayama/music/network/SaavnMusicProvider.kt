package com.nayama.music.network

import com.nayama.music.model.MusicSource
import com.nayama.music.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object SaavnMusicProvider {

    private const val DES_KEY = "38346591"
    private const val BASE_SEARCH_URL = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&ctx=android"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun searchTracks(query: String, limit: Int = 20): List<Track> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "$BASE_SEARCH_URL&n=$limit&p=1&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) NayamaMusic/1.0")
                .header("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val responseBody = response.body?.string() ?: return@withContext emptyList()
            parseSaavnSearchResults(responseBody)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseSaavnSearchResults(jsonStr: String): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            val root = JSONObject(jsonStr)
            val results = root.optJSONArray("results") ?: return emptyList()

            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val id = item.optString("id").ifBlank { continue }
                val songTitle = cleanHtmlEntities(item.optString("song").ifBlank { item.optString("title") })
                if (songTitle.isBlank()) continue

                val artist = cleanHtmlEntities(
                    item.optString("primary_artists")
                        .ifBlank { item.optString("singers") }
                        .ifBlank { "Unknown Artist" }
                )
                val album = cleanHtmlEntities(item.optString("album").ifBlank { "Single" })
                val durationSec = item.optString("duration").toIntOrNull() ?: 180

                var artworkUrl = item.optString("image").ifBlank { null }
                if (artworkUrl != null) {
                    artworkUrl = artworkUrl.replace("150x150", "500x500")
                }

                val encUrl = item.optString("encrypted_media_url").ifBlank { null }
                val streamUrl = if (encUrl != null) decryptMediaUrl(encUrl) else null

                if (!streamUrl.isNullOrBlank()) {
                    tracks.add(
                        Track(
                            id = "saavn_$id",
                            title = songTitle,
                            artist = artist,
                            album = album,
                            durationSeconds = durationSec,
                            artworkUrl = artworkUrl,
                            streamUrl = streamUrl,
                            genre = item.optString("language").ifBlank { "World" },
                            playCount = item.optString("play_count").toLongOrNull(),
                            isPlayable = true,
                            sourceAttribution = "JioSaavn 320kbps",
                            source = MusicSource.SAAVN,
                            audioQualityLabel = "AAC 320 KBPS"
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Ignore parse errors
        }
        return tracks
    }

    fun decryptMediaUrl(encryptedMediaUrl: String): String? {
        return try {
            val secretKey = SecretKeySpec(DES_KEY.toByteArray(Charsets.UTF_8), "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)

            val cipherTextBytes = try {
                android.util.Base64.decode(encryptedMediaUrl, android.util.Base64.DEFAULT)
            } catch (_: Throwable) {
                java.util.Base64.getDecoder().decode(encryptedMediaUrl)
            }

            val decryptedBytes = cipher.doFinal(cipherTextBytes)
            val rawUrl = String(decryptedBytes, Charsets.UTF_8).trim()

            // Upgrade to 320kbps direct AAC stream if available
            rawUrl.replace("_96.mp4", "_320.mp4")
                .replace("_160.mp4", "_320.mp4")
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanHtmlEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }
}

