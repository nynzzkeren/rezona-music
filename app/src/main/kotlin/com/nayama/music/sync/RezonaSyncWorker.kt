package com.nayama.music.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class RezonaSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val syncManager = RezonaCloudSyncManager.getInstance(applicationContext)
        val flushed = syncManager.flushPendingForCurrentUser()
        return if (flushed) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
