package com.nayama.music.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import com.nayama.music.ui.components.BackupAndRestoreDialog
import com.nayama.music.ui.components.SupportCoffeeDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nayama.music.R
import com.nayama.music.data.Resource
import com.nayama.music.data.SamplePresentationData
import com.nayama.music.model.Album
import com.nayama.music.model.Track
import com.nayama.music.playback.AudioEqualizerController
import com.nayama.music.ui.components.AlbumCard
import com.nayama.music.ui.components.MonochromeErrorView
import com.nayama.music.ui.components.MonochromeLoadingIndicator
import com.nayama.music.ui.components.NayamaImage
import com.nayama.music.ui.components.SectionHeader
import com.nayama.music.ui.components.TrackListItem
import com.nayama.music.ui.theme.CoralAccent
import com.nayama.music.ui.theme.CoralSurface
import com.nayama.music.ui.theme.DeepCharcoal
import com.nayama.music.ui.theme.GlassBorder
import com.nayama.music.ui.theme.GlassSurface
import com.nayama.music.ui.theme.GraphiteSurface
import com.nayama.music.ui.theme.ObsidianBlack
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTrackSelect: (Track) -> Unit,
    onAlbumSelect: (Album) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToProfile: () -> Unit = {},
    onScrollFoldChanged: (Boolean) -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.initRecommendationEngine(context)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedMood by remember { mutableStateOf<String?>("Bersantai") }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showBackupRestoreDialog by remember { mutableStateOf(false) }
    var showSupportCoffeeDialog by remember { mutableStateOf(false) }

    // Quick access items (can be reordered or shuffled)
    var quickAccessList by remember { mutableStateOf(SamplePresentationData.quickAccessTracks) }

    // Scroll state with auto-fold reporting for navbar & mini-player
    val listState = rememberLazyListState()
    val isScrollingDown by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 30
        }
    }
    LaunchedEffect(isScrollingDown) {
        onScrollFoldChanged(isScrollingDown)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // ── 1. Top Header with 4 Glass Action Buttons ──────────────────────
        item {
            LiquidGlassHomeHeader(
                onHistoryClick = { showHistoryDialog = true },
                onEqualizerClick = { showEqualizerDialog = true },
                onProfileClick = onNavigateToProfile,
                onSettingsClick = { showSettingsDialog = true }
            )
        }

        // ── 2. Mood Filter Pill Chips ──────────────────────────────────────
        item {
            MoodFilterRow(
                selectedMood = selectedMood,
                onMoodSelected = { mood ->
                    selectedMood = if (selectedMood == mood) null else mood
                }
            )
            Spacer(modifier = Modifier.height(18.dp))
        }

        // ── 3. "Panggilan Cepat" (3x3 Bento Grid) ──────────────────────────
        item {
            PanggilanCepatSection(
                tracks = quickAccessList,
                onTrackClick = onTrackSelect,
                onMatrixDotClick = {
                    // Shuffle / cycle quick access
                    quickAccessList = quickAccessList.shuffled()
                }
            )
            Spacer(modifier = Modifier.height(28.dp))
        }

        // ── 4. "Dari Komunitas" (Community Stage) ──────────────────────────
        item {
            DariKomunitasSection(
                communityTracks = SamplePresentationData.communityTracks,
                onTrackClick = onTrackSelect
            )
            Spacer(modifier = Modifier.height(28.dp))
        }

        // ── 5. Trending Musik (Live from Provider / Unified Multi-source) ──
        item {
            SectionHeader(
                title = "Trending Sekarang",
                subtitle = "Tangga lagu terpopuler lintas platform"
            )
        }

        when (val tracksState = uiState.trendingTracksState) {
            is Resource.Loading -> item {
                MonochromeLoadingIndicator(message = "Memuat musik trending...")
            }
            is Resource.Error -> item {
                MonochromeErrorView(
                    message = tracksState.message,
                    onRetry = { viewModel.retry() }
                )
            }
            is Resource.Success -> {
                items(tracksState.data.take(8), key = { "trend_${it.id}" }) { track ->
                    TrackListItem(
                        track = track,
                        onClick = { onTrackSelect(track) }
                    )
                }
            }
        }

        // ── 6. Album Pilihan (Curated Albums) ──────────────────────────────
        item {
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(
                title = "Album Pilihan",
                subtitle = "Koleksi album editorial & terpopuler"
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(SamplePresentationData.popularAlbums, key = { "alb_${it.id}" }) { album ->
                    AlbumCard(album = album, onClick = { onAlbumSelect(album) })
                }
            }
        }

        // ── 7. Artis Terkemuka ─────────────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(
                title = "Artis Unggulan",
                subtitle = "Penyanyi & produser pilihan O - Reverie"
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(SamplePresentationData.featuredArtists, key = { "art_${it.id}" }) { artist ->
                    ArtistItem(
                        name = artist.name,
                        avatarUrl = artist.avatarUrl,
                        monthlyListeners = artist.monthlyListeners
                    )
                }
            }
        }
    }

    // ── Dialogs triggered from Top Header ──────────────────────────────────
    if (showEqualizerDialog) {
        AudioEqualizerDialog(onDismiss = { showEqualizerDialog = false })
    }

    if (showHistoryDialog) {
        HistoryQuickDialog(
            recentTracks = quickAccessList,
            onTrackSelect = { track ->
                showHistoryDialog = false
                onTrackSelect(track)
            },
            onDismiss = { showHistoryDialog = false }
        )
    }

    if (showSettingsDialog) {
        AppSettingsDialog(
            onDismiss = { showSettingsDialog = false },
            onOpenBackupRestore = { showBackupRestoreDialog = true },
            onOpenSupportCoffee = { showSupportCoffeeDialog = true }
        )
    }

    if (showBackupRestoreDialog) {
        BackupAndRestoreDialog(onDismiss = { showBackupRestoreDialog = false })
    }

    if (showSupportCoffeeDialog) {
        SupportCoffeeDialog(onDismiss = { showSupportCoffeeDialog = false })
    }
}

