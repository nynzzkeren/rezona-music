package com.nayama.music.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log
import com.nayama.music.R

enum class SoundPresetCategory(val label: String) {
    ALL("All"),
    TEMPO("Tempo"),
    SPACE("Space"),
    TONE("Tone"),
    FUN("Fun")
}

/**
 * Full Part 2 Sound Preset catalog (18 presets: Normal + 17 tuned effects across Tempo, Space, Tone, Fun).
 */
enum class SoundPreset(
    val id: String,
    val label: String,
    val description: String,
    val category: SoundPresetCategory,
    val iconRes: Int,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val reverbProfile: EchoProfile? = null,
    val reverbIntensity: Float = 0f,
    val bassBoostStrength: Short = 0,          // 0..1000
    val virtualizerStrength: Short = 0,        // 0..1000
    val loudnessGainMb: Int = 0,               // millibels (0..600)
    val eqNormalizedBands: FloatArray = floatArrayOf(0f, 0f, 0f, 0f, 0f),
    val dspMode: RezonaDspMode = RezonaDspMode.BYPASS
) {
    NORMAL(
        id = "NORMAL",
        label = "Normal",
        description = "Pure studio output with all effects off",
        category = SoundPresetCategory.ALL,
        iconRes = R.drawable.ic_sound_waves
    ),

    // ── TEMPO ────────────────────────────────────────────────────────────────
    SLOWED_REVERB(
        id = "SLOWED_REVERB",
        label = "Slowed + Reverb",
        description = "Dreamy 0.84x tempo with lush hall decay",
        category = SoundPresetCategory.TEMPO,
        iconRes = R.drawable.ic_ambient,
        speed = 0.84f,
        pitch = 0.88f,
        reverbProfile = EchoProfile.GLASS_HALL,
        reverbIntensity = 0.72f,
        bassBoostStrength = 300,
        eqNormalizedBands = floatArrayOf(0.45f, 0.30f, 0.0f, -0.20f, -0.30f)
    ),
    SPED_UP(
        id = "SPED_UP",
        label = "Sped up",
        description = "Upbeat 1.18x pace with crisp presence",
        category = SoundPresetCategory.TEMPO,
        iconRes = R.drawable.ic_trending,
        speed = 1.18f,
        pitch = 1.12f,
        eqNormalizedBands = floatArrayOf(0.15f, 0.05f, 0.15f, 0.35f, 0.40f)
    ),
    NIGHTCORE(
        id = "NIGHTCORE",
        label = "Nightcore",
        description = "High-energy 1.26x speed & bright vocals",
        category = SoundPresetCategory.TEMPO,
        iconRes = R.drawable.ic_music_note,
        speed = 1.26f,
        pitch = 1.22f,
        eqNormalizedBands = floatArrayOf(0.30f, 0.10f, 0.25f, 0.55f, 0.68f)
    ),

    // ── SPACE ────────────────────────────────────────────────────────────────
    ECHO(
        id = "ECHO",
        label = "Echo",
        description = "Stereo slapback repeats & airy reflections",
        category = SoundPresetCategory.SPACE,
        iconRes = R.drawable.ic_radio,
        reverbProfile = EchoProfile.GLASS_HALL,
        reverbIntensity = 0.65f,
        virtualizerStrength = 500,
        eqNormalizedBands = floatArrayOf(0.10f, 0.0f, 0.15f, 0.35f, 0.45f),
        dspMode = RezonaDspMode.ECHO_SLAPBACK
    ),
    CONCERT(
        id = "CONCERT",
        label = "Concert hall",
        description = "Wide arena stage with deep stadium punch",
        category = SoundPresetCategory.SPACE,
        iconRes = R.drawable.ic_speaker,
        reverbProfile = EchoProfile.CATHEDRAL,
        reverbIntensity = 0.58f,
        bassBoostStrength = 450,
        virtualizerStrength = 750,
        loudnessGainMb = 220,
        eqNormalizedBands = floatArrayOf(0.60f, 0.30f, -0.15f, 0.40f, 0.65f)
    ),
    CATHEDRAL(
        id = "CATHEDRAL",
        label = "Cathedral",
        description = "Expansive stone acoustics & long tail",
        category = SoundPresetCategory.SPACE,
        iconRes = R.drawable.ic_ambient,
        reverbProfile = EchoProfile.DEEP_VOID,
        reverbIntensity = 0.88f,
        virtualizerStrength = 650,
        eqNormalizedBands = floatArrayOf(0.25f, 0.10f, 0.05f, 0.30f, 0.50f),
        dspMode = RezonaDspMode.CATHEDRAL_TAIL
    ),

    // ── TONE ─────────────────────────────────────────────────────────────────
    BASS_BOOST(
        id = "BASS_BOOST",
        label = "Bass boost",
        description = "Deep sub-bass punch without muddy mids",
        category = SoundPresetCategory.TONE,
        iconRes = R.drawable.ic_equalizer,
        bassBoostStrength = 780,
        loudnessGainMb = 180,
        eqNormalizedBands = floatArrayOf(0.88f, 0.52f, -0.05f, 0.05f, 0.10f)
    ),
    VOCAL_BOOST(
        id = "VOCAL_BOOST",
        label = "Vocal boost",
        description = "Forward crystal-clear lead vocals",
        category = SoundPresetCategory.TONE,
        iconRes = R.drawable.ic_mic,
        loudnessGainMb = 240,
        eqNormalizedBands = floatArrayOf(-0.35f, -0.10f, 0.65f, 0.72f, 0.25f)
    ),
    TREBLE_BOOST(
        id = "TREBLE_BOOST",
        label = "Treble boost",
        description = "Sparkling hi-hats & detailed upper air",
        category = SoundPresetCategory.TONE,
        iconRes = R.drawable.ic_tune,
        eqNormalizedBands = floatArrayOf(-0.10f, 0.0f, 0.15f, 0.60f, 0.88f)
    ),
    WARM(
        id = "WARM",
        label = "Warm",
        description = "Rich analog low-mids & silky treble",
        category = SoundPresetCategory.TONE,
        iconRes = R.drawable.ic_headphones,
        bassBoostStrength = 260,
        eqNormalizedBands = floatArrayOf(0.45f, 0.48f, 0.15f, -0.22f, -0.40f),
        dspMode = RezonaDspMode.WARM_LPF
    ),
    RADIO(
        id = "RADIO",
        label = "Radio",
        description = "Vintage AM/FM band-pass broadcast tone",
        category = SoundPresetCategory.TONE,
        iconRes = R.drawable.ic_radio,
        eqNormalizedBands = floatArrayOf(-0.85f, -0.30f, 0.70f, 0.45f, -0.85f),
        dspMode = RezonaDspMode.RADIO_BANDPASS
    ),
    MUFFLED(
        id = "MUFFLED",
        label = "Muffled / Underwater",
        description = "Submerged 620Hz low-pass next-room vibe",
        category = SoundPresetCategory.TONE,
        iconRes = R.drawable.ic_volume_low,
        reverbProfile = EchoProfile.INTIMATE_STUDIO,
        reverbIntensity = 0.55f,
        eqNormalizedBands = floatArrayOf(0.50f, 0.25f, -0.50f, -0.85f, -1.0f),
        dspMode = RezonaDspMode.MUFFLED_LPF
    ),

    // ── FUN ──────────────────────────────────────────────────────────────────
    LOFI(
        id = "LOFI",
        label = "Lo-fi",
        description = "Cozy tape warmth with 3.8kHz mellow filter",
        category = SoundPresetCategory.FUN,
        iconRes = R.drawable.ic_headphones,
        speed = 0.95f,
        pitch = 0.96f,
        reverbProfile = EchoProfile.INTIMATE_STUDIO,
        reverbIntensity = 0.38f,
        eqNormalizedBands = floatArrayOf(0.40f, 0.32f, -0.10f, -0.55f, -0.85f),
        dspMode = RezonaDspMode.LOFI_LPF
    ),
    VINYL(
        id = "VINYL",
        label = "Vinyl",
        description = "Warm turntable tone with gentle crackle",
        category = SoundPresetCategory.FUN,
        iconRes = R.drawable.ic_music_note,
        reverbProfile = EchoProfile.INTIMATE_STUDIO,
        reverbIntensity = 0.25f,
        eqNormalizedBands = floatArrayOf(0.32f, 0.28f, 0.10f, -0.25f, -0.55f),
        dspMode = RezonaDspMode.VINYL_CRACKLE
    ),
    CLUB(
        id = "CLUB",
        label = "Club",
        description = "Heavy sub-bass, wide stereo & dance punch",
        category = SoundPresetCategory.FUN,
        iconRes = R.drawable.ic_speaker,
        reverbProfile = EchoProfile.INTIMATE_STUDIO,
        reverbIntensity = 0.42f,
        bassBoostStrength = 900,
        virtualizerStrength = 680,
        loudnessGainMb = 340,
        eqNormalizedBands = floatArrayOf(0.92f, 0.55f, -0.20f, 0.35f, 0.60f)
    ),
    ACOUSTIC(
        id = "ACOUSTIC",
        label = "Acoustic",
        description = "Natural woody resonance & intimate room",
        category = SoundPresetCategory.FUN,
        iconRes = R.drawable.ic_rezona_studio,
        reverbProfile = EchoProfile.INTIMATE_STUDIO,
        reverbIntensity = 0.34f,
        eqNormalizedBands = floatArrayOf(0.28f, 0.34f, 0.20f, 0.45f, 0.48f)
    ),
    EIGHT_D(
        id = "EIGHT_D",
        label = "8D Audio",
        description = "Circular stereo auto-pan for headphones",
        category = SoundPresetCategory.FUN,
        iconRes = R.drawable.ic_headphones,
        reverbProfile = EchoProfile.GLASS_HALL,
        reverbIntensity = 0.52f,
        virtualizerStrength = 800,
        dspMode = RezonaDspMode.EIGHT_D_PAN
    );

    companion object {
        fun byCategory(category: SoundPresetCategory): List<SoundPreset> {
            return if (category == SoundPresetCategory.ALL) {
                entries.toList()
            } else {
                entries.filter { it.category == category || it == NORMAL }
            }
        }

        fun fromId(raw: String): SoundPreset = entries.firstOrNull {
            it.id.equals(raw, ignoreCase = true) ||
                it.label.replace("\n", " ").equals(raw, ignoreCase = true)
        } ?: NORMAL
    }
}

