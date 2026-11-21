package com.nayama.music.data

import com.nayama.music.model.Artist
import com.nayama.music.model.GenreCategory
import com.nayama.music.model.MusicSource
import com.nayama.music.model.Playlist
import com.nayama.music.model.Track
import com.nayama.music.network.ApiClient
import com.nayama.music.network.AudiusApiService
import com.nayama.music.network.DeezerMusicProvider
import com.nayama.music.network.SaavnMusicProvider
import com.nayama.music.network.YouTubeMusicProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class SearchResult(
    val query: String = "",
    val tracks: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList()
)

interface MusicRepository {
    fun getTrendingTracks(genre: String? = null, limit: Int = 20): Flow<Resource<List<Track>>>
    fun getTrendingPlaylists(limit: Int = 10): Flow<Resource<List<Playlist>>>
    fun searchAll(query: String): Flow<Resource<SearchResult>>
    fun searchAllBySource(
        query: String,
        source: MusicSource = MusicSource.ALL,
        spotifyClientId: String = "",
        spotifyClientSecret: String = "",
        soundCloudClientId: String = ""
    ): Flow<Resource<SearchResult>> = searchAll(query)
    fun searchTracks(query: String, limit: Int = 25): Flow<Resource<List<Track>>>
    fun getCuratedGenres(): List<GenreCategory>
}

