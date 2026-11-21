package com.nayama.music.ui.player

import kotlin.math.abs
import kotlin.math.sin

data class TrackSection(
    val name: String,
    val startFraction: Float,
    val endFraction: Float
)

object WaveformGenerator {
    val SECTIONS = listOf(
        TrackSection("Intro", 0.00f, 0.15f),
        TrackSection("Verse", 0.15f, 0.45f),
        TrackSection("Chorus", 0.45f, 0.75f),
        TrackSection("Drop", 0.75f, 0.90f),
        TrackSection("Outro", 0.90f, 1.00f)
    )

    fun getSectionAtFraction(fraction: Float): TrackSection {
        return SECTIONS.firstOrNull { fraction in it.startFraction..it.endFraction }
            ?: SECTIONS.last()
    }

    /**
     * Generates a deterministic list of bar amplitudes for the track.
     * Normalized between 0.15f and 1.0f.
     */
    fun generateBars(trackIndex: Int, count: Int = 44): List<Float> {
        val seed = (trackIndex + 1) * 31
        return (0 until count).map { i ->
            val angle1 = (i + seed) * 0.35
            val angle2 = (i * 2 + seed) * 0.18
            val raw = abs(sin(angle1) * 0.65 + sin(angle2) * 0.35).toFloat()
            // Shape so chorus & drop have highest peaks
            val frac = i.toFloat() / count.toFloat()
            val sectionBoost = when {
                frac in 0.45f..0.75f -> 1.25f // Chorus peak
                frac in 0.75f..0.90f -> 1.35f // Drop peak
                frac < 0.15f -> 0.75f        // Intro quiet
                else -> 1.0f
            }
            (raw * sectionBoost).coerceIn(0.18f, 1.0f)
        }
    }
}
