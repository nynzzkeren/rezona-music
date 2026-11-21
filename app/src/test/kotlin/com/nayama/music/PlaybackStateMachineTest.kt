package com.nayama.music

import com.nayama.music.model.Track
import com.nayama.music.playback.PlaybackState
import com.nayama.music.playback.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateMachineTest {

    @Test
    fun testInitialPlaybackState() {
        val state = PlaybackState()
        assertNull(state.currentTrack)
        assertFalse(state.isPlaying)
        assertFalse(state.isBuffering)
        assertEquals(0L, state.currentPositionMs)
        assertEquals(0L, state.durationMs)
        assertEquals(0f, state.progressFraction, 0.001f)
        assertEquals("0:00", state.formattedCurrentPosition)
        assertEquals(RepeatMode.OFF, state.repeatMode)
        assertFalse(state.isShuffleEnabled)
    }

    @Test
    fun testProgressFractionCalculation() {
        val stateHalf = PlaybackState(
            currentPositionMs = 60000L,
            durationMs = 120000L
        )
        assertEquals(0.5f, stateHalf.progressFraction, 0.001f)

        val stateQuarter = PlaybackState(
            currentPositionMs = 30000L,
            durationMs = 120000L
        )
        assertEquals(0.25f, stateQuarter.progressFraction, 0.001f)

        val stateOverflow = PlaybackState(
            currentPositionMs = 200000L,
            durationMs = 120000L
        )
        assertEquals(1.0f, stateOverflow.progressFraction, 0.001f)
    }

    @Test
    fun testTimeFormatting() {
        val state = PlaybackState(
            currentPositionMs = 75000L, // 1m 15s
            durationMs = 214000L       // 3m 34s
        )
        assertEquals("1:15", state.formattedCurrentPosition)
        assertEquals("3:34", state.formattedTotalDuration)
    }

    @Test
    fun testRepeatModeCycle() {
        var mode = RepeatMode.OFF
        mode = mode.next()
        assertEquals(RepeatMode.ALL, mode)
        mode = mode.next()
        assertEquals(RepeatMode.ONE, mode)
        mode = mode.next()
        assertEquals(RepeatMode.OFF, mode)
    }

    @Test
    fun testQueueWrappingNavigation() {
        val tracks = listOf(
            Track(id = "1", title = "Track 1", artist = "Artist", album = "Album", durationSeconds = 100),
            Track(id = "2", title = "Track 2", artist = "Artist", album = "Album", durationSeconds = 200),
            Track(id = "3", title = "Track 3", artist = "Artist", album = "Album", durationSeconds = 300)
        )

        var currentIdx = 0

        // Next forward
        currentIdx = (currentIdx + 1) % tracks.size
        assertEquals(1, currentIdx)
        assertEquals("Track 2", tracks[currentIdx].title)

        currentIdx = (currentIdx + 1) % tracks.size
        assertEquals(2, currentIdx)
        assertEquals("Track 3", tracks[currentIdx].title)

        // Wraparound to start
        currentIdx = (currentIdx + 1) % tracks.size
        assertEquals(0, currentIdx)
        assertEquals("Track 1", tracks[currentIdx].title)

        // Previous backwards with underflow wrap
        currentIdx = if (currentIdx - 1 < 0) tracks.size - 1 else currentIdx - 1
        assertEquals(2, currentIdx)
        assertEquals("Track 3", tracks[currentIdx].title)
    }

    @Test
    fun testPlaybackStateWithBufferingAndError() {
        val bufferingState = PlaybackState(
            isBuffering = true,
            isPlaying = false
        )
        assertTrue(bufferingState.isBuffering)
        assertFalse(bufferingState.isPlaying)

        val errorState = PlaybackState(
            errorMessage = "Koneksi stream terputus",
            isPlaying = false
        )
        assertEquals("Koneksi stream terputus", errorState.errorMessage)
        assertFalse(errorState.isPlaying)
    }
}

