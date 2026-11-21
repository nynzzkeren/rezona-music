package com.nayama.music.data

import com.nayama.music.model.Album
import com.nayama.music.model.Artist
import com.nayama.music.model.Playlist
import com.nayama.music.model.Track
import com.nayama.music.network.ApiClient
import com.nayama.music.network.dto.PlaylistDto
import com.nayama.music.network.dto.TrackDto
import com.nayama.music.network.dto.UserDto

object DataMapper {

    fun mapTrackDtoToDomain(dto: TrackDto): Track {
        val streamUrl = ApiClient.getStreamUrl(dto.id)
        val artistName = dto.user?.name?.trim()?.ifBlank { null }
            ?: dto.user?.handle?.trim()?.ifBlank { null }
            ?: "Unknown Artist"

        val albumName = when {
            !dto.mood.isNullOrBlank() -> dto.mood
            !dto.genre.isNullOrBlank() -> dto.genre
            else -> "Single"
        }

        return Track(
            id = dto.id,
            title = dto.title.trim().ifBlank { "Untitled Track" },
            artist = artistName,
            album = albumName,
            durationSeconds = dto.duration ?: 0,
            artworkUrl = dto.artwork?.bestAvailableUrl,
            streamUrl = streamUrl,
            genre = dto.genre,
            playCount = dto.playCount,
            isPlayable = true,
            sourceAttribution = "Audius Network"
        )
    }

    fun mapUserDtoToArtist(dto: UserDto): Artist {
        val displayName = dto.name.trim().ifBlank { null }
            ?: dto.handle.trim().ifBlank { null }
            ?: "Unknown Artist"

        val bioGenre = dto.bio?.trim()?.take(40)?.ifBlank { null } ?: "Electronic / Ambient"
        val count = dto.followerCount ?: 0
        val followersFormatted = if (count >= 1000) "${count / 1000}k followers" else "$count followers"

        return Artist(
            id = dto.id,
            name = displayName,
            genre = bioGenre,
            monthlyListeners = followersFormatted,
            avatarUrl = dto.profilePicture?.bestAvailableUrl
        )
    }

    fun mapPlaylistDtoToDomain(dto: PlaylistDto): Playlist {
        return Playlist(
            id = dto.id,
            title = dto.playlistName.trim().ifBlank { "Curated Playlist" },
            description = dto.description?.trim()?.ifBlank { null } ?: "Audius verified playlist",
            trackCount = dto.trackCount ?: 0,
            artworkUrl = dto.artwork?.bestAvailableUrl,
            isUserCreated = false
        )
    }
}

