package com.nayama.music.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nayama.music.R
import com.nayama.music.data.preferences.RezonaPreferences
import com.nayama.music.model.RezonaTrack
import com.nayama.music.playback.RepeatMode
import com.nayama.music.playback.RezonaPlayerController
import com.nayama.music.playback.SoundPreset
import com.nayama.music.ui.components.GlassSurface
import com.nayama.music.ui.components.GlassSurfaceVariant
import com.nayama.music.ui.components.LocalAmbientAccentColor
import com.nayama.music.ui.components.LocalAmbientBaseColor
import com.nayama.music.ui.components.LocalTintedGlassColor
import com.nayama.music.ui.components.RezonaAsyncCoverArt
import com.nayama.music.ui.components.RezonaContinuousAmbientLayer
import com.nayama.music.ui.components.RezonaEqualizerModalSheet
import com.nayama.music.ui.components.RezonaGlassButton
import com.nayama.music.ui.components.RezonaGlassDialog
import com.nayama.music.ui.components.RezonaGlassPill
import com.nayama.music.ui.components.RezonaGlassSlider
import com.nayama.music.ui.components.RezonaGlassTextField
import com.nayama.music.ui.components.RezonaHairlineDivider
import com.nayama.music.ui.components.RezonaSongCardRenderer
import com.nayama.music.ui.components.RezonaTrackMenuSheet
import com.nayama.music.ui.lyrics.PosterRenderer
import com.nayama.music.ui.lyrics.RezonaInlineLyricsSection
import com.nayama.music.ui.lyrics.RezonaPosterStudio
import com.nayama.music.ui.theme.DynamicArtworkPalette
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.SpecularCardBorder
import com.nayama.music.ui.theme.SpecularDockBorder
import com.nayama.music.ui.theme.Typography
import com.nayama.music.ui.theme.rememberAnimatedArtworkPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Rezona Now Playing Screen (V2 Stage D + Stage F Inline Lyrics):
 * - Full-bleed edge-to-edge cover extending under status bar, blending vertically into ambient dark tone
 * - Top-left overlay: "Now playing" + bold italic queue name (tap -> Queue sheet)
 * - Left-aligned Title & Artist + two 48dp glass circle buttons (Heart & 3-dot Menu)
 * - Clean progress line (or Waveform in Settings), Bare transport row (transient glass bubble on press),
 *   System STREAM_MUSIC volume row, 5 one-tap Sound Presets row, 3-button glass pill (Lyrics | Share | Sleep timer)
 * - Tapping Lyrics seamlessly morphs top view to inline Lyrics (lyrics.jpg) sharing the same bottom controls!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RezonaExpandedPlayer(
    controller: RezonaPlayerController,
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { RezonaPreferences(context) }

    val scrubberStyle by preferences.scrubberStyleFlow.collectAsState(initial = "Line")
    val vividAmbient by preferences.ambientVividPlayerFlow.collectAsState(initial = true)

    val track = controller.currentTrack.value ?: return
    val isPlaying = controller.isPlaying.value
    val isLiked = controller.isLiked.value
    val currentPosition = controller.currentPositionMs.longValue
    val duration = controller.durationMs.longValue
    val activePreset = controller.activeSoundPreset.value
    val sleepSecRemaining = controller.sleepTimerSecondsRemaining.value
    val sleepEndOfTrack = controller.sleepTimerEndOfTrack.value
    val hasVideoStream = controller.hasVideoStream.value
    val isVideoMode = controller.isVideoMode.value

    var isLyricsMode by remember { mutableStateOf(false) }
    var showSoundSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showTrackMenuSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showPosterStudio by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showAmbientMode by remember { mutableStateOf(false) }
    var showHeartBurst by remember { mutableStateOf(false) }
    var dismissDragY by remember { mutableFloatStateOf(0f) }

    // Part 3: Pause video decoding in background (keep audio playing), resume video on return
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> controller.setAppInForeground(false)
                Lifecycle.Event.ON_START -> controller.setAppInForeground(true)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            controller.syncSystemVolume()
        }
    }

    val bars = remember(track.index) { WaveformGenerator.generateBars(track.index, count = 44) }
    val artworkPalette = rememberAnimatedArtworkPalette(
        context = context,
        artworkUrl = track.coverArtUrl,
        fallbackKey = track.id
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.84f, stiffness = 290f)
        ) + fadeIn(tween(220)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.84f, stiffness = 290f)
        ) + fadeOut(tween(180))
    ) {
        CompositionLocalProvider(
            LocalAmbientBaseColor provides artworkPalette.ambientBase,
            LocalAmbientAccentColor provides artworkPalette.primaryAmbient,
            LocalTintedGlassColor provides artworkPalette.glassTint
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, dismissDragY.coerceAtLeast(0f).roundToInt()) }
            ) {
                // ── 1. CONTINUOUS COVER-MATCHED AMBIENT LAYER (ZERO BLACK/PURPLE BOXES) ─
                RezonaContinuousAmbientLayer(
                    primaryColor = artworkPalette.primaryAmbient,
                    secondaryColor = artworkPalette.secondaryAmbient,
                    tertiaryColor = artworkPalette.tertiaryAmbient,
                    ambientBase = artworkPalette.ambientBase,
                    vividMode = vividAmbient,
                    modifier = Modifier.fillMaxSize()
                )

                // ── 2. FULL-BLEED COVER (y = 0, height ~1.25x screenWidth, DstIn Alpha Mask) ─
                if (!isLyricsMode && !isVideoMode) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val coverHeight = (maxWidth * 1.25f).coerceAtMost(maxHeight * 0.64f)
                        Crossfade(
                            targetState = track.coverArtUrl.ifBlank { "idx_${track.index}" },
                            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                            label = "cover_blend_crossfade",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(coverHeight)
                                .align(Alignment.TopCenter)
                        ) { _ ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }
                                    .drawWithContent {
                                        drawContent()
                                        // Smooth cubic-eased alpha mask over lower ~42% -> dissolves seamlessly into ambientBase
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                0.00f to Color.White,
                                                0.54f to Color.White,
                                                0.66f to Color.White.copy(alpha = 0.86f),
                                                0.78f to Color.White.copy(alpha = 0.48f),
                                                0.90f to Color.White.copy(alpha = 0.14f),
                                                1.00f to Color.Transparent
                                            ),
                                            blendMode = BlendMode.DstIn
                                        )
                                    }
                            ) {
                                RezonaAsyncCoverArt(
                                    coverArtUrl = track.coverArtUrl,
                                    fallbackIndex = track.index,
                                    shape = RoundedCornerShape(0.dp),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // ── 3. MAIN COLUMN (TOP CONTENT + SHARED BOTTOM CONTROL DECK) ────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    AnimatedContent(
                        targetState = isLyricsMode,
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                        modifier = Modifier.weight(1f),
                        label = "player_top_mode"
                    ) { lyricsActive ->
                        if (lyricsActive) {
                            RezonaInlineLyricsSection(
                                controller = controller,
                                onCollapseToCover = { isLyricsMode = false },
                                onOpenPosterFromShare = { showPosterStudio = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            FullBleedCoverTopSection(
                                track = track,
                                queueName = controller.activeQueueName.value,
                                isLiked = isLiked,
                                palette = artworkPalette,
                                hasVideoStream = hasVideoStream,
                                isVideoMode = isVideoMode,
                                exoPlayerProvider = { controller.getExoPlayer() },
                                onToggleVideoMode = { enableVideo -> controller.setVideoMode(enableVideo) },
                                onOpenQueue = { showQueueSheet = true },
                                onDoubleTapLike = {
                                    controller.toggleLike()
                                    showHeartBurst = true
                                    scope.launch {
                                        delay(850L)
                                        showHeartBurst = false
                                    }
                                },
                                onSwipeNext = { controller.skipNext() },
                                onSwipePrev = { controller.skipPrevious() },
                                onSwipeDownDelta = { dy ->
                                    dismissDragY = (dismissDragY + dy).coerceAtLeast(0f)
                                },
                                onSwipeDownRelease = {
                                    if (dismissDragY > 180f) onDismiss()
                                    dismissDragY = 0f
                                },
                                onLikeClick = { controller.toggleLike() },
                                onMenuClick = { showTrackMenuSheet = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Bottom Shared Control Deck (Part 1 Tinted Glass + Part 2 4-Icon Bottom Pill)
                    NowPlayingControlDeck(
                        bars = bars,
                        scrubberStyle = scrubberStyle,
                        currentPositionMs = currentPosition,
                        durationMs = duration,
                        isPlaying = isPlaying,
                        isShuffle = controller.isShuffle.value,
                        repeatMode = controller.repeatMode.value,
                        volumeFraction = controller.volume.floatValue,
                        activePreset = activePreset,
                        isLyricsMode = isLyricsMode,
                        isSoundSheetOpen = showSoundSheet,
                        sleepSecRemaining = sleepSecRemaining,
                        sleepEndOfTrack = sleepEndOfTrack,
                        onSeekFraction = { controller.seekToFraction(it) },
                        onToggleShuffle = { controller.toggleShuffle() },
                        onSkipPrev = { controller.skipPrevious() },
                        onPlayPause = { controller.togglePlayPause() },
                        onSkipNext = { controller.skipNext() },
                        onToggleRepeat = { controller.toggleRepeat() },
                        onVolumeChange = { controller.setVolume(it) },
                        onToggleLyrics = { isLyricsMode = !isLyricsMode },
                        onOpenShare = { showShareSheet = true },
                        onOpenSleepTimer = { showSleepTimerSheet = true },
                        onOpenSoundSheet = { showSoundSheet = true }
                    )
                }

                if (showHeartBurst) {
                    HeartBurstOverlay(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }

    if (showSoundSheet) {
        CompositionLocalProvider(
            LocalAmbientBaseColor provides artworkPalette.ambientBase,
            LocalAmbientAccentColor provides artworkPalette.primaryAmbient,
            LocalTintedGlassColor provides artworkPalette.glassTint
        ) {
            RezonaSoundEffectsSheet(
                activePreset = activePreset,
                onSelectPreset = { preset -> controller.applySoundPreset(preset) },
                onDismiss = { showSoundSheet = false }
            )
        }
    }

    // ── SHEETS & MODALS ──────────────────────────────────────────────────────
    if (showTrackMenuSheet) {
        RezonaTrackMenuSheet(
            track = track,
            controller = controller,
            showVolumeSlider = true,
            onDismiss = { showTrackMenuSheet = false },
            onOpenEqualizer = { showEqualizerSheet = true },
            onOpenAmbientMode = { showAmbientMode = true }
        )
    }

    if (showQueueSheet) {
        QueueBottomSheet(
            queueName = controller.activeQueueName.value,
            currentTrack = track,
            queue = controller.queue,
            history = controller.history,
            onPlayTrack = { controller.playTrack(it, queueTitle = controller.activeQueueName.value) },
            onJumpToQueueIndex = { controller.jumpToQueueIndex(it) },
            onRemoveQueueIndex = { controller.removeFromQueue(it) },
            onReorder = { from, to -> controller.reorderQueue(from, to) },
            onShuffleQueue = { controller.shuffleQueue() },
            onClearQueue = { controller.clearQueue() },
            onRestoreFromHistory = { controller.restoreFromHistory(it) },
            onDismiss = { showQueueSheet = false }
        )
    }

    if (showShareSheet) {
        ShareOptionsBottomSheet(
            track = track,
            onShareLink = {
                showShareSheet = false
                val url = track.streamUrl.ifBlank { "https://music.youtube.com/watch?v=${track.id.removePrefix("yt_")}" }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "${track.title} — ${track.artist}\n$url")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Share Song Link").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            },
            onShareCard = {
                showShareSheet = false
                scope.launch { RezonaSongCardRenderer.shareSongCard(context, track) }
            },
            onShareLyricsText = {
                showShareSheet = false
                val text = controller.currentLyrics.value.take(6).joinToString("\n") { it.text }
                    .ifBlank { "${track.title} — ${track.artist}" }
                PosterRenderer.shareText(context, listOf(text), track.title, track.artist)
            },
            onShareLyricsPoster = {
                showShareSheet = false
                showPosterStudio = true
            },
            onDismiss = { showShareSheet = false }
        )
    }

    if (showSleepTimerSheet) {
        SleepTimerBottomSheet(
            currentMinutes = controller.sleepTimerMinutes.value,
            sleepEndOfTrack = sleepEndOfTrack,
            remainingSeconds = sleepSecRemaining,
            onSelectMinutes = {
                controller.setSleepTimer(it)
                showSleepTimerSheet = false
            },
            onSelectEndOfTrack = {
                controller.setSleepTimerEndOfTrack()
                showSleepTimerSheet = false
            },
            onDismiss = { showSleepTimerSheet = false }
        )
    }

    if (showPosterStudio) {
        val allLineTexts = controller.currentLyrics.value.map { it.text }
        RezonaPosterStudio(
            track = track,
            allLines = allLineTexts,
            initialSelectedLines = allLineTexts.take(3),
            onDismiss = { showPosterStudio = false }
        )
    }

    if (showEqualizerSheet) {
        RezonaEqualizerModalSheet(onDismiss = { showEqualizerSheet = false })
    }

    if (showAmbientMode) {
        AmbientCalmCoverOverlay(
            track = track,
            isPlaying = isPlaying,
            onTogglePlayPause = { controller.togglePlayPause() },
            onDismiss = { showAmbientMode = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FULL-BLEED COVER TOP SECTION (`now_playing.jpg`)
// ─────────────────────────────────────────────────────────────────────────────

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun FullBleedCoverTopSection(
    track: RezonaTrack,
    queueName: String,
    isLiked: Boolean,
    palette: DynamicArtworkPalette,
    hasVideoStream: Boolean,
    isVideoMode: Boolean,
    exoPlayerProvider: () -> androidx.media3.exoplayer.ExoPlayer?,
    onToggleVideoMode: (Boolean) -> Unit,
    onOpenQueue: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onSwipeNext: () -> Unit,
    onSwipePrev: () -> Unit,
    onSwipeDownDelta: (Float) -> Unit,
    onSwipeDownRelease: () -> Unit,
    onLikeClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var swipeX by remember { mutableFloatStateOf(0f) }
    val animX by animateFloatAsState(swipeX, spring(dampingRatio = 0.78f, stiffness = 340f), label = "cover_x")
    val view = LocalView.current

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Upper Gesture / Video Area (cover itself is drawn full-bleed behind this in parent with DstIn mask!)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onDoubleTapLike() })
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount -> swipeX += dragAmount * 0.6f },
                        onDragEnd = {
                            if (swipeX > 120f) onSwipePrev()
                            else if (swipeX < -120f) onSwipeNext()
                            swipeX = 0f
                        },
                        onDragCancel = { swipeX = 0f }
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dy -> onSwipeDownDelta(dy) },
                        onDragEnd = { onSwipeDownRelease() },
                        onDragCancel = { onSwipeDownRelease() }
                    )
                }
        ) {
            // Minimal top contrast guard ONLY if cover top strip drops below 4.5:1 against white header
            if (palette.needsTopContrastScrim && !isVideoMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(palette.ambientBase.copy(alpha = 0.62f), Color.Transparent)
                            )
                        )
                )
            }

            // Part 3: Centered 16:9 Video Surface in Video Mode over continuous ambient background
            if (isVideoMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .align(Alignment.Center)
                        .offset { IntOffset(animX.roundToInt(), 0) }
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                player = exoPlayerProvider()
                            }
                        },
                        update = { playerView ->
                            val activePlayer = exoPlayerProvider()
                            if (playerView.player != activePlayer) {
                                playerView.player = activePlayer
                            }
                        },
                        onRelease = { playerView ->
                            playerView.player = null
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Top-left Queue Header ("Now playing" + bold italic queue name)
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenQueue)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Now playing",
                    style = Typography.bodySmall.copy(fontSize = 12.sp),
                    color = Color(0xD9FFFFFF)
                )
                Text(
                    text = queueName,
                    style = Typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic
                    ),
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Part 3: Glass Segmented Control "Audio | Video" centered above title row (ONLY when video stream exists)
            if (hasVideoStream) {
                AudioVideoSegmentedToggle(
                    isVideoMode = isVideoMode,
                    tintedGlass = palette.glassTint,
                    onSelectVideo = onToggleVideoMode,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )
            }
        }

        // Minimal contrast guard scrim ONLY if cover fade region contrast drops below 4.5:1
        val titleRowBg = if (palette.needsFadeContrastScrim && !isVideoMode) {
            Brush.verticalGradient(listOf(Color.Transparent, palette.ambientBase.copy(alpha = 0.45f)))
        } else {
            Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
        }

        // Below Cover: Title & Artist left-aligned, Heart & 3-dot cover-tinted 48dp glass circles right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(titleRowBg)
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = Typography.headlineMedium.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    style = Typography.bodyMedium.copy(fontSize = 15.sp),
                    color = Color(0xCCFFFFFF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassCircleIconButton(
                    iconRes = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
                    tint = if (isLiked) Color(0xFFFF5A6E) else PureWhite,
                    contentDescription = if (isLiked) "Unlike" else "Like",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onLikeClick()
                    }
                )
                GlassCircleIconButton(
                    iconRes = R.drawable.ic_more_vert,
                    tint = PureWhite,
                    contentDescription = "Track Options",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onMenuClick()
                    }
                )
            }
        }
    }
}

