package com.nayama.music.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

enum class RezonaDspMode {
    BYPASS,
    WARM_LPF,        // Gentle 9.5 kHz low-pass for Warm
    LOFI_LPF,        // 3.8 kHz low-pass + subtle warm saturation
    MUFFLED_LPF,     // Steep 620 Hz cascaded low-pass (Underwater / Muffled)
    RADIO_BANDPASS,  // 380 Hz - 3.4 kHz band-pass + subtle transistor drive
    VINYL_CRACKLE,   // 6.5 kHz warm LPF + gentle procedural surface dust & crackle
    ECHO_SLAPBACK,   // 215 ms stereo slapback echo tail
    CATHEDRAL_TAIL,  // 360 ms lush diffused feedback tail
    EIGHT_D_PAN      // Smooth 0.14 Hz constant-power circular stereo LFO pan
}

/**
 * Zero-allocation real-time Media3 PCM AudioProcessor implementing biquad filters,
 * stereo echo/cathedral delay lines, procedural vinyl crackle, and 8D constant-power panning.
 */
@OptIn(UnstableApi::class)
class RezonaDspAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var mode: RezonaDspMode = RezonaDspMode.BYPASS
        set(value) {
            if (field != value) {
                field = value
                pendingReset = true
            }
        }

    @Volatile
    private var pendingReset = false

    private var sampleRate = 44100
    private var channelCount = 2

    // Biquad stage 1 & 2 state (L/R)
    private var x1L = 0f; private var x2L = 0f; private var y1L = 0f; private var y2L = 0f
    private var x1R = 0f; private var x2R = 0f; private var y1R = 0f; private var y2R = 0f
    private var s2x1L = 0f; private var s2x2L = 0f; private var s2y1L = 0f; private var s2y2L = 0f
    private var s2x1R = 0f; private var s2x2R = 0f; private var s2y1R = 0f; private var s2y2R = 0f

    // Biquad coefficients
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f; private var a1 = 0f; private var a2 = 0f
    private var hpB0 = 1f; private var hpB1 = 0f; private var hpB2 = 0f; private var hpA1 = 0f; private var hpA2 = 0f

    // Delay ring buffer for Echo / Cathedral (max ~0.5s stereo at 48kHz = 48000 floats)
    private var delayBufL = FloatArray(24000)
    private var delayBufR = FloatArray(24000)
    private var delayWritePos = 0

    // Vinyl PRNG & crackle envelope (xorshift32, zero allocations)
    private var prngState = 0x52455A4E
    private var crackleEnv = 0f
    private var noiseLp = 0f

    // 8D panner phase
    private var lfoPhase = 0.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        channelCount = inputAudioFormat.channelCount.coerceIn(1, 2)
        val neededDelay = (sampleRate * 0.5f).toInt().coerceIn(8000, 48000)
        if (delayBufL.size < neededDelay) {
            delayBufL = FloatArray(neededDelay)
            delayBufR = FloatArray(neededDelay)
        }
        pendingReset = true
        return inputAudioFormat
    }

    override fun onFlush() {
        resetDspStates()
    }

    override fun onReset() {
        resetDspStates()
    }

    private fun resetDspStates() {
        x1L = 0f; x2L = 0f; y1L = 0f; y2L = 0f
        x1R = 0f; x2R = 0f; y1R = 0f; y2R = 0f
        s2x1L = 0f; s2x2L = 0f; s2y1L = 0f; s2y2L = 0f
        s2x1R = 0f; s2x2R = 0f; s2y1R = 0f; s2y2R = 0f
        delayBufL.fill(0f)
        delayBufR.fill(0f)
        delayWritePos = 0
        crackleEnv = 0f
        noiseLp = 0f
        recomputeCoefficients(mode)
        pendingReset = false
    }

    private fun recomputeCoefficients(activeMode: RezonaDspMode) {
        val sr = sampleRate.toDouble()
        when (activeMode) {
            RezonaDspMode.WARM_LPF -> setLowPass(9500.0, 0.707, sr)
            RezonaDspMode.LOFI_LPF -> setLowPass(3800.0, 0.78, sr)
            RezonaDspMode.MUFFLED_LPF -> setLowPass(620.0, 0.85, sr)
            RezonaDspMode.VINYL_CRACKLE -> setLowPass(6500.0, 0.707, sr)
            RezonaDspMode.RADIO_BANDPASS -> {
                setLowPass(3400.0, 0.85, sr)
                setHighPass(380.0, 0.85, sr)
            }
            else -> {
                b0 = 1f; b1 = 0f; b2 = 0f; a1 = 0f; a2 = 0f
                hpB0 = 1f; hpB1 = 0f; hpB2 = 0f; hpA1 = 0f; hpA2 = 0f
            }
        }
    }

    private fun setLowPass(cutoffHz: Double, q: Double, sr: Double) {
        val clampedFc = cutoffHz.coerceIn(80.0, sr * 0.45)
        val w0 = 2.0 * PI * clampedFc / sr
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        b0 = ((1.0 - cosW0) / 2.0 / a0).toFloat()
        b1 = ((1.0 - cosW0) / a0).toFloat()
        b2 = ((1.0 - cosW0) / 2.0 / a0).toFloat()
        a1 = (-2.0 * cosW0 / a0).toFloat()
        a2 = ((1.0 - alpha) / a0).toFloat()
    }

    private fun setHighPass(cutoffHz: Double, q: Double, sr: Double) {
        val clampedFc = cutoffHz.coerceIn(60.0, sr * 0.40)
        val w0 = 2.0 * PI * clampedFc / sr
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        hpB0 = ((1.0 + cosW0) / 2.0 / a0).toFloat()
        hpB1 = (-(1.0 + cosW0) / a0).toFloat()
        hpB2 = ((1.0 + cosW0) / 2.0 / a0).toFloat()
        hpA1 = (-2.0 * cosW0 / a0).toFloat()
        hpA2 = ((1.0 - alpha) / a0).toFloat()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val currentMode = mode
        if (pendingReset) {
            resetDspStates()
        }

        val out = replaceOutputBuffer(remaining)
        if (currentMode == RezonaDspMode.BYPASS) {
            out.put(inputBuffer)
            out.flip()
            return
        }

        val frameBytes = channelCount * 2
        val frames = remaining / frameBytes
        val sr = sampleRate.toDouble()
        val lfoInc = (2.0 * PI * 0.14) / sr // ~7.1s full 8D circular rotation

        val delaySamplesL = when (currentMode) {
            RezonaDspMode.ECHO_SLAPBACK -> (sampleRate * 0.215f).toInt()
            RezonaDspMode.CATHEDRAL_TAIL -> (sampleRate * 0.340f).toInt()
            else -> 0
        }.coerceIn(1, delayBufL.size - 1)
        val delaySamplesR = when (currentMode) {
            RezonaDspMode.ECHO_SLAPBACK -> (sampleRate * 0.255f).toInt()
            RezonaDspMode.CATHEDRAL_TAIL -> (sampleRate * 0.395f).toInt()
            else -> 0
        }.coerceIn(1, delayBufR.size - 1)

        for (i in 0 until frames) {
            val inL = inputBuffer.short / 32768f
            val inR = if (channelCount == 2) inputBuffer.short / 32768f else inL

            var outL = inL
            var outR = inR

            when (currentMode) {
                RezonaDspMode.WARM_LPF -> {
                    outL = runLpfL(inL) * 1.04f
                    outR = runLpfR(inR) * 1.04f
                }
                RezonaDspMode.LOFI_LPF -> {
                    val fL = runLpfL(inL)
                    val fR = runLpfR(inR)
                    outL = tanh((fL * 1.25f).toDouble()).toFloat() * 0.92f
                    outR = tanh((fR * 1.25f).toDouble()).toFloat() * 0.92f
                }
                RezonaDspMode.MUFFLED_LPF -> {
                    // Cascaded 2-stage low-pass for unmistakable underwater/muffled rolloff
                    val s1L = runLpfL(inL)
                    val s1R = runLpfR(inR)
                    outL = runLpfStage2L(s1L) * 1.15f
                    outR = runLpfStage2R(s1R) * 1.15f
                }
                RezonaDspMode.RADIO_BANDPASS -> {
                    val lpL = runLpfL(inL)
                    val lpR = runLpfR(inR)
                    val bpL = runHpStage2L(lpL)
                    val bpR = runHpStage2R(lpR)
                    val mono = (bpL + bpR) * 0.5f
                    val driven = tanh((mono * 1.75f).toDouble()).toFloat() * 0.88f
                    outL = driven
                    outR = driven
                }
                RezonaDspMode.VINYL_CRACKLE -> {
                    val fL = runLpfL(inL)
                    val fR = runLpfR(inR)
                    val crackle = nextVinylCrackleSample()
                    outL = (fL * 0.94f + crackle)
                    outR = (fR * 0.94f + crackle * 0.85f)
                }
                RezonaDspMode.ECHO_SLAPBACK, RezonaDspMode.CATHEDRAL_TAIL -> {
                    val bufSize = delayBufL.size
                    val rIdxL = (delayWritePos - delaySamplesL + bufSize) % bufSize
                    val rIdxR = (delayWritePos - delaySamplesR + bufSize) % bufSize
                    val delayedL = delayBufL[rIdxL]
                    val delayedR = delayBufR[rIdxR]
                    val feedback = if (currentMode == RezonaDspMode.ECHO_SLAPBACK) 0.34f else 0.48f
                    val wet = if (currentMode == RezonaDspMode.ECHO_SLAPBACK) 0.38f else 0.44f
                    delayBufL[delayWritePos] = (inL + delayedR * feedback).coerceIn(-1f, 1f)
                    delayBufR[delayWritePos] = (inR + delayedL * feedback).coerceIn(-1f, 1f)
                    delayWritePos = (delayWritePos + 1) % bufSize
                    outL = inL * 0.82f + delayedL * wet
                    outR = inR * 0.82f + delayedR * wet
                }
                RezonaDspMode.EIGHT_D_PAN -> {
                    // Constant-power circular stereo sweep (pan in 0..1)
                    val pan = (0.5 + 0.46 * sin(lfoPhase)).toFloat()
                    lfoPhase += lfoInc
                    if (lfoPhase >= 2.0 * PI) lfoPhase -= 2.0 * PI
                    val gainL = sqrt((1f - pan).toDouble()).toFloat()
                    val gainR = sqrt(pan.toDouble()).toFloat()
                    val mono = (inL + inR) * 0.55f
                    val diff = (inL - inR) * 0.25f
                    outL = (mono + diff) * gainL * 1.15f
                    outR = (mono - diff) * gainR * 1.15f
                }
                RezonaDspMode.BYPASS -> Unit
            }

            out.putShort((outL.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            if (channelCount == 2) {
                out.putShort((outR.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            }
        }
        out.flip()
    }

    private fun runLpfL(x: Float): Float {
        val y = b0 * x + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
        x2L = x1L; x1L = x; y2L = y1L; y1L = y
        return y
    }

    private fun runLpfR(x: Float): Float {
        val y = b0 * x + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R
        x2R = x1R; x1R = x; y2R = y1R; y1R = y
        return y
    }

    private fun runLpfStage2L(x: Float): Float {
        val y = b0 * x + b1 * s2x1L + b2 * s2x2L - a1 * s2y1L - a2 * s2y2L
        s2x2L = s2x1L; s2x1L = x; s2y2L = s2y1L; s2y1L = y
        return y
    }

    private fun runLpfStage2R(x: Float): Float {
        val y = b0 * x + b1 * s2x1R + b2 * s2x2R - a1 * s2y1R - a2 * s2y2R
        s2x2R = s2x1R; s2x1R = x; s2y2R = s2y1R; s2y1R = y
        return y
    }

    private fun runHpStage2L(x: Float): Float {
        val y = hpB0 * x + hpB1 * s2x1L + hpB2 * s2x2L - hpA1 * s2y1L - hpA2 * s2y2L
        s2x2L = s2x1L; s2x1L = x; s2y2L = s2y1L; s2y1L = y
        return y
    }

    private fun runHpStage2R(x: Float): Float {
        val y = hpB0 * x + hpB1 * s2x1R + hpB2 * s2x2R - hpA1 * s2y1R - hpA2 * s2y2R
        s2x2R = s2x1R; s2x1R = x; s2y2R = s2y1R; s2y1R = y
        return y
    }

    private fun nextVinylCrackleSample(): Float {
        var s = prngState
        s = s xor (s shl 13)
        s = s xor (s ushr 17)
        s = s xor (s shl 5)
        prngState = s
        val u = (s and 0x7FFFFFFF) / 2147483647f // 0..1
        // Soft surface hiss (low-passed warm floor)
        val white = (u * 2f - 1f) * 0.008f
        noiseLp += (white - noiseLp) * 0.12f
        // Sparse realistic vinyl dust pops
        if (u > 0.99935f) {
            crackleEnv = 0.055f + (u - 0.99935f) * 45f
        } else {
            crackleEnv *= 0.86f
        }
        return noiseLp + crackleEnv * (if ((s and 1) == 0) 1f else -1f)
    }
}
