package com.nayama.music

import androidx.compose.ui.graphics.Color
import com.nayama.music.backend.ResolvedVideoVariant
import com.nayama.music.backend.RezonaUpdateChecker
import com.nayama.music.backend.SponsorBlockRepository
import com.nayama.music.backend.VideoQualitySelector
import com.nayama.music.model.LyricWord
import com.nayama.music.model.SyncedLyricLine
import com.nayama.music.playback.RezonaDspAudioProcessor
import com.nayama.music.playback.RezonaDspMode
import com.nayama.music.playback.SoundPreset
import com.nayama.music.playback.SoundPresetCategory
import com.nayama.music.ui.lyrics.LyricsFontFamilyOption
import com.nayama.music.ui.lyrics.LyricsMotionMath
import com.nayama.music.ui.lyrics.LyricsStyleConfig
import com.nayama.music.ui.theme.CoverPaletteMath
import com.nayama.music.ui.theme.PureWhite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sin

class PlayerV3Test {

    // ── PART 1: Test 8 Real Cover Color Families ─────────────────────────────

    private fun solidCoverPixels(r: Int, g: Int, b: Int, w: Int = 32, h: Int = 32): IntArray {
        val argb = (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
        return IntArray(w * h) { argb }
    }

    private fun bwCoverPixels(w: Int = 32, h: Int = 32): IntArray {
        return IntArray(w * h) { idx ->
            val v = if ((idx % w) < w / 2) 30 else 225
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    private fun hueDiff(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    @Test
    fun part1_eightCoverPalettes_preserveHueContrastAndAvoidPurpleBlock() {
        data class CoverCase(
            val name: String,
            val pixels: IntArray,
            val expectedHue: Float?, // null for neutral/achromatic covers
            val maxHueDrift: Float = 28f
        )

        val cases = listOf(
            CoverCase("Orange/Brown", solidCoverPixels(214, 128, 56), expectedHue = 27f),
            CoverCase("Red", solidCoverPixels(196, 42, 48), expectedHue = 358f),
            CoverCase("Blue", solidCoverPixels(38, 108, 212), expectedHue = 216f),
            CoverCase("Green", solidCoverPixels(42, 168, 86), expectedHue = 141f),
            CoverCase("Pure Black", solidCoverPixels(6, 6, 6), expectedHue = null),
            CoverCase("Pure White", solidCoverPixels(250, 250, 250), expectedHue = null),
            CoverCase("Pastel Pink", solidCoverPixels(244, 186, 206), expectedHue = 339f),
            CoverCase("Black-and-White", bwCoverPixels(), expectedHue = null)
        )

        for (c in cases) {
            val palette = CoverPaletteMath.computeFromPixels(c.pixels, 32, 32)
            val hsl = CoverPaletteMath.rgbToHsl(
                (palette.ambientBase.red * 255f).toInt(),
                (palette.ambientBase.green * 255f).toInt(),
                (palette.ambientBase.blue * 255f).toInt()
            )
            val contrast = CoverPaletteMath.contrastRatio(PureWhite, palette.ambientBase)

            // 1. White text contrast must be >= 4.5:1
            assertTrue("${c.name}: contrast $contrast must be >= 4.5", contrast >= 4.5f)
            // 2. Lightness clamped ~0.08..0.21
            assertTrue("${c.name}: lightness ${hsl.l} out of dark-readable bounds", hsl.l in 0.08f..0.21f)

            if (c.expectedHue != null) {
                val drift = hueDiff(hsl.h, c.expectedHue)
                assertTrue("${c.name}: hue ${hsl.h} drifted $drift° from ${c.expectedHue}°", drift <= c.maxHueDrift)
                assertTrue("${c.name}: chromatic cover should keep color saturation (got ${hsl.s})", hsl.s >= 0.25f)
            } else {
                // Neutral covers must remain low saturation (no purple wash!)
                assertTrue("${c.name}: neutral cover should stay low saturation (got ${hsl.s})", hsl.s <= 0.12f)
            }

            // 3. Tinted glass must be lighter than ambientBase
            val glassHsl = CoverPaletteMath.rgbToHsl(
                (palette.glassTint.red * 255f).toInt(),
                (palette.glassTint.green * 255f).toInt(),
                (palette.glassTint.blue * 255f).toInt()
            )
            assertTrue("${c.name}: glass tint lightness ${glassHsl.l} should exceed ambientBase ${hsl.l}", glassHsl.l > hsl.l)
        }
    }

    // ── PART 2: Sound Presets & Custom DSP AudioProcessor ────────────────────

    @Test
    fun part2_soundPresetsCoverAllCategoriesAndCustomDspAltersAudio() {
        assertEquals(18, SoundPreset.entries.size)
        assertTrue(SoundPreset.byCategory(SoundPresetCategory.TEMPO).any { it == SoundPreset.SLOWED_REVERB })
        assertTrue(SoundPreset.byCategory(SoundPresetCategory.SPACE).any { it == SoundPreset.CATHEDRAL })
        assertTrue(SoundPreset.byCategory(SoundPresetCategory.TONE).any { it == SoundPreset.RADIO })
        assertTrue(SoundPreset.byCategory(SoundPresetCategory.TONE).any { it == SoundPreset.MUFFLED })
        assertTrue(SoundPreset.byCategory(SoundPresetCategory.FUN).any { it == SoundPreset.VINYL })
        assertTrue(SoundPreset.byCategory(SoundPresetCategory.FUN).any { it == SoundPreset.EIGHT_D })

        // Generate 256 stereo PCM16 frames of a 4 kHz tone
        val frames = 256
        val pcmIn = ByteBuffer.allocate(frames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            val s = (sin(2.0 * Math.PI * 4000.0 * i / 44100.0) * 16000.0).toInt().toShort()
            pcmIn.putShort(s)
            pcmIn.putShort(s)
        }
        pcmIn.flip()

        val proc = RezonaDspAudioProcessor()
        proc.configure(
            androidx.media3.common.audio.AudioProcessor.AudioFormat(
                44100,
                2,
                androidx.media3.common.C.ENCODING_PCM_16BIT
            )
        )
        proc.flush()

        // Muffled 620Hz LPF should strongly attenuate a 4 kHz sine wave
        proc.mode = RezonaDspMode.MUFFLED_LPF
        proc.queueInput(pcmIn.duplicate().order(ByteOrder.nativeOrder()))
        val outMuffled = proc.output
        var peakMuffled = 0
        while (outMuffled.remaining() >= 2) {
            val v = abs(outMuffled.short.toInt())
            if (v > peakMuffled) peakMuffled = v
        }
        assertTrue("Muffled LPF should attenuate 4kHz tone significantly (peak=$peakMuffled)", peakMuffled < 2000)

        // Radio band-pass should pass mid-range energy cleanly
        proc.mode = RezonaDspMode.RADIO_BANDPASS
        proc.queueInput(pcmIn.duplicate().order(ByteOrder.nativeOrder()))
        val outRadio = proc.output
        assertEquals(frames * 4, outRadio.remaining())
    }

    // ── PART 3: Video Quality Selection & Wi-Fi Cap ──────────────────────────

    @Test
    fun part3_videoQualitySelectorRespectsDefault480pAndWifiCap() {
        val variants = listOf(
            ResolvedVideoVariant("https://v/360", 360, "360p", isMuxed = true),
            ResolvedVideoVariant("https://v/480", 480, "480p", isMuxed = false),
            ResolvedVideoVariant("https://v/720", 720, "720p", isMuxed = false),
            ResolvedVideoVariant("https://v/1080", 1080, "1080p", isMuxed = false)
        )

        assertEquals(
            480,
            VideoQualitySelector.pickVideoVariant(variants, "480p", isOnWifi = true, highQualityOnWifiOnly = true)?.heightPx
        )
        assertEquals(
            1080,
            VideoQualitySelector.pickVideoVariant(variants, "1080p", isOnWifi = true, highQualityOnWifiOnly = true)?.heightPx
        )
        assertEquals(
            480,
            VideoQualitySelector.pickVideoVariant(variants, "1080p", isOnWifi = false, highQualityOnWifiOnly = true)?.heightPx
        )
        assertEquals(
            1080,
            VideoQualitySelector.pickVideoVariant(variants, "1080p", isOnWifi = false, highQualityOnWifiOnly = false)?.heightPx
        )
    }

    // ── PART 4: Butter-Smooth Lyrics Clock & Focus Math ──────────────────────

    @Test
    fun part4_lyricsClockAndContinuousFocusIndex() {
        val lines = listOf(
            SyncedLyricLine(
                timeMs = 2000L,
                text = "Hello world",
                words = listOf(
                    LyricWord("Hello", 2000L, 3000L),
                    LyricWord("world", 3000L, 4000L)
                )
            ),
            SyncedLyricLine(timeMs = 5000L, text = "Second line")
        )

        val focusMid = LyricsMotionMath.computeContinuousFocusIndex(lines, 3500L)
        assertTrue("Focus index at midpoint of line 0 should be ~0.5 (got $focusMid)", focusMid in 0.4f..0.6f)

        val fillWord = LyricsMotionMath.computeLineFillProgress(lines[0], 5000L, 3000L)
        assertTrue("Word fill after 'Hello' should be near 5/11 (got $fillWord)", fillWord in 0.4f..0.55f)

        // Clock interpolation advances smoothly at playbackSpeed
        val advanced = LyricsMotionMath.advanceClockMs(
            currentClockMs = 1000.0,
            deltaWallMs = 16.0,
            playbackSpeed = 1.25f,
            polledPlayerMs = 1010L,
            isPlaying = true
        )
        assertEquals(1020.0, advanced, 0.01)

        // Quantized blur returns 0 beyond 4 positions
        assertEquals(0, LyricsMotionMath.quantizedBlurDp(0.2f, blurEnabled = true))
        assertTrue(LyricsMotionMath.quantizedBlurDp(2.0f, blurEnabled = true) in 1..4)
        assertEquals(0, LyricsMotionMath.quantizedBlurDp(5.5f, blurEnabled = true))
    }

    // ── PART 5: 8 Bundled Open-License Fonts & LyricsStyleConfig ─────────────

    @Test
    fun part5_allEightOpenLicenseFontsAndStyleDefaults() {
        assertEquals(8, LyricsFontFamilyOption.entries.size)
        val cfg = LyricsStyleConfig()
        assertEquals(LyricsFontFamilyOption.SATOSHI, cfg.fontFamily)
        assertFalse(cfg.centerAlign)
        assertTrue(cfg.lineHeightSp > cfg.fontSizeSp)
        assertNotNull(LyricsFontFamilyOption.fromId("PLAYFAIR_DISPLAY"))
        assertNotEquals(LyricsFontFamilyOption.SATOSHI, LyricsFontFamilyOption.fromId("BEBAS_NEUE"))
    }

    // ── PART 6: SponsorBlock Segment Parsing & Update Checker ────────────────

    @Test
    fun part6_sponsorBlockParsingAndUpdateChecker() {
        val rawSegments = """
            [
              {"UUID":"u2","category":"outro","segment":[190.5,205.0]},
              {"UUID":"u1","category":"intro","segment":[0.0,14.2]},
              {"UUID":"u_short","category":"music_offtopic","segment":[40.0,40.8]}
            ]
        """.trimIndent()
        val parsed = SponsorBlockRepository.parseSegmentsJson(rawSegments)
        assertEquals(2, parsed.size)
        assertEquals("intro", parsed[0].category)
        assertEquals(0L, parsed[0].startMs)
        assertEquals(14200L, parsed[0].endMs)
        assertEquals("outro", parsed[1].category)
        assertEquals(190500L, parsed[1].startMs)

        val updateJson = """
            {"versionCode":2,"versionName":"1.0.1","apkUrl":"https://example.com/app.apk","changelog":"Liquid Glass Widgets + Skip Intro"}
        """.trimIndent()
        val infoNewer = RezonaUpdateChecker.parseAndCheck(updateJson, currentVersionCode = 1)
        assertNotNull(infoNewer)
        assertEquals(2, infoNewer?.versionCode)
        assertEquals("1.0.1", infoNewer?.versionName)

        val infoSame = RezonaUpdateChecker.parseAndCheck(updateJson, currentVersionCode = 2)
        assertNull(infoSame)

        val gtxJson = """[[["Aku merindukanmu\n","I miss you\n",null,null,10],["Malam ini","Tonight",null,null,10]],null,"en"]"""
        val translated = com.nayama.music.backend.LyricLanguageHelper.parseGtxTranslationJson(gtxJson)
        assertEquals("Aku merindukanmu\nMalam ini", translated)
    }
}