@Composable
private fun AudioVideoSegmentedToggle(
    isVideoMode: Boolean,
    tintedGlass: Color,
    onSelectVideo: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = CircleShape
    val view = LocalView.current
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(tintedGlass, PureWhite, 0.14f).copy(alpha = 0.44f),
                        tintedGlass.copy(alpha = 0.34f)
                    )
                )
            )
            .border(1.dp, SpecularDockBorder, shape)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SegmentedModePill(
            label = "Audio",
            selected = !isVideoMode,
            tintedGlass = tintedGlass,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onSelectVideo(false)
            }
        )
        SegmentedModePill(
            label = "Video",
            selected = isVideoMode,
            tintedGlass = tintedGlass,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onSelectVideo(true)
            }
        )
    }
}

@Composable
private fun SegmentedModePill(
    label: String,
    selected: Boolean,
    tintedGlass: Color,
    onClick: () -> Unit
) {
    val pillShape = CircleShape
    Box(
        modifier = Modifier
            .height(38.dp)
            .clip(pillShape)
            .then(
                if (selected) {
                    Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    lerp(tintedGlass, PureWhite, 0.30f).copy(alpha = 0.62f),
                                    lerp(tintedGlass, PureWhite, 0.14f).copy(alpha = 0.48f)
                                )
                            )
                        )
                        .border(0.9.dp, Color(0x66FFFFFF), pillShape)
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = Typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) PureWhite else Color(0xCCFFFFFF)
        )
    }
}