/**
 * Top Header matching Image 1:
 * Time & "Beranda" title on the left, 4 round glass action buttons on the right.
 */
@Composable
private fun LiquidGlassHomeHeader(
    onHistoryClick: () -> Unit,
    onEqualizerClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val currentTime = remember {
        val sdf = SimpleDateFormat("HH.mm", Locale.getDefault())
        sdf.format(Date())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Subtle clock time indicator
        Text(
            text = currentTime,
            color = SmokySilver,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Beranda",
                color = PearlWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )

            // 4 Circular Glass Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassCircularButton(
                    iconRes = R.drawable.ic_history,
                    contentDescription = "Riwayat Mendengar",
                    onClick = onHistoryClick
                )

                GlassCircularButton(
                    iconRes = R.drawable.ic_equalizer,
                    contentDescription = "Equalizer Audio",
                    onClick = onEqualizerClick
                )

                GlassCircularButton(
                    iconRes = R.drawable.ic_community,
                    contentDescription = "Komunitas & Profil",
                    onClick = onProfileClick
                )

                GlassCircularButton(
                    iconRes = R.drawable.ic_settings,
                    contentDescription = "Pengaturan",
                    onClick = onSettingsClick
                )
            }
        }
    }
}

@Composable
private fun GlassCircularButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(100),
        label = "glass_btn_scale"
    )

    Box(
        modifier = Modifier
            .size(38.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(GraphiteSurface.copy(alpha = 0.85f))
            .border(1.dp, GlassBorder, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = SoftSilver,
            modifier = Modifier.size(19.dp)
        )
    }
}

/**
 * Mood filter chips: "Bersantai", "Senang", "Pesta", "Perjalanan"
 */
