package com.nayama.music.backend

import com.nayama.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RezonaUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String
)

/**
 * Checks for newer APK releases published on GitHub / Vercel without blocking app start.
 */
object RezonaUpdateChecker {

    private const val VERSION_JSON_URL =
        "https://raw.githubusercontent.com/nynzzkeren/rezona-music/main/web/public/version.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(): RezonaUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(VERSION_JSON_URL)
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                parseAndCheck(body, BuildConfig.VERSION_CODE)
            }
        }.getOrNull()
    }

    fun parseAndCheck(rawJson: String, currentVersionCode: Int): RezonaUpdateInfo? {
        val obj = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
        val remoteCode = obj.optInt("versionCode", 0)
        if (remoteCode <= currentVersionCode) return null
        val versionName = obj.optString("versionName", "").trim().ifBlank { "v$remoteCode" }
        val apkUrl = obj.optString("apkUrl", "").trim().ifBlank {
            "https://github.com/nynzzkeren/rezona-music/raw/main/web/public/Rezona-release.apk"
        }
        val changelog = obj.optString("changelog", "Performance improvements and new features.")
        return RezonaUpdateInfo(
            versionCode = remoteCode,
            versionName = versionName,
            apkUrl = apkUrl,
            changelog = changelog
        )
    }
}