@Composable
private fun GlassCircleIconButton(
    iconRes: Int,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit
) {
    GlassSurface(
        variant = GlassSurfaceVariant.IconButton,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        onClick = onClick,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(21.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SHARED BOTTOM CONTROL DECK (`now_playing.jpg` & `lyrics.jpg`)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NowPlayingControlDeck(
    bars: List<Float>,
    scrubberStyle: String,
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    volumeFraction: Float,
    activePreset: SoundPreset,
    isLyricsMode: Boolean,
    isSoundSheetOpen: Boolean,
    sleepSecRemaining: Int?,
    sleepEndOfTrack: Boolean,
    onSeekFraction: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onSkipPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleRepeat: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenShare: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenSoundSheet: () -> Unit
) {
    val progressFraction = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val elapsedStr = remember(currentPositionMs) { formatDuration(currentPositionMs) }
    val remainingStr = remember(currentPositionMs, durationMs) {
        val rem = (durationMs - currentPositionMs).coerceAtLeast(0L)
        "-${formatDuration(rem)}"
    }
    val tintedGlass = LocalTintedGlassColor.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Progress Line (or Waveform Scrubber if chosen in Settings)
        Column(modifier = Modifier.fillMaxWidth()) {
            if (scrubberStyle == "Waveform") {
                WaveformScrubberBar(bars = bars, progressFraction = progressFraction, onSeekFraction = onSeekFraction)
            } else {
                LineProgressScrubber(progressFraction = progressFraction, onSeekFraction = onSeekFraction)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = elapsedStr, style = Typography.bodySmall.copy(fontSize = 12.sp), color = Color(0xB8FFFFFF))
                Text(text = remainingStr, style = Typography.bodySmall.copy(fontSize = 12.sp), color = Color(0x99FFFFFF))
            }
        }

        // 2. Bare Transport Row (transient glass bubble appears ONLY while pressed)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BareTransportButton(
                iconRes = R.drawable.ic_shuffle,
                tint = if (isShuffle) PureWhite else Color(0xAAFFFFFF),
                iconSizeDp = 21,
                contentDescription = "Shuffle",
                onClick = onToggleShuffle
            )
            BareTransportButton(
                iconRes = R.drawable.ic_skip_previous,
                tint = PureWhite,
                iconSizeDp = 28,
                contentDescription = "Previous",
                onClick = onSkipPrev
            )
            BareTransportButton(
                iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                tint = PureWhite,
                iconSizeDp = 36,
                buttonSizeDp = 62,
                contentDescription = if (isPlaying) "Pause" else "Play",
                onClick = onPlayPause
            )
            BareTransportButton(
                iconRes = R.drawable.ic_skip_next,
                tint = PureWhite,
                iconSizeDp = 28,
                contentDescription = "Next",
                onClick = onSkipNext
            )
            BareTransportButton(
                iconRes = if (repeatMode == RepeatMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
                tint = if (repeatMode != RepeatMode.OFF) PureWhite else Color(0xAAFFFFFF),
                iconSizeDp = 21,
                contentDescription = "Repeat",
                onClick = onToggleRepeat
            )
        }

        // 3. Volume Row (bound to STREAM_MUSIC)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_volume_low),
                contentDescription = null,
                tint = SoftSilver,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            RezonaGlassSlider(
                value = volumeFraction,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_volume_high),
                contentDescription = null,
                tint = PureWhite,
                modifier = Modifier.size(18.dp)
            )
        }

        // 4. Bottom 4-Button Cover-Tinted Glass Pill: Lyrics | Share | Sleep timer | Sound
        val timerActive = sleepSecRemaining != null || sleepEndOfTrack
        val soundPresetActive = activePreset != SoundPreset.NORMAL
        val pillShape = RoundedCornerShape(28.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(pillShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            lerp(tintedGlass, PureWhite, 0.14f).copy(alpha = 0.44f),
                            tintedGlass.copy(alpha = 0.34f)
                        )
                    )
                )
                .border(1.dp, SpecularDockBorder, pillShape)
                .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomPillIconSlot(
                    iconRes = R.drawable.ic_lyrics,
                    isActive = isLyricsMode,
                    badgeText = null,
                    showIndicatorDot = false,
                    tintedGlass = tintedGlass,
                    contentDescription = "Lyrics",
                    onClick = onToggleLyrics,
                    modifier = Modifier.weight(1f)
                )
                BottomPillIconSlot(
                    iconRes = R.drawable.ic_share,
                    isActive = false,
                    badgeText = null,
                    showIndicatorDot = false,
                    tintedGlass = tintedGlass,
                    contentDescription = "Share",
                    onClick = onOpenShare,
                    modifier = Modifier.weight(1f)
                )
                val countdownLabel = when {
                    sleepEndOfTrack -> "End"
                    sleepSecRemaining != null -> {
                        val m = (sleepSecRemaining + 59) / 60
                        "${m}m"
                    }
                    else -> null
                }
                BottomPillIconSlot(
                    iconRes = R.drawable.ic_timer,
                    isActive = timerActive,
                    badgeText = countdownLabel,
                    showIndicatorDot = false,
                    tintedGlass = tintedGlass,
                    contentDescription = "Sleep Timer",
                    onClick = onOpenSleepTimer,
                    modifier = Modifier.weight(1f)
                )
                BottomPillIconSlot(
                    iconRes = R.drawable.ic_sound_waves,
                    isActive = isSoundSheetOpen,
                    badgeText = null,
                    showIndicatorDot = soundPresetActive,
                    tintedGlass = tintedGlass,
                    contentDescription = "Sound Effects",
                    onClick = onOpenSoundSheet,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LineProgressScrubber(
    progressFraction: Float,
    onSeekFraction: (Float) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(progressFraction) }
    val active = if (isDragging) dragFrac else progressFraction

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeekFraction((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFrac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onDrag = { change, _ ->
                        dragFrac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        onSeekFraction(dragFrac)
                        isDragging = false
                    },
                    onDragCancel = { isDragging = false }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val h = 3.dp.toPx()
            drawRoundRect(
                color = Color(0x3DFFFFFF),
                topLeft = Offset(0f, cy - h / 2f),
                size = Size(size.width, h),
                cornerRadius = CornerRadius(h / 2f, h / 2f)
            )
            val fillW = (size.width * active).coerceIn(0f, size.width)
            drawRoundRect(
                color = PureWhite,
                topLeft = Offset(0f, cy - h / 2f),
                size = Size(fillW, h),
                cornerRadius = CornerRadius(h / 2f, h / 2f)
            )
            if (isDragging) {
                drawCircle(color = PureWhite, radius = 6.dp.toPx(), center = Offset(fillW, cy))
            }
        }
    }
}

