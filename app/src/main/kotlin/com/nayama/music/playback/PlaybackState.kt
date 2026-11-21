package com.nayama.music.playback

import com.nayama.music.model.Track

enum class RepeatMode {
    OFF, ALL, ONE;

    fun next(): RepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }
}

data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Track> = emptyList(),
    val errorMessage: String? = null,
    val endedTrackId: String? = null,
    val endedEventSeq: Long = 0L,
    val errorEventSeq: Long = 0L,
    val sleepTimerRemainingSeconds: Int? = null,
    val audioSessionId: Int? = null
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedCurrentPosition: String
        get() = formatTime(currentPositionMs / 1000)

    val formattedTotalDuration: String
        get() = if (durationMs > 0) formatTime(durationMs / 1000) else currentTrack?.formattedDuration ?: "0:00"

    private fun formatTime(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%d:%02d".format(m, s)
    }
}
