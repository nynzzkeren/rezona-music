package com.nayama.music.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nayama.music.navigation.NayamaScreen
import com.nayama.music.playback.MusicPlayerController
import com.nayama.music.ui.components.ExpandedPlayerModal
import com.nayama.music.ui.components.MiniPlayer
import com.nayama.music.ui.components.NayamaBottomBar
import com.nayama.music.ui.components.NayamaNavigationRail
import com.nayama.music.ui.explore.ExploreScreen
import com.nayama.music.ui.home.HomeScreen
import com.nayama.music.ui.library.LibraryScreen
import com.nayama.music.ui.profile.ProfileScreen
import com.nayama.music.ui.search.SearchScreen
import com.nayama.music.ui.theme.rememberAnimatedArtworkPalette

@Composable
fun NayamaApp() {
    val context = LocalContext.current
    val playerController = remember { MusicPlayerController.getInstance(context) }
    val playbackState by playerController.playbackState.collectAsStateWithLifecycle()

    var currentScreen by remember { mutableStateOf<NayamaScreen>(NayamaScreen.Home) }
    var isExpandedPlayerVisible by remember { mutableStateOf(false) }
    var isNavFolded by remember { mutableStateOf(false) }

    val currentTrack = playbackState.currentTrack
    val dynamicPalette = rememberAnimatedArtworkPalette(context, currentTrack?.artworkUrl)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ── 1. Dynamic Ambient Artwork Atmosphere ("Tema ikut cover") ───────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0B0D11),
                            Color(0xFF08090C),
                            Color(0xFF040507)
                        )
                    )
                )
        )

        // Top ambient diffuse glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .align(Alignment.TopCenter)
                .blur(85.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            dynamicPalette.primaryAmbient.copy(alpha = 0.52f),
                            dynamicPalette.secondaryAmbient.copy(alpha = 0.28f),
                            Color.Transparent
                        ),
                        radius = 900f
                    )
                )
        )

        // Bottom ambient diffuse glow behind mini-player & navbar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .align(Alignment.BottomCenter)
                .blur(80.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            dynamicPalette.primaryAmbient.copy(alpha = 0.45f),
                            dynamicPalette.highlightAccent.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        radius = 850f
                    )
                )
        )
        val isWideScreen = maxWidth >= 600.dp

        if (isWideScreen) {
            // ── Tablet / Wide Screen Responsive Shell (Navigation Rail + Split Content) ──
            Row(modifier = Modifier.fillMaxSize()) {
                NayamaNavigationRail(
                    currentScreen = currentScreen,
                    onScreenSelected = { screen -> currentScreen = screen }
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    ScreenHost(
                        currentScreen = currentScreen,
                        onNavigateScreen = { currentScreen = it },
                        onTrackSelect = { track -> playerController.playTrack(track) }
                    )

                    MiniPlayer(
                        playbackState = playbackState,
                        onPlayPauseClick = { playerController.togglePlayPause() },
                        onSkipNextClick = { playerController.skipToNext() },
                        onClick = {
                            if (playbackState.currentTrack != null) {
                                isExpandedPlayerVisible = true
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = 640.dp)
                    )
                }
            }
        } else {
            // ── Mobile Shell (Floating Mini-Player above Liquid Glass Bottom Bar) ──
            Box(modifier = Modifier.fillMaxSize()) {
                ScreenHost(
                    currentScreen = currentScreen,
                    onNavigateScreen = { currentScreen = it },
                    onScrollFoldChanged = { isNavFolded = it },
                    onTrackSelect = { track -> playerController.playTrack(track) }
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    MiniPlayer(
                        playbackState = playbackState,
                        onPlayPauseClick = { playerController.togglePlayPause() },
                        onSkipNextClick = { playerController.skipToNext() },
                        onClick = {
                            if (playbackState.currentTrack != null) {
                                isExpandedPlayerVisible = true
                            }
                        }
                    )

                    NayamaBottomBar(
                        currentScreen = currentScreen,
                        isFolded = isNavFolded,
                        onScreenSelected = { screen -> currentScreen = screen }
                    )
                }
            }
        }

        // Expanded Full Player Modal (Auto-switches to Split-Screen on Wide/Tablet)
        ExpandedPlayerModal(
            playbackState = playbackState,
            isVisible = isExpandedPlayerVisible,
            onDismiss = { isExpandedPlayerVisible = false },
            onPlayPauseClick = { playerController.togglePlayPause() },
            onSeek = { fraction -> playerController.seekToFraction(fraction) },
            onSkipNext = { playerController.skipToNext() },
            onSkipPrevious = { playerController.skipToPrevious() },
            onToggleShuffle = { playerController.toggleShuffle() },
            onToggleRepeat = { playerController.toggleRepeatMode() }
        )
    }
}

@Composable
private fun ScreenHost(
    currentScreen: NayamaScreen,
    onNavigateScreen: (NayamaScreen) -> Unit,
    onScrollFoldChanged: (Boolean) -> Unit = {},
    onTrackSelect: (com.nayama.music.model.Track) -> Unit
) {
    Crossfade(
        targetState = currentScreen,
        modifier = Modifier.fillMaxSize(),
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            NayamaScreen.Home -> HomeScreen(
                onTrackSelect = onTrackSelect,
                onAlbumSelect = { /* Navigates to album details */ },
                onNavigateToProfile = { onNavigateScreen(NayamaScreen.Profile) },
                onScrollFoldChanged = onScrollFoldChanged
            )
            NayamaScreen.Explore -> ExploreScreen(
                onCategorySelect = { /* Filter by category */ },
                onAlbumSelect = { /* Navigates to album */ },
                onTrackSelect = onTrackSelect
            )
            NayamaScreen.Search -> SearchScreen(
                onTrackSelect = onTrackSelect,
                onAlbumSelect = { /* Navigates to album */ }
            )
            NayamaScreen.Library -> LibraryScreen(
                onPlaylistSelect = { /* Navigates to playlist */ },
                onTrackSelect = onTrackSelect
            )
            NayamaScreen.Profile -> ProfileScreen()
        }
    }
}
