package com.nayama.music.playback

import android.content.Context
import android.media.audiofx.Equalizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqualizerBand(
    val index: Short,
    val centerFreqHz: Int,
    val minLevel: Short,
    val maxLevel: Short,
    val currentLevel: Short
)

data class EqualizerState(
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val currentPreset: String = "Flat",
    val presets: List<String> = listOf("Flat", "Bass Boost", "Vocal", "Electronic", "Acoustic", "Rock", "Lo-Fi"),
    val bands: List<EqualizerBand> = emptyList()
)

class AudioEqualizerController private constructor(private val context: Context) {

    private val _equalizerState = MutableStateFlow(EqualizerState())
    val equalizerState: StateFlow<EqualizerState> = _equalizerState.asStateFlow()

    private var equalizer: Equalizer? = null
    private var currentSessionId: Int? = null

    fun attachToSession(sessionId: Int) {
        if (currentSessionId == sessionId && equalizer != null) return
        currentSessionId = sessionId

        release()

        try {
            val eq = Equalizer(0, sessionId)
            eq.enabled = _equalizerState.value.isEnabled
            this.equalizer = eq

            val minLevel = eq.bandLevelRange[0]
            val maxLevel = eq.bandLevelRange[1]
            val bandCount = eq.numberOfBands.toInt()

            val bandList = mutableListOf<EqualizerBand>()
            for (i in 0 until bandCount) {
                val bandIndex = i.toShort()
                val freq = eq.getCenterFreq(bandIndex) / 1000 // Convert mHz to Hz
                val currentLevel = eq.getBandLevel(bandIndex)
                bandList.add(
                    EqualizerBand(
                        index = bandIndex,
                        centerFreqHz = freq,
                        minLevel = minLevel,
                        maxLevel = maxLevel,
                        currentLevel = currentLevel
                    )
                )
            }

            _equalizerState.value = _equalizerState.value.copy(
                isAvailable = true,
                bands = bandList
            )
        } catch (e: Throwable) {
            Log.w("EqualizerController", "AudioFx Equalizer not supported on this device/session", e)
            _equalizerState.value = _equalizerState.value.copy(isAvailable = false)
        }
    }

    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            _equalizerState.value = _equalizerState.value.copy(isEnabled = enabled)
        } catch (e: Throwable) {
            Log.w("EqualizerController", "Failed to set equalizer enabled", e)
        }
    }

    fun setBandLevel(bandIndex: Short, level: Short) {
        try {
            equalizer?.setBandLevel(bandIndex, level)
            val updatedBands = _equalizerState.value.bands.map {
                if (it.index == bandIndex) it.copy(currentLevel = level) else it
            }
            _equalizerState.value = _equalizerState.value.copy(
                bands = updatedBands,
                currentPreset = "Custom"
            )
        } catch (e: Throwable) {
            Log.w("EqualizerController", "Failed to set band level", e)
        }
    }

    fun applyPreset(presetName: String) {
        val eq = equalizer ?: return
        val bands = _equalizerState.value.bands
        if (bands.isEmpty()) return

        try {
            val max = bands.first().maxLevel.toInt()
            val min = bands.first().minLevel.toInt()
            val step = max / 3

            val targetLevels = when (presetName) {
                "Bass Boost" -> listOf(max, (max * 0.6).toInt(), 0, 0, 0)
                "Vocal" -> listOf(-step, 0, (max * 0.8).toInt(), (max * 0.5).toInt(), -step)
                "Electronic" -> listOf((max * 0.8).toInt(), (max * 0.4).toInt(), 0, (max * 0.6).toInt(), (max * 0.9).toInt())
                "Acoustic" -> listOf((max * 0.4).toInt(), (max * 0.3).toInt(), (max * 0.2).toInt(), (max * 0.5).toInt(), (max * 0.6).toInt())
                "Rock" -> listOf((max * 0.7).toInt(), (max * 0.3).toInt(), -step, (max * 0.4).toInt(), (max * 0.8).toInt())
                "Lo-Fi" -> listOf((max * 0.5).toInt(), (max * 0.4).toInt(), 0, -step, -max)
                else -> listOf(0, 0, 0, 0, 0) // Flat
            }

            bands.forEachIndexed { i, band ->
                val level = targetLevels.getOrElse(i) { 0 }.coerceIn(min, max).toShort()
                eq.setBandLevel(band.index, level)
            }

            val updated = bands.mapIndexed { i, band ->
                val level = targetLevels.getOrElse(i) { 0 }.coerceIn(min, max).toShort()
                band.copy(currentLevel = level)
            }

            _equalizerState.value = _equalizerState.value.copy(
                currentPreset = presetName,
                bands = updated
            )
        } catch (e: Throwable) {
            Log.w("EqualizerController", "Failed to apply preset $presetName", e)
        }
    }

    fun applySoundPresetCurve(presetId: String) {
        val eq = equalizer ?: return
        val bands = _equalizerState.value.bands
        if (bands.isEmpty()) return
        try {
            val max = bands.first().maxLevel.toInt()
            val min = bands.first().minLevel.toInt()
            val targetLevels = when (presetId) {
                "SLOWED_REVERB" -> listOf((max * 0.55).toInt(), (max * 0.35).toInt(), 0, (-max * 0.25).toInt(), (-max * 0.35).toInt())
                "ECHO" -> listOf((max * 0.2).toInt(), 0, (max * 0.15).toInt(), (max * 0.4).toInt(), (max * 0.55).toInt())
                "LOFI" -> listOf((max * 0.45).toInt(), (max * 0.35).toInt(), (-max * 0.15).toInt(), (-max * 0.55).toInt(), (-max * 0.85).toInt())
                "NIGHTCORE" -> listOf((max * 0.35).toInt(), (max * 0.1).toInt(), (max * 0.2).toInt(), (max * 0.55).toInt(), (max * 0.7).toInt())
                "CONCERT" -> listOf((max * 0.75).toInt(), (max * 0.35).toInt(), (-max * 0.2).toInt(), (max * 0.45).toInt(), (max * 0.8).toInt())
                else -> listOf(0, 0, 0, 0, 0)
            }
            val shouldEnable = presetId != "NORMAL"
            eq.enabled = shouldEnable || _equalizerState.value.isEnabled
            bands.forEachIndexed { i, band ->
                val lvl = targetLevels.getOrElse(i) { 0 }.coerceIn(min, max).toShort()
                eq.setBandLevel(band.index, lvl)
            }
        } catch (e: Throwable) {
            Log.w("EqualizerController", "Failed to apply sound preset EQ curve", e)
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (e: Throwable) {
            // Ignore release exceptions
        }
        equalizer = null
    }

    companion object {
        @Volatile
        private var instance: AudioEqualizerController? = null

        fun getInstance(context: Context): AudioEqualizerController {
            return instance ?: synchronized(this) {
                instance ?: AudioEqualizerController(context.applicationContext).also { instance = it }
            }
        }
    }
}