@Composable
private fun WaveformScrubberBar(
    bars: List<Float>,
    progressFraction: Float,
    onSeekFraction: (Float) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(progressFraction) }
    val active = if (isDragging) dragFrac else progressFraction

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset -> onSeekFraction((offset.x / size.width.toFloat()).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o -> isDragging = true; dragFrac = (o.x / size.width.toFloat()).coerceIn(0f, 1f) },
                    onDrag = { c, _ -> dragFrac = (c.position.x / size.width.toFloat()).coerceIn(0f, 1f) },
                    onDragEnd = { onSeekFraction(dragFrac); isDragging = false },
                    onDragCancel = { isDragging = false }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val count = bars.size.coerceAtLeast(1)
            val gap = 2.5.dp.toPx()
            val bw = (size.width - gap * (count - 1)) / count
            bars.forEachIndexed { i, amp ->
                val played = i.toFloat() / count <= active
                val bh = (size.height * 0.85f * amp).coerceAtLeast(3.dp.toPx())
                drawRoundRect(
                    color = if (played) PureWhite else Color(0x38FFFFFF),
                    topLeft = Offset(i * (bw + gap), (size.height - bh) / 2f),
                    size = Size(bw, bh),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun BareTransportButton(
    iconRes: Int,
    tint: Color,
    iconSizeDp: Int,
    buttonSizeDp: Int = 48,
    contentDescription: String,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.90f else 1f, spring(0.6f, 420f), label = "tb")

    Box(
        modifier = Modifier
            .size(buttonSizeDp.dp)
            .scale(scale)
            .clip(CircleShape)
            .then(
                if (isPressed) {
                    Modifier
                        .background(Color(0x2EFFFFFF))
                        .border(1.dp, Color(0x66FFFFFF), CircleShape)
                } else Modifier
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSizeDp.dp)
        )
    }
}

