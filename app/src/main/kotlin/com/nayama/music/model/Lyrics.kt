package com.nayama.music.model

data class WordToken(
    val timeMs: Long,
    val text: String
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<WordToken> = emptyList()
)

data class Lyrics(
    val trackId: String,
    val trackName: String,
    val artistName: String,
    val isSynced: Boolean,
    val lines: List<LyricLine>,
    val plainLyrics: String? = null,
    val sourceAttribution: String = "LrcLib Open Database",
    val hasWordSync: Boolean = false
)