@Composable
private fun MoodFilterRow(
    selectedMood: String?,
    onMoodSelected: (String) -> Unit
) {
    val moods = listOf("Bersantai", "Senang", "Pesta", "Perjalanan")

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(moods) { mood ->
            val isSelected = selectedMood == mood
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) GraphiteSurface else DeepCharcoal.copy(alpha = 0.7f),
                animationSpec = tween(200),
                label = "mood_bg"
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) PearlWhite.copy(alpha = 0.5f) else Color(0x1AFFFFFF),
                animationSpec = tween(200),
                label = "mood_border"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) PearlWhite else SmokySilver,
                animationSpec = tween(200),
                label = "mood_text"
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundColor)
                    .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                    .clickable { onMoodSelected(mood) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mood,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * 3x3 Bento Grid "Panggilan cepat":
 * 8 track cards with artwork and title underneath,
 * plus 1 dot-matrix card (5 coral dots in 2-1-2 dice formation).
 */
@Composable
private fun PanggilanCepatSection(
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    onMatrixDotClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "Panggilan cepat",
            color = PearlWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 14.dp)
        )

        // 3 Rows x 3 Columns
        val itemsList = tracks.take(8)

        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (i in 0 until 3) {
                val track = itemsList.getOrNull(i)
                Box(modifier = Modifier.weight(1f)) {
                    if (track != null) {
                        BentoTrackCard(track = track, onClick = { onTrackClick(track) })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (i in 3 until 6) {
                val track = itemsList.getOrNull(i)
                Box(modifier = Modifier.weight(1f)) {
                    if (track != null) {
                        BentoTrackCard(track = track, onClick = { onTrackClick(track) })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Row 3 (2 Track cards + 1 Dot Matrix Card)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val track7 = itemsList.getOrNull(6)
            val track8 = itemsList.getOrNull(7)

            Box(modifier = Modifier.weight(1f)) {
                if (track7 != null) {
                    BentoTrackCard(track = track7, onClick = { onTrackClick(track7) })
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (track8 != null) {
                    BentoTrackCard(track = track8, onClick = { onTrackClick(track8) })
                }
            }

            // 5-Dot Matrix Card
            Box(modifier = Modifier.weight(1f)) {
                BentoDotMatrixCard(onClick = onMatrixDotClick)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Pagination Dots ([ = ]  •  •) matching reference photo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CoralAccent)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(SmokySilver.copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(SmokySilver.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
private fun BentoTrackCard(
    track: Track,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(100),
        label = "bento_card_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Squircle Artwork Container with Liquid Glass Border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x55282F3E),
                            Color(0x33171B24)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0x55FFFFFF),
                            Color(0x12FFFFFF)
                        )
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            NayamaImage(
                url = track.artworkUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(18.dp),
                fallbackText = track.title,
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = track.title,
            color = PearlWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 5-Dot Matrix Card in 2-1-2 formation (matching the reference image).
 */
@Composable
private fun BentoDotMatrixCard(
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(100),
        label = "matrix_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x552A303F),
                            Color(0x35191C25)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0x55FFFFFF),
                            Color(0x12FFFFFF)
                        )
                    ),
                    shape = RoundedCornerShape(18.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // 2-1-2 Coral Dot Matrix Layout
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top 2 dots
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(CoralAccent))
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(CoralAccent))
                }
                // Center 1 dot
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(CoralAccent))
                // Bottom 2 dots
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(CoralAccent))
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(CoralAccent))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Lainnya",
            color = SmokySilver,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * "Dari Komunitas" section:
 * Horizontal cards with community label, artwork, and action buttons.
 */
@Composable
private fun DariKomunitasSection(
    communityTracks: List<Track>,
    onTrackClick: (Track) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "Dari komunitas",
            color = PearlWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 14.dp)
        )

        communityTracks.forEach { track ->
            CommunityCardItem(track = track, onClick = { onTrackClick(track) })
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun CommunityCardItem(
    track: Track,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0x60262C3A),
                        Color(0x40161822)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0x60FFFFFF),
                        Color(0x15FFFFFF)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Squircle Artwork
            NayamaImage(
                url = track.artworkUrl,
                contentDescription = track.title,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                fallbackText = track.title,
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Track metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.artist.uppercase(),
                    color = CoralAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = track.title,
                    color = PearlWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = track.album ?: "Live Session",
                    color = SmokySilver,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right Action Buttons: Mic/Search & Coral Play
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Secondary Glass Button (Mic)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(GraphiteSurface.copy(alpha = 0.8f))
                        .border(0.75.dp, GlassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mic),
                        contentDescription = "Search / Mic",
                        tint = SoftSilver,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Coral Play Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CoralAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_play_arrow),
                        contentDescription = "Putar",
                        tint = PureWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistItem(
    name: String,
    avatarUrl: String?,
    monthlyListeners: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(DeepCharcoal)
                .border(1.dp, GlassBorder, CircleShape)
        ) {
            NayamaImage(
                url = avatarUrl,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                fallbackText = name
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = name,
            color = PearlWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "$monthlyListeners pendengar",
            color = SmokySilver,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Built-in Quick Dialogs for Equalizer, History, Settings
 */
@Composable
private fun AudioEqualizerDialog(
    onDismiss: () -> Unit
) {
    var bassBoost by remember { mutableStateOf(0.7f) }
    var trebleBoost by remember { mutableStateOf(0.5f) }
    var vocalClarity by remember { mutableStateOf(0.65f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Equalizer Audio O - Reverie",
                color = PearlWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Preset: Studio Master (Hi-Res Audio)",
                    color = SmokySilver,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Bass Boost", color = SoftSilver, fontSize = 12.sp)
                Slider(
                    value = bassBoost,
                    onValueChange = { bassBoost = it },
                    colors = SliderDefaults.colors(
                        thumbColor = CoralAccent,
                        activeTrackColor = CoralAccent,
                        inactiveTrackColor = GraphiteSurface
                    )
                )

                Text(text = "Vocal Clarity", color = SoftSilver, fontSize = 12.sp)
                Slider(
                    value = vocalClarity,
                    onValueChange = { vocalClarity = it },
                    colors = SliderDefaults.colors(
                        thumbColor = CoralAccent,
                        activeTrackColor = CoralAccent,
                        inactiveTrackColor = GraphiteSurface
                    )
                )

                Text(text = "Treble Air", color = SoftSilver, fontSize = 12.sp)
                Slider(
                    value = trebleBoost,
                    onValueChange = { trebleBoost = it },
                    colors = SliderDefaults.colors(
                        thumbColor = CoralAccent,
                        activeTrackColor = CoralAccent,
                        inactiveTrackColor = GraphiteSurface
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Terapkan", color = CoralAccent, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DeepCharcoal,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun HistoryQuickDialog(
    recentTracks: List<Track>,
    onTrackSelect: (Track) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Riwayat Mendengar",
                color = PearlWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                recentTracks.take(4).forEach { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackSelect(track) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NayamaImage(
                            url = track.artworkUrl,
                            contentDescription = track.title,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            shape = RoundedCornerShape(8.dp),
                            fallbackText = track.title
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = track.title,
                                color = PearlWhite,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                            Text(
                                text = track.artist,
                                color = SmokySilver,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = SoftSilver)
            }
        },
        containerColor = DeepCharcoal,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun AppSettingsDialog(
    onDismiss: () -> Unit,
    onOpenBackupRestore: () -> Unit = {},
    onOpenSupportCoffee: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "O - Reverie",
                color = PearlWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Versi: 2.1.0 Obsidian Silver Edition",
                    color = SoftSilver,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Unified Multi-Source Streaming (YouTube Music, Spotify, SoundCloud, Audius)",
                    color = SmokySilver,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Mode Tampilan: Liquid Glass / Navbar Lensa Melipat",
                    color = CoralAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Tombol Buka Backup & Restore / Impor Spotify
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GraphiteSurface)
                        .clickable {
                            onDismiss()
                            onOpenBackupRestore()
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Backup & Impor Playlist",
                        color = PearlWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Spotify/Apple >",
                        color = CoralAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tombol Traktir Kopi (QRIS Support)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GraphiteSurface)
                        .clickable {
                            onDismiss()
                            onOpenSupportCoffee()
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Mau Traktir Kopi? (QRIS)",
                        color = PearlWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Dukung >",
                        color = SoftSilver,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Selesai", color = CoralAccent, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DeepCharcoal,
        shape = RoundedCornerShape(20.dp)
    )
}