@Composable
private fun BottomPillIconSlot(
    iconRes: Int,
    isActive: Boolean,
    badgeText: String?,
    showIndicatorDot: Boolean,
    tintedGlass: Color,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val capsuleShape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(capsuleShape)
            .then(
                if (isActive) {
                    Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    lerp(tintedGlass, PureWhite, 0.30f).copy(alpha = 0.62f),
                                    lerp(tintedGlass, PureWhite, 0.14f).copy(alpha = 0.44f)
                                )
                            )
                        )
                        .border(1.dp, Color(0x66FFFFFF), capsuleShape)
                } else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = PureWhite),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = contentDescription,
                    tint = if (isActive || showIndicatorDot) PureWhite else Color(0xCCFFFFFF),
                    modifier = Modifier.size(20.dp)
                )
                if (!badgeText.isNullOrBlank()) {
                    Text(
                        text = badgeText,
                        style = Typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                        color = PureWhite
                    )
                }
            }
            if (showIndicatorDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-4).dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(PureWhite)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QUEUE SHEET, SHARE SHEET, SLEEP TIMER SHEET, AMBIENT MODE
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueBottomSheet(
    queueName: String,
    currentTrack: RezonaTrack,
    queue: List<RezonaTrack>,
    history: List<RezonaTrack>,
    onPlayTrack: (RezonaTrack) -> Unit,
    onJumpToQueueIndex: (Int) -> Unit,
    onRemoveQueueIndex: (Int) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onShuffleQueue: () -> Unit,
    onClearQueue: () -> Unit,
    onRestoreFromHistory: (RezonaTrack) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Up Next, 1 = History
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xEC101218),
        scrimColor = Color(0x88000000)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(540.dp)
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(queueName, style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PearlWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${queue.size} up next · ${history.size} played", style = Typography.bodySmall, color = SmokySilver)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RezonaGlassPill(isActive = false, onClick = onShuffleQueue) {
                        Text("Shuffle", style = Typography.labelSmall, color = PureWhite)
                    }
                    RezonaGlassPill(isActive = false, onClick = onClearQueue) {
                        Text("Clear", style = Typography.labelSmall, color = SoftSilver)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                RezonaGlassPill(isActive = selectedTab == 0, onClick = { selectedTab = 0 }, modifier = Modifier.weight(1f)) {
                    Text("Up Next (${queue.size})", style = Typography.labelLarge, color = if (selectedTab == 0) PureWhite else SoftSilver)
                }
                RezonaGlassPill(isActive = selectedTab == 1, onClick = { selectedTab = 1 }, modifier = Modifier.weight(1f)) {
                    Text("History (${history.size})", style = Typography.labelLarge, color = if (selectedTab == 1) PureWhite else SoftSilver)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            RezonaHairlineDivider()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (selectedTab == 0) {
                    item(key = "now_playing_head") {
                        Text(
                            text = "NOW PLAYING",
                            style = Typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                            color = SoftSilver,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RezonaAsyncCoverArt(currentTrack.coverArtUrl, currentTrack.index, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(42.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(currentTrack.title, style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PureWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(currentTrack.artist, style = Typography.bodySmall, color = SmokySilver, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        RezonaHairlineDivider()
                    }
                    if (queue.isEmpty()) {
                        item(key = "empty_q") {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("Queue is empty — radio tracks will auto-load.", style = Typography.bodySmall, color = SmokySilver)
                            }
                        }
                    } else {
                        itemsIndexed(queue, key = { idx, item -> "q_${item.id}_$idx" }) { idx, item ->
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onJumpToQueueIndex(idx) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RezonaAsyncCoverArt(item.coverArtUrl, item.index, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(42.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, style = Typography.titleMedium.copy(fontSize = 14.sp), color = PearlWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(item.artist, style = Typography.bodySmall, color = SmokySilver, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (idx > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .clickable { onReorder(idx, idx - 1) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("↑", color = SoftSilver, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .clickable { onRemoveQueueIndex(idx) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(painter = painterResource(id = R.drawable.ic_close), contentDescription = "Remove", tint = SmokySilver, modifier = Modifier.size(16.dp))
                                    }
                                }
                                RezonaHairlineDivider(startIndent = 52.dp)
                            }
                        }
                    }
                } else {
                    if (history.isEmpty()) {
                        item(key = "empty_h") {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No session history yet.", style = Typography.bodySmall, color = SmokySilver)
                            }
                        }
                    } else {
                        itemsIndexed(history, key = { idx, item -> "h_${item.id}_$idx" }) { _, item ->
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPlayTrack(item) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RezonaAsyncCoverArt(item.coverArtUrl, item.index, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(42.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, style = Typography.titleMedium.copy(fontSize = 14.sp), color = PearlWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(item.artist, style = Typography.bodySmall, color = SmokySilver, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    RezonaGlassPill(isActive = false, onClick = { onRestoreFromHistory(item) }) {
                                        Text("Re-queue", style = Typography.labelSmall, color = PureWhite)
                                    }
                                }
                                RezonaHairlineDivider(startIndent = 54.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareOptionsBottomSheet(
    track: RezonaTrack,
    onShareLink: () -> Unit,
    onShareCard: () -> Unit,
    onShareLyricsText: () -> Unit,
    onShareLyricsPoster: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xEC101218),
        scrimColor = Color(0x88000000)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Share", style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PearlWhite)
            Text("${track.title} · ${track.artist}", style = Typography.bodySmall, color = SmokySilver)
            Spacer(modifier = Modifier.height(14.dp))
            RezonaHairlineDivider()

            ShareSheetRow(R.drawable.ic_link, "Share song link", "Send YouTube Music track link", onShareLink)
            RezonaHairlineDivider()
            ShareSheetRow(R.drawable.ic_share, "Share song card (image)", "1080px Liquid Glass cover card", onShareCard)
            RezonaHairlineDivider()
            ShareSheetRow(R.drawable.ic_lyrics, "Share lyrics as text", "Copy / send formatted lyric excerpt", onShareLyricsText)
            RezonaHairlineDivider()
            ShareSheetRow(R.drawable.ic_ambient, "Share lyrics poster", "Open Poster Studio (1:1 or 9:16)", onShareLyricsPoster)
            RezonaHairlineDivider()
            ShareSheetRow(R.drawable.ic_playlist_add, "Copy link to clipboard", null) {
                val url = track.streamUrl.ifBlank { "https://music.youtube.com/watch?v=${track.id.removePrefix("yt_")}" }
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Song Link", url))
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }
    }
}

@Composable
private fun ShareSheetRow(iconRes: Int, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painter = painterResource(id = iconRes), contentDescription = null, tint = PureWhite, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(title, style = Typography.titleMedium, color = PearlWhite)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = Typography.bodySmall, color = SmokySilver)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerBottomSheet(
    currentMinutes: Int?,
    sleepEndOfTrack: Boolean,
    remainingSeconds: Int?,
    onSelectMinutes: (Int?) -> Unit,
    onSelectEndOfTrack: () -> Unit,
    onDismiss: () -> Unit
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xEC101218),
        scrimColor = Color(0x88000000)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Sleep Timer", style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PearlWhite)
            if (remainingSeconds != null) {
                val mins = remainingSeconds / 60
                val secs = remainingSeconds % 60
                Text(
                    text = String.format("Stopping playback in %d:%02d", mins, secs),
                    style = Typography.bodySmall,
                    color = PureWhite
                )
            } else if (sleepEndOfTrack) {
                Text("Stopping at end of current track", style = Typography.bodySmall, color = PureWhite)
            } else {
                Text("Automatically pause playback after timer", style = Typography.bodySmall, color = SmokySilver)
            }

            Spacer(modifier = Modifier.height(14.dp))
            RezonaHairlineDivider()

            listOf(15, 30, 45, 60).forEach { mins ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectMinutes(mins) }
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$mins minutes", style = Typography.titleMedium, color = if (currentMinutes == mins) PureWhite else PearlWhite)
                    if (currentMinutes == mins) {
                        Icon(painter = painterResource(id = R.drawable.ic_check), contentDescription = null, tint = PureWhite, modifier = Modifier.size(18.dp))
                    }
                }
                RezonaHairlineDivider()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSelectEndOfTrack)
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("End of track", style = Typography.titleMedium, color = if (sleepEndOfTrack) PureWhite else PearlWhite)
                if (sleepEndOfTrack) {
                    Icon(painter = painterResource(id = R.drawable.ic_check), contentDescription = null, tint = PureWhite, modifier = Modifier.size(18.dp))
                }
            }
            RezonaHairlineDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCustomDialog = true }
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Custom duration...", style = Typography.titleMedium, color = PearlWhite)
            }
            RezonaHairlineDivider()

            if (currentMinutes != null || sleepEndOfTrack) {
                Spacer(modifier = Modifier.height(12.dp))
                RezonaGlassButton(
                    text = "Turn Off Timer",
                    isPrimary = false,
                    onClick = { onSelectMinutes(null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showCustomDialog) {
        var customText by remember { mutableStateOf("20") }
        RezonaGlassDialog(
            title = "Custom Sleep Timer",
            onDismissRequest = { showCustomDialog = false },
            confirmText = "Start",
            onConfirm = {
                val m = customText.toIntOrNull()?.coerceIn(1, 240) ?: 20
                showCustomDialog = false
                onSelectMinutes(m)
            },
            dismissText = "Cancel"
        ) {
            RezonaGlassTextField(
                value = customText,
                onValueChange = { customText = it.filter { ch -> ch.isDigit() } },
                placeholder = "Minutes (e.g. 25)"
            )
        }
    }
}

@Composable
private fun AmbientCalmCoverOverlay(
    track: RezonaTrack,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onDismiss: () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "ambient_breathe")
    val pulse by inf.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "pulse_scale"
    )
    val context = LocalContext.current
    val pal = rememberAnimatedArtworkPalette(context, track.coverArtUrl, track.id)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss)
    ) {
        RezonaContinuousAmbientLayer(
            primaryColor = pal.primaryAmbient,
            secondaryColor = pal.secondaryAmbient,
            tertiaryColor = pal.tertiaryAmbient,
            ambientBase = pal.ambientBase,
            vividMode = true,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            RezonaAsyncCoverArt(
                coverArtUrl = track.coverArtUrl,
                fallbackIndex = track.index,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .aspectRatio(1f)
                    .scale(if (isPlaying) pulse else 1f)
                    .border(1.dp, SpecularCardBorder, RoundedCornerShape(28.dp))
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(track.title, style = Typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = PureWhite, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(4.dp))
            Text(track.artist, style = Typography.bodyMedium, color = SoftSilver, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(28.dp))
            RezonaGlassPill(isActive = true, onClick = onTogglePlayPause) {
                Text(if (isPlaying) "Pause" else "Play", style = Typography.labelLarge, color = PureWhite)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text("Tap anywhere to exit ambient mode", style = Typography.labelSmall, color = SmokySilver)
        }
    }
}

@Composable
private fun HeartBurstOverlay(modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(0.5f) }
    val animScale by animateFloatAsState(scale, spring(0.5f, 300f), label = "heart_burst")
    LaunchedEffect(Unit) { scale = 1.35f }
    Box(
        modifier = modifier
            .size(100.dp)
            .scale(animScale)
            .clip(CircleShape)
            .background(Color(0x55FFFFFF))
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_heart_filled),
            contentDescription = null,
            tint = Color(0xFFFF5A6E),
            modifier = Modifier.size(44.dp)
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format("%d:%02d", min, sec)
}