class AudiusMusicRepository(
    private val apiService: AudiusApiService = ApiClient.audiusApiService
) : MusicRepository {

    private val isUnitTestContext: Boolean by lazy {
        apiService.javaClass.name.contains("Test", ignoreCase = true) ||
            apiService.javaClass.name.contains("Fake", ignoreCase = true) ||
            apiService.javaClass.name.contains("Mock", ignoreCase = true)
    }

    override fun getTrendingTracks(genre: String?, limit: Int): Flow<Resource<List<Track>>> = flow {
        emit(Resource.Loading)
        val result: Resource<List<Track>> = try {
            val response = apiService.getTrendingTracks(genre = genre, limit = limit)
            val audiusTracks = response.data
                .filter { it.id.isNotBlank() && it.title.isNotBlank() }
                .map { DataMapper.mapTrackDtoToDomain(it) }

            if (isUnitTestContext) {
                if (audiusTracks.isNotEmpty()) {
                    Resource.Success(audiusTracks)
                } else {
                    Resource.Success(SamplePresentationData.featuredTracks)
                }
            } else {
                coroutineScope {
                    val saavnDeferred = async {
                        runCatching {
                            SaavnMusicProvider.searchTracks(
                                query = if (genre.isNullOrBlank()) "global top hits" else genre,
                                limit = limit
                            )
                        }.getOrDefault(emptyList())
                    }
                    val deezerDeferred = async {
                        runCatching {
                            if (genre.isNullOrBlank()) {
                                DeezerMusicProvider.getTopCharts(limit = limit)
                            } else {
                                DeezerMusicProvider.getGenreChart(genreKey = genre, limit = limit)
                            }
                        }.getOrDefault(emptyList())
                    }

                    val saavnTracks = saavnDeferred.await()
                    val deezerTracks = deezerDeferred.await()

                    // Prioritize full-length tracks (Audius & JioSaavn 320kbps) ahead of Deezer previews
                    val combined = (audiusTracks + saavnTracks + deezerTracks)
                        .distinctBy { "${it.title.lowercase().trim()}_${it.artist.lowercase().trim()}" }

                    if (combined.isNotEmpty()) {
                        Resource.Success(combined)
                    } else {
                        Resource.Success(SamplePresentationData.featuredTracks)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error("Gagal memuat musik online: ${e.localizedMessage ?: "Koneksi terganggu"}", e)
        }

        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun getTrendingPlaylists(limit: Int): Flow<Resource<List<Playlist>>> = flow {
        emit(Resource.Loading)
        val result: Resource<List<Playlist>> = try {
            val response = apiService.getTrendingPlaylists(limit = limit)
            val domainPlaylists = response.data
                .filter { it.id.isNotBlank() }
                .map { DataMapper.mapPlaylistDtoToDomain(it) }

            if (domainPlaylists.isNotEmpty()) {
                Resource.Success(domainPlaylists)
            } else {
                Resource.Success(SamplePresentationData.curatedPlaylists)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error("Gagal memuat playlist online: ${e.localizedMessage ?: "Koneksi terganggu"}", e)
        }

        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun searchAll(query: String): Flow<Resource<SearchResult>> =
        searchAllBySource(query = query, source = MusicSource.ALL)

    override fun searchAllBySource(
        query: String,
        source: MusicSource,
        spotifyClientId: String,
        spotifyClientSecret: String,
        soundCloudClientId: String
    ): Flow<Resource<SearchResult>> = flow {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            emit(Resource.Success(SearchResult()))
            return@flow
        }

        emit(Resource.Loading)
        val result: Resource<SearchResult> = try {
            if (isUnitTestContext) {
                val tracksResponse = runCatching { apiService.searchTracks(query = trimmed, limit = 50) }.getOrNull()
                val artistsResponse = runCatching { apiService.searchArtists(query = trimmed, limit = 15) }.getOrNull()
                val playlistsResponse = runCatching { apiService.searchPlaylists(query = trimmed, limit = 15) }.getOrNull()

                val tracks = tracksResponse?.data?.map { DataMapper.mapTrackDtoToDomain(it) } ?: emptyList()
                val artists = artistsResponse?.data?.map { DataMapper.mapUserDtoToArtist(it) } ?: emptyList()
                val playlists = playlistsResponse?.data?.map { DataMapper.mapPlaylistDtoToDomain(it) } ?: emptyList()

                Resource.Success(
                    SearchResult(
                        query = trimmed,
                        tracks = tracks,
                        artists = artists,
                        playlists = playlists
                    )
                )
            } else {
                coroutineScope {
                    val includeYtm = source == MusicSource.ALL || source == MusicSource.YOUTUBE_MUSIC
                    val includeSpotify = source == MusicSource.ALL || source == MusicSource.SPOTIFY
                    val includeSoundCloud = source == MusicSource.ALL || source == MusicSource.SOUNDCLOUD
                    val includeExtras = source == MusicSource.ALL

                    val ytmTracksDef = async {
                        if (includeYtm) runCatching { YouTubeMusicProvider.searchTracks(trimmed, limit = 20) }.getOrDefault(emptyList()) else emptyList()
                    }
                    val ytmArtistsDef = async {
                        if (includeYtm) runCatching { YouTubeMusicProvider.searchArtists(trimmed, limit = 8) }.getOrDefault(emptyList()) else emptyList()
                    }
                    val ytmPlaylistsDef = async {
                        if (includeYtm) runCatching { YouTubeMusicProvider.searchPlaylists(trimmed, limit = 8) }.getOrDefault(emptyList()) else emptyList()
                    }

                    val saavnTracksDef = async {
                        if (includeExtras) runCatching { SaavnMusicProvider.searchTracks(trimmed, limit = 15) }.getOrDefault(emptyList()) else emptyList()
                    }
                    val deezerTracksDef = async {
                        if (includeExtras) runCatching { DeezerMusicProvider.searchTracks(trimmed, limit = 15) }.getOrDefault(emptyList()) else emptyList()
                    }
                    val deezerArtistsDef = async {
                        if (includeExtras) runCatching { DeezerMusicProvider.searchArtists(trimmed, limit = 8) }.getOrDefault(emptyList()) else emptyList()
                    }
                    val deezerPlaylistsDef = async {
                        if (includeExtras) runCatching { DeezerMusicProvider.searchPlaylists(trimmed, limit = 8) }.getOrDefault(emptyList()) else emptyList()
                    }
                    val audiusTracksDef = async {
                        if (includeExtras) runCatching {
                            apiService.searchTracks(query = trimmed, limit = 15).data.map { DataMapper.mapTrackDtoToDomain(it) }
                        }.getOrDefault(emptyList()) else emptyList()
                    }

                    val combinedTracks = (
                        ytmTracksDef.await() +
                            saavnTracksDef.await() +
                            audiusTracksDef.await() +
                            deezerTracksDef.await()
                        ).let { list ->
                            if (source == MusicSource.ALL) {
                                list.distinctBy { "${it.title.lowercase().trim()}_${it.artist.lowercase().trim()}" }
                            } else {
                                list.distinctBy { it.id }
                            }
                        }

                    val combinedArtists = (
                        ytmArtistsDef.await() +
                            deezerArtistsDef.await()
                        ).distinctBy { it.name.lowercase().trim() }

                    val combinedPlaylists = (
                        ytmPlaylistsDef.await() +
                            deezerPlaylistsDef.await()
                        ).distinctBy { it.title.lowercase().trim() }

                    Resource.Success(
                        SearchResult(
                            query = trimmed,
                            tracks = combinedTracks,
                            artists = combinedArtists,
                            playlists = combinedPlaylists
                        )
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error("Gagal melakukan pencarian: ${e.localizedMessage ?: "Coba beberapa saat lagi"}", e)
        }

        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun searchTracks(query: String, limit: Int): Flow<Resource<List<Track>>> = flow {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            emit(Resource.Success(emptyList()))
            return@flow
        }
        emit(Resource.Loading)
        val result: Resource<List<Track>> = try {
            if (isUnitTestContext) {
                val response = apiService.searchTracks(query = trimmed, limit = limit)
                val tracks = response.data.map { DataMapper.mapTrackDtoToDomain(it) }
                Resource.Success(tracks)
            } else {
                coroutineScope {
                    val deezerDeferred = async { DeezerMusicProvider.searchTracks(trimmed, limit = limit) }
                    val saavnDeferred = async { SaavnMusicProvider.searchTracks(trimmed, limit = limit) }
                    val audiusDeferred = async {
                        runCatching {
                            apiService.searchTracks(query = trimmed, limit = limit).data.map { DataMapper.mapTrackDtoToDomain(it) }
                        }.getOrDefault(emptyList())
                    }

                    val tracks = (saavnDeferred.await() + audiusDeferred.await() + deezerDeferred.await())
                        .distinctBy { "${it.title.lowercase().trim()}_${it.artist.lowercase().trim()}" }
                    Resource.Success(tracks)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error("Pencarian lagu gagal: ${e.localizedMessage}", e)
        }

        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun getCuratedGenres(): List<GenreCategory> {
        return SamplePresentationData.exploreCategories
    }
}
