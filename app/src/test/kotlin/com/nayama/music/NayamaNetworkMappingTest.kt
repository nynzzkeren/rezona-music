package com.nayama.music

import com.nayama.music.data.AudiusMusicRepository
import com.nayama.music.data.DataMapper
import com.nayama.music.data.Resource
import com.nayama.music.network.AudiusApiService
import com.nayama.music.network.dto.ArtworkDto
import com.nayama.music.network.dto.AudiusResponse
import com.nayama.music.network.dto.PlaylistDto
import com.nayama.music.network.dto.TrackDto
import com.nayama.music.network.dto.UserDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NayamaNetworkMappingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun testRealApiClientRetrofitInitialization() {
        val service = com.nayama.music.network.ApiClient.audiusApiService
        assertNotNull(service)
    }

    @Test
    fun testHomeViewModelCreation() {
        val vm = com.nayama.music.ui.home.HomeViewModel()
        assertNotNull(vm)
    }

    @Test
    fun testJsonParsingOfAudiusTrackResponse() {
        val sampleJson = """
            {
                "data": [
                    {
                        "id": "trk_001",
                        "title": "Minimal Echoes",
                        "duration": 215,
                        "genre": "Ambient",
                        "mood": "Peaceful",
                        "play_count": 9482,
                        "artwork": {
                            "150x150": "https://audius.co/art_150.jpg",
                            "480x480": "https://audius.co/art_480.jpg"
                        },
                        "user": {
                            "id": "usr_99",
                            "name": "Komorebi",
                            "handle": "komorebi_ambient",
                            "follower_count": 1420
                        },
                        "unknown_future_field": "safe_to_ignore"
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<AudiusResponse<List<TrackDto>>>(sampleJson)
        assertEquals(1, response.data.size)
        val trackDto = response.data.first()
        assertEquals("trk_001", trackDto.id)
        assertEquals("Minimal Echoes", trackDto.title)
        assertEquals(215, trackDto.duration)
        assertEquals("Ambient", trackDto.genre)
        assertEquals("https://audius.co/art_480.jpg", trackDto.artwork?.bestAvailableUrl)
        assertEquals("Komorebi", trackDto.user?.name)
    }

    @Test
    fun testDataMapperTrackDtoToDomain() {
        val dto = TrackDto(
            id = "audius_abc123",
            title = "Midnight Tape",
            duration = 188,
            genre = "Lo-Fi",
            mood = "Mellow",
            playCount = 12000,
            artwork = ArtworkDto(medium = "https://cdn.nayama.io/art.jpg"),
            user = UserDto(name = "Tape Master", handle = "tapemaster")
        )

        val domainTrack = DataMapper.mapTrackDtoToDomain(dto)
        assertEquals("audius_abc123", domainTrack.id)
        assertEquals("Midnight Tape", domainTrack.title)
        assertEquals("Tape Master", domainTrack.artist)
        assertEquals("Mellow", domainTrack.album)
        assertEquals(188, domainTrack.durationSeconds)
        assertEquals("3:08", domainTrack.formattedDuration)
        assertEquals("https://cdn.nayama.io/art.jpg", domainTrack.artworkUrl)
        assertNotNull(domainTrack.streamUrl)
        assertTrue(domainTrack.streamUrl!!.contains("audius_abc123"))
        assertEquals("Audius Network", domainTrack.sourceAttribution)
    }

    @Test
    fun testDataMapperUserDtoToArtist() {
        val dto = UserDto(
            id = "usr_42",
            name = "Acoustic Mirage",
            handle = "mirage_sound",
            bio = "Minimalist neoclassical textures",
            followerCount = 8520,
            profilePicture = ArtworkDto(large = "https://cdn.nayama.io/avatar.jpg")
        )

        val domainArtist = DataMapper.mapUserDtoToArtist(dto)
        assertEquals("usr_42", domainArtist.id)
        assertEquals("Acoustic Mirage", domainArtist.name)
        assertEquals("Minimalist neoclassical textures", domainArtist.genre)
        assertEquals("8k followers", domainArtist.monthlyListeners)
        assertEquals("https://cdn.nayama.io/avatar.jpg", domainArtist.avatarUrl)
    }

    @Test
    fun testDataMapperPlaylistDtoToDomain() {
        val dto = PlaylistDto(
            id = "pl_999",
            playlistName = "Late Night Tokyo",
            description = "Subtle synth lines for late night journeys",
            trackCount = 32,
            artwork = ArtworkDto(medium = "https://cdn.nayama.io/playlist.jpg")
        )

        val domainPlaylist = DataMapper.mapPlaylistDtoToDomain(dto)
        assertEquals("pl_999", domainPlaylist.id)
        assertEquals("Late Night Tokyo", domainPlaylist.title)
        assertEquals("Subtle synth lines for late night journeys", domainPlaylist.description)
        assertEquals(32, domainPlaylist.trackCount)
        assertEquals("https://cdn.nayama.io/playlist.jpg", domainPlaylist.artworkUrl)
    }

    @Test
    fun testRepositoryWithFakeService() = runBlocking {
        val fakeService = object : AudiusApiService {
            override suspend fun getTrendingTracks(appName: String, genre: String?, limit: Int): AudiusResponse<List<TrackDto>> {
                return AudiusResponse(
                    listOf(
                        TrackDto(id = "trk_1", title = "Track One", duration = 120),
                        TrackDto(id = "trk_2", title = "Track Two", duration = 240)
                    )
                )
            }

            override suspend fun searchTracks(query: String, appName: String, limit: Int): AudiusResponse<List<TrackDto>> {
                return AudiusResponse(listOf(TrackDto(id = "trk_res", title = "Found $query")))
            }

            override suspend fun searchArtists(query: String, appName: String, limit: Int): AudiusResponse<List<UserDto>> {
                return AudiusResponse(listOf(UserDto(id = "usr_res", name = "Artist $query")))
            }

            override suspend fun getTrendingPlaylists(appName: String, limit: Int): AudiusResponse<List<PlaylistDto>> {
                return AudiusResponse(listOf(PlaylistDto(id = "pl_1", playlistName = "Trending Playlist")))
            }

            override suspend fun searchPlaylists(query: String, appName: String, limit: Int): AudiusResponse<List<PlaylistDto>> {
                return AudiusResponse(listOf(PlaylistDto(id = "pl_res", playlistName = "Playlist $query")))
            }

            override suspend fun getTrackById(trackId: String, appName: String): AudiusResponse<TrackDto> {
                return AudiusResponse(TrackDto(id = trackId, title = "Specific Track"))
            }
        }

        val repository = AudiusMusicRepository(apiService = fakeService)

        // Test trending tracks
        val trendingResult = repository.getTrendingTracks().last()
        assertTrue(trendingResult is Resource.Success)
        val tracks = (trendingResult as Resource.Success).data
        assertEquals(2, tracks.size)
        assertEquals("Track One", tracks[0].title)

        // Test search all
        val searchResult = repository.searchAll("Lofi").last()
        assertTrue(searchResult is Resource.Success)
        val data = (searchResult as Resource.Success).data
        assertEquals("Lofi", data.query)
        assertEquals(1, data.tracks.size)
        assertEquals(1, data.artists.size)
        assertEquals(1, data.playlists.size)
        assertEquals("Found Lofi", data.tracks.first().title)
    }

    @Test
    fun testRepositoryErrorHandling() = runBlocking {
        val throwingService = object : AudiusApiService {
            override suspend fun getTrendingTracks(appName: String, genre: String?, limit: Int): AudiusResponse<List<TrackDto>> {
                throw java.io.IOException("Network connection timeout")
            }

            override suspend fun searchTracks(query: String, appName: String, limit: Int): AudiusResponse<List<TrackDto>> {
                throw java.net.UnknownHostException("No host found")
            }

            override suspend fun searchArtists(query: String, appName: String, limit: Int): AudiusResponse<List<UserDto>> {
                throw RuntimeException()
            }

            override suspend fun getTrendingPlaylists(appName: String, limit: Int): AudiusResponse<List<PlaylistDto>> {
                throw java.io.IOException("Service unavailable")
            }

            override suspend fun searchPlaylists(query: String, appName: String, limit: Int): AudiusResponse<List<PlaylistDto>> {
                throw RuntimeException()
            }

            override suspend fun getTrackById(trackId: String, appName: String): AudiusResponse<TrackDto> {
                throw RuntimeException()
            }
        }

        val repository = AudiusMusicRepository(apiService = throwingService)

        val result = repository.getTrendingTracks().last()
        assertTrue(result is Resource.Error)
        val error = result as Resource.Error
        assertTrue(error.message.contains("Gagal memuat musik online"))
    }
}

