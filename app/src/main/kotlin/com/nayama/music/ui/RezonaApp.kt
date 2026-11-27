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
    val glassIntensityPref by preferences.glassIntensityFlow.collectAsState(initial = "High")
    val glassOpacityPercent by preferences.glassOpacityPercentFlow.collectAsState(initial = 72)
    val glassBlurRadiusDp by preferences.glassBlurRadiusDpFlow.collectAsState(initial = 22)
    val autoPlayBluetooth by preferences.autoPlayBluetoothFlow.collectAsState(initial = true)
    val dynamicIslandEnabled by preferences.dynamicIslandEnabledFlow.collectAsState(initial = false)
    val iosSidePlayerEnabled by preferences.iosSidePlayerEnabledFlow.collectAsState(initial = false)
    val islandCutoutMode by preferences.dynamicIslandCutoutModeFlow.collectAsState(initial = "Auto")
    var inAppIslandExpanded by remember { mutableStateOf(false) }
    var inAppIosSideOpen by remember { mutableStateOf(false) }
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

    LaunchedEffect(glassIntensityPref, glassOpacityPercent, glassBlurRadiusDp) {
        GlassIntensity.current = when (glassIntensityPref) {
            "Low" -> GlassIntensity.LOW
            "Medium" -> GlassIntensity.MEDIUM
            else -> GlassIntensity.HIGH
        }
        GlassIntensity.customOpacityPercent = glassOpacityPercent.toFloat()
        GlassIntensity.customBlurDp = glassBlurRadiusDp.toFloat()
    }

    val hazeState = remember { HazeState() }
    val playerController = remember { RezonaPlayerController.getInstance(context.applicationContext) }

    // Auto-Play when entering or inside the app with Bluetooth Speaker / Headset / Car Audio connected
    val audioOutputInfo = com.nayama.music.playback.rememberAudioOutputInfo(context)
    var lastAutoPlayedDevice by remember { mutableStateOf("") }
    LaunchedEffect(audioOutputInfo.isExternalDevice, audioOutputInfo.label, autoPlayBluetooth) {
        if (autoPlayBluetooth && audioOutputInfo.isExternalDevice) {
            if (lastAutoPlayedDevice != audioOutputInfo.label) {
                lastAutoPlayedDevice = audioOutputInfo.label
                playerController.triggerExternalAudioAutoPlay(audioOutputInfo.label)
            }
        } else if (!audioOutputInfo.isExternalDevice) {
            lastAutoPlayedDevice = ""
        }
    }

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
        val currentVersionCode = com.nayama.music.BuildConfig.VERSION_CODE
        val lastSeenAppVersion = preferences.lastSeenAppVersionCodeFlow.first()
        val dismissedVersion = preferences.dismissedUpdateVersionFlow.first()
        val dismissedAnnouncement = preferences.dismissedAnnouncementIdFlow.first()
        val info = RezonaUpdateChecker.checkForUpdate()

        if (info != null) {
            val shouldShow = RezonaUpdateChecker.shouldShowBulletin(
                info = info,
                currentAppVersionCode = currentVersionCode,
                lastSeenAppVersionCode = lastSeenAppVersion,
                dismissedUpdateVersion = dismissedVersion,
                dismissedAnnouncementId = dismissedAnnouncement
            )
            if (shouldShow) {
                availableUpdate = info
                RezonaUpdateChecker.postSystemAnnouncementNotification(context, info)
            } else if (!info.isNewerApk && lastSeenAppVersion < currentVersionCode) {
                // First-time install or fresh update to the latest APK: silently mark baseline as seen
                // so no redundant "update" or old changelog notification pops up.
                if (info.announcementId.isNotBlank()) {
                    preferences.setDismissedAnnouncementId(info.announcementId)
                }
                preferences.setDismissedUpdateVersion(currentVersionCode)
            }
        }
        if (lastSeenAppVersion < currentVersionCode) {
            preferences.setLastSeenAppVersionCode(currentVersionCode)
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

            // 3. Universal In-App Dynamic Island fallback (for devices before/without system overlay permission)
            if (dynamicIslandEnabled &&
                !android.provider.Settings.canDrawOverlays(context) &&
                currentTrack != null &&
                !isExpandedPlayerVisible &&
                !isSettingsVisible &&
                !isSoundLabVisible
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (islandCutoutMode == "Overlap") 8.dp else 34.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    com.nayama.music.overlay.RezonaDynamicIslandCapsule(
                        controller = playerController,
                        isExpanded = inAppIslandExpanded,
                        onExpandedChange = { inAppIslandExpanded = it },
                        onOpenApp = {
                            inAppIslandExpanded = false
                            isExpandedPlayerVisible = true
                        }
                    )
                }
            }

            // 3b. Full-Screen Expanded Now Playing + Inline Lyrics
            RezonaExpandedPlayer(
                controller = playerController,
                isVisible = isExpandedPlayerVisible,
                onDismiss = { isExpandedPlayerVisible = false }
            )

            // 3c. Universal In-App iOS Control Center Side-Edge Music Handle & Pull-Out Card
            if (iosSidePlayerEnabled && currentTrack != null) {
                if (!inAppIosSideOpen) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        com.nayama.music.overlay.RezonaIosSideEdgeHandle(
                            onOpenCard = { inAppIosSideOpen = true }
                        )
                    }
                }
                com.nayama.music.overlay.RezonaIosMusicCardOverlay(
                    controller = playerController,
                    isOpen = inAppIosSideOpen,
                    onClose = { inAppIosSideOpen = false }
                )
            }

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

            // 8. In-App Update & Announcement Bulletin Notifier
            availableUpdate?.let { updateInfo ->
                val markSeen: () -> Unit = {
                    scope.launch {
                        preferences.setDismissedUpdateVersion(updateInfo.versionCode)
                        if (updateInfo.announcementId.isNotBlank()) {
                            preferences.setDismissedAnnouncementId(updateInfo.announcementId)
                        }
                    }
                    availableUpdate = null
                }
                RezonaGlassDialog(
                    title = updateInfo.title.ifBlank { "Update Rezona v${updateInfo.versionName}" },
                    onDismissRequest = markSeen,
                    confirmText = if (updateInfo.isNewerApk) "Update APK" else "Got It",
                    onConfirm = {
                        if (updateInfo.isNewerApk) {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.apkUrl))
                                context.startActivity(intent)
                            }
                        }
                        markSeen()
                    },
                    dismissText = if (updateInfo.isNewerApk) "Later" else null
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (updateInfo.added.isNotEmpty()) {
                            Text(
                                text = "1. Add :",
                                style = Typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = PureWhite
                            )
                            updateInfo.added.forEach { item ->
                                Text("• $item", style = Typography.bodySmall, color = PearlWhite)
                            }
                        }
                        if (updateInfo.fixed.isNotEmpty()) {
                            Text(
                                text = "2. Fix :",
                                style = Typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = PureWhite
                            )
                            updateInfo.fixed.forEach { item ->
                                Text("• $item", style = Typography.bodySmall, color = PearlWhite)
                            }
                        }
                        if (updateInfo.improved.isNotEmpty()) {
                            Text(
                                text = "3. Improved :",
                                style = Typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = PureWhite
                            )
                            updateInfo.improved.forEach { item ->
                                Text("• $item", style = Typography.bodySmall, color = PearlWhite)
                            }
                        }
                        if (updateInfo.changelog.isNotBlank()) {
                            Text(
                                text = updateInfo.changelog,
                                style = Typography.bodySmall,
                                color = SoftSilver
                            )
                        }
                    }
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
