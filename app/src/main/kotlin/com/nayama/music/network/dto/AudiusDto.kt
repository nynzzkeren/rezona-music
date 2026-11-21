package com.nayama.music.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AudiusResponse<T>(
    val data: T
)

@Serializable
data class ArtworkDto(
    @SerialName("150x150") val small: String? = null,
    @SerialName("480x480") val medium: String? = null,
    @SerialName("1000x1000") val large: String? = null
) {
    val bestAvailableUrl: String?
        get() = medium ?: large ?: small
}

@Serializable
data class UserDto(
    val id: String = "",
    val name: String = "",
    val handle: String = "",
    val bio: String? = null,
    @SerialName("follower_count") val followerCount: Long? = 0,
    @SerialName("profile_picture") val profilePicture: ArtworkDto? = null
)

@Serializable
data class TrackDto(
    val id: String = "",
    val title: String = "Untitled Track",
    val duration: Int? = 0,
    val genre: String? = null,
    val mood: String? = null,
    @SerialName("play_count") val playCount: Long? = 0,
    val artwork: ArtworkDto? = null,
    val user: UserDto? = null
)

@Serializable
data class PlaylistDto(
    val id: String = "",
    @SerialName("playlist_name") val playlistName: String = "Curated Playlist",
    val description: String? = null,
    @SerialName("total_play_count") val totalPlayCount: Long? = 0,
    @SerialName("track_count") val trackCount: Int? = 0,
    val artwork: ArtworkDto? = null,
    val user: UserDto? = null
)

