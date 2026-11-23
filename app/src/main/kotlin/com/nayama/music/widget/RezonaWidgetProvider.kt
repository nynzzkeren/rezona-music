package com.nayama.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.nayama.music.MainActivity
import com.nayama.music.R
import com.nayama.music.playback.NayamaMediaService
import com.nayama.music.playback.RezonaPlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RezonaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        RezonaWidgetUpdater.updateAll(context)
    }
}

class RezonaCompactWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        RezonaWidgetUpdater.updateAll(context)
    }
}

object RezonaWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cachedArtUrl: String? = null
    @Volatile
    private var cachedArtBitmap: Bitmap? = null

    fun updateAll(context: Context) {
        val appCtx = context.applicationContext
        val manager = AppWidgetManager.getInstance(appCtx)
        val cardIds = manager.getAppWidgetIds(ComponentName(appCtx, RezonaWidgetProvider::class.java))
        val compactIds = manager.getAppWidgetIds(ComponentName(appCtx, RezonaCompactWidgetProvider::class.java))
        if (cardIds.isEmpty() && compactIds.isEmpty()) return

        val controller = RezonaPlayerController.getExistingInstance()
        val track = controller?.currentTrack?.value
        val title = track?.title ?: "No song playing"
        val artist = track?.artist ?: "Tap to open Rezona"
        val isPlaying = controller?.isPlaying?.value ?: false
        val isLiked = controller?.isLiked?.value ?: false
        val posMs = controller?.currentPositionMs?.longValue ?: 0L
        val durMs = controller?.durationMs?.longValue ?: 0L
        val progress = if (durMs > 0L) ((posMs * 1000L) / durMs).toInt().coerceIn(0, 1000) else 0
        val artUrl = track?.coverArtUrl.orEmpty()
        val streakText = "♪ ${cachedPlayCount.coerceAtLeast(if (track != null) 1 else 0)}"

        val initialBmp = if (artUrl.isNotBlank() && artUrl == cachedArtUrl) cachedArtBitmap else null
        pushRemoteViews(appCtx, manager, cardIds, compactIds, title, artist, streakText, isPlaying, isLiked, progress, initialBmp)

        scope.launch {
            val totalPlays = runCatching {
                com.nayama.music.data.local.RezonaDatabase.getInstance(appCtx)
                    .historyDao().getAllEntriesRaw(500).count { !it.deleted }
            }.getOrDefault(cachedPlayCount)
            val needsArt = artUrl.isNotBlank() && artUrl != cachedArtUrl
            val bmp = if (needsArt) loadRoundedCover(appCtx, artUrl) else initialBmp
            if (bmp != null && needsArt) {
                cachedArtUrl = artUrl
                cachedArtBitmap = bmp
            }
            if (totalPlays != cachedPlayCount || needsArt) {
                cachedPlayCount = totalPlays
                val updatedStreak = "♪ ${totalPlays.coerceAtLeast(if (track != null) 1 else 0)}"
                pushRemoteViews(appCtx, manager, cardIds, compactIds, title, artist, updatedStreak, isPlaying, isLiked, progress, bmp)
            }
        }
    }

    @Volatile
    private var cachedPlayCount: Int = 0

    private fun pushRemoteViews(
        context: Context,
        manager: AppWidgetManager,
        cardIds: IntArray,
        compactIds: IntArray,
        title: String,
        artist: String,
        streakText: String,
        isPlaying: Boolean,
        isLiked: Boolean,
        progress: Int,
        coverBitmap: Bitmap?
    ) {
        val openAppIntent = PendingIntent.getActivity(
            context,
            100,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val prevPi = serviceActionPendingIntent(context, 101, NayamaMediaService.ACTION_PREV)
        val playPi = serviceActionPendingIntent(
            context,
            102,
            if (isPlaying) NayamaMediaService.ACTION_PAUSE else NayamaMediaService.ACTION_PLAY
        )
        val nextPi = serviceActionPendingIntent(context, 103, NayamaMediaService.ACTION_NEXT)
        val likePi = serviceActionPendingIntent(context, 104, NayamaMediaService.ACTION_TOGGLE_LIKE)

        val playIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        val likeIcon = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        val likeTint = if (isLiked) 0xFFFF5A6E.toInt() else 0xFFFFFFFF.toInt()

        for (id in cardIds) {
            val rv = RemoteViews(context.packageName, R.layout.widget_rezona_card)
            rv.setTextViewText(R.id.widget_title, title)
            rv.setTextViewText(R.id.widget_artist, artist)
            rv.setTextViewText(R.id.widget_streak_badge, streakText)
            rv.setProgressBar(R.id.widget_progress, 1000, progress, false)
            rv.setImageViewResource(R.id.widget_btn_play, playIcon)
            rv.setImageViewResource(R.id.widget_btn_like, likeIcon)
            rv.setInt(R.id.widget_btn_like, "setColorFilter", likeTint)
            if (coverBitmap != null) {
                rv.setImageViewBitmap(R.id.widget_cover, coverBitmap)
            } else {
                rv.setImageViewResource(R.id.widget_cover, R.drawable.ic_rezona_logo)
            }
            rv.setOnClickPendingIntent(R.id.widget_root, openAppIntent)
            rv.setOnClickPendingIntent(R.id.widget_cover, openAppIntent)
            rv.setOnClickPendingIntent(R.id.widget_meta, openAppIntent)
            rv.setOnClickPendingIntent(R.id.widget_btn_prev, prevPi)
            rv.setOnClickPendingIntent(R.id.widget_btn_play, playPi)
            rv.setOnClickPendingIntent(R.id.widget_btn_next, nextPi)
            rv.setOnClickPendingIntent(R.id.widget_btn_like, likePi)
            manager.updateAppWidget(id, rv)
        }

        for (id in compactIds) {
            val rv = RemoteViews(context.packageName, R.layout.widget_rezona_compact)
            rv.setTextViewText(R.id.widget_compact_title, title)
            rv.setTextViewText(R.id.widget_compact_artist, artist)
            rv.setImageViewResource(R.id.widget_compact_play, playIcon)
            rv.setImageViewResource(R.id.widget_compact_like, likeIcon)
            rv.setInt(R.id.widget_compact_like, "setColorFilter", likeTint)
            if (coverBitmap != null) {
                rv.setImageViewBitmap(R.id.widget_compact_cover, coverBitmap)
            } else {
                rv.setImageViewResource(R.id.widget_compact_cover, R.drawable.ic_rezona_logo)
            }
            rv.setOnClickPendingIntent(R.id.widget_compact_root, openAppIntent)
            rv.setOnClickPendingIntent(R.id.widget_compact_cover, openAppIntent)
            rv.setOnClickPendingIntent(R.id.widget_compact_meta, openAppIntent)
            rv.setOnClickPendingIntent(R.id.widget_compact_play, playPi)
            rv.setOnClickPendingIntent(R.id.widget_compact_next, nextPi)
            rv.setOnClickPendingIntent(R.id.widget_compact_like, likePi)
            manager.updateAppWidget(id, rv)
        }
    }

    private fun serviceActionPendingIntent(context: Context, reqCode: Int, action: String): PendingIntent {
        val intent = Intent(context, NayamaMediaService::class.java).apply { this.action = action }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            PendingIntent.getService(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }

    private suspend fun loadRoundedCover(context: Context, url: String): Bitmap? {
        return runCatching {
            val req = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(192, 192)
                .build()
            val res = context.imageLoader.execute(req)
            val raw = (res as? SuccessResult)?.drawable?.toBitmap(192, 192, Bitmap.Config.ARGB_8888)
                ?: return null
            val out = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawRoundRect(RectF(0f, 0f, 192f, 192f), 32f, 32f, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(raw, 0f, 0f, paint)
            out
        }.getOrNull()
    }
}
