package com.nayama.music.ui

import android.app.ActivityManager
import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nayama.music.data.preferences.RezonaPreferences
import com.nayama.music.navigation.RezonaTab
import com.nayama.music.playback.RezonaPlayerController
import com.nayama.music.ui.auth.RezonaAuthScreen
import com.nayama.music.ui.components.GlassIntensity
import com.nayama.music.ui.components.LocalAmbientAccentColor
import com.nayama.music.ui.components.LocalRezonaHazeState
import com.nayama.music.ui.components.RezonaBottomBar
import com.nayama.music.ui.components.RezonaContinuousAmbientLayer
import com.nayama.music.ui.components.RezonaMiniPlayer
import com.nayama.music.ui.components.RezonaNavigationRail
import com.nayama.music.ui.components.deriveAmbientColors
import com.nayama.music.ui.home.RezonaHomeScreen
import com.nayama.music.ui.library.RezonaLibraryScreen
import com.nayama.music.ui.player.RezonaExpandedPlayer
import com.nayama.music.ui.search.RezonaSearchScreen
import com.nayama.music.ui.settings.RezonaSettingsScreen
import com.nayama.music.ui.studio.RezonaStudioScreen
import com.nayama.music.ui.you.RezonaYouScreen
import dev.chrisbanes.haze.HazeState

private fun isLowEndDevice(context: Context): Boolean {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)
    val totalMb = memInfo.totalMem / (1024L * 1024L)
    return totalMb < 3072L
}

/**
 * Rezona Root Application Shell (UI V2 — Stages A through H Unified):
 * - 4 Primary Tabs: Home, Search, Library, You (Legacy Studio accessible via You > Sound Lab flag)
 * - Continuous full-screen edge-to-edge ambient layer shared across all screens
 * - Morphing Floating Dock (4-tab capsule -> collapsed round button on scroll down) + widening Mini Player
 */
