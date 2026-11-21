package com.nayama.music.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nayama.music.data.Resource
import com.nayama.music.data.SamplePresentationData
import com.nayama.music.model.Album
import com.nayama.music.model.GenreCategory
import com.nayama.music.model.Track
import com.nayama.music.ui.components.AlbumCard
import com.nayama.music.ui.components.CategoryCard
import com.nayama.music.ui.components.MonochromeErrorView
import com.nayama.music.ui.components.MonochromeLoadingIndicator
import com.nayama.music.ui.components.SectionHeader
import com.nayama.music.ui.components.TrackListItem
import com.nayama.music.ui.theme.DarkCharcoal
import com.nayama.music.ui.theme.DeepBlack
import com.nayama.music.ui.theme.ElevatedSurface
import com.nayama.music.ui.theme.Graphite
import com.nayama.music.ui.theme.MediumGray
import com.nayama.music.ui.theme.PureBlack
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SurfaceBlack

@Composable
fun ExploreScreen(
    onCategorySelect: (GenreCategory) -> Unit,
    onAlbumSelect: (Album) -> Unit,
    onTrackSelect: (Track) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentPadding = PaddingValues(bottom = 130.dp)
    ) {
        // ── Header ───────────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Text(
                    text = "EXPLORE",
                    color = PureWhite,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
                Text(
                    text = "Discover genres, charts & new releases",
                    color = MediumGray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // ── Chart Filter Cards ────────────────────────────────────────────
        item {
            SectionHeader(title = "Charts & Rankings", subtitle = "Live genre charts")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                data class ChartCategoryItem(
                    val genreKey: String,
                    val rank: String,
                    val label: String,
                    val gradient: List<Color>
                )

                val chartCategories = listOf(
                    ChartCategoryItem("Electronic", "RANK 01", "Top Electronic", listOf(DarkCharcoal, ElevatedSurface)),
                    ChartCategoryItem("Hip-Hop/Rap", "RANK 02", "Top Urban & Rap", listOf(ElevatedSurface, SurfaceBlack)),
                    ChartCategoryItem("Ambient", "RANK 03", "Minimal Ambient", listOf(DarkCharcoal, SurfaceBlack)),
                    ChartCategoryItem("Classical", "RANK 04", "Modern Acoustic", listOf(ElevatedSurface, DeepBlack))
                )
                items(chartCategories, key = { it.genreKey }) { item ->
                    val isSelected = uiState.selectedCategory?.title?.contains(item.genreKey, ignoreCase = true) == true
                    ChartCard(
                        label = item.label,
                        rank = item.rank,
                        gradientColors = item.gradient,
                        isSelected = isSelected,
                        onClick = {
                            val cat = uiState.categories.find { it.title.contains(item.genreKey, ignoreCase = true) }
                                ?: GenreCategory(
                                    id = item.genreKey.lowercase(),
                                    title = item.label,
                                    subtitle = "Top Streaming",
                                    tag = item.genreKey.uppercase()
                                )
                            viewModel.selectCategory(cat)
                        }
                    )
                }
            }
        }

        // ── Genre Tracks ──────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(24.dp))
            SectionHeader(
                title = uiState.selectedCategory?.title ?: "Electronic Highlights",
                subtitle = "Streaming tracks in this spectrum"
            )
        }

        when (val genreTracks = uiState.categoryTracksState) {
            is Resource.Loading -> {
                item {
                    MonochromeLoadingIndicator(message = "Memuat tracks...")
                }
            }
            is Resource.Error -> {
                item {
                    MonochromeErrorView(message = genreTracks.message, onRetry = { viewModel.retry() })
                }
            }
            is Resource.Success -> {
                items(genreTracks.data, key = { "exp_${it.id}" }) { track ->
                    TrackListItem(track = track, onClick = { onTrackSelect(track) })
                }
            }
        }

        // ── Genres & Moods Grid ───────────────────────────────────────────
        item {
            Spacer(Modifier.height(24.dp))
            SectionHeader(title = "Genres & Moods", subtitle = "Browse all sound categories")

            val categories = uiState.categories.ifEmpty { SamplePresentationData.exploreCategories }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (i in categories.indices step 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CategoryCard(
                            category = categories[i],
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.selectCategory(categories[i])
                                onCategorySelect(categories[i])
                            }
                        )
                        if (i + 1 < categories.size) {
                            CategoryCard(
                                category = categories[i + 1],
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.selectCategory(categories[i + 1])
                                    onCategorySelect(categories[i + 1])
                                }
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ── New Releases ──────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(24.dp))
            SectionHeader(title = "Curated Releases", subtitle = "Editorial sound collections")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(SamplePresentationData.popularAlbums, key = { "exp_alb_${it.id}" }) { album ->
                    AlbumCard(album = album, onClick = { onAlbumSelect(album) })
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun ChartCard(
    label: String,
    rank: String,
    gradientColors: List<Color>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "chart_card_scale"
    )

    Box(
        modifier = Modifier
            .width(155.dp)
            .height(96.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isSelected)
                    Brush.linearGradient(gradientColors.map { it.copy(alpha = 1f) })
                else
                    Brush.linearGradient(gradientColors.map { it.copy(alpha = 0.85f) })
            )
            .border(
                width = if (isSelected) 1.2.dp else 0.75.dp,
                color = if (isSelected) PureWhite else Color(0x28FFFFFF),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) PureWhite else Color(0x1EFFFFFF))
                    .border(0.5.dp, if (isSelected) PureWhite else Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = rank,
                    color = if (isSelected) PureBlack else PureWhite,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
            Text(
                text = label,
                color = PureWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
        }
    }
}