/**
 * Hardware android.media.audiofx chain (BassBoost, Virtualizer, LoudnessEnhancer, Equalizer, PresetReverb)
 * synchronized cleanly with ExoPlayer's audioSessionId.
 */
class RezonaAudioFxController {
    private var sessionId: Int = 0
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var reverb: PresetReverb? = null

    private var activePreset: SoundPreset = SoundPreset.NORMAL

    fun attachToSession(newSessionId: Int) {
        if (newSessionId <= 0) return
        if (sessionId == newSessionId && (equalizer != null || bassBoost != null)) {
            applyPreset(activePreset)
            return
        }
        release()
        sessionId = newSessionId

        bassBoost = runCatching { BassBoost(0, newSessionId) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, newSessionId) }.getOrNull()
        loudness = runCatching { LoudnessEnhancer(newSessionId) }.getOrNull()
        equalizer = runCatching { Equalizer(0, newSessionId) }.getOrNull()
        reverb = runCatching { PresetReverb(0, newSessionId) }.getOrNull()

        applyPreset(activePreset)
    }

    fun applyPreset(preset: SoundPreset) {
        activePreset = preset
        val isNormal = preset == SoundPreset.NORMAL

        runCatching {
            bassBoost?.let { bb ->
                if (!isNormal && preset.bassBoostStrength > 0 && bb.strengthSupported) {
                    bb.setStrength(preset.bassBoostStrength.coerceIn(0, 1000))
                    bb.enabled = true
                } else {
                    bb.enabled = false
                }
            }
        }.onFailure { Log.w(TAG, "BassBoost apply skipped: ${it.message}") }

        runCatching {
            virtualizer?.let { vz ->
                if (!isNormal && preset.virtualizerStrength > 0 && vz.strengthSupported) {
                    vz.setStrength(preset.virtualizerStrength.coerceIn(0, 1000))
                    vz.enabled = true
                } else {
                    vz.enabled = false
                }
            }
        }.onFailure { Log.w(TAG, "Virtualizer apply skipped: ${it.message}") }

        runCatching {
            loudness?.let { le ->
                if (!isNormal && preset.loudnessGainMb > 0) {
                    le.setTargetGain(preset.loudnessGainMb.coerceIn(0, 600))
                    le.enabled = true
                } else {
                    le.setTargetGain(0)
                    le.enabled = false
                }
            }
        }.onFailure { Log.w(TAG, "LoudnessEnhancer apply skipped: ${it.message}") }

        runCatching {
            reverb?.let { pr ->
                val prof = preset.reverbProfile
                if (!isNormal && prof != null) {
                    pr.preset = prof.presetCode
                    pr.enabled = true
                } else {
                    pr.enabled = false
                }
            }
        }.onFailure { Log.w(TAG, "PresetReverb apply skipped: ${it.message}") }

        runCatching {
            equalizer?.let { eq ->
                val range = eq.bandLevelRange
                val minLvl = range[0].toInt()
                val maxLvl = range[1].toInt()
                val count = eq.numberOfBands.toInt()
                if (isNormal) {
                    for (i in 0 until count) {
                        eq.setBandLevel(i.toShort(), 0)
                    }
                    eq.enabled = false
                } else {
                    val curve = preset.eqNormalizedBands
                    for (i in 0 until count) {
                        val normIdx = if (count <= 1) 0 else ((i.toFloat() / (count - 1)) * (curve.size - 1)).toInt()
                        val factor = curve.getOrElse(normIdx) { 0f }
                        val level = (if (factor >= 0f) factor * maxLvl else factor * (-minLvl))
                            .toInt()
                            .coerceIn(minLvl, maxLvl)
                            .toShort()
                        eq.setBandLevel(i.toShort(), level)
                    }
                    eq.enabled = true
                }
            }
        }.onFailure { Log.w(TAG, "Equalizer preset curve skipped: ${it.message}") }
    }

    fun release() {
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudness?.release() }
        runCatching { equalizer?.release() }
        runCatching { reverb?.release() }
        bassBoost = null
        virtualizer = null
        loudness = null
        equalizer = null
        reverb = null
        sessionId = 0
    }

    companion object {
        private const val TAG = "RezonaAudioFx"
    }
}