@Composable
fun RezonaApp() {
    val context = LocalContext.current
    val preferences = remember { RezonaPreferences(context) }
    val dockFoldEnabled by preferences.dockFoldOnScrollFlow.collectAsState(initial = true)
    val authGateSeen by preferences.authGateSeenFlow.collectAsState(initial = null as Boolean?)
    var showManualAuthOverlay by remember { mutableStateOf(false) }

    if (authGateSeen == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050608)))
        return
    }

    if (authGateSeen == false || showManualAuthOverlay) {
        RezonaAuthScreen(
            onAuthenticated = { showManualAuthOverlay = false }
        )
        return
    }

    val isLowEnd = remember { isLowEndDevice(context) }
    val defaultIntensity = remember { if (isLowEnd) GlassIntensity.LOW else GlassIntensity.MEDIUM }
    remember { GlassIntensity.current = defaultIntensity }

    val hazeState = remember { HazeState() }
    val playerController = remember { RezonaPlayerController.getInstance(context.applicationContext) }

    var currentTab by remember { mutableStateOf(RezonaTab.Home) }
    var isNavFolded by remember { mutableStateOf(false) }
    var isExpandedPlayerVisible by remember { mutableStateOf(false) }
    var settingsSection by remember { mutableStateOf<String?>(null) }
    var isSettingsVisible by remember { mutableStateOf(false) }
    var isSoundLabVisible by remember { mutableStateOf(false) }

    val currentTrack = playerController.currentTrack.value
    LaunchedEffect(currentTrack) {
        if (currentTrack == null) {
            isExpandedPlayerVisible = false
        }
    }

    val (ambientPrimary, ambientSecondary, ambientTertiary) = remember(currentTrack?.id, currentTrack?.title, currentTrack?.artist) {
        if (currentTrack != null) {
            deriveAmbientColors(currentTrack)
        } else {
            Triple(Color(0xFF151822), Color(0xFF0C0E14), Color(0xFF06070A))
        }
    }

    val effectiveFolded = isNavFolded && dockFoldEnabled

    CompositionLocalProvider(
        LocalRezonaHazeState provides hazeState,
        LocalAmbientAccentColor provides ambientPrimary
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Root Continuous Ambient Layer (Edge-to-Edge, Zero Seams)
            RezonaContinuousAmbientLayer(
                primaryColor = ambientPrimary,
                secondaryColor = ambientSecondary,
                tertiaryColor = ambientTertiary,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Navigation Scaffolding (Tabs handle statusBarsPadding cleanly for seamless edge-to-edge scrolling)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                val isTablet = maxWidth >= 600.dp

                if (isTablet) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        RezonaNavigationRail(
                            currentTab = currentTab,
                            onTabSelected = { currentTab = it },
                            hazeState = hazeState
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            TabContent(
                                currentTab = currentTab,
                                playerController = playerController,
                                isNavFolded = false,
                                onFoldChanged = {},
                                onSelectTab = { currentTab = it },
                                onOpenSettings = { sec ->
                                    settingsSection = sec
                                    isSettingsVisible = true
                                },
                                onOpenSoundLab = { isSoundLabVisible = true },
                                onOpenAuth = { showManualAuthOverlay = true }
                            )
                            if (currentTrack != null) {
                                RezonaMiniPlayer(
                                    controller = playerController,
                                    isNavFolded = false,
                                    onExpandPlayer = { isExpandedPlayerVisible = true },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        TabContent(
                            currentTab = currentTab,
                            playerController = playerController,
                            isNavFolded = effectiveFolded,
                            onFoldChanged = { folded -> isNavFolded = folded },
                            onSelectTab = {
                                currentTab = it
                                isNavFolded = false
                            },
                            onOpenSettings = { sec ->
                                settingsSection = sec
                                isSettingsVisible = true
                            },
                            onOpenSoundLab = { isSoundLabVisible = true },
                            onOpenAuth = { showManualAuthOverlay = true }
                        )

                        // Morphing Mini Player (only shown once a track is loaded) + Morphing 4-Tab Dock
                        if (currentTrack != null) {
                            RezonaMiniPlayer(
                                controller = playerController,
                                isNavFolded = effectiveFolded,
                                onExpandPlayer = { isExpandedPlayerVisible = true },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                        RezonaBottomBar(
                            currentTab = currentTab,
                            onTabSelected = {
                                currentTab = it
                                isNavFolded = false
                            },
                            isFolded = effectiveFolded,
                            onExpandDock = { isNavFolded = false },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            // 3. Full-Screen Expanded Now Playing + Inline Lyrics
            RezonaExpandedPlayer(
                controller = playerController,
                isVisible = isExpandedPlayerVisible,
                onDismiss = { isExpandedPlayerVisible = false }
            )

            // 4. Legacy Sound Lab (behind feature flag in You tab)
            if (isSoundLabVisible) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RezonaStudioScreen(
                        controller = playerController,
                        onFoldChanged = {},
                        onOpenSettings = {
                            isSoundLabVisible = false
                            settingsSection = "Sound"
                            isSettingsVisible = true
                        }
                    )
                    com.nayama.music.ui.components.RezonaGlassPill(
                        isActive = true,
                        onClick = { isSoundLabVisible = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 46.dp, end = 20.dp)
                    ) {
                        androidx.compose.material3.Text(
                            text = "Done",
                            color = com.nayama.music.ui.theme.PureWhite,
                            style = com.nayama.music.ui.theme.Typography.labelLarge
                        )
                    }
                }
            }

            // 5. Settings Overlay
            if (isSettingsVisible) {
                RezonaSettingsScreen(
                    initialSection = settingsSection,
                    onDismiss = { isSettingsVisible = false }
                )
            }
        }
    }
}

@Composable
private fun TabContent(
    currentTab: RezonaTab,
    playerController: RezonaPlayerController,
    isNavFolded: Boolean,
    onFoldChanged: (Boolean) -> Unit,
    onSelectTab: (RezonaTab) -> Unit,
    onOpenSettings: (String?) -> Unit,
    onOpenSoundLab: () -> Unit,
    onOpenAuth: () -> Unit
) {
    Crossfade(
        targetState = currentTab,
        label = "tab_crossfade"
    ) { tab ->
        when (tab) {
            RezonaTab.Home -> RezonaHomeScreen(
                controller = playerController,
                onFoldChanged = onFoldChanged,
                onOpenSettings = { onOpenSettings(null) },
                onOpenSoundCheck = onOpenSoundLab,
                onOpenSessions = onOpenSoundLab,
                onOpenTimeCapsule = { onSelectTab(RezonaTab.Library) }
            )
            RezonaTab.Search -> RezonaSearchScreen(
                controller = playerController,
                isNavFolded = isNavFolded,
                onFoldChanged = onFoldChanged,
                onBack = { onSelectTab(RezonaTab.Home) },
                onTrackSelected = { playerController.playTrack(it, autoQueueRadio = true) }
            )
            RezonaTab.Library -> RezonaLibraryScreen(
                controller = playerController,
                isNavFolded = isNavFolded,
                onFoldChanged = onFoldChanged,
                onTrackSelected = { playerController.playTrackByIndex(it) },
                onPlayTrack = { playerController.playTrack(it, autoQueueRadio = true) }
            )
            RezonaTab.You -> RezonaYouScreen(
                controller = playerController,
                onFoldChanged = onFoldChanged,
                onOpenSettingsSection = onOpenSettings,
                onOpenSoundLab = onOpenSoundLab,
                onOpenAuth = onOpenAuth
            )
        }
    }
}
