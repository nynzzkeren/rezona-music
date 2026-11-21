package com.nayama.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nayama.music.R
import com.nayama.music.data.LyricsRepository
import com.nayama.music.data.local.LibraryRepository
import com.nayama.music.model.Lyrics
import com.nayama.music.model.Track
import com.nayama.music.playback.AudioOutputDetector
import com.nayama.music.playback.AudioOutputInfo
import com.nayama.music.playback.MusicPlayerController
import com.nayama.music.playback.PlaybackState
import com.nayama.music.playback.RepeatMode
import com.nayama.music.playback.rememberAudioOutputInfo
import com.nayama.music.ui.theme.DynamicArtworkPalette
import com.nayama.music.ui.theme.ElevatedSurface
import com.nayama.music.ui.theme.Graphite
import com.nayama.music.ui.theme.LightGray
import com.nayama.music.ui.theme.MediumGray
import com.nayama.music.ui.theme.PureBlack
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SurfaceBlack
import com.nayama.music.ui.theme.rememberAnimatedArtworkPalette

enum class PlayerTab(val label: String) {
    ARTWORK("COVER"),
    LYRICS("LIRIK"),
    EQUALIZER("EQUALIZER"),
    DETAILS("INFO")
}

@Composable
fun ExpandedPlayerModal(
    playbackState: PlaybackState,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 260)
        ),
        modifier = modifier
    ) {
        val context = LocalContext.current
        val playerController = remember { MusicPlayerController.getInstance(context) }
        val libraryRepo = remember { LibraryRepository.getInstance(context) }
        val lyricsRepo = remember { LyricsRepository.getInstance() }

        val likedSongs by libraryRepo.likedSongs.collectAsStateWithLifecycle()
        val playlists by libraryRepo.playlists.collectAsStateWithLifecycle()
        val eqState by playerController.equalizerController.equalizerState.collectAsStateWithLifecycle()

        // Dynamic dominant color palette extracted from active cover artwork
        val dynamicPalette = rememberAnimatedArtworkPalette(context, track.artworkUrl)
        // Active Android hardware audio output route (Bluetooth / USB DAC / Wired / Speaker)
        val audioOutputInfo = rememberAudioOutputInfo(context)

        val isFavorite = likedSongs.any { it.id == track.id }

        var currentTab by remember { mutableStateOf(PlayerTab.ARTWORK) }
        var isUserSeeking by remember { mutableStateOf(false) }
        var seekPositionFraction by remember { mutableStateOf(0f) }
        var showAddToPlaylistDialog by remember { mutableStateOf(false) }
        var showSleepTimerDialog by remember { mutableStateOf(false) }
        var showSharePosterDialog by remember { mutableStateOf(false) }
        var posterSnippet by remember { mutableStateOf<String?>(null) }

        // Automatic Lyrics Fetch (never throws, hides panel cleanly if instrumental/missing)
        var lyrics by remember(track.id) { mutableStateOf<Lyrics?>(null) }
        var isLoadingLyrics by remember(track.id) { mutableStateOf(true) }

        LaunchedEffect(track.id) {
            try {
                isLoadingLyrics = true
                lyrics = lyricsRepo.getLyrics(track)
            } catch (_: Throwable) {
                lyrics = null
            } finally {
                isLoadingLyrics = false
            }
        }

        val hasLyrics = !isLoadingLyrics && lyrics != null && lyrics!!.lines.isNotEmpty()

        // If user was on LYRICS tab and next track is instrumental/unavailable, smoothly switch off
        LaunchedEffect(hasLyrics, isLoadingLyrics) {
            if (!isLoadingLyrics && !hasLyrics && currentTab == PlayerTab.LYRICS) {
                currentTab = PlayerTab.ARTWORK
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to dynamicPalette.primaryAmbient,
                            0.45f to dynamicPalette.secondaryAmbient,
                            1.0f to PureBlack
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val isSplitScreen = maxWidth >= 600.dp

            if (isSplitScreen) {
                // ── Tablet / Wide Screen Split-Screen Apple Music Player ──
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    // Left Pane: Header + Artwork + Metadata + Scrubber + Transport + Output Pill
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        PlayerTopBar(
                            track = track,
                            playbackState = playbackState,
                            onDismiss = onDismiss,
                            onShowSleepTimer = { showSleepTimerDialog = true },
                            onShowAddToPlaylist = { showAddToPlaylistDialog = true }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        ArtworkStage(
                            track = track,
                            isPlaying = playbackState.isPlaying,
                            isBuffering = playbackState.isBuffering,
                            palette = dynamicPalette,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(0.76f)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        TrackMetadataAndHeart(
                            track = track,
                            isFavorite = isFavorite,
                            onToggleLike = { libraryRepo.toggleLike(track) }
                        )

                        SeekScrubberSection(
                            playbackState = playbackState,
                            isUserSeeking = isUserSeeking,
                            seekPositionFraction = seekPositionFraction,
                            onSeekChange = {
                                isUserSeeking = true
                                seekPositionFraction = it
                            },
                            onSeekFinished = {
                                onSeek(seekPositionFraction)
                                isUserSeeking = false
                            }
                        )

                        TransportControlsRow(
                            playbackState = playbackState,
                            onToggleShuffle = onToggleShuffle,
                            onSkipPrevious = onSkipPrevious,
                            onPlayPauseClick = onPlayPauseClick,
                            onSkipNext = onSkipNext,
                            onToggleRepeat = onToggleRepeat
                        )

                        OutputDeviceAndQualityStrip(
                            track = track,
                            audioOutputInfo = audioOutputInfo,
                            palette = dynamicPalette,
                            onOutputClick = { AudioOutputDetector.openSystemAudioOutputPanel(context) }
                        )
                    }

                    // Right Pane: Liquid Glass Panel with Lyrics (when available), Equalizer & Info
                    val sideTabs = remember(hasLyrics) {
                        buildList {
                            if (hasLyrics) add(PlayerTab.LYRICS)
                            add(PlayerTab.EQUALIZER)
                            add(PlayerTab.DETAILS)
                        }
                    }
                    val effectiveSideTab = remember(currentTab, hasLyrics) {
                        when {
                            currentTab == PlayerTab.ARTWORK -> if (hasLyrics) PlayerTab.LYRICS else PlayerTab.EQUALIZER
                            currentTab == PlayerTab.LYRICS && !hasLyrics -> PlayerTab.EQUALIZER
                            else -> currentTab
                        }
                    }

                    GlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(26.dp),
                        backgroundColor = dynamicPalette.glassTint
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp)
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 14.dp)
                            ) {
                                items(sideTabs, key = { it.name }) { tab ->
                                    GlassPillChip(
                                        text = tab.label,
                                        isSelected = effectiveSideTab == tab,
                                        onClick = { currentTab = tab }
                                    )
                                }
                            }

                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                Crossfade(targetState = effectiveSideTab, label = "split_side_tab") { tab ->
                                    when (tab) {
                                        PlayerTab.LYRICS -> {
                                            if (hasLyrics) {
                                                LyricsView(
                                                    lyrics = lyrics!!,
                                                    currentPositionMs = playbackState.currentPositionMs,
                                                    onLineClick = { timeMs ->
                                                        if (playbackState.durationMs > 0) {
                                                            val frac = (timeMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
                                                            onSeek(frac)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                        PlayerTab.EQUALIZER -> {
                                            EqualizerView(
                                                eqState = eqState,
                                                onToggleEnabled = { playerController.equalizerController.setEnabled(it) },
                                                onSelectPreset = { playerController.equalizerController.applyPreset(it) },
                                                onBandChange = { idx, level -> playerController.equalizerController.setBandLevel(idx, level) }
                                            )
                                        }
                                        else -> {
                                            PlaybackDetailsView(track = track, playbackState = playbackState)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ── Mobile Portrait Apple Music / BitChord Liquid Glass Layout ──
                val availableTabs = remember(hasLyrics) {
                    buildList {
                        add(PlayerTab.ARTWORK)
                        if (hasLyrics) add(PlayerTab.LYRICS)
                        add(PlayerTab.EQUALIZER)
                        add(PlayerTab.DETAILS)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    PlayerTopBar(
                        track = track,
                        playbackState = playbackState,
                        onDismiss = onDismiss,
                        onShowSleepTimer = { showSleepTimerDialog = true },
                        onShowAddToPlaylist = { showAddToPlaylistDialog = true },
                        onShowSharePoster = {
                            posterSnippet = null
                            showSharePosterDialog = true
                        }
                    )

                    LazyRow(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableTabs, key = { it.name }) { tab ->
                            GlassPillChip(
                                text = tab.label,
                                isSelected = currentTab == tab,
                                onClick = { currentTab = tab }
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Crossfade(targetState = currentTab, label = "tab_crossfade") { tab ->
                            when (tab) {
                                PlayerTab.ARTWORK -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        ArtworkStage(
                                            track = track,
                                            isPlaying = playbackState.isPlaying,
                                            isBuffering = playbackState.isBuffering,
                                            palette = dynamicPalette,
                                            modifier = Modifier.fillMaxWidth(0.88f)
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        AudioVisualizer(
                                            isPlaying = playbackState.isPlaying,
                                            modifier = Modifier.fillMaxWidth(0.88f)
                                        )
                                    }
                                }

                                PlayerTab.LYRICS -> {
                                    if (hasLyrics) {
                                        LyricsView(
                                            lyrics = lyrics!!,
                                            currentPositionMs = playbackState.currentPositionMs,
                                            onLineClick = { timeMs ->
                                                if (playbackState.durationMs > 0) {
                                                    val frac = (timeMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
                                                    onSeek(frac)
                                                }
                                            }
                                        )
                                    }
                                }

                                PlayerTab.EQUALIZER -> {
                                    EqualizerView(
                                        eqState = eqState,
                                        onToggleEnabled = { playerController.equalizerController.setEnabled(it) },
                                        onSelectPreset = { playerController.equalizerController.applyPreset(it) },
                                        onBandChange = { idx, level -> playerController.equalizerController.setBandLevel(idx, level) }
                                    )
                                }

                                PlayerTab.DETAILS -> {
                                    PlaybackDetailsView(track = track, playbackState = playbackState)
                                }
                            }
                        }
                    }

                    TrackMetadataAndHeart(
                        track = track,
                        isFavorite = isFavorite,
                        onToggleLike = { libraryRepo.toggleLike(track) }
                    )

                    SeekScrubberSection(
                        playbackState = playbackState,
                        isUserSeeking = isUserSeeking,
                        seekPositionFraction = seekPositionFraction,
                        onSeekChange = {
                            isUserSeeking = true
                            seekPositionFraction = it
                        },
                        onSeekFinished = {
                            onSeek(seekPositionFraction)
                            isUserSeeking = false
                        }
                    )

                    TransportControlsRow(
                        playbackState = playbackState,
                        onToggleShuffle = onToggleShuffle,
                        onSkipPrevious = onSkipPrevious,
                        onPlayPauseClick = onPlayPauseClick,
                        onSkipNext = onSkipNext,
                        onToggleRepeat = onToggleRepeat
                    )

                    OutputDeviceAndQualityStrip(
                        track = track,
                        audioOutputInfo = audioOutputInfo,
                        palette = dynamicPalette,
                        onOutputClick = { AudioOutputDetector.openSystemAudioOutputPanel(context) }
                    )
                }
            }
        }

        // ── Add To Playlist Dialog ────────────────────────────────────────
        if (showAddToPlaylistDialog) {
            Dialog(onDismissRequest = { showAddToPlaylistDialog = false }) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = ElevatedSurface
                ) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ADD TO PLAYLIST",
                                color = PureWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceBlack)
                                    .clickable { showAddToPlaylistDialog = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = "Tutup",
                                    tint = MediumGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (playlists.isEmpty()) {
                            Text(
                                text = "Belum ada playlist. Buat playlist baru di Library.",
                                color = MediumGray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            ) {
                                items(playlists, key = { it.id }) { pl ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                libraryRepo.addTrackToPlaylist(pl.id, track)
                                                showAddToPlaylistDialog = false
                                            }
                                            .padding(vertical = 10.dp, horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(SurfaceBlack)
                                                .border(0.5.dp, Graphite, RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_music_note),
                                                contentDescription = null,
                                                tint = LightGray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = pl.title,
                                                color = PureWhite,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "${pl.trackCount} lagu",
                                                color = MediumGray,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Sleep Timer Dialog ────────────────────────────────────────────
        if (showSleepTimerDialog) {
            Dialog(onDismissRequest = { showSleepTimerDialog = false }) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = ElevatedSurface
                ) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        Text(
                            text = "SLEEP TIMER",
                            color = PureWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Musik akan otomatis berhenti setelah durasi habis.",
                            color = MediumGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )

                        val timerOptions = listOf(
                            15 to "15 Menit",
                            30 to "30 Menit",
                            45 to "45 Menit",
                            60 to "60 Menit"
                        )

                        timerOptions.forEach { (mins, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        playerController.setSleepTimer(mins)
                                        showSleepTimerDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = label, color = PureWhite, fontSize = 14.sp)
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_timer),
                                    contentDescription = null,
                                    tint = MediumGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (playbackState.sleepTimerRemainingSeconds != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        playerController.cancelSleepTimer()
                                        showSleepTimerDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Matikan Sleep Timer",
                                    color = PureWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .align(Alignment.End)
                                .clickable { showSleepTimerDialog = false }
                                .padding(8.dp)
                        ) {
                            Text(text = "BATAL", color = MediumGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── Share Lyrics Poster Dialog (Lirik Jadi Poster) ───────────────
        if (showSharePosterDialog) {
            ShareLyricsPosterDialog(
                track = track,
                lyrics = lyrics,
                initialLyricSnippet = posterSnippet,
                onDismiss = { showSharePosterDialog = false }
            )
        }
    }
}

@Composable
private fun PlayerTopBar(
    track: Track,
    playbackState: PlaybackState,
    onDismiss: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onShowAddToPlaylist: () -> Unit,
    onShowSharePoster: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x88141416))
                .border(0.75.dp, Color(0x36FFFFFF), CircleShape)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_expand_more),
                contentDescription = "Tutup pemutar",
                tint = PureWhite,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "NOW PLAYING",
                color = Color(0xAAFFFFFF),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = track.album,
                color = LightGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (playbackState.sleepTimerRemainingSeconds != null) PureWhite else Color(0x88141416))
                    .border(
                        0.75.dp,
                        if (playbackState.sleepTimerRemainingSeconds != null) PureWhite else Color(0x36FFFFFF),
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { onShowSleepTimer() }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_timer),
                    contentDescription = "Sleep Timer",
                    tint = if (playbackState.sleepTimerRemainingSeconds != null) PureBlack else PureWhite,
                    modifier = Modifier.size(16.dp)
                )
                if (playbackState.sleepTimerRemainingSeconds != null) {
                    val s = playbackState.sleepTimerRemainingSeconds
                    Text(
                        text = "%d:%02d".format(s / 60, s % 60),
                        color = PureBlack,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x88141416))
                    .border(0.75.dp, Color(0x36FFFFFF), CircleShape)
                    .clickable { onShowAddToPlaylist() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add),
                    contentDescription = "Tambah ke Playlist",
                    tint = PureWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Poster Lirik Button (Lirik Jadi Poster)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x88141416))
                    .border(0.75.dp, Color(0x36FFFFFF), CircleShape)
                    .clickable { onShowSharePoster() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_edit),
                    contentDescription = "Lirik Jadi Poster",
                    tint = PureWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ArtworkStage(
    track: Track,
    isPlaying: Boolean,
    isBuffering: Boolean,
    palette: DynamicArtworkPalette,
    modifier: Modifier = Modifier
) {
    val artworkScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.91f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "apple_artwork_scale"
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = artworkScale
                scaleY = artworkScale
            }
            .clip(RoundedCornerShape(26.dp))
            .background(palette.secondaryAmbient)
            .border(
                0.9.dp,
                Brush.verticalGradient(
                    listOf(Color(0x4DFFFFFF), Color(0x14FFFFFF))
                ),
                RoundedCornerShape(26.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        NayamaImage(
            url = track.artworkUrl,
            contentDescription = track.title,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(26.dp),
            fallbackText = track.title
        )

        if (isBuffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PureBlack.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = PureWhite,
                    modifier = Modifier.size(46.dp),
                    strokeWidth = 2.5.dp
                )
            }
        }
    }
}

@Composable
private fun TrackMetadataAndHeart(
    track: Track,
    isFavorite: Boolean,
    onToggleLike: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = track.title,
                color = PureWhite,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = Color(0xBBFFFFFF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        val heartScale by animateFloatAsState(
            targetValue = if (isFavorite) 1.15f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "heart_anim"
        )

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0x44FFFFFF).copy(alpha = if (isFavorite) 0.16f else 0.08f))
                .border(0.6.dp, Color(0x2EFFFFFF), CircleShape)
                .clickable { onToggleLike() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(
                    id = if (isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                ),
                contentDescription = if (isFavorite) "Hapus dari Suka" else "Suka",
                tint = if (isFavorite) PureWhite else LightGray,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = heartScale
                        scaleY = heartScale
                    }
            )
        }
    }
}

@Composable
private fun SeekScrubberSection(
    playbackState: PlaybackState,
    isUserSeeking: Boolean,
    seekPositionFraction: Float,
    onSeekChange: (Float) -> Unit,
    onSeekFinished: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val currentSliderValue = if (isUserSeeking) seekPositionFraction else playbackState.progressFraction

        Slider(
            value = currentSliderValue,
            onValueChange = onSeekChange,
            onValueChangeFinished = onSeekFinished,
            colors = SliderDefaults.colors(
                thumbColor = PureWhite,
                activeTrackColor = PureWhite,
                inactiveTrackColor = Color(0x33FFFFFF)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isUserSeeking) {
                    val targetSec = (seekPositionFraction * (playbackState.durationMs / 1000)).toLong()
                    "%d:%02d".format(targetSec / 60, targetSec % 60)
                } else playbackState.formattedCurrentPosition,
                color = Color(0xAAFFFFFF),
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )
            Text(
                text = playbackState.formattedTotalDuration,
                color = Color(0xAAFFFFFF),
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun TransportControlsRow(
    playbackState: PlaybackState,
    onToggleShuffle: () -> Unit,
    onSkipPrevious: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleRepeat: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable { onToggleShuffle() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_shuffle),
                    contentDescription = "Acak",
                    tint = if (playbackState.isShuffleEnabled) PureWhite else Color(0x77FFFFFF),
                    modifier = Modifier.size(22.dp)
                )
                if (playbackState.isShuffleEnabled) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(PureWhite))
                }
            }
        }

        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .border(0.75.dp, Color(0x28FFFFFF), CircleShape)
                .clickable { onSkipPrevious() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_previous),
                contentDescription = "Sebelumnya",
                tint = PureWhite,
                modifier = Modifier.size(26.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(PureWhite)
                .clickable { onPlayPauseClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(
                    id = if (playbackState.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                ),
                contentDescription = if (playbackState.isPlaying) "Jeda" else "Putar",
                tint = PureBlack,
                modifier = Modifier.size(34.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .border(0.75.dp, Color(0x28FFFFFF), CircleShape)
                .clickable { onSkipNext() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_next),
                contentDescription = "Selanjutnya",
                tint = PureWhite,
                modifier = Modifier.size(26.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable { onToggleRepeat() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(
                        id = if (playbackState.repeatMode == RepeatMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat
                    ),
                    contentDescription = "Ulangi",
                    tint = if (playbackState.repeatMode != RepeatMode.OFF) PureWhite else Color(0x77FFFFFF),
                    modifier = Modifier.size(22.dp)
                )
                if (playbackState.repeatMode != RepeatMode.OFF) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(PureWhite))
                }
            }
        }
    }
}

/**
 * BitChord / Apple Music Footer Bar: Active Output Device Pill + Audio Quality Badge.
 */
@Composable
private fun OutputDeviceAndQualityStrip(
    track: Track,
    audioOutputInfo: AudioOutputInfo,
    palette: DynamicArtworkPalette,
    onOutputClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Real Hardware Output Device Pill
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(50.dp))
                .background(palette.glassTint.copy(alpha = 0.72f))
                .border(0.75.dp, Color(0x33FFFFFF), RoundedCornerShape(50.dp))
                .clickable { onOutputClick() }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(id = audioOutputInfo.iconResId),
                contentDescription = audioOutputInfo.label,
                tint = PureWhite,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = audioOutputInfo.label,
                color = PureWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Audio Quality + Source Pill Badge
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(palette.glassTint.copy(alpha = 0.72f))
                .border(0.75.dp, Color(0x33FFFFFF), RoundedCornerShape(50.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = track.audioQualityLabel,
                color = PureWhite,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Text(
                text = "· ${track.source.shortBadge}",
                color = Color(0xAAFFFFFF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Animated Synced Lyrics View (Word-by-word highlight when supported + beat-synced auto-scroll).
 */
@Composable
private fun LyricsView(
    lyrics: Lyrics,
    currentPositionMs: Long,
    onLineClick: (Long) -> Unit
) {
    val listState = rememberLazyListState()

    val activeIndex = if (lyrics.isSynced) {
        lyrics.lines.indexOfLast { it.timeMs <= currentPositionMs }
    } else -1

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 36.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        itemsIndexed(lyrics.lines, key = { index, line -> "lyr_${line.timeMs}_$index" }) { index, line ->
            val isActive = index == activeIndex
            val lineScale by animateFloatAsState(
                targetValue = if (isActive) 1.06f else 0.96f,
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                label = "lyric_line_scale"
            )
            val lineAlpha by animateFloatAsState(
                targetValue = when {
                    !lyrics.isSynced -> 0.88f
                    isActive -> 1f
                    index < activeIndex -> 0.35f
                    else -> 0.50f
                },
                animationSpec = tween(220),
                label = "lyric_line_alpha"
            )

            if (isActive && line.words.isNotEmpty()) {
                // Word-by-word animated progressive highlight
                val annotated = buildAnnotatedString {
                    line.words.forEachIndexed { wIdx, token ->
                        if (wIdx > 0) append(" ")
                        val isSpoken = currentPositionMs >= token.timeMs
                        withStyle(
                            SpanStyle(
                                color = if (isSpoken) PureWhite else Color(0x66FFFFFF),
                                fontWeight = if (isSpoken) FontWeight.Black else FontWeight.SemiBold
                            )
                        ) {
                            append(token.text)
                        }
                    }
                }
                Text(
                    text = annotated,
                    fontSize = 21.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = lineScale
                            scaleY = lineScale
                        }
                        .clickable { if (line.timeMs >= 0) onLineClick(line.timeMs) }
                        .padding(vertical = 4.dp)
                )
            } else {
                Text(
                    text = line.text,
                    color = PureWhite.copy(alpha = lineAlpha),
                    fontSize = if (isActive) 21.sp else 16.sp,
                    lineHeight = if (isActive) 28.sp else 22.sp,
                    fontWeight = if (isActive) FontWeight.Black else FontWeight.SemiBold,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = lineScale
                            scaleY = lineScale
                        }
                        .clickable { if (line.timeMs >= 0) onLineClick(line.timeMs) }
                        .padding(vertical = 4.dp)
                )
            }
        }

        item {
            Text(
                text = "Lirik · ${lyrics.sourceAttribution}",
                color = Color(0x55FFFFFF),
                fontSize = 10.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            )
        }
    }
}

@Composable
private fun EqualizerView(
    eqState: com.nayama.music.playback.EqualizerState,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectPreset: (String) -> Unit,
    onBandChange: (Short, Short) -> Unit
) {
    if (!eqState.isAvailable) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Equalizer hardware belum diinisialisasi atau sesi belum aktif.",
                color = MediumGray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x55101012))
                    .border(0.5.dp, Color(0x28FFFFFF), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EQUALIZER ENGINE",
                    color = PureWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Switch(
                    checked = eqState.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PureBlack,
                        checkedTrackColor = PureWhite,
                        uncheckedThumbColor = MediumGray,
                        uncheckedTrackColor = SurfaceBlack
                    )
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Text(
                text = "PRESET AUDIO",
                color = LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            LazyRow(
                modifier = Modifier.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(eqState.presets, key = { it }) { preset ->
                    GlassPillChip(
                        text = preset,
                        isSelected = eqState.currentPreset == preset,
                        onClick = { onSelectPreset(preset) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Text(
                text = "FREKUENSI (5-BAND)",
                color = LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        items(eqState.bands, key = { it.index }) { band ->
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (band.centerFreqHz >= 1000) "${band.centerFreqHz / 1000} kHz" else "${band.centerFreqHz} Hz",
                        color = PureWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "%+d dB".format(band.currentLevel / 100),
                        color = LightGray,
                        fontSize = 11.sp
                    )
                }

                Slider(
                    value = band.currentLevel.toFloat(),
                    onValueChange = { onBandChange(band.index, it.toInt().toShort()) },
                    valueRange = band.minLevel.toFloat()..band.maxLevel.toFloat(),
                    enabled = eqState.isEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = PureWhite,
                        activeTrackColor = PureWhite,
                        inactiveTrackColor = Color(0x33FFFFFF)
                    )
                )
            }
        }
    }
}

@Composable
private fun PlaybackDetailsView(
    track: Track,
    playbackState: PlaybackState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "AUDIO STREAM SPECIFICATIONS",
            color = PureWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        DetailRow(label = "Provider", value = track.source.displayName)
        DetailRow(label = "Quality Badge", value = track.audioQualityLabel)
        DetailRow(label = "Audio Session ID", value = "${playbackState.audioSessionId ?: "Hardware Session Active"}")
        DetailRow(label = "Track ID", value = track.id)
        DetailRow(label = "Genre", value = track.genre ?: "Various / Indie")
        DetailRow(label = "Source Attribution", value = track.sourceAttribution)
        DetailRow(label = "Duration", value = "${track.durationSeconds} detik (${track.formattedDuration})")
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x55101012))
            .border(0.5.dp, Color(0x28FFFFFF), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = MediumGray, fontSize = 12.sp)
        Text(text = value, color = PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
