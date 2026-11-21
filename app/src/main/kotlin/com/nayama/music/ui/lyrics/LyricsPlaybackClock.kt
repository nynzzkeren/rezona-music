package com.nayama.music.ui.lyrics

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.nayama.music.model.SyncedLyricLine
import com.nayama.music.playback.RezonaPlayerController
import kotlin.math.abs

/**
 * Pure math helpers for Part 4 Butter-Smooth Lyrics (100% JVM unit-testable, zero allocations).
 */
object LyricsMotionMath {

    /**
     * Computes a continuous Float focus index:
     * activeLineIndex + easedProgressThroughActiveLine (0f..0.999f).
     * Before the first line, smoothly interpolates from -1f toward 0f.
     */
    fun computeContinuousFocusIndex(
        lines: List<SyncedLyricLine>,
        positionMs: Long
    ): Float {
        if (lines.isEmpty()) return 0f
        val firstTime = lines[0].timeMs
        if (positionMs < firstTime) {
            val leadWindow = firstTime.coerceAtLeast(1L).coerceAtMost(2500L).toFloat()
            val dist = (firstTime - positionMs).toFloat().coerceIn(0f, leadWindow)
            return -(dist / leadWindow)
        }

        // Binary search for active line index (O(log N), zero allocations)
        var lo = 0
        var hi = lines.lastIndex
        var active = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                active = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }

        val startMs = lines[active].timeMs
        val endMs = if (active < lines.lastIndex) {
            lines[active + 1].timeMs
        } else {
            startMs + 4500L
        }
        val dur = (endMs - startMs).coerceAtLeast(250L).toFloat()
        val rawFrac = ((positionMs - startMs).toFloat() / dur).coerceIn(0f, 1f)
        // Gentle cubic ease-in-out so focus transitions glide effortlessly
        val eased = rawFrac * rawFrac * (3f - 2f * rawFrac)
        return active.toFloat() + eased * 0.999f
    }

    /**
     * Computes exact character-level fill progress [0f..1f] for a line at [positionMs].
     * Uses word-by-word timestamps when present, else smooth line-level progression.
     */
    fun computeLineFillProgress(
        line: SyncedLyricLine,
        nextLineTimeMs: Long,
        positionMs: Long
    ): Float {
        if (positionMs <= line.timeMs) return 0f
        val words = line.words
        if (!words.isNullOrEmpty()) {
            val totalChars = line.text.length.coerceAtLeast(1)
            var charCursor = 0
            for (i in words.indices) {
                val w = words[i]
                val wLen = w.word.length
                val spaceLen = if (i < words.lastIndex) 1 else 0
                when {
                    positionMs < w.startMs -> {
                        return (charCursor.toFloat() / totalChars).coerceIn(0f, 1f)
                    }
                    positionMs <= w.endMs -> {
                        val wDur = (w.endMs - w.startMs).coerceAtLeast(1L).toFloat()
                        val wFrac = ((positionMs - w.startMs).toFloat() / wDur).coerceIn(0f, 1f)
                        return ((charCursor + wLen * wFrac) / totalChars).coerceIn(0f, 1f)
                    }
                    else -> {
                        charCursor += wLen + spaceLen
                    }
                }
            }
            return 1f
        }

        val dur = (nextLineTimeMs - line.timeMs).coerceAtLeast(400L).toFloat()
        return ((positionMs - line.timeMs).toFloat() / dur).coerceIn(0f, 1f)
    }

    /**
     * Interpolates playback clock position smoothly using elapsedRealtime delta x playbackSpeed
     * and applies gentle drift correction when ExoPlayer diverges by > 120ms.
     */
    fun advanceClockMs(
        currentClockMs: Double,
        deltaWallMs: Double,
        playbackSpeed: Float,
        polledPlayerMs: Long,
        isPlaying: Boolean
    ): Double {
        if (!isPlaying) return polledPlayerMs.toDouble()
        val predicted = currentClockMs + (deltaWallMs * playbackSpeed.coerceIn(0.25f, 2.5f))
        val drift = polledPlayerMs.toDouble() - predicted
        return when {
            abs(drift) > 650.0 -> polledPlayerMs.toDouble() // Large jump / seek
            abs(drift) > 120.0 -> predicted + drift * 0.14  // Gentle convergence
            else -> predicted
        }.coerceAtLeast(0.0)
    }

    /**
     * Derives smooth alpha (1.0 -> 0.14) from continuous distance to focusIndex.
     */
    fun alphaForDistance(distance: Float): Float {
        val d = abs(distance)
        return when {
            d <= 0.35f -> 1.0f
            d <= 1.35f -> 1.0f - ((d - 0.35f) * 0.52f)
            d <= 3.0f -> 0.48f - ((d - 1.35f) / 1.65f) * 0.24f
            else -> (0.24f - ((d - 3.0f) * 0.04f)).coerceAtLeast(0.14f)
        }
    }

    /**
     * Derives subtle scale (1.0 down to 0.94) from continuous distance to focusIndex.
     */
    fun scaleForDistance(distance: Float): Float {
        val d = abs(distance).coerceIn(0f, 3f)
        return 1.0f - (d / 3f) * 0.06f
    }

    /**
     * Quantized integer blur dp (0..4) ONLY within 4 positions of active line so RenderEffect
     * instances can be cached & reused with zero per-frame allocations.
     */
    fun quantizedBlurDp(distance: Float, blurEnabled: Boolean): Int {
        if (!blurEnabled) return 0
        val d = abs(distance)
        return when {
            d < 0.72f -> 0
            d < 1.65f -> 1
            d < 2.65f -> 2
            d < 3.60f -> 3
            d <= 4.35f -> 4
            else -> 0
        }
    }
}

