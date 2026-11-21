package com.nayama.music

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class NayamaApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        setupStrictMode()
        setupCrashLogging()
        createNotificationChannels()
        initFirebaseAndSync()
        initNewPipe()
    }

    private fun initFirebaseAndSync() {
        runCatching {
            if (com.google.firebase.FirebaseApp.getApps(this).isNotEmpty()) {
                val appCheck = com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
                appCheck.installAppCheckProviderFactory(
                    com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            }
        }.onFailure {
            Log.w("NayamaApplication", "App Check init skipped: ${it.javaClass.simpleName}")
        }
        runCatching {
            com.nayama.music.auth.FirebaseAuthManager.getInstance(this)
            com.nayama.music.sync.RezonaCloudSyncManager.getInstance(this)
        }.onFailure {
            Log.w("NayamaApplication", "Firebase/Sync init skipped: ${it.javaClass.simpleName}")
        }
    }

    private fun initNewPipe() {
        try {
            org.schabi.newpipe.extractor.NewPipe.init(com.nayama.music.backend.NewPipeDownloader.create())
        } catch (e: Throwable) {
            Log.w("NayamaApplication", "Failed to init NewPipeExtractor", e)
        }
    }

    private fun setupStrictMode() {
        if (BuildConfig.DEBUG) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }

    private fun setupCrashLogging() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stackTrace = Log.getStackTraceString(throwable)
                Log.e("NayamaCrash", "FATAL CRASH on thread ${thread.name}: $stackTrace")
                getSharedPreferences("nayama_debug_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_crash_message", throwable.message ?: "Unknown crash")
                    .putString("last_crash_stacktrace", stackTrace)
                    .commit()
            } catch (_: Exception) {}

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    PLAYBACK_CHANNEL_ID,
                    "Nayama Playback Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Media player controls and streaming notification"
                    setShowBadge(false)
                }

                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.e("NayamaApp", "Failed to create notification channel", e)
            }
        }
    }

    companion object {
        const val PLAYBACK_CHANNEL_ID = "nayama_playback_channel"
        const val NOTIFICATION_ID = 1001
    }
}
