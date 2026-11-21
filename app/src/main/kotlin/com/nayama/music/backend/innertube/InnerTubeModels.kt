package com.nayama.music.backend.innertube

import com.nayama.music.model.RezonaTrack

/**
 * YouTube Music InnerTube content type filter.
 * Note: Params are sent inside JSON POST payload, so raw base64 '==' padding is used (not URL-encoded '%3D%3D').
 */
enum class InnerTubeFilter(val label: String, val param: String?) {
    ALL("All", null),
    SONGS("Songs", "EgWKAQIIAWoKEAUQAxAEEAkQFA=="),
    VIDEOS("Videos", "EgWKAQIQAWoKEAUQAxAEEAkQFA=="),
    ALBUMS("Albums", "EgWKAQIBAWoKEAUQAxAEEAkQFA=="),
    ARTISTS("Artists", "EgWKAQIgAWoKEAUQAxAEEAkQFA=="),
    PLAYLISTS("Playlists", "EgWKAQIoAWoKEAUQAxAEEAkQFA==");

    companion object {
        fun fromLabel(label: String): InnerTubeFilter {
            return entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: ALL
        }
    }
}

data class InnerTubeSearchPage(
    val items: List<RezonaTrack>,
    val continuation: String? = null,
    val sectionCounts: Map<String, Int> = emptyMap(),
    val requestParams: String? = null
)

data class InnerTubeSection(
    val title: String,
    val items: List<RezonaTrack>,
    val browseId: String? = null
)

data class InnerTubeMoodCategory(
    val title: String,
    val browseId: String,
    val params: String? = null
)

data class InnerTubeArtistPage(
    val name: String,
    val description: String = "",
    val thumbnailUrl: String = "",
    val subscribers: String = "",
    val topSongs: List<RezonaTrack> = emptyList(),
    val albums: List<RezonaTrack> = emptyList(),
    val relatedArtists: List<RezonaTrack> = emptyList()
)

data class InnerTubeAlbumPage(
    val title: String,
    val artist: String = "",
    val year: String = "",
    val thumbnailUrl: String = "",
    val tracks: List<RezonaTrack> = emptyList()
)
