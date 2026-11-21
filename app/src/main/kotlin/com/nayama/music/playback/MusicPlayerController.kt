package com.nayama.music.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.nayama.music.MainActivity
import com.nayama.music.data.local.LibraryRepository
import com.nayama.music.data.local.UserPreferencesRepository
import com.nayama.music.model.Track
import com.nayama.music.model.MusicSource
import com.nayama.music.network.AudioStreamResolver
import com.nayama.music.network.AudiusStreamResolver
import com.nayama.music.network.NetworkConnectivityMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(UnstableApi::class)
class MusicPlayerController private constructor(
    private val context: Context
) {
    companion object {
        @Volatile
        private var instance: MusicPlayerController? = null

        fun getInstance(context: Context): MusicPlayerController {
            return instance ?: synchronized(this) {
                instance ?: MusicPlayerController(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var resolveJob: Job? = null
    private var retryCount = 0

    val equalizerController: AudioEqualizerController by lazy {
        AudioEqualizerController.getInstance(context)
    }

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()

            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
            val mediaSourceFactory = DefaultMediaSourceFactory(
                DefaultDataSource.Factory(context, httpDataSourceFactory)
            )

            val player = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()

            this.exoPlayer = player

            val sessionIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val sessionPendingIntent = PendingIntent.getActivity(
                context,
                0,
                sessionIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            mediaSession = MediaSession.Builder(context, player)
                .setSessionActivity(sessionPendingIntent)
                .setId("nayama_media_session")
                .build()

            try {
                AudioEqualizerController.getInstance(context).attachToSession(player.audioSessionId)
                _playbackState.value = _playbackState.value.copy(audioSessionId = player.audioSessionId)
            } catch (e: Throwable) {
                // Graceful fallback if equalizer fails
            }

            setupPlayerListener(player)
        } catch (e: Throwable) {
            _playbackState.value = _playbackState.value.copy(
                errorMessage = "Gagal inisialisasi audio player: ${e.message}"
            )
        }
    }

    fun getMediaSession(): MediaSession? = mediaSession

    fun stopAndClearForTaskRemoval() {
        resolveJob?.cancel()
        resolveJob = null
        progressJob?.cancel()
        progressJob = null
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        exoPlayer?.let { player ->
            runCatching {
                player.pause()
                player.stop()
                player.clearMediaItems()
            }
        }
        _playbackState.value = PlaybackState(audioSessionId = exoPlayer?.audioSessionId)
        NayamaMediaService.stop(context)
    }

    private fun setupPlayerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                val track = _playbackState.value.currentTrack
                if (isPlaying) {
                    startProgressTracker()
                    if (track != null) {
                        NayamaMediaService.start(context, track.title, track.artist, isPlaying = true, artworkUrl = track.artworkUrl)
                    }
                } else {
                    stopProgressTracker()
                    if (track != null) {
                        NayamaMediaService.start(context, track.title, track.artist, isPlaying = false, artworkUrl = track.artworkUrl)
                    } else {
                        NayamaMediaService.stop(context)
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                val isBuffering = state == Player.STATE_BUFFERING
                val duration = if (player.duration > 0) player.duration else _playbackState.value.durationMs

                _playbackState.value = _playbackState.value.copy(
                    isBuffering = isBuffering,
                    durationMs = duration,
                    errorMessage = if (state != Player.STATE_IDLE) null else _playbackState.value.errorMessage
                )

                if (state == Player.STATE_READY) {
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                        durationMs = player.duration.coerceAtLeast(0L)
                    )
                } else if (state == Player.STATE_ENDED) {
                    stopProgressTracker()
                    val finishedId = _playbackState.value.currentTrack?.id
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        isBuffering = false,
                        endedTrackId = finishedId,
                        endedEventSeq = _playbackState.value.endedEventSeq + 1L
                    )
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w("MusicPlayerController", "ExoPlayer stream error: ${error.errorCodeName}", error)
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = "Audio stream error: ${error.message ?: "Unable to play"}",
                    errorEventSeq = _playbackState.value.errorEventSeq + 1L
                )
                NayamaMediaService.stop(context)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val trackId = mediaItem?.mediaId
                val track = _playbackState.value.queue.find { it.id == trackId }
                if (track != null) {
                    _playbackState.value = _playbackState.value.copy(
                        currentTrack = track,
                        currentPositionMs = 0L,
                        durationMs = (track.durationSeconds * 1000L).coerceAtLeast(0L)
                    )
                    if (exoPlayer?.isPlaying == true) {
                        NayamaMediaService.start(context, track.title, track.artist, isPlaying = true, artworkUrl = track.artworkUrl)
                    }

                    // Upgrade queue track to full-length stream if preview or lazy-resolved (YT Music / SoundCloud / Spotify / Deezer)
                    val queueNeedsResolve = track.streamUrl.isNullOrBlank() ||
                        track.streamUrl.contains("dzcdn.net") ||
                        track.streamUrl.contains("scdn.co/mp3-preview") ||
                        track.id.startsWith("deezer_") ||
                        track.id.startsWith("ytm_") ||
                        track.id.startsWith("sc_") ||
                        track.id.startsWith("spotify_")
                    if (queueNeedsResolve) {
                        val scId = UserPreferencesRepository.getInstance(context).preferences.value.soundCloudClientId
                        scope.launch {
                            val resolved = withContext(Dispatchers.IO) {
                                withTimeoutOrNull(4500L) {
                                    AudioStreamResolver.resolveFullLengthTrack(track, scId)
                                }
                            }
                            if (resolved != null &&
                                resolved.streamUrl != track.streamUrl &&
                                !resolved.streamUrl.isNullOrBlank() &&
                                _playbackState.value.currentTrack?.id == track.id
                            ) {
                                val updatedQueue = _playbackState.value.queue.map {
                                    if (it.id == resolved.id) resolved else it
                                }
                                _playbackState.value = _playbackState.value.copy(
                                    currentTrack = resolved,
                                    queue = updatedQueue,
                                    durationMs = (resolved.durationSeconds * 1000L).coerceAtLeast(0L)
                                )
                                val currentIdx = exoPlayer?.currentMediaItemIndex ?: return@launch
                                val pos = exoPlayer?.currentPosition ?: 0L
                                val newMetadata = MediaMetadata.Builder()
                                    .setTitle(resolved.title)
                                    .setArtist(resolved.artist)
                                    .setAlbumTitle(resolved.album)
                                    .setArtworkUri(resolved.artworkUrl?.let { Uri.parse(it) })
                                    .build()
                                val newItem = MediaItem.Builder()
                                    .setMediaId(resolved.id)
                                    .setUri(resolved.streamUrl)
                                    .setMediaMetadata(newMetadata)
                                    .build()
                                exoPlayer?.replaceMediaItem(currentIdx, newItem)
                                if (pos > 0L) exoPlayer?.seekTo(currentIdx, pos)
                            }
                        }
                    }
                }
            }
        })
    }

    fun playTrack(track: Track, queue: List<Track> = listOf(track)) {
        val player = exoPlayer ?: return

        val prefs = UserPreferencesRepository.getInstance(context).preferences.value
        val networkMonitor = NetworkConnectivityMonitor.getInstance(context)

        if (prefs.wifiOnlyStreaming && !networkMonitor.isWifi.value) {
            _playbackState.value = _playbackState.value.copy(
                errorMessage = "Streaming dibatasi melalui Wi-Fi saja (sesuai pengaturan profil Anda)."
            )
            return
        }

        retryCount = 0
        resolveJob?.cancel()

        val initialQueue = if (queue.none { it.id == track.id }) listOf(track) + queue else queue

        // Record to persistent listening history
        LibraryRepository.getInstance(context).recordHistory(track)

        _playbackState.value = _playbackState.value.copy(
            currentTrack = track,
            queue = initialQueue,
            isBuffering = true,
            errorMessage = null,
            durationMs = (track.durationSeconds * 1000L)
        )

        resolveJob = scope.launch {
            val needsResolution = track.streamUrl.isNullOrBlank() ||
                track.streamUrl.contains("dzcdn.net") ||
                track.streamUrl.contains("scdn.co/mp3-preview") ||
                track.id.startsWith("deezer_") ||
                track.id.startsWith("ytm_") ||
                track.id.startsWith("sc_") ||
                track.id.startsWith("spotify_") ||
                track.durationSeconds <= 35

            val effectiveTrack = if (needsResolution) {
                withContext(Dispatchers.IO) {
                    withTimeoutOrNull(4500L) {
                        AudioStreamResolver.resolveFullLengthTrack(track, prefs.soundCloudClientId)
                    }
                } ?: track
            } else {
                track
            }

            if (effectiveTrack.streamUrl.isNullOrBlank()) {
                _playbackState.value = _playbackState.value.copy(
                    isBuffering = false,
                    isPlaying = false,
                    errorMessage = "Audio stream tidak tersedia dari sumber ini. Coba hasil dari YT Music atau JioSaavn."
                )
                return@launch
            }

            val updatedQueue = initialQueue.map {
                if (it.id == effectiveTrack.id) effectiveTrack else it
            }

            _playbackState.value = _playbackState.value.copy(
                currentTrack = effectiveTrack,
                queue = updatedQueue,
                durationMs = (effectiveTrack.durationSeconds * 1000L)
            )

            try {
                val mediaItems = updatedQueue.map { item ->
                    val metadata = MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setAlbumTitle(item.album)
                        .setArtworkUri(item.artworkUrl?.let { Uri.parse(it) })
                        .build()

                    MediaItem.Builder()
                        .setMediaId(item.id)
                        .setUri(item.streamUrl ?: "")
                        .setMediaMetadata(metadata)
                        .build()
                }

                val targetIndex = updatedQueue.indexOfFirst { it.id == effectiveTrack.id }.coerceAtLeast(0)
                player.setMediaItems(mediaItems, targetIndex, 0L)
                player.prepare()
                player.play()
            } catch (e: Exception) {
                _playbackState.value = _playbackState.value.copy(
                    isBuffering = false,
                    errorMessage = "Gagal memuat URL audio: ${e.message}"
                )
            }
        }
    }

    fun playDirectStream(
        streamUrl: String,
        title: String,
        artist: String,
        album: String = "",
        artworkUrl: String? = null,
        durationMs: Long = 0L,
        trackId: String? = null
    ) {
        val player = exoPlayer ?: return
        val resolvedTrackId = trackId?.takeIf { it.isNotBlank() } ?: "rezona_${title.hashCode()}"
        try {
            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUrl?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(resolvedTrackId)
                .setUri(Uri.parse(streamUrl))
                .setMediaMetadata(metadata)
                .build()

            player.stop()
            player.setMediaItem(mediaItem)
            player.seekTo(0L)
            player.prepare()
            player.play()

            val currentTrack = Track(
                id = resolvedTrackId,
                title = title,
                artist = artist,
                album = album,
                durationSeconds = (durationMs / 1000L).toInt().coerceAtLeast(180),
                artworkUrl = artworkUrl,
                streamUrl = streamUrl,
                source = MusicSource.YOUTUBE_MUSIC
            )
            _playbackState.value = _playbackState.value.copy(
                currentTrack = currentTrack,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = if (durationMs > 0L) durationMs else _playbackState.value.durationMs,
                errorMessage = null,
                endedTrackId = null
            )

            NayamaMediaService.start(context, title, artist, isPlaying = true, artworkUrl = artworkUrl)
        } catch (e: Throwable) {
            Log.w("MusicPlayerController", "Failed to play direct stream", e)
        }
    }

    fun setPlaybackSpeedAndPitch(speed: Float, pitch: Float) {
        try {
            exoPlayer?.playbackParameters = PlaybackParameters(
                speed.coerceIn(0.5f, 2.0f),
                pitch.coerceIn(0.5f, 2.0f)
            )
        } catch (e: Throwable) {
            Log.w("MusicPlayerController", "Failed to apply playback parameters", e)
        }
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE && _playbackState.value.currentTrack != null) {
                _playbackState.value.currentTrack?.let { playTrack(it, _playbackState.value.queue) }
            } else {
                player.play()
            }
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        val player = exoPlayer ?: return
        if (player.playbackState == Player.STATE_IDLE && _playbackState.value.currentTrack != null) {
            _playbackState.value.currentTrack?.let { playTrack(it, _playbackState.value.queue) }
        } else {
            player.play()
        }
    }

    fun seekToFraction(fraction: Float) {
        val player = exoPlayer ?: return
        val duration = _playbackState.value.durationMs
        if (duration > 0) {
            val targetMs = (fraction * duration).toLong().coerceIn(0L, duration)
            player.seekTo(targetMs)
            _playbackState.value = _playbackState.value.copy(currentPositionMs = targetMs)
        }
    }

    fun seekToPosition(positionMs: Long) {
        val player = exoPlayer ?: return
        player.seekTo(positionMs)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    fun skipToNext() {
        val player = exoPlayer ?: return
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else {
            val queue = _playbackState.value.queue
            val current = _playbackState.value.currentTrack
            if (queue.isNotEmpty() && current != null) {
                val nextIdx = (queue.indexOf(current) + 1) % queue.size
                playTrack(queue[nextIdx], queue)
            }
        }
    }

    fun skipToPrevious() {
        val player = exoPlayer ?: return
        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            val queue = _playbackState.value.queue
            val current = _playbackState.value.currentTrack
            if (queue.isNotEmpty() && current != null) {
                val prevIdx = if (queue.indexOf(current) - 1 < 0) queue.size - 1 else queue.indexOf(current) - 1
                playTrack(queue[prevIdx], queue)
            }
        }
    }

    fun toggleShuffle() {
        val player = exoPlayer ?: return
        val newShuffle = !player.shuffleModeEnabled
        player.shuffleModeEnabled = newShuffle
        _playbackState.value = _playbackState.value.copy(isShuffleEnabled = newShuffle)
    }

    fun toggleRepeatMode() {
        val player = exoPlayer ?: return
        val nextMode = _playbackState.value.repeatMode.next()
        val exoRepeatMode = when (nextMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        player.repeatMode = exoRepeatMode
        _playbackState.value = _playbackState.value.copy(repeatMode = nextMode)
    }

    fun addToQueue(track: Track) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (currentQueue.none { it.id == track.id }) {
            currentQueue.add(track)
            _playbackState.value = _playbackState.value.copy(queue = currentQueue)

            exoPlayer?.let { player ->
                val metadata = MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(track.artworkUrl?.let { Uri.parse(it) })
                    .build()
                val mediaItem = MediaItem.Builder()
                    .setMediaId(track.id)
                    .setUri(track.streamUrl ?: AudiusStreamResolver.resolvePrimaryStreamUrl(track.id))
                    .setMediaMetadata(metadata)
                    .build()
                player.addMediaItem(mediaItem)
            }
        }
    }

    fun playNext(track: Track) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        val currentTrack = _playbackState.value.currentTrack
        val insertIndex = if (currentTrack != null) {
            (currentQueue.indexOfFirst { it.id == currentTrack.id } + 1).coerceAtMost(currentQueue.size)
        } else {
            0
        }

        currentQueue.removeAll { it.id == track.id }
        currentQueue.add(insertIndex, track)
        _playbackState.value = _playbackState.value.copy(queue = currentQueue)

        exoPlayer?.let { player ->
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setArtworkUri(track.artworkUrl?.let { Uri.parse(it) })
                .build()
            val mediaItem = MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(track.streamUrl ?: AudiusStreamResolver.resolvePrimaryStreamUrl(track.id))
                .setMediaMetadata(metadata)
                .build()
            val exoInsertIndex = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
            player.addMediaItem(exoInsertIndex, mediaItem)
        }
    }

    fun removeFromQueue(trackId: String) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        val removeIndex = currentQueue.indexOfFirst { it.id == trackId }
        if (removeIndex >= 0) {
            currentQueue.removeAt(removeIndex)
            _playbackState.value = _playbackState.value.copy(queue = currentQueue)
            exoPlayer?.let { player ->
                if (removeIndex < player.mediaItemCount && removeIndex != player.currentMediaItemIndex) {
                    player.removeMediaItem(removeIndex)
                }
            }
        }
    }

    fun clearQueue() {
        val current = _playbackState.value.currentTrack
        val newQueue = if (current != null) listOf(current) else emptyList()
        _playbackState.value = _playbackState.value.copy(queue = newQueue)
        exoPlayer?.let { player ->
            val currentIndex = player.currentMediaItemIndex
            if (player.mediaItemCount > 1) {
                for (i in player.mediaItemCount - 1 downTo 0) {
                    if (i != currentIndex) {
                        player.removeMediaItem(i)
                    }
                }
            }
        }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _playbackState.value = _playbackState.value.copy(sleepTimerRemainingSeconds = null)
            return
        }

        sleepTimerJob = scope.launch {
            var remaining = minutes * 60
            while (remaining > 0 && isActive) {
                _playbackState.value = _playbackState.value.copy(sleepTimerRemainingSeconds = remaining)
                delay(1000L)
                remaining--
            }
            _playbackState.value = _playbackState.value.copy(sleepTimerRemainingSeconds = null)
            pause()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _playbackState.value = _playbackState.value.copy(sleepTimerRemainingSeconds = null)
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        _playbackState.value = _playbackState.value.copy(
                            currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                            durationMs = player.duration.coerceAtLeast(0L)
                        )
                    }
                }
                delay(300)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressTracker()
        NayamaMediaService.stop(context)
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
        instance = null
    }
}
