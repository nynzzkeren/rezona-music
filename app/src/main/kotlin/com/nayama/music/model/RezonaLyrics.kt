package com.nayama.music.model

data class LyricWord(
    val word: String,
    val startMs: Long,
    val endMs: Long
)

data class SyncedLyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord>? = null
)

object RezonaLyricsRepository {
    private fun createLine(timeMs: Long, text: String, durationMs: Long): SyncedLyricLine {
        val wordTokens = text.split(" ").filter { it.isNotBlank() }
        val wordDuration = durationMs / wordTokens.size.coerceAtLeast(1)
        val words = wordTokens.mapIndexed { i, w ->
            val s = timeMs + (i * wordDuration)
            val e = s + (wordDuration * 0.90f).toLong()
            LyricWord(word = w, startMs = s, endMs = e)
        }
        return SyncedLyricLine(timeMs = timeMs, text = text, words = words)
    }

    private val DEFAULT_LYRICS = listOf(
        createLine(4000L, "Frequencies carving through obsidian silence", 10000L),
        createLine(14000L, "Light refracts on milled glass boundaries", 12000L),
        createLine(26000L, "We dissolve into the low-end resonance", 12000L),
        createLine(38000L, "Hear the slow decay of magnetic tape", 14000L),
        createLine(52000L, "A pulse awakens beneath the dark floor", 16000L),
        createLine(68000L, "Drifting past the acoustic event horizon", 16000L),
        createLine(84000L, "Every whisper caught in the plate reverb", 18000L),
        createLine(102000L, "Synchronized breath in an empty cathedral", 22000L),
        createLine(124000L, "The timbre deepens into tactile warmth", 24000L),
        createLine(148000L, "Suspended in pure monochrome light", 24000L),
        createLine(172000L, "Sub-bass currents anchor the drift", 26000L),
        createLine(198000L, "Fading into stillness, carved from sound", 30000L)
    )

    fun getLyricsForTrack(trackId: String): List<SyncedLyricLine> {
        return DEFAULT_LYRICS
    }
}
