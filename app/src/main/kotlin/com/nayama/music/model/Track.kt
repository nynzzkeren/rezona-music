package com.nayama.music.model

enum class MusicSource(val displayName: String, val shortBadge: String) {
    ALL("Semua", "ALL"),
    YOUTUBE_MUSIC("YT Music", "YTM"),
    SPOTIFY("Spotify", "SPT"),
    SOUNDCLOUD("SoundCloud", "SC"),
    SAAVN("JioSaavn HD", "HD"),
    AUDIUS("Audius", "AUD"),
    DEEZER("Deezer", "DZR")
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val artworkUrl: String? = null,
    val streamUrl: String? = null,
    val genre: String? = null,
    val playCount: Long? = null,
    val isPlayable: Boolean = true,
    val sourceAttribution: String = "Authorized Provider",
    val source: MusicSource = MusicSource.AUDIUS,
    val audioQualityLabel: String = "AAC 320 KBPS"
) {
    val formattedDuration: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}
