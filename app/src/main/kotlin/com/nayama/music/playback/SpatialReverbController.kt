package com.nayama.music.playback

import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.PresetReverb
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EchoProfile(
    val title: String,
    val subtitle: String,
    val presetCode: Short,
    val decayTimeMs: Int
) {
    INTIMATE_STUDIO(
        title = "Intimate Studio",
        subtitle = "Crisp, tight dry acoustic room with transparent reflections",
        presetCode = PresetReverb.PRESET_SMALLROOM,
        decayTimeMs = 600
    ),
    GLASS_HALL(
        title = "Glass Hall",
        subtitle = "Lustrous specular reflections echoing across obsidian glass",
        presetCode = PresetReverb.PRESET_MEDIUMHALL,
        decayTimeMs = 1800
    ),
    CATHEDRAL(
        title = "Cathedral",
        subtitle = "Expansive acoustic envelope with rich cathedral tail decay",
        presetCode = PresetReverb.PRESET_LARGEHALL,
        decayTimeMs = 3800
    ),
    DEEP_VOID(
        title = "Deep Void",
        subtitle = "Infinite sub-bass space with surreal acoustic dispersion",
        presetCode = PresetReverb.PRESET_PLATE,
        decayTimeMs = 5200
    )
}

data class EchoModeState(
    val isEnabled: Boolean = false,
    val activeProfile: EchoProfile = EchoProfile.INTIMATE_STUDIO,
    val intensity: Float = 0.35f, // 0.0f .. 1.0f
    val isHardwareSupported: Boolean = true
)

/**
 * Echo Mode Spatial Reverb Controller.
 * Leverages android.media.audiofx on the player's active audio session.
 */
class SpatialReverbController {

    private val _state = MutableStateFlow(EchoModeState())
    val state: StateFlow<EchoModeState> = _state.asStateFlow()

    private var presetReverb: PresetReverb? = null
    private var environmentalReverb: EnvironmentalReverb? = null
    private var currentSessionId: Int? = null

    fun attachToSession(sessionId: Int) {
        if (currentSessionId == sessionId && (presetReverb != null || environmentalReverb != null)) return
        currentSessionId = sessionId
        release()

        try {
            val pr = PresetReverb(0, sessionId)
            pr.enabled = _state.value.isEnabled
            pr.preset = _state.value.activeProfile.presetCode
            presetReverb = pr
            _state.value = _state.value.copy(isHardwareSupported = true)
        } catch (e: Throwable) {
            Log.w("SpatialReverb", "PresetReverb audiofx not available on this session", e)
            try {
                val er = EnvironmentalReverb(0, sessionId)
                er.enabled = _state.value.isEnabled
                applyEnvironmentalIntensity(er, _state.value.intensity)
                environmentalReverb = er
                _state.value = _state.value.copy(isHardwareSupported = true)
            } catch (e2: Throwable) {
                Log.w("SpatialReverb", "EnvironmentalReverb audiofx not available", e2)
                _state.value = _state.value.copy(isHardwareSupported = false)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(isEnabled = enabled)
        try {
            presetReverb?.enabled = enabled
            environmentalReverb?.enabled = enabled
        } catch (e: Throwable) {
            Log.w("SpatialReverb", "Failed to toggle reverb", e)
        }
    }

    fun selectProfile(profile: EchoProfile) {
        _state.value = _state.value.copy(activeProfile = profile)
        try {
            presetReverb?.preset = profile.presetCode
        } catch (e: Throwable) {
            Log.w("SpatialReverb", "Failed to set preset profile", e)
        }
    }

    fun setIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0f, 1f)
        _state.value = _state.value.copy(intensity = clamped)
        environmentalReverb?.let { applyEnvironmentalIntensity(it, clamped) }
    }

    private fun applyEnvironmentalIntensity(er: EnvironmentalReverb, intensity: Float) {
        try {
            // Room level in millibels (-9000 to 0 mB)
            val mB = (-6000 + (intensity * 6000)).toInt().toShort()
            er.roomLevel = mB
            er.reverbLevel = (mB + 500).toShort().coerceAtMost(0)
        } catch (_: Throwable) {}
    }

    fun release() {
        try {
            presetReverb?.release()
            environmentalReverb?.release()
        } catch (_: Throwable) {}
        presetReverb = null
        environmentalReverb = null
    }
}
