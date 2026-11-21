package com.nayama.music.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nayama.music.R
import com.nayama.music.data.Resource
import com.nayama.music.data.SamplePresentationData
import com.nayama.music.data.local.UserPreferencesRepository
import com.nayama.music.model.Album
import com.nayama.music.model.Artist
import com.nayama.music.model.MusicSource
import com.nayama.music.model.Playlist
import com.nayama.music.model.Track
import com.nayama.music.ui.components.GlassPillChip
import com.nayama.music.ui.components.MonochromeEmptyView
import com.nayama.music.ui.components.MonochromeErrorView
import com.nayama.music.ui.components.MonochromeLoadingIndicator
import com.nayama.music.ui.components.NayamaImage
import com.nayama.music.ui.components.SectionHeader
import com.nayama.music.ui.components.TrackListItem
import com.nayama.music.ui.theme.CoralAccent
import com.nayama.music.ui.theme.DeepCharcoal
import com.nayama.music.ui.theme.GlassBorder
import com.nayama.music.ui.theme.GraphiteSurface
import com.nayama.music.ui.theme.ObsidianBlack
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver

/**
 * Search Screen Matching Video Frame 38:
 * "Cari lagu, ga pake bingung. Terakhir diputar, paling sering, sampai genre. Semuanya udah disiapin."
 * - Search bar with clear button & phonetic icon
 * - Horizontal quick cards
 * - "Your most played" numbered ranking #1 to #6
 * - "Your favourite artists" carousel
 * - Floating Coral Voice Search / Mic FAB
 */
