package com.nayama.music.playback

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaStyleNotificationHelper
import com.nayama.music.MainActivity
import com.nayama.music.NayamaApplication
import com.nayama.music.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * Foreground MediaPlayback Service using Media3 MediaStyleNotificationHelper.MediaStyle(mediaSession)
 * so Android 13-15 / Samsung OneUI automatically displays the full album-art Quick Settings &
 * Lockscreen Media Control Card with SeekBar, artwork backdrop, and transport buttons,
 * and stops playback immediately when removed from Recents (`onTaskRemoved`).
 */
class NayamaMediaService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var artworkFetchJob: Job? = null
    private var cachedArtworkUrl: String? = null
    private var cachedArtworkBitmap: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_STOP -> {
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
            ACTION_PLAY -> {
                MusicPlayerController.getInstance(this).resume()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                MusicPlayerController.getInstance(this).pause()
                return START_NOT_STICKY
            }
            ACTION_NEXT -> {
                MusicPlayerController.getInstance(this).skipToNext()
                return START_NOT_STICKY
            }
            ACTION_PREV -> {
                MusicPlayerController.getInstance(this).skipToPrevious()
                return START_NOT_STICKY
            }
        }

        val trackTitle = intent?.getStringExtra(EXTRA_TRACK_TITLE) ?: "Rezona"
        val trackArtist = intent?.getStringExtra(EXTRA_TRACK_ARTIST) ?: "Streaming"
        val artworkUrl = intent?.getStringExtra(EXTRA_ARTWORK_URL)
        val isPlaying = intent?.getBooleanExtra(EXTRA_IS_PLAYING, true) ?: true

        val initialArt = if (!artworkUrl.isNullOrBlank() && artworkUrl == cachedArtworkUrl) cachedArtworkBitmap else null
        val notification = buildMediaNotification(trackTitle, trackArtist, isPlaying, initialArt)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NayamaApplication.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NayamaApplication.NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
        }

        if (!artworkUrl.isNullOrBlank() && artworkUrl != cachedArtworkUrl) {
            artworkFetchJob?.cancel()
            artworkFetchJob = serviceScope.launch {
                val bmp = downloadBitmap(artworkUrl)
                if (bmp != null) {
                    cachedArtworkUrl = artworkUrl
                    cachedArtworkBitmap = bmp
                    val updated = buildMediaNotification(trackTitle, trackArtist, isPlaying, bmp)
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.notify(NayamaApplication.NOTIFICATION_ID, updated)
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        runCatching {
            MusicPlayerController.getInstance(applicationContext).stopAndClearForTaskRemoval()
        }
        runCatching {
            RezonaPlayerController.getExistingInstance()?.stopPlaybackOnTaskRemoval()
        }
        stopForegroundAndSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        artworkFetchJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun stopForegroundAndSelf() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    @OptIn(UnstableApi::class)
    private fun buildMediaNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        artworkBitmap: Bitmap?
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, NayamaMediaService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val playPausePendingIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, NayamaMediaService::class.java).apply {
                action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nextPendingIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, NayamaMediaService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val mediaSession = MusicPlayerController.getInstance(applicationContext).getMediaSession()

        val builder = NotificationCompat.Builder(this, NayamaApplication.PLAYBACK_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_skip_previous, "Previous", prevPendingIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                if (isPlaying) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(R.drawable.ic_skip_next, "Next", nextPendingIntent)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (artworkBitmap != null) {
            builder.setLargeIcon(artworkBitmap)
        }

        if (mediaSession != null) {
            builder.setStyle(MediaStyleNotificationHelper.MediaStyle(mediaSession))
        }

        return builder.build()
    }

    private fun downloadBitmap(urlStr: String): Bitmap? {
        return runCatching {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 5000
            conn.connect()
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    companion object {
        const val ACTION_START = "com.nayama.music.ACTION_START"
        const val ACTION_STOP = "com.nayama.music.ACTION_STOP"
        const val ACTION_PLAY = "com.nayama.music.ACTION_PLAY"
        const val ACTION_PAUSE = "com.nayama.music.ACTION_PAUSE"
        const val ACTION_NEXT = "com.nayama.music.ACTION_NEXT"
        const val ACTION_PREV = "com.nayama.music.ACTION_PREV"
        const val EXTRA_TRACK_TITLE = "extra_title"
        const val EXTRA_TRACK_ARTIST = "extra_artist"
        const val EXTRA_ARTWORK_URL = "extra_artwork_url"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        fun start(
            context: Context,
            title: String,
            artist: String,
            artworkUrl: String? = null,
            isPlaying: Boolean = true
        ) {
            val intent = Intent(context, NayamaMediaService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TRACK_TITLE, title)
                putExtra(EXTRA_TRACK_ARTIST, artist)
                putExtra(EXTRA_ARTWORK_URL, artworkUrl.orEmpty())
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NayamaMediaService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }
}
