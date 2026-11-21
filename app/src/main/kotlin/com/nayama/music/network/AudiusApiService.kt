package com.nayama.music.network

import com.nayama.music.network.dto.AudiusResponse
import com.nayama.music.network.dto.PlaylistDto
import com.nayama.music.network.dto.TrackDto
import com.nayama.music.network.dto.UserDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AudiusApiService {

    @GET("v1/tracks/trending")
    suspend fun getTrendingTracks(
        @Query("app_name") appName: String = "NayamaMusic",
        @Query("genre") genre: String? = null,
        @Query("limit") limit: Int = 40
    ): AudiusResponse<List<TrackDto>>

    @GET("v1/tracks/search")
    suspend fun searchTracks(
        @Query("query") query: String,
        @Query("app_name") appName: String = "NayamaMusic",
        @Query("limit") limit: Int = 50
    ): AudiusResponse<List<TrackDto>>

    @GET("v1/users/search")
    suspend fun searchArtists(
        @Query("query") query: String,
        @Query("app_name") appName: String = "NayamaMusic",
        @Query("limit") limit: Int = 15
    ): AudiusResponse<List<UserDto>>

    @GET("v1/playlists/trending")
    suspend fun getTrendingPlaylists(
        @Query("app_name") appName: String = "NayamaMusic",
        @Query("limit") limit: Int = 15
    ): AudiusResponse<List<PlaylistDto>>

    @GET("v1/playlists/search")
    suspend fun searchPlaylists(
        @Query("query") query: String,
        @Query("app_name") appName: String = "NayamaMusic",
        @Query("limit") limit: Int = 15
    ): AudiusResponse<List<PlaylistDto>>

    @GET("v1/tracks/{track_id}")
    suspend fun getTrackById(
        @Path("track_id") trackId: String,
        @Query("app_name") appName: String = "NayamaMusic"
    ): AudiusResponse<TrackDto>
}

