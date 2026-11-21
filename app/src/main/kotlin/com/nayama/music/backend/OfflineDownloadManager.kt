package com.nayama.music.backend

import android.content.Context
import com.nayama.music.data.local.OfflineDownloadEntity
import com.nayama.music.data.local.RezonaDatabase
import com.nayama.music.data.repository.toTrackEntity
import com.nayama.music.model.RezonaTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Saves an app-private offline copy inside Context.filesDir/offline_audio for in-app playback only.
 * No external export, no file format conversion.
 */
class OfflineDownloadManager private constructor(private val context: Context) {

    private val db = RezonaDatabase.getInstance(context)
    private val offlineDao = db.offlineDownloadDao()
    private val trackDao = db.trackDao()
    private val engine by lazy { MusicEngineRepository.getInstance(context) }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    private fun downloadDir(): File {
        val dir = File(context.filesDir, "offline_audio")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun getLocalUriIfDownloaded(trackId: String): String? = withContext(Dispatchers.IO) {
        val rec = offlineDao.getDownloadRecord(trackId) ?: return@withContext null
        val file = File(rec.localFilePath)
        if (file.exists() && file.length() > 1024L) {
            file.toURI().toString()
        } else {
            offlineDao.deleteDownload(trackId)
            null
        }
    }

    suspend fun downloadTrack(track: RezonaTrack): Boolean = withContext(Dispatchers.IO) {
        val existing = getLocalUriIfDownloaded(track.id)
        if (existing != null) return@withContext true

        try {
            updateProgress(track.id, 0.05f)
            val resolved = engine.resolveStreamWithFallback(track) ?: run {
                clearProgress(track.id)
                return@withContext false
            }

            val req = Request.Builder()
                .url(resolved.streamUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/128.0.0.0 Safari/537.36")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    clearProgress(track.id)
                    return@withContext false
                }
                val body = resp.body ?: run {
                    clearProgress(track.id)
                    return@withContext false
                }
                val totalBytes = body.contentLength()
                val safeName = track.id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".audio"
                val outFile = File(downloadDir(), safeName)

                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var copied = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            copied += read
                            if (totalBytes > 0L) {
                                updateProgress(track.id, (copied.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.99f))
                            }
                        }
                        output.flush()
                    }
                }

                trackDao.insertTrack(track.toTrackEntity())
                offlineDao.upsertDownload(
                    OfflineDownloadEntity(
                        trackId = track.id,
                        localFilePath = outFile.absolutePath,
                        fileSizeBytes = outFile.length()
                    )
                )
                clearProgress(track.id)
                true
            }
        } catch (_: Throwable) {
            clearProgress(track.id)
            false
        }
    }

    suspend fun removeDownload(trackId: String) = withContext(Dispatchers.IO) {
        val rec = offlineDao.getDownloadRecord(trackId)
        if (rec != null) {
            runCatching { File(rec.localFilePath).delete() }
            offlineDao.deleteDownload(trackId)
        }
    }

    suspend fun clearAllDownloads() = withContext(Dispatchers.IO) {
        downloadDir().listFiles()?.forEach { runCatching { it.delete() } }
        downloadDir().delete()
    }

    fun totalStorageBytes(): Long {
        val dir = downloadDir()
        return dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun updateProgress(trackId: String, pct: Float) {
        val m = _progress.value.toMutableMap()
        m[trackId] = pct
        _progress.value = m
    }

    private fun clearProgress(trackId: String) {
        val m = _progress.value.toMutableMap()
        m.remove(trackId)
        _progress.value = m
    }

    companion object {
        @Volatile
        private var INSTANCE: OfflineDownloadManager? = null

        fun getInstance(context: Context): OfflineDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OfflineDownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
