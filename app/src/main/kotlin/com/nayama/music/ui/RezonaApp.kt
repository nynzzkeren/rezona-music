package com.nayama.music.ui

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nayama.music.auth.FirebaseAuthManager
import com.nayama.music.backend.RezonaUpdateChecker
import com.nayama.music.backend.RezonaUpdateInfo
import com.nayama.music.data.preferences.RezonaPreferences
import com.nayama.music.navigation.RezonaTab
import com.nayama.music.playback.RezonaPlayerController
import com.nayama.music.sync.RezonaCloudSyncManager
import com.nayama.music.ui.auth.RezonaAuthScreen
import com.nayama.music.ui.components.GlassIntensity
import com.nayama.music.ui.components.LocalAmbientAccentColor
import com.nayama.music.ui.components.LocalAmbientBaseColor
import com.nayama.music.ui.components.LocalRezonaHazeState
import com.nayama.music.ui.components.LocalTintedGlassColor
import com.nayama.music.ui.theme.ArtworkColorExtractor
import com.nayama.music.ui.theme.rememberAnimatedArtworkPalette
import com.nayama.music.ui.components.RezonaBottomBar
import com.nayama.music.ui.components.RezonaContinuousAmbientLayer
import com.nayama.music.ui.components.RezonaGlassButton
import com.nayama.music.ui.components.RezonaGlassCard
import com.nayama.music.ui.components.RezonaGlassDialog
import com.nayama.music.ui.components.RezonaMiniPlayer
import com.nayama.music.ui.components.RezonaNavigationRail
import com.nayama.music.ui.components.deriveAmbientColors
import com.nayama.music.ui.home.RezonaHomeScreen
import com.nayama.music.ui.library.RezonaLibraryScreen
import com.nayama.music.ui.player.RezonaExpandedPlayer
import com.nayama.music.ui.search.RezonaSearchScreen
import com.nayama.music.ui.settings.RezonaSettingsScreen
import com.nayama.music.ui.studio.RezonaStudioScreen
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.Typography
import com.nayama.music.ui.you.RezonaYouScreen
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    val authManager = remember { FirebaseAuthManager.getInstance(context) }
    val syncManager = remember { RezonaCloudSyncManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    val dockFoldEnabled by preferences.dockFoldOnScrollFlow.collectAsState(initial = true)
    val authGateSeen by preferences.authGateSeenFlow.collectAsState(initial = null as Boolean?)
    val authSession by authManager.session.collectAsState()
    val syncStatus by syncManager.status.collectAsState()
    var showManualAuthOverlay by remember { mutableStateOf(false) }

    if (authGateSeen == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050608)))
        return
    }

    val mustShowAuthScreen = authGateSeen == false ||
        showManualAuthOverlay ||
        authSession.needsEmailVerification

    if (mustShowAuthScreen) {
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
    var availableUpdate by remember { mutableStateOf<RezonaUpdateInfo?>(null) }
    var lastBackPressMs by remember { mutableStateOf(0L) }

    // Prevent accidental immediate exit on back gesture:
    // Close overlays -> return to Home tab -> require double-back within 2s to exit.
    BackHandler(enabled = true) {
        when {
            isExpandedPlayerVisible -> isExpandedPlayerVisible = false
            isSettingsVisible -> isSettingsVisible = false
            isSoundLabVisible -> isSoundLabVisible = false
            currentTab != RezonaTab.Home -> currentTab = RezonaTab.Home
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressMs < 2000L) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPressMs = now
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val dismissedVersion = preferences.dismissedUpdateVersionFlow.first()
        val info = RezonaUpdateChecker.checkForUpdate()
        if (info != null && info.versionCode > dismissedVersion) {
            availableUpdate = info
        }
    }

    val currentTrack = playerController.currentTrack.value
    LaunchedEffect(currentTrack) {
        if (currentTrack == null) {
            isExpandedPlayerVisible = false
        }
    }

    val artworkPalette = rememberAnimatedArtworkPalette(
        context = context,
        artworkUrl = currentTrack?.coverArtUrl,
        fallbackKey = currentTrack?.id
    )

    // Precompute palette for upcoming queued tracks asynchronously before track change to prevent flashes
    LaunchedEffect(playerController.queue.size, currentTrack?.id) {
        val nextTracks = playerController.queue.take(3)
        nextTracks.forEach { upcoming ->
            ArtworkColorExtractor.prefetch(context, upcoming.coverArtUrl)
        }
    }

    val effectiveFolded = isNavFolded && dockFoldEnabled

    CompositionLocalProvider(
        LocalRezonaHazeState provides hazeState,
        LocalAmbientAccentColor provides artworkPalette.primaryAmbient,
        LocalAmbientBaseColor provides artworkPalette.ambientBase,
        LocalTintedGlassColor provides artworkPalette.glassTint
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Root Continuous Ambient Layer (Edge-to-Edge, Cover Color Family, Zero Seams)
            RezonaContinuousAmbientLayer(
                primaryColor = artworkPalette.primaryAmbient,
                secondaryColor = artworkPalette.secondaryAmbient,
                tertiaryColor = artworkPalette.tertiaryAmbient,
                ambientBase = artworkPalette.ambientBase,
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

            // 6. Initial Cloud Restore Progress Overlay
            if (syncStatus.isRestoring && !syncStatus.showGuestUploadPrompt) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC05070B)),
                    contentAlignment = Alignment.Center
                ) {
                    RezonaGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = PureWhite, strokeWidth = 2.5.dp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Restoring your library...",
                                style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = PureWhite
                            )
                            Text(
                                text = "Fetching your likes, playlists & settings from cloud.",
                                style = Typography.bodySmall,
                                color = SoftSilver
                            )
                        }
                    }
                }
            }

            // 7. First Sign-In Guest Library Upload & Merge Prompt
            if (syncStatus.showGuestUploadPrompt) {
                RezonaGlassDialog(
                    title = "Upload Guest Library?",
                    onDismissRequest = {
                        scope.launch { syncManager.respondToGuestUploadPrompt(uploadAndMerge = false) }
                    },
                    dismissText = null,
                    onConfirm = null
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Upload your guest library to your account? Local likes, playlists, and pins will merge without duplicates.",
                            style = Typography.bodyMedium,
                            color = PearlWhite
                        )
                        RezonaGlassButton(
                            text = "Upload & Merge",
                            isPrimary = true,
                            onClick = {
                                scope.launch { syncManager.respondToGuestUploadPrompt(uploadAndMerge = true) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        )
                        RezonaGlassButton(
                            text = "Keep Cloud Only",
                            isPrimary = false,
                            onClick = {
                                scope.launch { syncManager.respondToGuestUploadPrompt(uploadAndMerge = false) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        )
                    }
                }
            }

            // 8. In-App Update Notifier
            availableUpdate?.let { updateInfo ->
                RezonaGlassDialog(
                    title = "Update Available · v${updateInfo.versionName}",
                    onDismissRequest = {
                        scope.launch {
                            preferences.setDismissedUpdateVersion(updateInfo.versionCode)
                        }
                        availableUpdate = null
                    },
                    confirmText = "Download APK",
                    onConfirm = {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.apkUrl))
                            context.startActivity(intent)
                        }
                        availableUpdate = null
                    },
                    dismissText = "Later"
                ) {
                    Text(
                        text = updateInfo.changelog,
                        style = Typography.bodyMedium,
                        color = PearlWhite
                    )
                }
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
