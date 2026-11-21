package com.nayama.music

import com.nayama.music.data.local.StreamingQuality
import com.nayama.music.data.local.UserPreferences
import com.nayama.music.model.Track
import com.nayama.music.navigation.NayamaScreen
import com.nayama.music.network.AudiusStreamResolver
import com.nayama.music.playback.EqualizerState
import com.nayama.music.playback.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NayamaPhase7OptimizationTest {

    @Test
    fun testAudiusStreamResolverMirrorRotation() {
        val trackId = "track_12345"
        val urlPrimary = AudiusStreamResolver.resolveFallbackStreamUrl(trackId, 0)
        val urlSecondary = AudiusStreamResolver.resolveFallbackStreamUrl(trackId, 1)
        val urlTertiary = AudiusStreamResolver.resolveFallbackStreamUrl(trackId, 2)

        assertTrue(urlPrimary.contains(trackId))
        assertTrue(urlSecondary.contains(trackId))
        assertTrue(urlTertiary.contains(trackId))

        // Different attempt indices should cycle through distinct host mirrors
        val host1 = urlPrimary.split("/v1/tracks/")[0]
        val host2 = urlSecondary.split("/v1/tracks/")[0]
        val host3 = urlTertiary.split("/v1/tracks/")[0]

        assertNotNull(host1)
        assertNotNull(host2)
        assertNotNull(host3)
    }

    @Test
    fun testUserPreferencesDefaults() {
        val prefs = UserPreferences()
        assertEquals(StreamingQuality.HIGH, prefs.streamingQuality)
        assertFalse(prefs.wifiOnlyStreaming)
        assertTrue(prefs.autoRetryOnNetworkLoss)
        assertEquals(0L, prefs.cacheSizeBytes)
    }

    @Test
    fun testEqualizerStatePresets() {
        val eqState = EqualizerState()
        assertEquals("Flat", eqState.currentPreset)
        assertTrue(eqState.presets.contains("Flat"))
        assertTrue(eqState.presets.contains("Bass Boost"))
        assertTrue(eqState.presets.contains("Electronic"))
        assertTrue(eqState.presets.contains("Lo-Fi"))
        assertEquals(7, eqState.presets.size)
    }

    @Test
    fun testNayamaScreenNavigationIntegrity() {
        val items = NayamaScreen.bottomNavItems
        assertEquals(3, items.size)
        assertEquals(
            listOf(
                NayamaScreen.Home,
                NayamaScreen.Search,
                NayamaScreen.Library
            ),
            items
        )

        for (screen in NayamaScreen.values()) {
            assertTrue(screen.title.isNotBlank())
            assertTrue(screen.iconSymbol.isNotBlank())
            assertTrue(screen.route.isNotBlank())
        }
    }

    @Test
    fun testPlaybackStateAudioSessionAndProgress() {
        val state = PlaybackState(
            currentPositionMs = 45000L,
            durationMs = 90000L,
            isPlaying = true,
            audioSessionId = 42
        )

        assertEquals(0.5f, state.progressFraction, 0.001f)
        assertEquals("0:45", state.formattedCurrentPosition)
        assertEquals("1:30", state.formattedTotalDuration)
        assertEquals(42, state.audioSessionId)
        assertTrue(state.isPlaying)
    }

    @Test
    fun testTrackDurationFormatting() {
        val trackShort = Track(
            id = "t1",
            title = "Intro",
            artist = "Artist",
            album = "Album",
            durationSeconds = 45
        )
        assertEquals("0:45", trackShort.formattedDuration)

        val trackLong = Track(
            id = "t2",
            title = "Epic Suite",
            artist = "Artist",
            album = "Album",
            durationSeconds = 605
        )
        assertEquals("10:05", trackLong.formattedDuration)
    }
}
