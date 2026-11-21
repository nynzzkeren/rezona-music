package com.nayama.music.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.sin

/**
 * Focus Rooms Procedural Ambient Engine.
 * Generates continuous soothing acoustic textures in real-time code without audio files:
 * 1. Rain on Glass (filtered pink noise + sporadic raindrop bursts)
 * 2. Cafe Murmur (warm low-pass multi-oscillator hum)
 * 3. Pink Static / Air (1/f spectral decay noise)
 * 4. Midnight Ocean (cycloid-modulated deep ambient wash)
 * 5. Vinyl Crackle (micro-impulse Poisson crackle & surface hiss)
 *
 * Runs on a low-priority thread streaming 16-bit PCM to an AudioTrack.
 */
class ProceduralAmbientEngine(
    private val scope: CoroutineScope
) {
    private var audioTrack: AudioTrack? = null
    private var generatorJob: Job? = null
    private val random = Random()

    // Independent layer volumes (0.0f .. 1.0f)
    var rainVolume = 0.0f
    var cafeVolume = 0.0f
    var staticVolume = 0.0f
    var oceanVolume = 0.0f
    var vinylVolume = 0.0f
    var masterVolume = 0.70f

    val isRunning: Boolean
        get() = generatorJob?.isActive == true

    fun setLayerVolume(layer: AmbientLayer, volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        when (layer) {
            AmbientLayer.RAIN -> rainVolume = clamped
            AmbientLayer.CAFE -> cafeVolume = clamped
            AmbientLayer.STATIC -> staticVolume = clamped
            AmbientLayer.OCEAN -> oceanVolume = clamped
            AmbientLayer.VINYL -> vinylVolume = clamped
        }
        checkAutoStartStop()
    }

    private fun checkAutoStartStop() {
        val anyActive = (rainVolume + cafeVolume + staticVolume + oceanVolume + vinylVolume) > 0.001f
        if (anyActive && !isRunning) {
            start()
        } else if (!anyActive && isRunning) {
            stop()
        }
    }

    @Synchronized
    fun start() {
        if (isRunning) return

        try {
            val sampleRate = 22050
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = (minBufSize * 2).coerceAtLeast(4096)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .setEncoding(audioFormat)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()

            generatorJob = scope.launch(Dispatchers.Default) {
                renderAudioLoop(sampleRate, bufferSize / 2)
            }
        } catch (e: Throwable) {
            Log.w("AmbientEngine", "Failed to initialize procedural ambient AudioTrack", e)
        }
    }

    @Synchronized
    fun stop() {
        generatorJob?.cancel()
        generatorJob = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Throwable) {}
        audioTrack = null
    }

    private fun renderAudioLoop(sampleRate: Int, chunkSize: Int) {
        val buffer = ShortArray(chunkSize)

        // Pink noise generator states (Voss-McCartney approximation)
        var b0 = 0.0; var b1 = 0.0; var b2 = 0.0; var b3 = 0.0; var b4 = 0.0; var b5 = 0.0; var b6 = 0.0
        var oceanPhase = 0.0
        var cafePhase = 0.0
        var rainFilterState = 0.0

        val twoPi = Math.PI * 2.0
        val oceanFreq = 0.14 // ~7 seconds per ocean wave cycle
        val cafeFreq = 110.0 // warm 110Hz body

        while (scope.isActive && audioTrack != null) {
            val vr = rainVolume * masterVolume
            val vc = cafeVolume * masterVolume
            val vs = staticVolume * masterVolume
            val vo = oceanVolume * masterVolume
            val vv = vinylVolume * masterVolume

            for (i in 0 until chunkSize) {
                val white = (random.nextDouble() * 2.0 - 1.0)

                // 1. Pink noise generator
                b0 = 0.99886 * b0 + white * 0.0555179
                b1 = 0.99332 * b1 + white * 0.0750759
                b2 = 0.96900 * b2 + white * 0.1538520
                b3 = 0.86650 * b3 + white * 0.3104856
                b4 = 0.55000 * b4 + white * 0.5329522
                b5 = -0.7616 * b5 - white * 0.0168980
                val pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362) * 0.11
                b6 = white * 0.115926

                var sample = 0.0

                // ── Pink Static ──────────────────────────────
                if (vs > 0f) {
                    sample += pink * vs * 0.35
                }

                // ── Rain on Glass ───────────────────────────
                if (vr > 0f) {
                    rainFilterState = 0.82 * rainFilterState + 0.18 * pink
                    var rainDrop = 0.0
                    if (random.nextInt(180) == 0) {
                        rainDrop = (random.nextDouble() * 0.45 + 0.2) * (if (random.nextBoolean()) 1.0 else -1.0)
                    }
                    sample += (rainFilterState * 0.4 + rainDrop) * vr
                }

                // ── Midnight Ocean ──────────────────────────
                if (vo > 0f) {
                    oceanPhase += (twoPi * oceanFreq) / sampleRate
                    if (oceanPhase > twoPi) oceanPhase -= twoPi
                    val waveMod = (sin(oceanPhase) * 0.5 + 0.5)
                    val waveModShaped = waveMod * waveMod // cycloid crest
                    sample += pink * waveModShaped * vo * 0.70
                }

                // ── Cafe Murmur ─────────────────────────────
                if (vc > 0f) {
                    cafePhase += (twoPi * cafeFreq) / sampleRate
                    if (cafePhase > twoPi) cafePhase -= twoPi
                    val murmurLow = sin(cafePhase) * 0.15
                    sample += (murmurLow + pink * 0.2) * vc * 0.5
                }

                // ── Vinyl Crackle ───────────────────────────
                if (vv > 0f) {
                    var pop = 0.0
                    if (random.nextInt(650) == 0) {
                        pop = (random.nextDouble() * 0.8 + 0.2) * (if (random.nextBoolean()) 1.0 else -1.0)
                    }
                    sample += (white * 0.04 + pop) * vv
                }

                // Clamp to 16-bit PCM range
                val clamped = (sample * 32767.0).coerceIn(-32767.0, 32767.0).toInt().toShort()
                buffer[i] = clamped
            }

            try {
                audioTrack?.write(buffer, 0, chunkSize)
            } catch (_: Throwable) {
                break
            }
        }
    }
}

enum class AmbientLayer(val displayName: String, val description: String) {
    RAIN("Rain on Glass", "Gentle droplets cascading on transparent pane"),
    CAFE("Acoustic Cafe", "Warm low-frequency room presence and murmurs"),
    STATIC("Pure Air (Pink)", "Balanced 1/f spectral noise for deep focus"),
    OCEAN("Midnight Ocean", "Slow cycloid waves washing across pebble beach"),
    VINYL("Vinyl Crackle", "Warm analogue surface dust and subtle pops")
}
