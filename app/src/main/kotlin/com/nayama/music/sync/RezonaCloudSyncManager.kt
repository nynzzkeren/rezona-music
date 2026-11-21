package com.nayama.music.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.nayama.music.auth.FirebaseAuthManager
import com.nayama.music.data.local.LibraryItemEntity
import com.nayama.music.data.local.LikedTrackEntity
import com.nayama.music.data.local.PinnedSpeedDialEntity
import com.nayama.music.data.local.PlaybackHistoryEntity
import com.nayama.music.data.local.PlaylistEntity
import com.nayama.music.data.local.PlaylistTrackCrossRef
import com.nayama.music.data.local.RezonaDatabase
import com.nayama.music.data.local.SyncOperationEntity
import com.nayama.music.data.local.TrackEntity
import com.nayama.music.data.preferences.RezonaPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit

data class SyncStatusUiState(
    val uid: String? = null,
    val isFirebaseConfigured: Boolean = false,
    val isRestoring: Boolean = false,
    val isSyncing: Boolean = false,
    val lastPushAtMs: Long = 0L,
    val lastPullAtMs: Long = 0L,
    val pendingOpsCount: Int = 0,
    val lastError: String? = null,
    val showGuestUploadPrompt: Boolean = false
)

class RezonaCloudSyncManager private constructor(private val appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = RezonaDatabase.getInstance(appContext)
    private val preferences = RezonaPreferences(appContext)
    private val authManager = FirebaseAuthManager.getInstance(appContext)
    private val json = Json { ignoreUnknownKeys = true }
    private val syncMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _status = MutableStateFlow(SyncStatusUiState())
    val status: StateFlow<SyncStatusUiState> = _status.asStateFlow()

    private val activeListeners = mutableListOf<ListenerRegistration>()
    private var attachedUid: String? = null
    private var lastToastMs: Long = 0L

    @Serializable
    private data class QueuedTrackMeta(
        val videoId: String,
        val title: String,
        val artists: String,
        val album: String,
        val durationMs: Long,
        val thumbnailUrl: String,
        val addedAt: Long,
        val updatedAt: Long,
        val deleted: Boolean,
        val extraIndex: Int = 0,
        val parentPlaylistId: String = "",
        val itemType: String = "",
        val entryId: String = "",
        val playedAt: Long = 0L
    )

    @Serializable
    private data class QueuedPlaylistMeta(
        val playlistId: String,
        val name: String,
        val description: String,
        val createdAt: Long,
        val updatedAt: Long,
        val trackIds: List<String>,
        val deleted: Boolean
    )

    init {
        RezonaPreferences.onSettingsMutated = {
            scope.launch {
                val uid = authManager.currentVerifiedUid() ?: return@launch
                enqueueSettingsSync(uid)
                triggerOneTimeWorker()
            }
        }
        observeStatusSources()
        observeSessionForSyncLifecycle()
    }

    private fun observeStatusSources() {
        scope.launch {
            preferences.syncMetadataFlow.collect { meta ->
                _status.value = _status.value.copy(
                    lastPushAtMs = meta.lastPushAtMs,
                    lastPullAtMs = meta.lastPullAtMs,
                    lastError = meta.lastError
                )
            }
        }
        scope.launch {
            db.syncDao().observeTotalPendingCount().collect { count ->
                _status.value = _status.value.copy(pendingOpsCount = count)
            }
        }
    }

    private fun observeSessionForSyncLifecycle() {
        scope.launch {
            authManager.session.collect { session ->
                val uid = if (session.isSignedIn && session.isEmailVerified) session.uid else null
                _status.value = _status.value.copy(
                    uid = uid,
                    isFirebaseConfigured = session.isFirebaseConfigured
                )
                if (uid == null) {
                    detachListeners()
                    attachedUid = null
                    _status.value = _status.value.copy(
                        isRestoring = false,
                        isSyncing = false,
                        showGuestUploadPrompt = false
                    )
                } else if (uid != attachedUid) {
                    attachedUid = uid
                    onAuthenticatedSessionActive(uid)
                }
            }
        }
    }

    private suspend fun onAuthenticatedSessionActive(uid: String) {
        schedulePeriodicSync()
        val hasLocalGuestData = hasAnyLocalLibraryData()
        val alreadyHandledGuest = preferences.hasHandledGuestMergeForUid(uid)

        // Ensure profile doc exists & settings are preserved/synced
        if (hasLocalGuestData && !alreadyHandledGuest) {
            _status.value = _status.value.copy(showGuestUploadPrompt = true)
        } else {
            runInitialPullAndAttachListeners(uid, showBlockingOverlay = true)
        }
    }

    suspend fun respondToGuestUploadPrompt(uploadAndMerge: Boolean) {
        val uid = authManager.currentVerifiedUid()
        _status.value = _status.value.copy(showGuestUploadPrompt = false)
        if (uid.isNullOrBlank()) return

        preferences.markGuestMergeHandledForUid(uid)
        if (uploadAndMerge) {
            _status.value = _status.value.copy(isRestoring = true)
            enqueueEntireLocalLibraryForUid(uid)
            flushPendingQueueInternal(uid)
            runInitialPullAndAttachListeners(uid, showBlockingOverlay = true)
        } else {
            clearLocalUserLibraryTables()
            runInitialPullAndAttachListeners(uid, showBlockingOverlay = true)
        }
    }

    suspend fun runInitialPullAndAttachListeners(uid: String, showBlockingOverlay: Boolean = true) {
        if (showBlockingOverlay) {
            _status.value = _status.value.copy(isRestoring = true)
        }
        try {
            enqueueUserProfileDoc(uid)
            flushPendingQueueInternal(uid)
            pullAllFromFirestoreInternal(uid)
            attachRealtimeListeners(uid)
        } finally {
            _status.value = _status.value.copy(isRestoring = false)
        }
    }

    suspend fun syncNow(): Result<Unit> {
        val uid = authManager.currentVerifiedUid()
            ?: return Result.failure(IllegalStateException("Sign in to sync with cloud."))
        return try {
            _status.value = _status.value.copy(isSyncing = true)
            enqueueUserProfileDoc(uid)
            flushPendingQueueInternal(uid)
            pullAllFromFirestoreInternal(uid)
            Result.success(Unit)
        } catch (e: Throwable) {
            recordVisibleSyncError("Sync now failed", e)
            Result.failure(e)
        } finally {
            _status.value = _status.value.copy(isSyncing = false)
        }
    }

    suspend fun forceRestoreFromCloud(): Result<Unit> {
        val uid = authManager.currentVerifiedUid()
            ?: return Result.failure(IllegalStateException("Sign in to restore from cloud."))
        return try {
            _status.value = _status.value.copy(isRestoring = true, isSyncing = true)
            pullAllFromFirestoreInternal(uid, forceRemoteWin = true)
            Result.success(Unit)
        } catch (e: Throwable) {
            recordVisibleSyncError("Restore from cloud failed", e)
            Result.failure(e)
        } finally {
            _status.value = _status.value.copy(isRestoring = false, isSyncing = false)
        }
    }

    fun getFirestoreOrNull(): FirebaseFirestore? {
        val initialized = runCatching { FirebaseApp.getApps(appContext).isNotEmpty() }.getOrDefault(false)
        if (!initialized) return null
        return runCatching { FirebaseFirestore.getInstance() }.getOrNull()
    }

    private fun userRoot(firestore: FirebaseFirestore, uid: String): DocumentReference =
        firestore.collection("users").document(uid)

    // ── Enqueue Operations (called after every Room mutation) ─────────────────

    suspend fun onLikeChanged(track: TrackEntity, liked: Boolean, updatedAt: Long) {
        val uid = authManager.currentVerifiedUid() ?: return
        val meta = RezonaSyncLogic.toCompactMetadata(track)
        val existingLike = db.likedTrackDao().getLikeRaw(track.id)
        val queued = QueuedTrackMeta(
            videoId = meta.videoId,
            title = meta.title,
            artists = meta.artists,
            album = meta.album,
            durationMs = meta.durationMs,
            thumbnailUrl = meta.thumbnailUrl,
            addedAt = (existingLike?.likedAt ?: updatedAt).coerceAtLeast(0L),
            updatedAt = updatedAt.coerceAtLeast(1L),
            deleted = !liked
        )
        db.syncDao().upsertOperation(
            SyncOperationEntity(
                opKey = "likes:${meta.videoId}",
                uid = uid,
                entityType = OP_LIKE,
                entityId = meta.videoId,
                action = if (liked) ACTION_UPSERT else ACTION_DELETE,
                payloadJson = json.encodeToString(queued),
                updatedAt = queued.updatedAt
            )
        )
        triggerOneTimeWorker()
    }

    suspend fun onPlaylistChanged(playlistId: String) {
        val uid = authManager.currentVerifiedUid() ?: return
        val playlist = db.playlistDao().getPlaylistByIdSync(playlistId) ?: return
        val activeRefs = db.playlistDao().getActiveCrossRefsForPlaylist(playlistId)
        val orderedVideoIds = mutableListOf<String>()
        for (ref in activeRefs) {
            val t = db.trackDao().getTrackById(ref.trackId)
            val vid = if (t != null) RezonaSyncLogic.extractVideoId(t.id, t.audioUrl) else RezonaSyncLogic.extractVideoId(ref.trackId)
            orderedVideoIds.add(vid)
        }
        val queued = QueuedPlaylistMeta(
            playlistId = playlist.id.take(64),
            name = playlist.title.ifBlank { "Untitled Playlist" }.take(200),
            description = playlist.description.take(500),
            createdAt = playlist.createdAt.coerceAtLeast(0L),
            updatedAt = playlist.updatedAt.coerceAtLeast(1L),
            trackIds = orderedVideoIds.distinct().take(2000),
            deleted = playlist.deleted
        )
        db.syncDao().upsertOperation(
            SyncOperationEntity(
                opKey = "playlists:${queued.playlistId}",
                uid = uid,
                entityType = OP_PLAYLIST,
                entityId = queued.playlistId,
                action = if (playlist.deleted) ACTION_DELETE else ACTION_UPSERT,
                payloadJson = json.encodeToString(queued),
                updatedAt = queued.updatedAt
            )
        )
        triggerOneTimeWorker()
    }

    suspend fun onPlaylistTrackChanged(
        playlistId: String,
        track: TrackEntity,
        orderIndex: Int,
        deleted: Boolean,
        updatedAt: Long
    ) {
        val uid = authManager.currentVerifiedUid() ?: return
        val meta = RezonaSyncLogic.toCompactMetadata(track)
        val queued = QueuedTrackMeta(
            videoId = meta.videoId,
            title = meta.title,
            artists = meta.artists,
            album = meta.album,
            durationMs = meta.durationMs,
            thumbnailUrl = meta.thumbnailUrl,
            addedAt = track.addedAt.coerceAtLeast(0L),
            updatedAt = updatedAt.coerceAtLeast(1L),
            deleted = deleted,
            extraIndex = orderIndex.coerceIn(0, 5000),
            parentPlaylistId = playlistId.take(64)
        )
        db.syncDao().upsertOperation(
            SyncOperationEntity(
                opKey = "playlist_track:$playlistId:${meta.videoId}",
                uid = uid,
                entityType = OP_PLAYLIST_TRACK,
                entityId = meta.videoId,
                parentId = playlistId.take(64),
                action = if (deleted) ACTION_DELETE else ACTION_UPSERT,
                payloadJson = json.encodeToString(queued),
                updatedAt = queued.updatedAt
            )
        )
        onPlaylistChanged(playlistId)
        triggerOneTimeWorker()
    }

    suspend fun onPinChanged(track: TrackEntity, pinned: Boolean, pinOrder: Int = 0, updatedAt: Long) {
        val uid = authManager.currentVerifiedUid() ?: return
        val meta = RezonaSyncLogic.toCompactMetadata(track)
        val existingPin = db.speedDialDao().getPinRaw(track.id)
        val queued = QueuedTrackMeta(
            videoId = meta.videoId,
            title = meta.title,
            artists = meta.artists,
            album = meta.album,
            durationMs = meta.durationMs,
            thumbnailUrl = meta.thumbnailUrl,
            addedAt = (existingPin?.pinnedAt ?: updatedAt).coerceAtLeast(0L),
            updatedAt = updatedAt.coerceAtLeast(1L),
            deleted = !pinned,
            extraIndex = pinOrder.coerceIn(0, 100)
        )
        db.syncDao().upsertOperation(
            SyncOperationEntity(
                opKey = "pins:${meta.videoId}",
                uid = uid,
                entityType = OP_PIN,
                entityId = meta.videoId,
                action = if (pinned) ACTION_UPSERT else ACTION_DELETE,
                payloadJson = json.encodeToString(queued),
                updatedAt = queued.updatedAt
            )
        )
        triggerOneTimeWorker()
    }

    suspend fun onLibraryItemChanged(item: LibraryItemEntity) {
        val uid = authManager.currentVerifiedUid() ?: return
        val queued = QueuedTrackMeta(
            videoId = item.itemId.take(128),
            title = item.title.ifBlank { "Saved Item" }.take(300),
            artists = item.subtitle.take(300),
            album = "",
            durationMs = 0L,
            thumbnailUrl = item.thumbnailUrl.take(1024),
            addedAt = item.addedAt.coerceAtLeast(0L),
            updatedAt = item.updatedAt.coerceAtLeast(1L),
            deleted = item.deleted,
            itemType = item.itemType
        )
        db.syncDao().upsertOperation(
            SyncOperationEntity(
                opKey = "libraryItem:${queued.videoId}",
                uid = uid,
                entityType = OP_LIBRARY_ITEM,
                entityId = queued.videoId,
                action = if (item.deleted) ACTION_DELETE else ACTION_UPSERT,
                payloadJson = json.encodeToString(queued),
                updatedAt = queued.updatedAt
            )
        )
        triggerOneTimeWorker()
    }

    suspend fun onHistoryRecorded(entry: PlaybackHistoryEntity, track: TrackEntity) {
        val uid = authManager.currentVerifiedUid() ?: return
        val eid = entry.entryId.ifBlank { "h_${entry.playedAt}_${UUID.randomUUID().toString().take(6)}" }.take(128)
        val meta = RezonaSyncLogic.toCompactMetadata(track)
        val queued = QueuedTrackMeta(
            videoId = meta.videoId,
            title = meta.title,
            artists = meta.artists,
            album = meta.album,
            durationMs = meta.durationMs,
            thumbnailUrl = meta.thumbnailUrl,
            addedAt = entry.playedAt.coerceAtLeast(0L),
            updatedAt = entry.updatedAt.coerceAtLeast(1L),
            deleted = entry.deleted,
            entryId = eid,
            playedAt = entry.playedAt.coerceAtLeast(0L)
        )
        db.syncDao().upsertOperation(
            SyncOperationEntity(
                opKey = "history:$eid",
                uid = uid,
                entityType = OP_HISTORY,
                entityId = eid,
                action = ACTION_UPSERT,
                payloadJson = json.encodeToString(queued),
                updatedAt = queued.updatedAt
            )
        )
        triggerOneTimeWorker()
    }

    suspend fun onHistoryCleared(updatedAt: Long = System.currentTimeMillis()) {
        val uid = authManager.currentVerifiedUid() ?: return
        val all = db.historyDao().getAllEntriesRaw(RezonaSyncLogic.HISTORY_CAP)
        val ops = mutableListOf<SyncOperationEntity>()
        for (entry in all) {
            val eid = entry.entryId.takeIf { it.isNotBlank() } ?: continue
            val track = db.trackDao().getTrackById(entry.trackId)
            val meta = if (track != null) RezonaSyncLogic.toCompactMetadata(track)
            else CompactTrackMetadata(
                videoId = RezonaSyncLogic.extractVideoId(entry.trackId),
                title = "Track",
                artists = "",
                album = "",
                durationMs = 0L,
                thumbnailUrl = ""
            )
            val queued = QueuedTrackMeta(
                videoId = meta.videoId,
                title = meta.title,
                artists = meta.artists,
                album = meta.album,
                durationMs = meta.durationMs,
                thumbnailUrl = meta.thumbnailUrl,
                addedAt = entry.playedAt.coerceAtLeast(0L),
                updatedAt = updatedAt.coerceAtLeast(1L),
                deleted = true,
                entryId = eid,
                playedAt = entry.playedAt.coerceAtLeast(0L)
            )
            ops.add(
                SyncOperationEntity(
                    opKey = "history:$eid",
                    uid = uid,
                    entityType = OP_HISTORY,
                    entityId = eid,
                    action = ACTION_DELETE,
                    payloadJson = json.encodeToString(queued),
                    updatedAt = updatedAt
                )
            )
        }
        if (ops.isNotEmpty()) {
            db.syncDao().upsertOperations(ops)
            triggerOneTimeWorker()
        }
    }

    private suspend fun enqueueSettingsSync(uid: String) {
        val now = System.currentTimeMillis()
        db.syncDao().upsertOperation(
            SyncOperationEntity(
                opKey = "settings:preferences",
                uid = uid,
                entityType = OP_SETTINGS,
                entityId = "preferences",
                action = ACTION_UPSERT,
                payloadJson = "{}",
                updatedAt = now
            )
        )
    }

    private suspend fun enqueueUserProfileDoc(uid: String) {
        val now = System.currentTimeMillis()
        db.syncDao().upsertOperation(
            SyncOperationEntity(
                opKey = "profile:$uid",
                uid = uid,
                entityType = OP_PROFILE,
                entityId = uid,
                action = ACTION_UPSERT,
                payloadJson = "{}",
                updatedAt = now
            )
        )
    }

    private suspend fun enqueueEntireLocalLibraryForUid(uid: String) {
        val now = System.currentTimeMillis().coerceAtLeast(1L)
        for (like in db.likedTrackDao().getAllLikesRaw().filter { !it.deleted }) {
            val track = db.trackDao().getTrackById(like.trackId) ?: continue
            onLikeChanged(track, liked = true, updatedAt = like.updatedAt.takeIf { it > 0L } ?: now)
        }
        for (pl in db.playlistDao().getAllPlaylistsRaw().filter { !it.deleted }) {
            onPlaylistChanged(pl.id)
            for (ref in db.playlistDao().getActiveCrossRefsForPlaylist(pl.id)) {
                val track = db.trackDao().getTrackById(ref.trackId) ?: continue
                onPlaylistTrackChanged(
                    playlistId = pl.id,
                    track = track,
                    orderIndex = ref.orderIndex,
                    deleted = false,
                    updatedAt = ref.updatedAt.takeIf { it > 0L } ?: now
                )
            }
        }
        for (pin in db.speedDialDao().getAllPinsRaw().filter { !it.deleted }) {
            val track = db.trackDao().getTrackById(pin.trackId) ?: continue
            onPinChanged(track, pinned = true, updatedAt = pin.updatedAt.takeIf { it > 0L } ?: now)
        }
        for (item in db.libraryItemDao().getAllItemsRaw().filter { !it.deleted }) {
            onLibraryItemChanged(item)
        }
        val trimmedHistory = RezonaSyncLogic.trimHistoryToCap(db.historyDao().getAllEntriesRaw(RezonaSyncLogic.HISTORY_CAP))
        for (entry in trimmedHistory) {
            val track = db.trackDao().getTrackById(entry.trackId) ?: continue
            onHistoryRecorded(entry, track)
        }
        enqueueSettingsSync(uid)
    }

    // ── Flush Queue via Batched Firestore Writes (<= 450 per batch) ──────────

    suspend fun flushPendingForCurrentUser(): Boolean {
        val uid = authManager.currentVerifiedUid() ?: return true
        return flushPendingQueueInternal(uid)
    }

    private suspend fun flushPendingQueueInternal(uid: String): Boolean = syncMutex.withLock {
        val firestore = getFirestoreOrNull() ?: run {
            val msg = "Cloud sync skipped: Firestore not initialized (missing google-services.json)."
            preferences.recordSyncError(msg)
            return@withLock false
        }

        val pending = db.syncDao().getPendingForUid(uid)
        if (pending.isEmpty()) return@withLock true

        _status.value = _status.value.copy(isSyncing = true)
        return try {
            val root = userRoot(firestore, uid)
            val session = authManager.session.value
            val chunks = RezonaSyncLogic.chunkBatchOperations(pending, RezonaSyncLogic.FIRESTORE_SAFE_BATCH_SIZE)

            for (chunk in chunks) {
                val batch = firestore.batch()
                val includedKeys = mutableListOf<String>()

                for (op in chunk) {
                    when (op.entityType) {
                        OP_PROFILE -> {
                            val data = linkedMapOf<String, Any>(
                                "displayName" to session.displayName.take(100),
                                "email" to session.email.take(254),
                                "provider" to session.providerLabel.take(40),
                                "updatedAt" to System.currentTimeMillis().coerceAtLeast(1L)
                            )
                            batch.set(root, data)
                            includedKeys.add(op.opKey)
                        }
                        OP_LIKE -> {
                            val meta = json.decodeFromString<QueuedTrackMeta>(op.payloadJson)
                            batch.set(root.collection("likes").document(meta.videoId), buildTrackPayload(meta))
                            includedKeys.add(op.opKey)
                        }
                        OP_PLAYLIST -> {
                            val pl = json.decodeFromString<QueuedPlaylistMeta>(op.payloadJson)
                            val plRef = root.collection("playlists").document(pl.playlistId)
                            val payload = linkedMapOf<String, Any>(
                                "playlistId" to pl.playlistId,
                                "name" to pl.name,
                                "createdAt" to pl.createdAt.coerceAtLeast(0L),
                                "updatedAt" to pl.updatedAt.coerceAtLeast(1L),
                                "trackIds" to pl.trackIds,
                                "deleted" to pl.deleted,
                                "description" to pl.description
                            )
                            batch.set(plRef, payload)
                            includedKeys.add(op.opKey)
                        }
                        OP_PLAYLIST_TRACK -> {
                            val meta = json.decodeFromString<QueuedTrackMeta>(op.payloadJson)
                            val ref = root.collection("playlists")
                                .document(op.parentId)
                                .collection("tracks")
                                .document(meta.videoId)
                            val map = buildTrackPayload(meta).toMutableMap()
                            map["orderIndex"] = meta.extraIndex.coerceIn(0, 5000)
                            batch.set(ref, map)
                            includedKeys.add(op.opKey)
                        }
                        OP_LIBRARY_ITEM -> {
                            val meta = json.decodeFromString<QueuedTrackMeta>(op.payloadJson)
                            val map = linkedMapOf<String, Any>(
                                "itemId" to meta.videoId.take(128),
                                "itemType" to meta.itemType.ifBlank { "album" },
                                "title" to meta.title,
                                "subtitle" to meta.artists,
                                "thumbnailUrl" to meta.thumbnailUrl,
                                "addedAt" to meta.addedAt,
                                "updatedAt" to meta.updatedAt,
                                "deleted" to meta.deleted
                            )
                            batch.set(root.collection("libraryItems").document(meta.videoId), map)
                            includedKeys.add(op.opKey)
                        }
                        OP_HISTORY -> {
                            val meta = json.decodeFromString<QueuedTrackMeta>(op.payloadJson)
                            val eid = meta.entryId.ifBlank { op.entityId }
                            val map = linkedMapOf<String, Any>(
                                "entryId" to eid,
                                "videoId" to meta.videoId,
                                "title" to meta.title,
                                "artists" to meta.artists,
                                "album" to meta.album,
                                "durationMs" to meta.durationMs,
                                "thumbnailUrl" to meta.thumbnailUrl,
                                "playedAt" to meta.playedAt.coerceAtLeast(0L),
                                "updatedAt" to meta.updatedAt.coerceAtLeast(1L),
                                "deleted" to meta.deleted
                            )
                            batch.set(root.collection("history").document(eid), map)
                            includedKeys.add(op.opKey)
                        }
                        OP_SETTINGS -> {
                            val settingsMap = preferences.exportSettingsForCloudSync(op.updatedAt)
                            batch.set(root.collection("settings").document("preferences"), settingsMap)
                            includedKeys.add(op.opKey)
                        }
                        OP_PIN -> {
                            val meta = json.decodeFromString<QueuedTrackMeta>(op.payloadJson)
                            val map = buildTrackPayload(meta).toMutableMap()
                            map["pinOrder"] = meta.extraIndex.coerceIn(0, 100)
                            batch.set(root.collection("pins").document(meta.videoId), map)
                            includedKeys.add(op.opKey)
                        }
                    }
                }

                if (includedKeys.isNotEmpty()) {
                    batch.commit().await()
                    db.syncDao().deleteByKeys(includedKeys)
                }
            }

            preferences.recordSyncPushSuccess()
            true
        } catch (e: Throwable) {
            recordVisibleSyncError("Push failed", e)
            val keys = pending.map { it.opKey }
            if (keys.isNotEmpty()) {
                db.syncDao().markRetryError(keys, e.localizedMessage ?: e.javaClass.simpleName)
            }
            false
        } finally {
            _status.value = _status.value.copy(isSyncing = false)
        }
    }

    private fun buildTrackPayload(meta: QueuedTrackMeta): LinkedHashMap<String, Any> = linkedMapOf(
        "videoId" to meta.videoId,
        "title" to meta.title,
        "artists" to meta.artists,
        "album" to meta.album,
        "durationMs" to meta.durationMs,
        "thumbnailUrl" to meta.thumbnailUrl,
        "addedAt" to meta.addedAt,
        "updatedAt" to meta.updatedAt,
        "deleted" to meta.deleted
    )

    // ── Paginated Pull + LWW Merge into Room ─────────────────────────────────

    private suspend fun fetchAllPaginated(queryBase: Query, pageSize: Long = 150L): List<DocumentSnapshot> {
        val docs = mutableListOf<DocumentSnapshot>()
        var lastDoc: DocumentSnapshot? = null
        while (true) {
            val q = if (lastDoc != null) queryBase.startAfter(lastDoc).limit(pageSize) else queryBase.limit(pageSize)
            val snap = q.get().await()
            if (snap.documents.isEmpty()) break
            docs.addAll(snap.documents)
            if (snap.documents.size < pageSize) break
            lastDoc = snap.documents.last()
        }
        return docs
    }

    private suspend fun pullAllFromFirestoreInternal(uid: String, forceRemoteWin: Boolean = false) {
        val firestore = getFirestoreOrNull() ?: return
        val root = userRoot(firestore, uid)

        try {
            // 1. Likes
            val likeDocs = fetchAllPaginated(root.collection("likes"))
            applyRemoteLikeDocs(likeDocs, forceRemoteWin)

            // 2. Playlists + tracks subcollection
            val playlistDocs = fetchAllPaginated(root.collection("playlists"))
            applyRemotePlaylistDocs(root, playlistDocs, forceRemoteWin)

            // 3. Library items
            val itemDocs = fetchAllPaginated(root.collection("libraryItems"))
            applyRemoteLibraryItemDocs(itemDocs, forceRemoteWin)

            // 4. Pins / Speed dial
            val pinDocs = fetchAllPaginated(root.collection("pins"))
            applyRemotePinDocs(pinDocs, forceRemoteWin)

            // 5. History (capped 500 compact plays)
            val historyDocs = root.collection("history")
                .orderBy("playedAt", Query.Direction.DESCENDING)
                .limit(RezonaSyncLogic.HISTORY_CAP.toLong())
                .get()
                .await()
                .documents
            applyRemoteHistoryDocs(historyDocs, forceRemoteWin)

            // 6. Settings
            val settingsSnap = root.collection("settings").document("preferences").get().await()
            if (settingsSnap.exists()) {
                settingsSnap.data?.let { preferences.applySyncedSettingsFromCloud(it) }
            }

            preferences.recordSyncPullSuccess()
        } catch (e: Throwable) {
            recordVisibleSyncError("Cloud pull failed", e)
            throw e
        }
    }

    private suspend fun upsertTrackFromRemoteDoc(doc: DocumentSnapshot): String? {
        val videoId = doc.getString("videoId")?.takeIf { it.isNotBlank() } ?: doc.id
        if (videoId.isBlank()) return null
        val roomTrackId = if (videoId.length == 11 && !videoId.startsWith("yt_")) "yt_$videoId" else videoId
        val existing = db.trackDao().getTrackById(roomTrackId) ?: db.trackDao().getTrackById(videoId)
        val effectiveId = existing?.id ?: roomTrackId
        val remoteUpdatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
        if (existing != null && existing.updatedAt >= remoteUpdatedAt) return effectiveId

        val entity = TrackEntity(
            id = effectiveId,
            title = doc.getString("title").orEmpty().ifBlank { existing?.title ?: "Untitled Track" },
            artist = doc.getString("artists").orEmpty().ifBlank { existing?.artist ?: "Unknown Artist" },
            durationMs = doc.getLong("durationMs") ?: existing?.durationMs ?: 180000L,
            coverIndex = existing?.coverIndex ?: ((effectiveId.hashCode() and Int.MAX_VALUE) % 10),
            source = existing?.source ?: "YouTube Music",
            isHighRes = existing?.isHighRes ?: true,
            audioUrl = existing?.audioUrl ?: if (videoId.length == 11) "https://www.youtube.com/watch?v=$videoId" else "",
            addedAt = doc.getLong("addedAt") ?: existing?.addedAt ?: System.currentTimeMillis(),
            album = doc.getString("album").orEmpty().ifBlank { existing?.album.orEmpty() },
            thumbnailUrl = doc.getString("thumbnailUrl").orEmpty().ifBlank { existing?.thumbnailUrl.orEmpty() },
            updatedAt = remoteUpdatedAt
        )
        db.trackDao().insertTrack(entity)
        return effectiveId
    }

    private suspend fun applyRemoteLikeDocs(docs: List<DocumentSnapshot>, forceRemoteWin: Boolean) {
        for (doc in docs) {
            val remoteUpdated = doc.getLong("updatedAt") ?: continue
            val deleted = doc.getBoolean("deleted") ?: false
            val trackId = upsertTrackFromRemoteDoc(doc) ?: continue
            val local = db.likedTrackDao().getLikeRaw(trackId)
            if (forceRemoteWin || RezonaSyncLogic.shouldApplyRemote(local?.updatedAt, remoteUpdated)) {
                db.likedTrackDao().likeTrack(
                    LikedTrackEntity(
                        trackId = trackId,
                        likedAt = doc.getLong("addedAt") ?: remoteUpdated,
                        updatedAt = remoteUpdated,
                        deleted = deleted
                    )
                )
            }
        }
    }

    private suspend fun applyRemotePlaylistDocs(
        userRootRef: DocumentReference,
        docs: List<DocumentSnapshot>,
        forceRemoteWin: Boolean,
        pullTrackSubcollections: Boolean = true
    ) {
        for (doc in docs) {
            val playlistId = doc.getString("playlistId")?.takeIf { it.isNotBlank() } ?: doc.id
            val remoteUpdated = doc.getLong("updatedAt") ?: continue
            val deleted = doc.getBoolean("deleted") ?: false
            val local = db.playlistDao().getPlaylistByIdSync(playlistId)
            val createdAt = local?.createdAt ?: doc.getLong("createdAt") ?: remoteUpdated

            if (forceRemoteWin || RezonaSyncLogic.shouldApplyRemote(local?.updatedAt, remoteUpdated)) {
                db.playlistDao().insertPlaylist(
                    PlaylistEntity(
                        id = playlistId,
                        title = doc.getString("name").orEmpty().ifBlank { local?.title ?: "Playlist" },
                        description = doc.getString("description").orEmpty(),
                        coverIndex = local?.coverIndex ?: ((playlistId.hashCode() and Int.MAX_VALUE) % 10),
                        isPinned = local?.isPinned ?: false,
                        sourceBlend = local?.sourceBlend ?: "YouTube Music",
                        createdAt = createdAt,
                        updatedAt = remoteUpdated,
                        deleted = deleted
                    )
                )
            }

            if (pullTrackSubcollections && !deleted) {
                val trackDocs = fetchAllPaginated(
                    userRootRef.collection("playlists").document(playlistId).collection("tracks")
                )
                val existingRefs = db.playlistDao().getCrossRefsForPlaylistRaw(playlistId)
                    .associateBy { RezonaSyncLogic.extractVideoId(it.trackId) }
                for (td in trackDocs) {
                    val tUpdated = td.getLong("updatedAt") ?: continue
                    val tDeleted = td.getBoolean("deleted") ?: false
                    val vid = td.getString("videoId")?.takeIf { it.isNotBlank() } ?: td.id
                    val trackId = upsertTrackFromRemoteDoc(td) ?: continue
                    val localRef = existingRefs[vid]
                    if (forceRemoteWin || RezonaSyncLogic.shouldApplyRemote(localRef?.updatedAt, tUpdated)) {
                        db.playlistDao().addTrackToPlaylist(
                            PlaylistTrackCrossRef(
                                playlistId = playlistId,
                                trackId = trackId,
                                orderIndex = (td.getLong("orderIndex") ?: 0L).toInt(),
                                addedAt = td.getLong("addedAt") ?: tUpdated,
                                updatedAt = tUpdated,
                                deleted = tDeleted
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun applyRemoteLibraryItemDocs(docs: List<DocumentSnapshot>, forceRemoteWin: Boolean) {
        for (doc in docs) {
            val itemId = doc.getString("itemId")?.takeIf { it.isNotBlank() } ?: doc.id
            val remoteUpdated = doc.getLong("updatedAt") ?: continue
            val deleted = doc.getBoolean("deleted") ?: false
            val local = db.libraryItemDao().getItemRaw(itemId)
            if (forceRemoteWin || RezonaSyncLogic.shouldApplyRemote(local?.updatedAt, remoteUpdated)) {
                db.libraryItemDao().upsertItem(
                    LibraryItemEntity(
                        itemId = itemId,
                        itemType = doc.getString("itemType").orEmpty().ifBlank { "album" },
                        title = doc.getString("title").orEmpty(),
                        subtitle = doc.getString("subtitle").orEmpty(),
                        thumbnailUrl = doc.getString("thumbnailUrl").orEmpty(),
                        addedAt = doc.getLong("addedAt") ?: remoteUpdated,
                        updatedAt = remoteUpdated,
                        deleted = deleted
                    )
                )
            }
        }
    }

    private suspend fun applyRemotePinDocs(docs: List<DocumentSnapshot>, forceRemoteWin: Boolean) {
        for (doc in docs) {
            val remoteUpdated = doc.getLong("updatedAt") ?: continue
            val deleted = doc.getBoolean("deleted") ?: false
            val trackId = upsertTrackFromRemoteDoc(doc) ?: continue
            val local = db.speedDialDao().getPinRaw(trackId)
            if (forceRemoteWin || RezonaSyncLogic.shouldApplyRemote(local?.updatedAt, remoteUpdated)) {
                db.speedDialDao().pinTrack(
                    PinnedSpeedDialEntity(
                        trackId = trackId,
                        pinnedAt = doc.getLong("addedAt") ?: remoteUpdated,
                        updatedAt = remoteUpdated,
                        deleted = deleted
                    )
                )
            }
        }
    }

    private suspend fun applyRemoteHistoryDocs(docs: List<DocumentSnapshot>, forceRemoteWin: Boolean) {
        for (doc in docs) {
            val entryId = doc.getString("entryId")?.takeIf { it.isNotBlank() } ?: doc.id
            val remoteUpdated = doc.getLong("updatedAt") ?: continue
            val deleted = doc.getBoolean("deleted") ?: false
            val trackId = upsertTrackFromRemoteDoc(doc) ?: continue
            val local = db.historyDao().getByEntryId(entryId)
            if (forceRemoteWin || RezonaSyncLogic.shouldApplyRemote(local?.updatedAt, remoteUpdated)) {
                db.historyDao().recordPlayback(
                    PlaybackHistoryEntity(
                        id = local?.id ?: 0L,
                        trackId = trackId,
                        playedAt = doc.getLong("playedAt") ?: remoteUpdated,
                        entryId = entryId,
                        updatedAt = remoteUpdated,
                        deleted = deleted
                    )
                )
            }
        }
    }

    // ── Real-Time Snapshot Listeners (likes, playlists, settings) ─────────────

    private fun attachRealtimeListeners(uid: String) {
        detachListeners()
        val firestore = getFirestoreOrNull() ?: return
        val root = userRoot(firestore, uid)

        activeListeners.add(
            root.collection("likes").addSnapshotListener { snap, err ->
                if (err != null) {
                    scope.launch { recordVisibleSyncError("Likes listener error", err) }
                    return@addSnapshotListener
                }
                if (snap != null && !snap.metadata.hasPendingWrites()) {
                    scope.launch { applyRemoteLikeDocs(snap.documents, forceRemoteWin = false) }
                }
            }
        )

        activeListeners.add(
            root.collection("playlists").addSnapshotListener { snap, err ->
                if (err != null) {
                    scope.launch { recordVisibleSyncError("Playlists listener error", err) }
                    return@addSnapshotListener
                }
                if (snap != null && !snap.metadata.hasPendingWrites()) {
                    scope.launch {
                        applyRemotePlaylistDocs(root, snap.documents, forceRemoteWin = false, pullTrackSubcollections = true)
                    }
                }
            }
        )

        activeListeners.add(
            root.collection("settings").document("preferences").addSnapshotListener { snap, err ->
                if (err != null) {
                    scope.launch { recordVisibleSyncError("Settings listener error", err) }
                    return@addSnapshotListener
                }
                if (snap != null && snap.exists() && !snap.metadata.hasPendingWrites()) {
                    scope.launch {
                        snap.data?.let { preferences.applySyncedSettingsFromCloud(it) }
                    }
                }
            }
        )
    }

    fun detachListeners() {
        activeListeners.forEach { runCatching { it.remove() } }
        activeListeners.clear()
    }

    // ── Delete All Cloud Data + Local Cleanup ────────────────────────────────

    suspend fun deleteAllCloudDataForUid(uid: String): Result<Unit> {
        val firestore = getFirestoreOrNull()
            ?: return Result.failure(IllegalStateException("Firestore unavailable."))
        return try {
            val root = userRoot(firestore, uid)
            val refsToDelete = mutableListOf<DocumentReference>()

            refsToDelete.addAll(fetchAllPaginated(root.collection("likes")).map { it.reference })
            val playlists = fetchAllPaginated(root.collection("playlists"))
            for (pl in playlists) {
                refsToDelete.addAll(
                    fetchAllPaginated(pl.reference.collection("tracks")).map { it.reference }
                )
                refsToDelete.add(pl.reference)
            }
            refsToDelete.addAll(fetchAllPaginated(root.collection("libraryItems")).map { it.reference })
            refsToDelete.addAll(fetchAllPaginated(root.collection("history")).map { it.reference })
            refsToDelete.addAll(fetchAllPaginated(root.collection("pins")).map { it.reference })
            refsToDelete.addAll(fetchAllPaginated(root.collection("settings")).map { it.reference })
            refsToDelete.add(root)

            for (chunk in RezonaSyncLogic.chunkBatchOperations(refsToDelete, 450)) {
                val batch = firestore.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: Throwable) {
            recordVisibleSyncError("Cloud wipe failed", e)
            Result.failure(e)
        }
    }

    suspend fun hasAnyLocalLibraryData(): Boolean {
        val likes = db.likedTrackDao().getAllLikesRaw().any { !it.deleted }
        val playlists = db.playlistDao().getAllPlaylistsRaw().any { !it.deleted }
        val pins = db.speedDialDao().getAllPinsRaw().any { !it.deleted }
        val items = db.libraryItemDao().getAllItemsRaw().any { !it.deleted }
        val history = db.historyDao().getAllEntriesRaw(5).any { !it.deleted }
        return likes || playlists || pins || items || history
    }

    suspend fun clearLocalUserLibraryTables() {
        db.likedTrackDao().deleteAllLikes()
        db.playlistDao().deleteAllPlaylists()
        db.speedDialDao().deleteAllPins()
        db.libraryItemDao().deleteAllItems()
        db.historyDao().clearHistory()
        db.trackDao().deleteAllTracks()
        db.syncDao().clearAll()
    }

    // ── WorkManager & Error Surface ───────────────────────────────────────────

    private fun triggerOneTimeWorker() {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = OneTimeWorkRequestBuilder<RezonaSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(appContext)
                .enqueueUniqueWork(UNIQUE_PUSH_WORK, ExistingWorkPolicy.REPLACE, req)
        }
    }

    private fun schedulePeriodicSync() {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = PeriodicWorkRequestBuilder<RezonaSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(appContext)
                .enqueueUniquePeriodicWork(UNIQUE_PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }

    private suspend fun recordVisibleSyncError(prefix: String, error: Throwable) {
        val detail = "$prefix: ${error.javaClass.simpleName} — ${error.localizedMessage ?: "Unknown error"}"
        Log.e(TAG, detail, error)
        preferences.recordSyncError(detail)
        val now = System.currentTimeMillis()
        if (now - lastToastMs > 6000L) {
            lastToastMs = now
            mainHandler.post {
                Toast.makeText(appContext, "Sync issue: ${error.localizedMessage ?: prefix}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val TAG = "RezonaCloudSync"
        private const val UNIQUE_PUSH_WORK = "rezona_cloud_sync_push"
        private const val UNIQUE_PERIODIC_WORK = "rezona_cloud_sync_periodic"

        private const val OP_PROFILE = "profile"
        private const val OP_LIKE = "like"
        private const val OP_PLAYLIST = "playlist"
        private const val OP_PLAYLIST_TRACK = "playlist_track"
        private const val OP_LIBRARY_ITEM = "library_item"
        private const val OP_HISTORY = "history"
        private const val OP_SETTINGS = "settings"
        private const val OP_PIN = "pin"

        private const val ACTION_UPSERT = "UPSERT"
        private const val ACTION_DELETE = "DELETE_TOMBSTONE"

        @Volatile
        private var INSTANCE: RezonaCloudSyncManager? = null

        fun getInstance(context: Context): RezonaCloudSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RezonaCloudSyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