/**
 * Frame-synchronized 60/120Hz PlaybackClock with runtime jank monitoring.
 * Read `positionMs.longValue` and `focusIndex.floatValue` ONLY inside draw / graphicsLayer lambdas
 * to prevent per-frame Compose recomposition!
 */
@Stable
class LyricsPlaybackClock {
    val positionMs: MutableLongState = mutableLongStateOf(0L)
    val focusIndex: MutableFloatState = mutableFloatStateOf(0f)
    val jankAutoDisabledBlur: MutableState<Boolean> = mutableStateOf(false)
    val jankFramePercent: MutableFloatState = mutableFloatStateOf(0f)

    internal var internalClockMs: Double = 0.0
    internal var lastWallNanos: Long = 0L
    internal var totalFramesWindow: Int = 0
    internal var jankyFramesWindow: Int = 0

    fun snapTo(posMs: Long, lines: List<SyncedLyricLine>) {
        val clamped = posMs.coerceAtLeast(0L)
        internalClockMs = clamped.toDouble()
        lastWallNanos = SystemClock.elapsedRealtimeNanos()
        positionMs.longValue = clamped
        focusIndex.floatValue = LyricsMotionMath.computeContinuousFocusIndex(lines, clamped)
    }
}

@Composable
fun rememberLyricsPlaybackClock(
    controller: RezonaPlayerController,
    lines: List<SyncedLyricLine>,
    timingOffsetMs: Int
): LyricsPlaybackClock {
    val clock = remember { LyricsPlaybackClock() }
    val trackId = controller.currentTrack.value?.id
    val seekSeq = controller.seekEventSeq.longValue
    val isPlaying = controller.isPlaying.value

    // Snap immediately on seek, track change, lyrics reload, or offset change
    LaunchedEffect(trackId, seekSeq, lines, timingOffsetMs) {
        val snapped = (controller.getExactClockPositionMs() + timingOffsetMs).coerceAtLeast(0L)
        clock.snapTo(snapped, lines)
    }

    // Frame-synced 60/120Hz Choreographer loop (never triggers parent recomposition!)
    LaunchedEffect(trackId, isPlaying, lines, timingOffsetMs) {
        clock.lastWallNanos = SystemClock.elapsedRealtimeNanos()
        var prevFrameNanos = 0L

        while (true) {
            withFrameNanos { frameNanos ->
                if (prevFrameNanos != 0L) {
                    val frameDeltaMs = (frameNanos - prevFrameNanos) / 1_000_000f
                    if (frameDeltaMs in 1f..200f) {
                        clock.totalFramesWindow++
                        if (frameDeltaMs > 18.5f) {
                            clock.jankyFramesWindow++
                        }
                        if (clock.totalFramesWindow >= 90) {
                            val pct = (clock.jankyFramesWindow.toFloat() / clock.totalFramesWindow) * 100f
                            clock.jankFramePercent.floatValue = pct
                            if (pct > 15f && !clock.jankAutoDisabledBlur.value) {
                                clock.jankAutoDisabledBlur.value = true
                            }
                            clock.totalFramesWindow = 0
                            clock.jankyFramesWindow = 0
                        }
                    }
                }
                prevFrameNanos = frameNanos

                val nowWall = SystemClock.elapsedRealtimeNanos()
                val deltaWallMs = ((nowWall - clock.lastWallNanos).coerceIn(0L, 100_000_000L)) / 1_000_000.0
                clock.lastWallNanos = nowWall

                val polled = (controller.getExactClockPositionMs() + timingOffsetMs).coerceAtLeast(0L)
                val speed = controller.getCurrentPlaybackSpeed()
                val nextMs = LyricsMotionMath.advanceClockMs(
                    currentClockMs = clock.internalClockMs,
                    deltaWallMs = deltaWallMs,
                    playbackSpeed = speed,
                    polledPlayerMs = polled,
                    isPlaying = isPlaying
                )
                clock.internalClockMs = nextMs
                val longMs = nextMs.toLong()
                if (clock.positionMs.longValue != longMs) {
                    clock.positionMs.longValue = longMs
                }
                clock.focusIndex.floatValue = LyricsMotionMath.computeContinuousFocusIndex(lines, longMs)
            }
        }
    }

    return clock
}
