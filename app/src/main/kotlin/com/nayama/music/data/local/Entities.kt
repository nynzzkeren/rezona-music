package com.nayama.music.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val coverIndex: Int = 0,
    val isPinned: Boolean = false,
    val sourceBlend: String = "YouTube Music",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long = 180000L,
    val coverIndex: Int = 0,
    val source: String = "YouTube Music",
    val isHighRes: Boolean = true,
    val audioUrl: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["trackId"])
    ]
)
data class PlaylistTrackCrossRef(
    val playlistId: String,
    val trackId: String,
    val orderIndex: Int = 0
)

@Entity(
    tableName = "liked_tracks",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["trackId"])]
)
data class LikedTrackEntity(
    @PrimaryKey val trackId: String,
    val likedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playback_history",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["trackId"])]
)
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackId: String,
    val playedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val trackKey: String,
    val syncedLrc: String = "",
    val plainLyrics: String = "",
    val fetchedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "pinned_speed_dial",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["trackId"])]
)
data class PinnedSpeedDialEntity(
    @PrimaryKey val trackId: String,
    val pinnedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "offline_downloads",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["trackId"])]
)
data class OfflineDownloadEntity(
    @PrimaryKey val trackId: String,
    val localFilePath: String,
    val fileSizeBytes: Long,
    val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)