@Composable
fun SearchScreen(
    onTrackSelect: (Track) -> Unit,
    onAlbumSelect: (Album) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefsRepo = remember { UserPreferencesRepository.getInstance(context) }
    val userPrefs by prefsRepo.preferences.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(
        userPrefs.spotifyClientId,
        userPrefs.spotifyClientSecret,
        userPrefs.soundCloudClientId
    ) {
        viewModel.updateCredentials(
            spotifyId = userPrefs.spotifyClientId,
            spotifySecret = userPrefs.spotifyClientSecret,
            scId = userPrefs.soundCloudClientId
        )
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    val sourceOptions = listOf(
        MusicSource.ALL,
        MusicSource.YOUTUBE_MUSIC,
        MusicSource.SPOTIFY,
        MusicSource.SOUNDCLOUD
    )

    val filterOptions = listOf(
        SearchCategoryFilter.ALL to "Semua",
        SearchCategoryFilter.TRACKS to "Lagu",
        SearchCategoryFilter.ARTISTS to "Artis",
        SearchCategoryFilter.PLAYLISTS to "Playlist"
    )

    Box(modifier = modifier.fillMaxSize().background(Color.Transparent)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            // ── 1. Search Bar (Frame 38) ──────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(GraphiteSurface.copy(alpha = 0.9f))
                        .border(1.dp, GlassBorder, RoundedCornerShape(32.dp))
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_nav_search),
                        contentDescription = "Cari",
                        tint = SoftSilver,
                        modifier = Modifier.padding(end = 12.dp).size(20.dp)
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        if (uiState.query.isEmpty()) {
                            Text(
                                text = "Search songs, albums, artists...",
                                color = SmokySilver,
                                fontSize = 14.sp
                            )
                        }
                        BasicTextField(
                            value = uiState.query,
                            onValueChange = { viewModel.onQueryChanged(it) },
                            textStyle = TextStyle(color = PearlWhite, fontSize = 14.sp),
                            cursorBrush = SolidColor(CoralAccent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    AnimatedVisibility(visible = uiState.query.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(DeepCharcoal)
                                .clickable { viewModel.onQueryChanged("") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Hapus teks",
                                tint = SoftSilver,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // ── 2. Mode Search Aktif (Ada Query) ───────────────────────────
            if (uiState.query.isNotEmpty()) {
                // Multi-Source Filter Row
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        items(sourceOptions) { source ->
                            GlassPillChip(
                                text = source.displayName,
                                isSelected = uiState.activeSource == source,
                                onClick = { viewModel.setSourceFilter(source) }
                            )
                        }
                    }
                }

                // Category Filter Row
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        items(filterOptions) { (filter, label) ->
                            GlassPillChip(
                                text = label,
                                isSelected = uiState.activeFilter == filter,
                                onClick = { viewModel.setFilter(filter) }
                            )
                        }
                    }
                }

                // Search Results
                when (val resultState = uiState.searchResultState) {
                    is Resource.Loading -> item {
                        MonochromeLoadingIndicator(message = "Mencari di ${uiState.activeSource.displayName}...")
                    }
                    is Resource.Error -> item {
                        MonochromeErrorView(
                            message = resultState.message,
                            onRetry = { viewModel.onQueryChanged(uiState.query) }
                        )
                    }
                    is Resource.Success -> {
                        val result = resultState.data
                        val showTracks = uiState.activeFilter == SearchCategoryFilter.ALL || uiState.activeFilter == SearchCategoryFilter.TRACKS
                        val showArtists = uiState.activeFilter == SearchCategoryFilter.ALL || uiState.activeFilter == SearchCategoryFilter.ARTISTS
                        val showPlaylists = uiState.activeFilter == SearchCategoryFilter.ALL || uiState.activeFilter == SearchCategoryFilter.PLAYLISTS

                        val hasAny = (showTracks && result.tracks.isNotEmpty()) ||
                                (showArtists && result.artists.isNotEmpty()) ||
                                (showPlaylists && result.playlists.isNotEmpty())

                        if (!hasAny) {
                            item {
                                MonochromeEmptyView(
                                    title = "Tidak ada hasil",
                                    subtitle = "Coba kata kunci lain atau pilih sumber musik lain."
                                )
                            }
                        } else {
                            if (showTracks && result.tracks.isNotEmpty()) {
                                item {
                                    SectionHeader(title = "Lagu Ditemukan", subtitle = "${result.tracks.size} lagu")
                                }
                                items(result.tracks, key = { "tr_${it.id}" }) { track ->
                                    TrackListItem(track = track, onClick = { onTrackSelect(track) })
                                }
                            }

                            if (showArtists && result.artists.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    SectionHeader(title = "Artis", subtitle = "${result.artists.size} artis")
                                }
                                items(result.artists, key = { "ar_${it.id}" }) { artist ->
                                    ArtistRowItem(artist = artist, onClick = { viewModel.onQueryChanged(artist.name) })
                                }
                            }

                            if (showPlaylists && result.playlists.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    SectionHeader(title = "Playlist", subtitle = "${result.playlists.size} playlist")
                                }
                                items(result.playlists, key = { "pl_${it.id}" }) { playlist ->
                                    PlaylistRowItem(playlist = playlist, onClick = { viewModel.onQueryChanged(playlist.title) })
                                }
                            }
                        }
                    }
                }
            } else {
                // ── 3. Mode Beranda Search (Frame 38 Video) ────────────────
                // Horizontal Quick Cards Carousel
                item {
                    val quickTracks = SamplePresentationData.quickAccessTracks.take(4)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(quickTracks, key = { "qk_${it.id}" }) { track ->
                            QuickHitCard(track = track, onClick = { onTrackSelect(track) })
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // "Your most played" Numbered List (#1 to #6)
                item {
                    Text(
                        text = "Your most played",
                        color = PearlWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }

                val topPlayed = SamplePresentationData.quickAccessTracks.take(6)
                itemsIndexed(topPlayed, key = { idx, t -> "mp_${t.id}_$idx" }) { index, track ->
                    NumberedTrackRow(
                        rank = index + 1,
                        track = track,
                        onClick = { onTrackSelect(track) }
                    )
                }

                // "Your favourite artists" Carousel
                item {
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = "Your favourite artists",
                        color = PearlWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        items(SamplePresentationData.featuredArtists, key = { "fav_art_${it.id}" }) { artist ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(76.dp)
                                    .clickable { viewModel.onQueryChanged(artist.name) }
                            ) {
                                NayamaImage(
                                    url = artist.avatarUrl,
                                    contentDescription = artist.name,
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, GlassBorder, CircleShape),
                                    shape = CircleShape,
                                    fallbackText = artist.name
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = artist.name,
                                    color = PearlWhite,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Floating Coral Mic Voice Search FAB (Frame 38) ──────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 120.dp)
                .size(52.dp)
                .shadow(12.dp, CircleShape, spotColor = CoralAccent)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            CoralAccent,
                            Color(0xFFE55837)
                        )
                    )
                )
                .clickable {
                    // Trigger voice search / quick recommendation
                    viewModel.onQueryChanged("Trending Indonesia")
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_nav_search),
                contentDescription = "Voice Search",
                tint = PearlWhite,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ─── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun QuickHitCard(
    track: Track,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DeepCharcoal.copy(alpha = 0.9f))
            .border(0.75.dp, GlassBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NayamaImage(
            url = track.artworkUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp)),
            shape = RoundedCornerShape(10.dp),
            fallbackText = track.title
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = PearlWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = SmokySilver,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NumberedTrackRow(
    rank: Int,
    track: Track,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank number
        Text(
            text = "$rank",
            color = if (rank <= 3) CoralAccent else SmokySilver,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(26.dp)
        )

        NayamaImage(
            url = track.artworkUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(0.5.dp, GlassBorder, RoundedCornerShape(10.dp)),
            shape = RoundedCornerShape(10.dp),
            fallbackText = track.title
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = PearlWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = SmokySilver,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            painter = painterResource(id = R.drawable.ic_play_arrow),
            contentDescription = "Putar",
            tint = SoftSilver,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ArtistRowItem(
    artist: Artist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NayamaImage(
            url = artist.avatarUrl,
            contentDescription = artist.name,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(0.75.dp, GlassBorder, CircleShape),
            shape = CircleShape,
            fallbackText = artist.name
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                color = PearlWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${artist.genre} • ${artist.monthlyListeners}",
                color = SmokySilver,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun PlaylistRowItem(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NayamaImage(
            url = playlist.artworkUrl,
            contentDescription = playlist.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(0.5.dp, GlassBorder, RoundedCornerShape(10.dp)),
            shape = RoundedCornerShape(10.dp),
            fallbackText = playlist.title
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                color = PearlWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${playlist.trackCount} lagu • ${playlist.description}",
                color = SmokySilver,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
