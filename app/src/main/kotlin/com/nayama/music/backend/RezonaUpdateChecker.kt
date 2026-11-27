package com.nayama.music.backend

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.nayama.music.BuildConfig
import com.nayama.music.MainActivity
import com.nayama.music.NayamaApplication
import com.nayama.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RezonaUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    val announcementId: String = "",
    val title: String = "",
    val added: List<String> = emptyList(),
    val fixed: List<String> = emptyList(),
    val improved: List<String> = emptyList(),
    val isNewerApk: Boolean = true
)

/**
 * Checks for newer APK releases and live in-app announcements published on GitHub / Vercel
 * without blocking app start.
 */
object RezonaUpdateChecker {

    private const val VERCEL_VERSION_JSON_URL =
        "https://rezona-music-web.vercel.app/version.json"
    private const val GITHUB_VERSION_JSON_URL =
        "https://raw.githubusercontent.com/nynzzkeren/rezona-music/main/web/public/version.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(): RezonaUpdateInfo? = withContext(Dispatchers.IO) {
        val urls = listOf(
            "$VERCEL_VERSION_JSON_URL?t=${System.currentTimeMillis() / 1000L}",
            GITHUB_VERSION_JSON_URL
        )
        for (url in urls) {
            val parsed = runCatching {
                val req = Request.Builder()
                    .url(url)
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string().orEmpty()
                    parseBulletin(body, BuildConfig.VERSION_CODE)
                }
            }.getOrNull()
            if (parsed != null) return@withContext parsed
        }
        null
    }

    /**
     * Determines whether an update/announcement popup & system notification should be shown:
     * - If there is a newer APK (`info.isNewerApk`), notify unless already dismissed for that `versionCode`.
     * - If the user is ALREADY on the latest APK (`!info.isNewerApk`) AND just installed or just updated
     *   (`lastSeenAppVersionCode < currentAppVersionCode`), do NOT show any popup or notification!
     *   Only show live announcements posted AFTER the user was already on this version.
     */
    fun shouldShowBulletin(
        info: RezonaUpdateInfo,
        currentAppVersionCode: Int,
        lastSeenAppVersionCode: Int,
        dismissedUpdateVersion: Int,
        dismissedAnnouncementId: String
    ): Boolean {
        if (info.isNewerApk) {
            return info.versionCode > dismissedUpdateVersion
        }
        if (lastSeenAppVersionCode < currentAppVersionCode) {
            return false
        }
        return info.announcementId.isNotBlank() && info.announcementId != dismissedAnnouncementId
    }

    /**
     * Strict version-code comparison (used by unit tests & APK version gate).
     */
    fun parseAndCheck(rawJson: String, currentVersionCode: Int): RezonaUpdateInfo? {
        val info = parseBulletin(rawJson, currentVersionCode) ?: return null
        return if (info.versionCode > currentVersionCode) info else null
    }

    /**
     * Parses both APK version updates AND live in-app announcements (`announcementId`, `added`, `fixed`, `improved`).
     */
    fun parseBulletin(rawJson: String, currentVersionCode: Int): RezonaUpdateInfo? {
        val obj = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
        val remoteCode = obj.optInt("versionCode", 0)
        val announcementId = obj.optString("announcementId", "").trim()
        val isNewer = remoteCode > currentVersionCode
        if (!isNewer && announcementId.isEmpty()) return null

        val versionName = obj.optString("versionName", "").trim().ifBlank { "1.0.$remoteCode" }
        val apkUrl = obj.optString("apkUrl", "").trim().ifBlank {
            "https://rezona-music-web.vercel.app"
        }
        val title = obj.optString("title", "").trim().ifBlank {
            "Update Rezona V$versionName"
        }
        val added = parseStringArray(obj.optJSONArray("added"))
        val fixed = parseStringArray(obj.optJSONArray("fixed"))
        val improved = parseStringArray(obj.optJSONArray("improved"))
        val changelog = obj.optString("changelog", "Performance improvements and new features.")

        return RezonaUpdateInfo(
            versionCode = remoteCode,
            versionName = versionName,
            apkUrl = apkUrl,
            changelog = changelog,
            announcementId = announcementId.ifEmpty { "v$remoteCode" },
            title = title,
            added = added,
            fixed = fixed,
            improved = improved,
            isNewerApk = isNewer
        )
    }

    private fun parseStringArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val item = arr.optString(i, "").trim()
            if (item.isNotEmpty()) list.add(item)
        }
        return list
    }

    fun formatBulletinBody(info: RezonaUpdateInfo): String {
        val sb = StringBuilder()
        if (info.added.isNotEmpty()) {
            sb.append("1. Add :\n")
            info.added.forEach { sb.append("• ").append(it).append('\n') }
        }
        if (info.fixed.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append("2. Fix :\n")
            info.fixed.forEach { sb.append("• ").append(it).append('\n') }
        }
        if (info.improved.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append("3. Improved :\n")
            info.improved.forEach { sb.append("• ").append(it).append('\n') }
        }
        if (info.changelog.isNotBlank()) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(info.changelog)
        }
        return sb.toString().trim()
    }

    /**
     * Posts a real Android System Notification from the Rezona APK to the phone's notification shade
     * whenever a new announcement or APK update is published.
     */
    fun postSystemAnnouncementNotification(context: Context, info: RezonaUpdateInfo) {
        runCatching {
            val appCtx = context.applicationContext
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val targetIntent = if (info.isNewerApk && info.apkUrl.isNotBlank()) {
                Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl))
            } else {
                Intent(appCtx, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            val pi = PendingIntent.getActivity(
                appCtx,
                2026,
                targetIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val fullBody = formatBulletinBody(info)
            val summaryLine = info.added.firstOrNull()
                ?: info.fixed.firstOrNull()
                ?: info.improved.firstOrNull()
                ?: info.changelog

            val notif = NotificationCompat.Builder(appCtx, NayamaApplication.ANNOUNCEMENT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(info.title.ifBlank { "Update Rezona V${info.versionName}" })
                .setContentText(summaryLine)
                .setStyle(NotificationCompat.BigTextStyle().bigText(fullBody))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(
                    R.drawable.ic_download,
                    if (info.isNewerApk) "Download Update" else "Open Rezona",
                    pi
                )
                .build()

            nm.notify(NayamaApplication.ANNOUNCEMENT_NOTIFICATION_ID, notif)
        }
    }
}
