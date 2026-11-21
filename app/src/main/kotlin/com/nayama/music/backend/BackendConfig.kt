package com.nayama.music.backend

/**
 * Backend Feature Flags & Configuration.
 * Rezona is powered exclusively by YouTube Music (InnerTube data layer + NewPipe stream resolution)
 * and LRCLIB for synced lyrics.
 */
object BackendConfig {
    const val ENABLE_UNOFFICIAL_PROVIDERS: Boolean = true

    const val ENABLE_YOUTUBE_MUSIC: Boolean = true
    const val ENABLE_LRCLIB_LYRICS: Boolean = true

    // Only enabled in debug mode; production behavior strictly uses real fetched data
    var ENABLE_MOCK_FALLBACK: Boolean = false

    // Stream cache expiry buffer (YouTube signed URLs typically expire after 6 hours)
    const val STREAM_URL_EXPIRY_MS: Long = 4 * 60 * 60 * 1000L // 4 hours
}
