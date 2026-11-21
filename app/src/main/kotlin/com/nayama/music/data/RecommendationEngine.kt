package com.nayama.music.data

import android.content.Context
import com.nayama.music.data.local.ListeningStatsCalculator
import com.nayama.music.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

data class RecommendationResult(
    val title: String,
    val subtitle: String,
    val tracks: List<Track>,
    val basedOnGenre: String?
)

class RecommendationEngine(
    private val context: Context,
    private val repository: MusicRepository = AudiusMusicRepository()
) {
    private val statsCalculator = ListeningStatsCalculator(context)

    suspend fun getPersonalizedRecommendations(): RecommendationResult = withContext(Dispatchers.IO) {
        val stats = statsCalculator.computeStats()

        val targetGenre = if (stats.totalTracksPlayed > 0 && stats.topGenre.isNotBlank() && stats.topGenre != "Belum Ada") {
            stats.topGenre
        } else {
            null
        }

        var tracks: List<Track> = emptyList()
        try {
            repository.getTrendingTracks(genre = targetGenre, limit = 15).collect { result ->
                if (result is Resource.Success && result.data.isNotEmpty()) {
                    tracks = result.data
                }
            }
        } catch (_: Throwable) {}

        if (tracks.isEmpty()) {
            tracks = SamplePresentationData.featuredTracks
        }

        if (targetGenre != null) {
            RecommendationResult(
                title = "RECOMMENDED FOR YOU",
                subtitle = "Berdasarkan riwayat genre: $targetGenre",
                tracks = tracks,
                basedOnGenre = targetGenre
            )
        } else {
            RecommendationResult(
                title = "POPULAR DISCOVERIES",
                subtitle = "Trek pilihan untuk memulai koleksi musik Anda",
                tracks = tracks,
                basedOnGenre = null
            )
        }
    }
}

