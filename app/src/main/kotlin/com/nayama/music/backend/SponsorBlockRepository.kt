package com.nayama.music.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class SkipSegment(
    val uuid: String,
    val category: String,
    val startMs: Long,
    val endMs: Long
)

/**
 * Queries SponsorBlock for non-music segments ("music_offtopic", "intro", "outro")
 * on YouTube Music tracks so Rezona can smoothly skip spoken intros/outros.
 */
object SponsorBlockRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, List<SkipSegment>>()

    suspend fun getNonMusicSegments(videoId: String): List<SkipSegment> = withContext(Dispatchers.IO) {
        val cleanId = videoId.removePrefix("yt_").trim()
        if (cleanId.length != 11) return@withContext emptyList()
        cache[cleanId]?.let { return@withContext it }

        val url = "https://sponsor.ajay.app/api/skipSegments?videoID=$cleanId" +
            "&categories=%5B%22music_offtopic%22%2C%22intro%22%2C%22outro%22%5D"

        val result = runCatching {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val body = resp.body?.string().orEmpty()
                if (body.isBlank() || !body.trimStart().startsWith("[")) return@use emptyList()
                parseSegmentsJson(body)
            }
        }.getOrDefault(emptyList())

        cache[cleanId] = result
        result
    }

    fun parseSegmentsJson(rawJson: String): List<SkipSegment> {
        val arr = runCatching { JSONArray(rawJson) }.getOrNull() ?: return emptyList()
        val list = mutableListOf<SkipSegment>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val seg = obj.optJSONArray("segment") ?: continue
            if (seg.length() < 2) continue
            val startMs = (seg.optDouble(0, 0.0) * 1000.0).toLong().coerceAtLeast(0L)
            val endMs = (seg.optDouble(1, 0.0) * 1000.0).toLong().coerceAtLeast(0L)
            if (endMs - startMs >= 1500L) {
                list.add(
                    SkipSegment(
                        uuid = obj.optString("UUID", "seg_$i"),
                        category = obj.optString("category", "music_offtopic"),
                        startMs = startMs,
                        endMs = endMs
                    )
                )
            }
        }
        return list.sortedBy { it.startMs }
    }
}
