package com.nayama.music.playback

import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.media3.exoplayer.ExoPlayer
import com.nayama.music.backend.LyricSourceId
import com.nayama.music.backend.LyricsProvider
import com.nayama.music.backend.MusicEngineRepository
import com.nayama.music.backend.ResolvedVideoVariant
import com.nayama.music.backend.VideoQualitySelector
import com.nayama.music.data.local.PlaybackHistoryEntity
import com.nayama.music.data.local.RezonaDatabase
import com.nayama.music.data.preferences.RezonaPreferences
import com.nayama.music.data.repository.RezonaLibraryRepository
import com.nayama.music.data.repository.toRezonaTrack
import com.nayama.music.data.repository.toTrackEntity
import com.nayama.music.model.RezonaTrack
import com.nayama.music.model.SyncedLyricLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single-source-of-truth PlayerController for Rezona Player V3.
 * Synchronizes MiniPlayer, Now Playing, Video Mode, Butter-Smooth Inline Lyrics, Queue Sheet,
 * Notification, 18 Sound Presets (PlaybackParameters + AudioFX + Custom PCM AudioProcessor),
 * System STREAM_MUSIC volume, Sleep Timer, and Room Playback History / Likes.
 */
class RezonaPlayerController(
    private val scope: CoroutineScope,
    private val context: Context? = null
) {
    private val musicPlayer = context?.let { MusicPlayerController.getInstance(it) }
    private val musicEngine = context?.let { MusicEngineRepository.getInstance(it) }
    private val libraryRepo = context?.let { RezonaLibraryRepository.getInstance(it) }
    private val preferences = context?.let { RezonaPreferences(it) }
    private val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    var currentLyrics = mutableStateOf<List<SyncedLyricLine>>(emptyList())
        private set

    var lyricsSourceLabel = mutableStateOf("Lyrics from LRCLIB")
        private set

    var selectedLyricsSource = mutableStateOf(LyricSourceId.AUTO)
        private set

    var activeResolvedSourceId = mutableStateOf(LyricSourceId.AUTO)
        private set

    val lyricsSourceStatuses = mutableStateMapOf<LyricSourceId, String>()
    private val lyricsSourceCache = mutableMapOf<LyricSourceId, List<SyncedLyricLine>>()
    private var lyricsProbeJob: Job? = null

    // Current playing track - Single Source of Truth (null until the user plays a song)
    var currentTrack = mutableStateOf<RezonaTrack?>(null)
        private set

    var activeQueueName = mutableStateOf("")
        private set

    // Playback state
    var isPlaying = mutableStateOf(false)
        private set

    var currentPositionMs = mutableLongStateOf(0L)
        private set

    var durationMs = mutableLongStateOf(0L)
        private set

    var isLiked = mutableStateOf(false)
        private set

    var isShuffle = mutableStateOf(false)
        private set

    var repeatMode = mutableStateOf(RepeatMode.OFF)
        private set

    var volume = mutableFloatStateOf(readSystemVolumeFraction())
        private set

    // Sound Presets & Advanced Tempo/Pitch
    var activeSoundPreset = mutableStateOf(SoundPreset.NORMAL)
        private set
    var manualTempo = mutableFloatStateOf(1.0f)
        private set
    var manualPitch = mutableFloatStateOf(1.0f)
        private set

    // Part 3: Audio / Video Mode (same ExoPlayer session)
    var hasVideoStream = mutableStateOf(false)
        private set
    var isVideoMode = mutableStateOf(false)
        private set
    var currentVideoQualityLabel = mutableStateOf("")
        private set
    private var currentResolvedAudioUrl: String? = null
    private var currentVideoVariants: List<ResolvedVideoVariant> = emptyList()
    private var videoPausedForBackground: Boolean = false

    // Part 4: Frame-synced Lyrics clock snap signal
    var seekEventSeq = mutableLongStateOf(0L)
        private set

    // Active Queue & Time Machine History
    val queue = mutableStateListOf<RezonaTrack>()
    val history = mutableStateListOf<RezonaTrack>()

    // Sleep timer state (minutes / countdown seconds / End of track)
    var sleepTimerMinutes = mutableStateOf<Int?>(null)
        private set
    var sleepTimerSecondsRemaining = mutableStateOf<Int?>(null)
        private set
    var sleepTimerEndOfTrack = mutableStateOf(false)
        private set

    var echoPromptVisible = mutableStateOf(false)
        private set

    var lastFeedbackAnswer = mutableStateOf<String?>(null)
        private set

    // Audio Output / Sound Check info
    var outputDevice = mutableStateOf("Phone Speaker")
        private set
    var audioFormatLabel = mutableStateOf("48 kHz · 24-bit PCM")
        private set

    // Studio DSP Engines
    val spatialReverb = SpatialReverbController()
    val ambientEngine = ProceduralAmbientEngine(scope)

    var moodDialAngle = mutableFloatStateOf(0f)
        private set
    var crossfadeSeconds = mutableIntStateOf(4)
        private set
    var gaplessEnabled = mutableStateOf(true)
        private set
    var focusTimerMinutesRemaining = mutableStateOf<Int?>(null)
        private set

    private var tickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var echoDismissJob: Job? = null
    private var focusTimerJob: Job? = null
    private var resolveJob: Job? = null
    private var likeObserverJob: Job? = null
    private var pendingTrackId: String? = null
    private var isStreamResolving: Boolean = false
    private var lastSeenEndedSeq: Long = 0L
    private var lastSeenErrorSeq: Long = 0L
    private var autoAdvancingForTrackId: String? = null
    private var retriedErrorForTrackId: String? = null

    init {
        if (context != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val savedPresetId = preferences?.soundPresetFlow?.first() ?: "NORMAL"
                    val preset = SoundPreset.fromId(savedPresetId)
                    withContext(Dispatchers.Main) {
                        if (preset != SoundPreset.NORMAL) {
                            applySoundPreset(preset, toggleOffIfSame = false, persist = false)
                        }
                    }
                }
                runCatching {
                    val detected = AudioOutputDetector.getCurrentOutputDevice(context)
                    withContext(Dispatchers.Main) {
                        outputDevice.value = detected.label
                        audioFormatLabel.value = if (detected.isExternalDevice) "48.0 kHz · 24-bit PCM" else "44.1 kHz · Stereo"
                    }
                }
            }
        }

        // Observe real ExoPlayer playback engine - Single Source of Truth
        if (musicPlayer != null) {
            scope.launch {
                musicPlayer.playbackState.collect { pbState ->
                    val engineTrack = pbState.currentTrack
                    if (engineTrack != null) {
                        if (pendingTrackId == engineTrack.id) {
                            pendingTrackId = null
                        }
                        if (engineTrack.id != currentTrack.value?.id && (pendingTrackId == null || currentTrack.value == null)) {
                            val synced = RezonaTrack(
                                id = engineTrack.id,
                                index = 0,
                                title = engineTrack.title,
                                artist = engineTrack.artist,
                                album = engineTrack.album.orEmpty(),
                                duration = formatSeconds(engineTrack.durationSeconds),
                                source = "YouTube Music",
                                coverArtUrl = engineTrack.artworkUrl.orEmpty(),
                                streamUrl = engineTrack.streamUrl.orEmpty()
                            )
                            currentTrack.value = synced
                            if (activeQueueName.value.isBlank()) {
                                activeQueueName.value = "Mix ${synced.title}"
                            }
                            observeLikedState(synced.id)
                        }
                    }

                    // CRITICAL: Never let previous track's stale position/duration overwrite while a new track resolves!
                    val isTargetTrackActive = !isStreamResolving &&
                        pendingTrackId == null &&
                        engineTrack != null &&
                        engineTrack.id == currentTrack.value?.id

                    if (isTargetTrackActive) {
                        if (pbState.isPlaying != isPlaying.value) {
                            isPlaying.value = pbState.isPlaying
                            if (pbState.isPlaying) startTicker() else stopTicker()
                        }
                        if (pbState.durationMs > 0L) {
                            durationMs.longValue = pbState.durationMs
                        }
                        if (pbState.currentPositionMs > 0L) {
                            currentPositionMs.longValue = pbState.currentPositionMs
                        }
                    }

                    if (pbState.endedEventSeq > lastSeenEndedSeq) {
                        lastSeenEndedSeq = pbState.endedEventSeq
                        val activeId = currentTrack.value?.id
                        if (!isStreamResolving && pendingTrackId == null && activeId != null && pbState.endedTrackId == activeId) {
                            onCurrentTrackEnded(activeId)
                        }
                    }

                    if (pbState.errorEventSeq > lastSeenErrorSeq) {
                        lastSeenErrorSeq = pbState.errorEventSeq
                        val active = currentTrack.value
                        if (isVideoMode.value && active != null && !currentResolvedAudioUrl.isNullOrBlank()) {
                            // Quiet fallback from video to audio-only without losing playback
                            isVideoMode.value = false
                            currentVideoQualityLabel.value = ""
                            musicPlayer.switchAudioVideoSource(
                                audioUrl = currentResolvedAudioUrl!!,
                                videoVariant = null,
                                useVideo = false,
                                title = active.title,
                                artist = active.artist,
                                album = active.album,
                                artworkUrl = active.coverArtUrl,
                                trackId = active.id
                            )
                            context?.let {
                                Toast.makeText(it, "Video stream unavailable · switched to audio", Toast.LENGTH_SHORT).show()
                            }
                        } else if (!isStreamResolving && active != null && retriedErrorForTrackId != active.id && musicEngine != null) {
                            retriedErrorForTrackId = active.id
                            isStreamResolving = true
                            pendingTrackId = active.id
                            stopTicker()
                            resolveJob?.cancel()
                            resolveJob = scope.launch(Dispatchers.IO) {
                                val retry = runCatching {
                                    musicEngine.resolveStreamWithFallback(active, bypassCache = true)
                                }.getOrNull()
                                withContext(Dispatchers.Main) {
                                    if (currentTrack.value?.id == active.id) {
                                        if (retry != null) {
                                            musicPlayer.playDirectStream(
                                                streamUrl = retry.streamUrl,
                                                title = active.title,
                                                artist = active.artist,
                                                album = active.album,
                                                artworkUrl = active.coverArtUrl,
                                                durationMs = durationMs.longValue,
                                                trackId = active.id
                                            )
                                            musicPlayer.setPlaybackSpeedAndPitch(manualTempo.floatValue, manualPitch.floatValue)
                                            isStreamResolving = false
                                            startTicker()
                                        } else {
                                            isStreamResolving = false
                                            pendingTrackId = null
                                            isPlaying.value = false
                                            context?.let {
                                                android.widget.Toast.makeText(
                                                    it,
                                                    "Unable to play \"${active.title}\"",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (!isStreamResolving && active != null && pbState.endedTrackId == null) {
                            isPlaying.value = false
                            stopTicker()
                        }
                    }

                    pbState.audioSessionId?.let { sessionId ->
                        spatialReverb.attachToSession(sessionId)
                        if (activeSoundPreset.value != SoundPreset.NORMAL) {
                            applySoundPresetEffectsToHardware(activeSoundPreset.value)
                        }
                    }
                }
            }
        }
    }

    private fun observeLikedState(trackId: String) {
        likeObserverJob?.cancel()
        val repo = libraryRepo ?: return
        likeObserverJob = scope.launch {
            repo.isLiked(trackId).collect { liked ->
                isLiked.value = liked
            }
        }
    }

    private fun readSystemVolumeFraction(): Float {
        val am = audioManager ?: return 0.75f
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (cur.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    }

    fun syncSystemVolume() {
        volume.floatValue = readSystemVolumeFraction()
    }

    fun setVolume(newVolume: Float) {
        val clamped = newVolume.coerceIn(0f, 1f)
        volume.floatValue = clamped
        audioManager?.let { am ->
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val targetIdx = kotlin.math.round(clamped * max).toInt().coerceIn(0, max)
            runCatching {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetIdx, 0)
            }
        }
    }

    private fun updateDurationFromTrack(track: RezonaTrack) {
        val parts = track.duration.split(":")
        val minutes = parts.getOrNull(0)?.toLongOrNull() ?: 3L
        val seconds = parts.getOrNull(1)?.toLongOrNull() ?: 30L
        durationMs.longValue = (minutes * 60L + seconds) * 1000L
    }

    private fun onCurrentTrackEnded(trackId: String) {
        if (autoAdvancingForTrackId == trackId) return
        autoAdvancingForTrackId = trackId
        stopTicker()

        if (sleepTimerEndOfTrack.value) {
            sleepTimerEndOfTrack.value = false
            sleepTimerMinutes.value = null
            sleepTimerSecondsRemaining.value = null
            isPlaying.value = false
            musicPlayer?.pause()
            currentPositionMs.longValue = 0L
            return
        }

        when (repeatMode.value) {
            RepeatMode.ONE -> {
                autoAdvancingForTrackId = null
                seekToPosition(0L)
                isPlaying.value = true
                musicPlayer?.let {
                    if (!it.playbackState.value.isPlaying) it.togglePlayPause()
                }
                startTicker()
            }
            RepeatMode.ALL, RepeatMode.OFF -> {
                if (queue.isNotEmpty()) {
                    skipNext(fromAuto = true)
                } else {
                    isPlaying.value = false
                    currentPositionMs.longValue = 0L
                }
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch(Dispatchers.Main) {
            while (isActive && isPlaying.value) {
                delay(500L)
                if (isStreamResolving || pendingTrackId != null) continue
                val activeId = currentTrack.value?.id ?: break
                val nextPos = currentPositionMs.longValue + 500L
                val dur = durationMs.longValue
                if (dur > 0 && nextPos >= dur + 1200L) {
                    onCurrentTrackEnded(activeId)
                    return@launch
                } else {
                    currentPositionMs.longValue = if (dur > 0) nextPos.coerceAtMost(dur) else nextPos
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun playTrack(track: RezonaTrack, queueTitle: String? = null, autoQueueRadio: Boolean = false) {
        val prev = currentTrack.value
        if (prev != null && track.id != prev.id) {
            history.add(0, prev)
            if (history.size > 30) history.removeAt(history.size - 1)
            currentLyrics.value = emptyList()
            lyricsSourceLabel.value = "Searching lyrics..."
            selectedLyricsSource.value = LyricSourceId.AUTO
            activeResolvedSourceId.value = LyricSourceId.AUTO
            lyricsSourceStatuses.clear()
            lyricsSourceCache.clear()
            lyricsProbeJob?.cancel()
        } else if (prev == null) {
            currentLyrics.value = emptyList()
            lyricsSourceLabel.value = "Searching lyrics..."
            selectedLyricsSource.value = LyricSourceId.AUTO
            activeResolvedSourceId.value = LyricSourceId.AUTO
            lyricsSourceStatuses.clear()
            lyricsSourceCache.clear()
            lyricsProbeJob?.cancel()
        }
        pendingTrackId = track.id
        isStreamResolving = true
        autoAdvancingForTrackId = null
        retriedErrorForTrackId = null
        hasVideoStream.value = false
        isVideoMode.value = false
        currentVideoQualityLabel.value = ""
        currentResolvedAudioUrl = null
        currentVideoVariants = emptyList()
        videoPausedForBackground = false
        seekEventSeq.longValue += 1L
        stopTicker()
        currentTrack.value = track
        if (queueTitle != null) {
            activeQueueName.value = queueTitle
        } else if (queue.isEmpty()) {
            activeQueueName.value = "Mix ${track.title}"
        }
        updateDurationFromTrack(track)
        observeLikedState(track.id)
        currentPositionMs.longValue = 0L
        isPlaying.value = true

        // Record to Room playback history (if not paused in Privacy settings) + enqueue cloud sync
        if (context != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val paused = preferences?.pauseHistoryFlow?.first() ?: false
                    if (!paused) {
                        com.nayama.music.data.repository.RezonaLibraryRepository
                            .getInstance(context)
                            .recordPlayback(track)
                    } else {
                        RezonaDatabase.getInstance(context).trackDao().insertTrack(track.toTrackEntity())
                    }
                }
            }
        }

        // Resolve stream and fetch lyrics asynchronously
        if (musicEngine != null && musicPlayer != null) {
            resolveJob?.cancel()
            resolveJob = scope.launch(Dispatchers.IO) {
                // 1. Fetch real lyrics (Multi-Source: lrc.red / LRCLIB / LyricsPlus / KuGou + YTM)
                launch {
                    runCatching {
                        val bundle = musicEngine.getLyricsWithSource(track, preferredSource = selectedLyricsSource.value)
                        withContext(Dispatchers.Main) {
                            if (currentTrack.value?.id == track.id) {
                                currentLyrics.value = bundle.lines
                                lyricsSourceLabel.value = bundle.sourceLabel
                                activeResolvedSourceId.value = bundle.sourceId
                                if (bundle.lines.isNotEmpty()) {
                                    lyricsSourceCache[bundle.sourceId] = bundle.lines
                                    lyricsSourceStatuses[bundle.sourceId] = if (bundle.isSynced) "Synced" else "Unsynced"
                                    if (selectedLyricsSource.value == LyricSourceId.AUTO && bundle.sourceId != LyricSourceId.LRC_RED) {
                                        lyricsSourceStatuses[LyricSourceId.LRC_RED] = "Not found"
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Resolve audio stream & start ExoPlayer
                val resolved = runCatching {
                    musicEngine.resolveStreamWithFallback(track)
                }.getOrNull()

                withContext(Dispatchers.Main) {
                    if (currentTrack.value?.id == track.id) {
                        if (resolved != null) {
                            currentResolvedAudioUrl = resolved.streamUrl
                            currentVideoVariants = resolved.videoVariants
                            hasVideoStream.value = resolved.videoVariants.isNotEmpty()
                            musicPlayer.playDirectStream(
                                streamUrl = resolved.streamUrl,
                                title = track.title,
                                artist = track.artist,
                                album = track.album,
                                artworkUrl = track.coverArtUrl,
                                durationMs = durationMs.longValue,
                                trackId = track.id
                            )
                            if (activeSoundPreset.value != SoundPreset.NORMAL) {
                                musicPlayer.applySoundPreset(activeSoundPreset.value)
                            } else {
                                musicPlayer.setPlaybackSpeedAndPitch(manualTempo.floatValue, manualPitch.floatValue)
                            }
                            isStreamResolving = false
                            seekEventSeq.longValue += 1L
                            startTicker()
                        } else {
                            isStreamResolving = false
                            pendingTrackId = null
                            isPlaying.value = false
                            stopTicker()
                            context?.let {
                                android.widget.Toast.makeText(
                                    it,
                                    "Unable to play \"${track.title}\"",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }

                // 3. Auto-queue YouTube Music radio if requested
                if (autoQueueRadio && !track.id.startsWith("local_")) {
                    runCatching {
                        val radio = musicEngine.getRadioSeeds(track).filter { it.id != track.id }
                        if (radio.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                queue.clear()
                                queue.addAll(radio)
                            }
                        }
                    }
                }
            }
        }
    }

    fun playQueue(tracks: List<RezonaTrack>, startIndex: Int = 0, queueTitle: String = "Queue") {
        if (tracks.isEmpty()) return
        val safeStart = startIndex.coerceIn(0, tracks.lastIndex)
        val target = tracks[safeStart]
        queue.clear()
        if (safeStart + 1 <= tracks.lastIndex) {
            queue.addAll(tracks.subList(safeStart + 1, tracks.size))
        }
        playTrack(target, queueTitle = queueTitle, autoQueueRadio = false)
    }

    fun refreshLyrics(forceReload: Boolean = true) {
        val track = currentTrack.value ?: return
        val source = selectedLyricsSource.value
        if (musicEngine != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val bundle = musicEngine.getLyricsWithSource(track, forceReload = forceReload, preferredSource = source)
                    withContext(Dispatchers.Main) {
                        if (currentTrack.value?.id == track.id) {
                            currentLyrics.value = bundle.lines
                            lyricsSourceLabel.value = bundle.sourceLabel
                            activeResolvedSourceId.value = bundle.sourceId
                            if (bundle.lines.isNotEmpty()) {
                                lyricsSourceCache[bundle.sourceId] = bundle.lines
                                lyricsSourceStatuses[bundle.sourceId] = if (bundle.isSynced) "Synced" else "Unsynced"
                            }
                        }
                    }
                }
            }
        }
    }

    fun selectLyricsSource(sourceId: LyricSourceId) {
        val track = currentTrack.value ?: return
        selectedLyricsSource.value = sourceId
        val cachedLines = lyricsSourceCache[sourceId]
        if (sourceId != LyricSourceId.AUTO && !cachedLines.isNullOrEmpty()) {
            currentLyrics.value = cachedLines
            activeResolvedSourceId.value = sourceId
            lyricsSourceLabel.value = "Lyrics from ${sourceId.displayName}"
            return
        }
        val engine = musicEngine ?: return
        lyricsSourceStatuses[sourceId] = "Searching..."
        scope.launch(Dispatchers.IO) {
            runCatching {
                val bundle = engine.getLyricsWithSource(track, forceReload = true, preferredSource = sourceId)
                withContext(Dispatchers.Main) {
                    if (currentTrack.value?.id == track.id) {
                        if (bundle.lines.isNotEmpty()) {
                            currentLyrics.value = bundle.lines
                            activeResolvedSourceId.value = bundle.sourceId
                            lyricsSourceLabel.value = bundle.sourceLabel
                            lyricsSourceCache[bundle.sourceId] = bundle.lines
                            lyricsSourceStatuses[sourceId] = if (bundle.isSynced) "Synced (${bundle.lines.size} lines)" else "Unsynced (${bundle.lines.size} lines)"
                        } else {
                            lyricsSourceStatuses[sourceId] = "Not found"
                        }
                    }
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    lyricsSourceStatuses[sourceId] = "Unavailable"
                }
            }
        }
    }

    fun probeAllLyricsSources() {
        val track = currentTrack.value ?: return
        val engine = musicEngine ?: return
        lyricsProbeJob?.cancel()
        lyricsProbeJob = scope.launch(Dispatchers.IO) {
            val targets = LyricSourceId.entries.filter { it != LyricSourceId.AUTO }
            for (src in targets) {
                if (currentTrack.value?.id != track.id) break
                val existing = lyricsSourceStatuses[src]
                if (existing != null && existing != "Searching..." && existing != "Tap to search") continue
                withContext(Dispatchers.Main) {
                    lyricsSourceStatuses[src] = "Searching..."
                }
                val res = runCatching { engine.probeLyricsSource(track, src) }.getOrNull()
                withContext(Dispatchers.Main) {
                    if (currentTrack.value?.id == track.id) {
                        if (res != null && res.lines.isNotEmpty()) {
                            lyricsSourceCache[src] = res.lines
                            lyricsSourceStatuses[src] = res.statusNote
                        } else {
                            lyricsSourceStatuses[src] = "Not found"
                        }
                    }
                }
            }
        }
    }

    fun searchAlternateLyrics(query: String) {
        val track = currentTrack.value ?: return
        val parts = track.duration.split(":")
        val sec = (parts.getOrNull(0)?.toIntOrNull() ?: 3) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 30)
        if (musicEngine != null) {
            scope.launch(Dispatchers.IO) {
                val found = musicEngine.searchLyricsQuery(query, sec)
                withContext(Dispatchers.Main) {
                    if (found.isNotEmpty()) {
                        currentLyrics.value = found
                        lyricsSourceLabel.value = "Lyrics from LRCLIB (Manual)"
                    }
                }
            }
        }
    }

    fun playTrackByIndex(index: Int) {
        val seeds = com.nayama.music.ui.home.REAL_POPULAR_SEEDS
        val track = seeds.getOrElse(index.coerceAtLeast(0) % seeds.size) { seeds.first() }
        playTrack(track)
    }

    fun togglePlayPause() {
        val nextPlaying = !isPlaying.value
        isPlaying.value = nextPlaying
        musicPlayer?.togglePlayPause()
        if (nextPlaying) startTicker() else stopTicker()
    }

    fun getExoPlayer(): ExoPlayer? = musicPlayer?.getExoPlayer()

    fun getExactClockPositionMs(): Long {
        if (isStreamResolving) return 0L
        return musicPlayer?.getExactPositionMs() ?: currentPositionMs.longValue
    }

    fun getCurrentPlaybackSpeed(): Float {
        return musicPlayer?.getPlaybackSpeed() ?: manualTempo.floatValue
    }

    private fun isDeviceOnWifi(): Boolean {
        val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
        }
    }

    fun setVideoMode(enabled: Boolean) {
        val track = currentTrack.value ?: return
        val audioUrl = currentResolvedAudioUrl ?: return
        if (enabled == isVideoMode.value) return
        if (enabled && currentVideoVariants.isEmpty()) {
            context?.let { Toast.makeText(it, "No music video available for this track", Toast.LENGTH_SHORT).show() }
            return
        }
        scope.launch {
            val qualityPref = preferences?.videoQualityFlow?.first() ?: "480p"
            val wifiOnly = preferences?.videoHqWifiOnlyFlow?.first() ?: true
            val selectedVideo = if (enabled) {
                VideoQualitySelector.pickVideoVariant(
                    variants = currentVideoVariants,
                    preferredQuality = qualityPref,
                    isOnWifi = isDeviceOnWifi(),
                    highQualityOnWifiOnly = wifiOnly
                )
            } else null

            val switched = musicPlayer?.switchAudioVideoSource(
                audioUrl = audioUrl,
                videoVariant = selectedVideo,
                useVideo = enabled && selectedVideo != null,
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUrl = track.coverArtUrl,
                trackId = track.id
            ) ?: false

            if (switched) {
                isVideoMode.value = enabled && selectedVideo != null
                currentVideoQualityLabel.value = selectedVideo?.label.orEmpty()
                if (activeSoundPreset.value != SoundPreset.NORMAL) {
                    musicPlayer?.applySoundPreset(activeSoundPreset.value)
                }
            } else if (enabled) {
                isVideoMode.value = false
                currentVideoQualityLabel.value = ""
                context?.let {
                    Toast.makeText(it, "Video stream failed · playing audio", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun setAppInForeground(inForeground: Boolean) {
        if (!inForeground) {
            if (isVideoMode.value) {
                videoPausedForBackground = true
                musicPlayer?.setVideoTrackEnabled(false)
            }
        } else {
            if (videoPausedForBackground && isVideoMode.value) {
                videoPausedForBackground = false
                musicPlayer?.setVideoTrackEnabled(true)
            }
        }
    }

    fun seekToPosition(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, durationMs.longValue)
        currentPositionMs.longValue = clamped
        seekEventSeq.longValue += 1L
        musicPlayer?.seekToPosition(clamped)
    }

    fun seekToFraction(fraction: Float) {
        val target = (durationMs.longValue * fraction.coerceIn(0f, 1f)).toLong()
        seekToPosition(target)
    }

    fun skipNext(fromAuto: Boolean = false) {
        if (!fromAuto) triggerEchoModePrompt()
        if (queue.isNotEmpty()) {
            val nextIdx = if (isShuffle.value) queue.indices.random() else 0
            val nextTrack = queue.removeAt(nextIdx)
            playTrack(nextTrack, queueTitle = activeQueueName.value, autoQueueRadio = false)
        } else {
            val seed = currentTrack.value
            if (musicEngine != null && seed != null && seed.streamUrl.isNotBlank()) {
                scope.launch(Dispatchers.IO) {
                    val related = musicEngine.getRadioSeeds(seed).filter { it.id != seed.id }
                    if (related.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            queue.clear()
                            queue.addAll(related.drop(1))
                            playTrack(related.first(), queueTitle = "Radio · ${seed.title}")
                        }
                    }
                }
            }
        }
    }

    fun skipPrevious() {
        if (currentPositionMs.longValue > 3000L) {
            seekToPosition(0L)
        } else if (history.isNotEmpty()) {
            val prev = history.removeAt(0)
            currentTrack.value?.let { queue.add(0, it) }
            playTrack(prev, queueTitle = activeQueueName.value)
        } else {
            seekToPosition(0L)
        }
    }

    fun toggleLike() {
        val track = currentTrack.value ?: return
        isLiked.value = !isLiked.value
        libraryRepo?.let { repo ->
            scope.launch(Dispatchers.IO) {
                runCatching { repo.toggleLikeTrack(track) }
            }
        }
    }

    fun toggleShuffle() {
        isShuffle.value = !isShuffle.value
    }

    fun toggleRepeat() {
        repeatMode.value = repeatMode.value.next()
    }

    // ── Sound Presets (Part 2: 18 Presets via Media3 + AudioFX + Custom PCM DSP) ─
    fun applySoundPreset(preset: SoundPreset, toggleOffIfSame: Boolean = true, persist: Boolean = true) {
        val target = if (toggleOffIfSame && activeSoundPreset.value == preset) SoundPreset.NORMAL else preset
        activeSoundPreset.value = target
        manualTempo.floatValue = target.speed
        manualPitch.floatValue = target.pitch
        applySoundPresetEffectsToHardware(target)
        if (persist) {
            scope.launch { preferences?.setSoundPreset(target.id) }
        }
    }

    private fun applySoundPresetEffectsToHardware(preset: SoundPreset) {
        musicPlayer?.applySoundPreset(preset)
        if (preset.reverbProfile != null) {
            spatialReverb.setEnabled(true)
            spatialReverb.selectProfile(preset.reverbProfile)
            spatialReverb.setIntensity(preset.reverbIntensity)
        } else {
            spatialReverb.setEnabled(false)
        }
    }

    fun setManualTempoAndPitch(speed: Float, pitch: Float) {
        manualTempo.floatValue = speed.coerceIn(0.5f, 1.8f)
        manualPitch.floatValue = pitch.coerceIn(0.5f, 1.8f)
        activeSoundPreset.value = SoundPreset.NORMAL
        musicPlayer?.applySoundPreset(SoundPreset.NORMAL)
        musicPlayer?.setPlaybackSpeedAndPitch(manualTempo.floatValue, manualPitch.floatValue)
    }

    // ── Sleep Timer (Minutes or End of Track) ─────────────────────────────────
    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        sleepTimerEndOfTrack.value = false
        sleepTimerMinutes.value = minutes
        if (minutes == null || minutes <= 0) {
            sleepTimerSecondsRemaining.value = null
            return
        }
        val totalSec = minutes * 60
        sleepTimerSecondsRemaining.value = totalSec
        sleepTimerJob = scope.launch(Dispatchers.Main) {
            var rem = totalSec
            while (isActive && rem > 0) {
                delay(1000L)
                rem--
                sleepTimerSecondsRemaining.value = rem
            }
            if (isActive) {
                sleepTimerMinutes.value = null
                sleepTimerSecondsRemaining.value = null
                isPlaying.value = false
                musicPlayer?.pause()
            }
        }
    }

    fun setSleepTimerEndOfTrack() {
        sleepTimerJob?.cancel()
        sleepTimerMinutes.value = null
        sleepTimerSecondsRemaining.value = null
        sleepTimerEndOfTrack.value = true
    }

    // ── Queue Management ─────────────────────────────────────────────────────
    fun addToQueueNext(track: RezonaTrack) {
        queue.removeAll { it.id == track.id }
        queue.add(0, track)
    }

    fun jumpToQueueIndex(index: Int) {
        if (index in queue.indices) {
            val picked = queue.removeAt(index)
            playTrack(picked, queueTitle = activeQueueName.value)
        }
    }

    fun removeFromQueue(index: Int) {
        if (index in queue.indices) queue.removeAt(index)
    }

    fun clearQueue() {
        queue.clear()
    }

    fun shuffleQueue() {
        val shuffled = queue.shuffled()
        queue.clear()
        queue.addAll(shuffled)
    }

    fun reorderQueue(from: Int, to: Int) {
        if (from in queue.indices && to in queue.indices) {
            val item = queue.removeAt(from)
            queue.add(to, item)
        }
    }

    fun restoreFromHistory(track: RezonaTrack) {
        history.remove(track)
        queue.add(0, track)
    }

    // ── Legacy Sound Lab / Studio Helpers ────────────────────────────────────
    fun setMoodDialAngle(angle: Float) {
        moodDialAngle.floatValue = angle.coerceIn(-135f, 135f)
    }

    fun setCrossfadeSeconds(sec: Int) {
        crossfadeSeconds.intValue = sec.coerceIn(0, 12)
    }

    fun setGaplessEnabled(enabled: Boolean) {
        gaplessEnabled.value = enabled
    }

    fun launchRadioFromTrack(seed: RezonaTrack) {
        queue.clear()
        playTrack(seed, queueTitle = "Radio · ${seed.title}", autoQueueRadio = true)
    }

    fun startFocusTimer(minutes: Int?) {
        focusTimerJob?.cancel()
        focusTimerMinutesRemaining.value = minutes
        if (minutes == null) return
        focusTimerJob = scope.launch(Dispatchers.Main) {
            var remaining = minutes
            while (isActive && remaining > 0) {
                delay(60000L)
                remaining--
                focusTimerMinutesRemaining.value = remaining
            }
            ambientEngine.stop()
            focusTimerMinutesRemaining.value = null
        }
    }

    private fun triggerEchoModePrompt() {
        echoDismissJob?.cancel()
        echoPromptVisible.value = true
        echoDismissJob = scope.launch(Dispatchers.Main) {
            delay(4000L)
            echoPromptVisible.value = false
        }
    }

    fun answerEchoPrompt(answer: String) {
        lastFeedbackAnswer.value = answer
        echoPromptVisible.value = false
        echoDismissJob?.cancel()
    }

    fun stopPlaybackOnTaskRemoval() {
        resolveJob?.cancel()
        resolveJob = null
        pendingTrackId = null
        isStreamResolving = false
        autoAdvancingForTrackId = null
        retriedErrorForTrackId = null
        stopTicker()
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        echoDismissJob?.cancel()
        isPlaying.value = false
        currentPositionMs.longValue = 0L
        durationMs.longValue = 0L
        queue.clear()
        history.clear()
        currentLyrics.value = emptyList()
        currentTrack.value = null
        activeQueueName.value = ""
    }

    private fun formatSeconds(sec: Int): String {
        val s = sec.coerceAtLeast(0)
        val m = s / 60
        val r = s % 60
        return "%d:%02d".format(m, r)
    }

    companion object {
        @Volatile
        private var instance: RezonaPlayerController? = null

        fun getInstance(context: Context): RezonaPlayerController {
            val appCtx = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: RezonaPlayerController(
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                    context = appCtx
                ).also { instance = it }
            }
        }

        fun getExistingInstance(): RezonaPlayerController? = instance
    }
}
