package com.nayama.music.network

object AudiusStreamResolver {

    private val mirrors = listOf(
        "https://discoveryprovider.audius.co/",
        "https://audius-creator-2.theblueprint.xyz/",
        "https://v.monophonic.digital/",
        "https://audius-content-9.figment.io/",
        "https://audius-01.staked.cloud/"
    )

    fun resolvePrimaryStreamUrl(trackId: String): String {
        return "${mirrors.first()}v1/tracks/$trackId/stream?app_name=NayamaMusic"
    }

    fun resolveFallbackStreamUrl(trackId: String, attempt: Int): String {
        val mirrorIndex = attempt.coerceAtLeast(0) % mirrors.size
        return "${mirrors[mirrorIndex]}v1/tracks/$trackId/stream?app_name=NayamaMusic"
    }
}

