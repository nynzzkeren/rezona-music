package com.nayama.music.ui.lyrics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.backend.LyricLanguageHelper
import com.nayama.music.backend.LyricSourceId
import com.nayama.music.data.preferences.RezonaPreferences
import com.nayama.music.model.SyncedLyricLine
import com.nayama.music.playback.RezonaPlayerController
import com.nayama.music.ui.components.GlassSurface
import com.nayama.music.ui.components.GlassSurfaceVariant
import com.nayama.music.ui.components.LocalAmbientBaseColor
import com.nayama.music.ui.components.RezonaActionDisc
import com.nayama.music.ui.components.RezonaAsyncCoverArt
import com.nayama.music.ui.components.RezonaGlassButton
import com.nayama.music.ui.components.RezonaGlassDialog
import com.nayama.music.ui.components.RezonaGlassPill
import com.nayama.music.ui.components.RezonaGlassSlider
import com.nayama.music.ui.components.RezonaGlassTextField
import com.nayama.music.ui.components.RezonaGlassToggle
import com.nayama.music.ui.components.RezonaHairlineDivider
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.SpecularCardBorder
import com.nayama.music.ui.theme.Typography
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Reusable quantized RenderEffect cache (1dp..4dp on API 31+) — zero per-frame allocations.
 */
private object QuantizedBlurCache {
    private val cache = HashMap<Int, RenderEffect>(8)

    fun get(dpBucket: Int, density: Float): RenderEffect? {
        if (dpBucket <= 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val key = dpBucket * 1000 + (density * 100f).roundToInt()
        return cache.getOrPut(key) {
            val px = (dpBucket * density).coerceAtLeast(1f)
            android.graphics.RenderEffect.createBlurEffect(
                px,
                px,
                android.graphics.Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
    }
}

/**
 * Part 4 + Part 5 Butter-Smooth Inline Synced Lyrics Viewport:
 * - Frame-synchronized 60/120Hz PlaybackClock (elapsedRealtime delta x playbackSpeed + gentle drift sync)
 * - Continuous focusIndex Float driving scroll, alpha, scale, quantized blur, and word-by-word soft gradient fill
 * - Single Animatable scroll offset with zero-overshoot spring; 3s manual scroll pause then glide back
 * - ZERO per-frame Compose recomposition & ZERO per-frame allocations (all animation read in graphicsLayer/Canvas)
 * - Full Lyrics Style customization (8 bundled open-license fonts, weight, size, alignment, spacing, italic)
 * - ZERO highlight boxes anywhere.
 */
@Composable
fun RezonaInlineLyricsSection(
    controller: RezonaPlayerController,
    onCollapseToCover: () -> Unit,
    onOpenPosterFromShare: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferences = remember { RezonaPreferences(context) }
    val scope = rememberCoroutineScope()

    val translationEnabled by preferences.lyricsTranslationEnabledFlow.collectAsState(initial = true)
    val romanizationEnabled by preferences.lyricsRomanizationEnabledFlow.collectAsState(initial = false)
    val blurUnfocused by preferences.lyricsBlurUnfocusedFlow.collectAsState(initial = true)
    val timingOffsetMs by preferences.lyricsOffsetMsFlow.collectAsState(initial = 0)
    val lyricsStyle by preferences.lyricsStyleFlow.collectAsState(initial = LyricsStyleConfig())

    val track = controller.currentTrack.value ?: return
    val lyrics = controller.currentLyrics.value
    val lyricsSource = controller.lyricsSourceLabel.value

    var showOptionsSheet by remember { mutableStateOf(false) }
    var showStyleSheet by remember { mutableStateOf(false) }
    var showSourcePickerSheet by remember { mutableStateOf(false) }
    var showSearchLyricsDialog by remember { mutableStateOf(false) }

    val supportsHardwareBlur = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
    val isReducedMotion = remember(context) {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) {
            false
        }
    }

    // Frame-synced 60/120Hz playback clock (DO NOT read .longValue/.floatValue directly in composable body!)
    val clock = rememberLyricsPlaybackClock(
        controller = controller,
        lines = lyrics,
        timingOffsetMs = timingOffsetMs
    )

    val effectiveBlurEnabled = blurUnfocused &&
        supportsHardwareBlur &&
        !isReducedMotion &&
        !clock.jankAutoDisabledBlur.value

    // Discrete active index used ONLY for instrumental gap check and scroll target anchoring
    val discreteActiveIndex by remember(lyrics) {
        derivedStateOf {
            if (lyrics.isEmpty()) -1 else floor(clock.focusIndex.floatValue).toInt().coerceIn(-1, lyrics.lastIndex)
        }
    }

    val isInstrumentalGap by remember(lyrics) {
        derivedStateOf {
            if (lyrics.isEmpty()) return@derivedStateOf false
            val pos = clock.positionMs.longValue
            val idx = discreteActiveIndex
            if (idx < 0) return@derivedStateOf (lyrics.first().timeMs - pos) > 2500L
            val cur = lyrics.getOrNull(idx) ?: return@derivedStateOf false
            val next = lyrics.getOrNull(idx + 1) ?: return@derivedStateOf false
            (next.timeMs - cur.timeMs) > 7500L && (pos - cur.timeMs) > 3500L && (next.timeMs - pos) > 1200L
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        // ── COMPACT LYRICS HEADER (Thumbnail + Title/Artist + Collapse + 3-dot) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RezonaAsyncCoverArt(
                coverArtUrl = track.coverArtUrl,
                fallbackIndex = track.index,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .size(44.dp)
                    .border(0.75.dp, SpecularCardBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onCollapseToCover)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onCollapseToCover)
            ) {
                Text(
                    text = track.title,
                    style = Typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = PearlWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = Typography.bodySmall.copy(fontSize = 12.sp),
                    color = SmokySilver,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            RezonaActionDisc(size = 48.dp, onClick = onCollapseToCover) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_ambient),
                    contentDescription = "Back to Cover View",
                    tint = PearlWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            RezonaActionDisc(size = 48.dp, onClick = { showOptionsSheet = true }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_more_vert),
                    contentDescription = "Lyrics Options",
                    tint = PearlWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ── INSTRUMENTAL GAP 3-DOT INDICATOR ─────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isInstrumentalGap) 24.dp else 4.dp)
                .padding(horizontal = 24.dp),
            contentAlignment = if (lyricsStyle.centerAlign) Alignment.Center else Alignment.CenterStart
        ) {
            if (isInstrumentalGap) {
                InstrumentalDotsIndicator()
            }
        }

        // ── BUTTER-SMOOTH UNBOXED LYRICS VIEWPORT ────────────────────────────
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
        ) {
            val viewportHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val contentWidthPx = (constraints.maxWidth - with(LocalDensity.current) { 48.dp.roundToPx() }).coerceAtLeast(120)

            if (lyrics.isEmpty()) {
                EmptyLyricsState(
                    onReload = { controller.refreshLyrics(true) },
                    onOpenSources = {
                        showSourcePickerSheet = true
                        controller.probeAllLyricsSources()
                    },
                    onOpenSearch = { showSearchLyricsDialog = true }
                )
            } else {
                ButterSmoothLyricsCanvasList(
                    trackId = track.id,
                    lyrics = lyrics,
                    lyricsSource = lyricsSource,
                    clock = clock,
                    lyricsStyle = lyricsStyle,
                    translationEnabled = translationEnabled,
                    romanizationEnabled = romanizationEnabled,
                    blurEnabled = effectiveBlurEnabled,
                    viewportHeightPx = viewportHeightPx,
                    contentWidthPx = contentWidthPx,
                    onSeekToLine = { line ->
                        val target = (line.timeMs - timingOffsetMs).coerceAtLeast(0L)
                        controller.seekToPosition(target)
                        clock.snapTo(line.timeMs, lyrics)
                    },
                    onOpenSourcePicker = {
                        showSourcePickerSheet = true
                        controller.probeAllLyricsSources()
                    }
                )
            }
        }
    }

    if (showStyleSheet) {
        RezonaLyricsStyleSheet(
            style = lyricsStyle,
            onStyleChange = { updated -> scope.launch { preferences.setLyricsStyle(updated) } },
            onReset = { scope.launch { preferences.resetLyricsStyle() } },
            onDismiss = { showStyleSheet = false }
        )
    }

    if (showSourcePickerSheet) {
        RezonaLyricsSourcePickerSheet(
            trackTitle = track.title,
            trackArtist = track.artist,
            selectedSource = controller.selectedLyricsSource.value,
            activeResolvedSource = controller.activeResolvedSourceId.value,
            sourceStatuses = controller.lyricsSourceStatuses,
            timingOffsetMs = timingOffsetMs,
            onSelectSource = { src ->
                controller.selectLyricsSource(src)
                showSourcePickerSheet = false
            },
            onOffsetChange = { nextOffset ->
                scope.launch { preferences.setLyricsOffsetMs(nextOffset.coerceIn(-5000, 5000)) }
            },
            onDismiss = { showSourcePickerSheet = false }
        )
    }

    if (showOptionsSheet) {
        RezonaLyricsOptionsSheet(
            lyricsSource = lyricsSource,
            translationEnabled = translationEnabled,
            romanizationEnabled = romanizationEnabled,
            blurEnabled = blurUnfocused,
            centerAlign = lyricsStyle.centerAlign,
            timingOffsetMs = timingOffsetMs,
            onToggleTranslation = { scope.launch { preferences.setLyricsTranslationEnabled(it) } },
            onToggleRomanization = { scope.launch { preferences.setLyricsRomanizationEnabled(it) } },
            onToggleBlur = { scope.launch { preferences.setLyricsBlurUnfocused(it) } },
            onToggleCenterAlign = {
                scope.launch { preferences.setLyricsStyle(lyricsStyle.copy(centerAlign = it)) }
            },
            onOffsetChange = { scope.launch { preferences.setLyricsOffsetMs(it) } },
            onOpenLyricsStyle = {
                showOptionsSheet = false
                showStyleSheet = true
            },
            onReload = {
                showOptionsSheet = false
                controller.refreshLyrics(forceReload = true)
            },
            onOpenManualSearch = {
                showOptionsSheet = false
                showSearchLyricsDialog = true
            },
            onCopyAll = {
                val allText = lyrics.joinToString("\n") { it.text }
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Lyrics - ${track.title}", allText))
                Toast.makeText(context, "Lyrics copied", Toast.LENGTH_SHORT).show()
                showOptionsSheet = false
            },
            onOpenPoster = {
                showOptionsSheet = false
                onOpenPosterFromShare()
            },
            onDismiss = { showOptionsSheet = false }
        )
    }

    if (showSearchLyricsDialog) {
        var manualQuery by remember { mutableStateOf("${track.title} ${track.artist}") }
        RezonaGlassDialog(
            title = "Search Lyrics",
            onDismissRequest = { showSearchLyricsDialog = false },
            confirmText = "Search",
            onConfirm = {
                controller.searchAlternateLyrics(manualQuery)
                showSearchLyricsDialog = false
            },
            dismissText = "Cancel"
        ) {
            RezonaGlassTextField(
                value = manualQuery,
                onValueChange = { manualQuery = it },
                placeholder = "Song title & artist..."
            )
        }
    }
}

@Composable
private fun EmptyLyricsState(
    onReload: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenSearch: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_lyrics),
                contentDescription = null,
                tint = SoftSilver,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "No Synchronized Lyrics Found",
                style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PearlWhite,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Source: lrc.red / LRCLIB & YouTube Music",
                style = Typography.bodySmall,
                color = SmokySilver,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RezonaGlassPill(isActive = true, onClick = onReload, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Reload", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                }
                RezonaGlassPill(isActive = false, onClick = onOpenSources, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Sources", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = PearlWhite)
                }
                RezonaGlassPill(isActive = false, onClick = onOpenSearch, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Search", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = SoftSilver)
                }
            }
        }
    }
}

/**
 * Core Part 4 Engine:
 * - Single Animatable<Float> scroll offset using measured line tops in content space
 * - Manual vertical drag pauses auto-follow for 3 seconds, then glides back smoothly
 * - Per-line alpha/scale/quantized-blur applied purely in graphicsLayer lambdas
 * - Word-by-word / line-level soft gradient fill rendered purely in Canvas draw phase
 * - Cached TextLayoutResults invalidated only when lyrics or LyricsStyleConfig change!
 */
@Composable
private fun ButterSmoothLyricsCanvasList(
    trackId: String,
    lyrics: List<SyncedLyricLine>,
    lyricsSource: String,
    clock: LyricsPlaybackClock,
    lyricsStyle: LyricsStyleConfig,
    translationEnabled: Boolean,
    romanizationEnabled: Boolean,
    blurEnabled: Boolean,
    viewportHeightPx: Float,
    contentWidthPx: Int,
    onSeekToLine: (SyncedLyricLine) -> Unit,
    onOpenSourcePicker: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 128)

    // Pre-resolved FontFamily and TextStyle (invalidates cached layouts cleanly when style changes)
    val fontFamily = remember(lyricsStyle.fontFamily) { lyricsStyle.fontFamily.toComposeFontFamily() }
    val primaryTextStyle = remember(lyricsStyle, fontFamily) {
        TextStyle(
            fontFamily = fontFamily,
            fontWeight = lyricsStyle.weight.weight,
            fontStyle = lyricsStyle.fontStyle,
            fontSize = lyricsStyle.fontSizeSp.sp,
            lineHeight = lyricsStyle.lineHeightSp.sp,
            letterSpacing = lyricsStyle.letterSpacingEm.em,
            textAlign = lyricsStyle.textAlign
        )
    }

    // Measured line top positions & heights in content space
    val lineTopsPx = remember(trackId, lyrics) { mutableStateMapOf<Int, Float>() }
    val lineHeightsPx = remember(trackId, lyrics) { mutableStateMapOf<Int, Float>() }
    var totalContentHeightPx by remember(trackId, lyrics) { mutableFloatStateOf(0f) }

    // ONE Animatable scroll offset (px in content space)
    val scrollAnim = remember(trackId) { Animatable(0f) }
    var userPausedAutoScroll by remember(trackId) { mutableStateOf(false) }
    var resumeAutoJob by remember { mutableStateOf<Job?>(null) }

    val targetAnchorY = viewportHeightPx * 0.30f

    fun maxScrollPx(): Float = (totalContentHeightPx - viewportHeightPx * 0.55f).coerceAtLeast(0f)

    fun computeTargetScrollForLine(idx: Int): Float {
        val top = lineTopsPx[idx] ?: return scrollAnim.value
        val h = lineHeightsPx[idx] ?: 0f
        return (top + h * 0.35f - targetAnchorY).coerceIn(0f, maxScrollPx())
    }

    // Keep active line anchored cleanly without jump when style or orientation changes
    LaunchedEffect(lyricsStyle, contentWidthPx, viewportHeightPx) {
        delay(32L)
        val idx = floor(clock.focusIndex.floatValue).toInt().coerceIn(0, (lyrics.size - 1).coerceAtLeast(0))
        if (lineTopsPx.containsKey(idx) && !userPausedAutoScroll) {
            scrollAnim.snapTo(computeTargetScrollForLine(idx))
        }
    }

    // Smoothly spring-scroll ONE Animatable toward the active line center whenever active line updates
    LaunchedEffect(trackId, lyrics, userPausedAutoScroll, viewportHeightPx) {
        if (userPausedAutoScroll) return@LaunchedEffect
        snapshotFlow { floor(clock.focusIndex.floatValue).toInt().coerceIn(0, (lyrics.size - 1).coerceAtLeast(0)) }
            .distinctUntilChanged()
            .collectLatest { activeIdx ->
                if (!lineTopsPx.containsKey(activeIdx)) return@collectLatest
                val target = computeTargetScrollForLine(activeIdx)
                val delta = abs(target - scrollAnim.value)
                if (delta > 3f) {
                    val spec = if (delta > 560f) {
                        // Large seek -> short critically-damped glide
                        spring<Float>(dampingRatio = 1.0f, stiffness = 320f)
                    } else {
                        // Normal line transition -> silky zero-overshoot low-stiffness spring
                        spring<Float>(dampingRatio = 0.92f, stiffness = 110f)
                    }
                    scrollAnim.animateTo(targetValue = target, animationSpec = spec)
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(trackId, totalContentHeightPx, viewportHeightPx) {
                detectVerticalDragGestures(
                    onDragStart = {
                        userPausedAutoScroll = true
                        resumeAutoJob?.cancel()
                    },
                    onVerticalDrag = { _, dragAmount ->
                        scope.launch {
                            val next = (scrollAnim.value - dragAmount).coerceIn(0f, maxScrollPx())
                            scrollAnim.snapTo(next)
                        }
                    },
                    onDragEnd = {
                        // Pause auto-follow for 3 seconds then smoothly glide back
                        resumeAutoJob?.cancel()
                        resumeAutoJob = scope.launch {
                            delay(3000L)
                            userPausedAutoScroll = false
                        }
                    },
                    onDragCancel = {
                        resumeAutoJob?.cancel()
                        resumeAutoJob = scope.launch {
                            delay(3000L)
                            userPausedAutoScroll = false
                        }
                    }
                )
            }
    ) {
        // Content translated smoothly by ONE Animatable scroll offset in graphicsLayer (no recomposition!)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    translationY = -scrollAnim.value
                }
                .onGloballyPositioned { coords ->
                    totalContentHeightPx = coords.size.height.toFloat()
                },
            verticalArrangement = Arrangement.spacedBy(lyricsStyle.lineSpacing.itemGapDp.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Source switcher chip at top
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (lyricsStyle.centerAlign) Arrangement.Center else Arrangement.Start
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenSourcePicker)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = lyricsSource,
                        style = Typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        color = SmokySilver
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.ic_expand_more),
                        contentDescription = "Choose lyrics source",
                        tint = SmokySilver,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            lyrics.forEachIndexed { index, line ->
                val nextTime = lyrics.getOrNull(index + 1)?.timeMs ?: (line.timeMs + 4200L)
                // Cache TextLayoutResult once per (line, style, width) — ZERO per-frame layout work!
                val cachedLayout = remember(line.text, primaryTextStyle, contentWidthPx) {
                    textMeasurer.measure(
                        text = line.text,
                        style = primaryTextStyle,
                        constraints = Constraints(maxWidth = contentWidthPx)
                    )
                }

                ButterSmoothLyricRow(
                    index = index,
                    line = line,
                    nextLineTimeMs = nextTime,
                    cachedLayout = cachedLayout,
                    lyricsStyle = lyricsStyle,
                    fontFamily = fontFamily,
                    translationEnabled = translationEnabled,
                    romanizationEnabled = romanizationEnabled,
                    blurEnabled = blurEnabled,
                    density = density.density,
                    clock = clock,
                    onPositioned = { topPx, heightPx ->
                        lineTopsPx[index] = topPx
                        lineHeightsPx[index] = heightPx
                    },
                    onTap = {
                        userPausedAutoScroll = false
                        resumeAutoJob?.cancel()
                        onSeekToLine(line)
                    }
                )
            }

            Spacer(modifier = Modifier.height(140.dp))
        }

        // Top & bottom subtle edge alpha fade for viewport polish
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(listOf(LocalAmbientBaseColor.current.copy(alpha = 0.85f), Color.Transparent))
                )
        )

        // Floating "Resync" pill when user manually scrolls away
        AnimatedVisibility(
            visible = userPausedAutoScroll,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f),
            exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            GlassSurface(
                variant = GlassSurfaceVariant.Floating,
                shape = CircleShape,
                hapticOnPress = true,
                onClick = {
                    resumeAutoJob?.cancel()
                    userPausedAutoScroll = false
                    val idx = floor(clock.focusIndex.floatValue).toInt().coerceIn(0, lyrics.lastIndex)
                    scope.launch {
                        scrollAnim.animateTo(
                            computeTargetScrollForLine(idx),
                            animationSpec = spring(dampingRatio = 0.92f, stiffness = 240f)
                        )
                    }
                },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_repeat),
                        contentDescription = null,
                        tint = PureWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Resync",
                        style = Typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        color = PureWhite
                    )
                }
            }
        }
    }
}

/**
 * Single lyric line + optional romanization + translation laid out as ONE item.
 * Reads `clock.focusIndex` and `clock.positionMs` ONLY inside `graphicsLayer` and `Canvas` draw phase!
 */
@Composable
private fun ButterSmoothLyricRow(
    index: Int,
    line: SyncedLyricLine,
    nextLineTimeMs: Long,
    cachedLayout: TextLayoutResult,
    lyricsStyle: LyricsStyleConfig,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    translationEnabled: Boolean,
    romanizationEnabled: Boolean,
    blurEnabled: Boolean,
    density: Float,
    clock: LyricsPlaybackClock,
    onPositioned: (topPx: Float, heightPx: Float) -> Unit,
    onTap: () -> Unit
) {
    val translation = remember(line.text, translationEnabled) {
        if (translationEnabled) LyricLanguageHelper.translateToDeviceLanguage(line.text) else null
    }
    val romanization = remember(line.text, romanizationEnabled) {
        if (romanizationEnabled) LyricLanguageHelper.romanize(line.text) else null
    }
    val textHeightDp = with(LocalDensity.current) { cachedLayout.size.height.toDp() }
    val originX = if (lyricsStyle.centerAlign) 0.5f else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                onPositioned(coords.positionInParent().y, coords.size.height.toFloat())
            }
            .graphicsLayer {
                val focus = clock.focusIndex.floatValue
                val dist = index.toFloat() - focus
                val absDist = abs(dist)
                if (absDist > 6.5f) {
                    alpha = 0f
                    renderEffect = null
                    return@graphicsLayer
                }
                alpha = LyricsMotionMath.alphaForDistance(dist)
                val sc = LyricsMotionMath.scaleForDistance(dist)
                scaleX = sc
                scaleY = sc
                transformOrigin = TransformOrigin(originX, 0.5f)
                val blurDp = LyricsMotionMath.quantizedBlurDp(dist, blurEnabled)
                renderEffect = QuantizedBlurCache.get(blurDp, density)
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = PureWhite),
                onClick = onTap
            )
            .padding(vertical = 2.dp)
    ) {
        // Draw cached TextLayoutResult with soft-edged progressive word/line fill in Canvas draw phase ONLY!
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(textHeightDp)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {
            val focus = clock.focusIndex.floatValue
            if (abs(index.toFloat() - focus) > 6.5f) return@Canvas

            val posMs = clock.positionMs.longValue
            val fillFrac = LyricsMotionMath.computeLineFillProgress(line, nextLineTimeMs, posMs)

            // 1. Dim base text
            drawText(
                textLayoutResult = cachedLayout,
                color = Color(0x78FFFFFF)
            )

            // 2. Bright white overlay clipped by soft-edged per-visual-line gradient mask
            if (fillFrac > 0.001f) {
                val visualLineCount = cachedLayout.lineCount.coerceAtLeast(1)
                val totalChars = line.text.length.coerceAtLeast(1).toFloat()
                val progressCharPos = fillFrac * totalChars
                val featherPx = 28f * density

                for (vl in 0 until visualLineCount) {
                    val lineStartChar = cachedLayout.getLineStart(vl)
                    val lineEndChar = cachedLayout.getLineEnd(vl, visibleEnd = true).coerceAtLeast(lineStartChar + 1)
                    if (progressCharPos <= lineStartChar) break

                    val top = cachedLayout.getLineTop(vl)
                    val bottom = cachedLayout.getLineBottom(vl)
                    val lineLeft = cachedLayout.getLineLeft(vl)
                    val lineRight = cachedLayout.getLineRight(vl)
                    val lineWidth = (lineRight - lineLeft).coerceAtLeast(1f)

                    if (progressCharPos >= lineEndChar) {
                        clipRect(left = 0f, top = top, right = size.width, bottom = bottom) {
                            drawText(textLayoutResult = cachedLayout, color = PureWhite)
                        }
                    } else {
                        val localFrac = ((progressCharPos - lineStartChar) / (lineEndChar - lineStartChar).coerceAtLeast(1)).coerceIn(0f, 1f)
                        val sweepX = lineLeft + lineWidth * localFrac
                        clipRect(left = 0f, top = top, right = (sweepX + featherPx).coerceAtMost(size.width), bottom = bottom) {
                            drawText(textLayoutResult = cachedLayout, color = PureWhite)
                            val maskStart = (sweepX - featherPx * 0.6f).coerceAtLeast(0f)
                            val maskEnd = (sweepX + featherPx).coerceAtLeast(maskStart + 1f)
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.White, Color.Transparent),
                                    startX = maskStart,
                                    endX = maskEnd
                                ),
                                topLeft = Offset(maskStart, top),
                                size = Size(maskEnd - maskStart, bottom - top),
                                blendMode = BlendMode.DstIn
                            )
                        }
                        break
                    }
                }
            }
        }

        if (!romanization.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = romanization,
                style = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = (lyricsStyle.translationSizeSp - 1f).coerceAtLeast(11f).sp,
                    fontWeight = FontWeight.Normal,
                    fontStyle = lyricsStyle.fontStyle
                ),
                color = SoftSilver.copy(alpha = 0.72f),
                textAlign = lyricsStyle.textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!translation.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = translation,
                style = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = lyricsStyle.translationSizeSp.sp,
                    fontWeight = FontWeight.Normal,
                    fontStyle = lyricsStyle.fontStyle
                ),
                color = Color(0x9EFFFFFF),
                textAlign = lyricsStyle.textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun InstrumentalDotsIndicator() {
    val inf = rememberInfiniteTransition(label = "inst_dots")
    val phase by inf.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "dots_phase"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..2) {
            val dist = abs(phase - i).coerceAtMost(1f)
            val alpha = 1.0f - (dist * 0.65f)
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(PureWhite.copy(alpha = alpha))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RezonaLyricsOptionsSheet(
    lyricsSource: String,
    translationEnabled: Boolean,
    romanizationEnabled: Boolean,
    blurEnabled: Boolean,
    centerAlign: Boolean,
    timingOffsetMs: Int,
    onToggleTranslation: (Boolean) -> Unit,
    onToggleRomanization: (Boolean) -> Unit,
    onToggleBlur: (Boolean) -> Unit,
    onToggleCenterAlign: (Boolean) -> Unit,
    onOffsetChange: (Int) -> Unit,
    onOpenLyricsStyle: () -> Unit,
    onReload: () -> Unit,
    onOpenManualSearch: () -> Unit,
    onCopyAll: () -> Unit,
    onOpenPoster: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ambientBase = LocalAmbientBaseColor.current
    val container = remember(ambientBase) { lerp(ambientBase, Color(0xFF0A0C10), 0.28f).copy(alpha = 0.95f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = container,
        scrimColor = Color(0x88000000)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Lyrics Options", style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PearlWhite)
            Text(lyricsSource, style = Typography.bodySmall, color = SmokySilver)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RezonaGlassButton(text = "Reload", isPrimary = false, onClick = onReload, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                RezonaGlassButton(text = "Search", isPrimary = false, onClick = onOpenManualSearch, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                RezonaGlassButton(text = "Copy", isPrimary = false, onClick = onCopyAll, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RezonaGlassButton(
                    text = "Lyrics Style",
                    isPrimary = true,
                    onClick = onOpenLyricsStyle,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                )
                RezonaGlassButton(
                    text = "Lyrics Poster",
                    isPrimary = false,
                    onClick = onOpenPoster,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                )
            }

            RezonaHairlineDivider()

            OptionsSwitchRow("On-device translation", translationEnabled, onToggleTranslation)
            OptionsSwitchRow("Romanization", romanizationEnabled, onToggleRomanization)
            OptionsSwitchRow("Blur distant lines", blurEnabled, onToggleBlur)
            OptionsSwitchRow("Center text alignment", centerAlign, onToggleCenterAlign)

            RezonaHairlineDivider()

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Timing offset", style = Typography.bodyMedium, color = PearlWhite)
                Text("${timingOffsetMs}ms", style = Typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = PureWhite)
            }
            RezonaGlassSlider(
                value = timingOffsetMs.toFloat(),
                onValueChange = { onOffsetChange((it / 100f).toInt() * 100) },
                valueRange = -2000f..2000f,
                formatBubble = { "${(it / 100f).toInt() * 100}ms" }
            )
        }
    }
}

@Composable
private fun OptionsSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = Typography.bodyMedium, color = PearlWhite)
        RezonaGlassToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RezonaLyricsSourcePickerSheet(
    trackTitle: String,
    trackArtist: String,
    selectedSource: LyricSourceId,
    activeResolvedSource: LyricSourceId,
    sourceStatuses: Map<LyricSourceId, String>,
    timingOffsetMs: Int,
    onSelectSource: (LyricSourceId) -> Unit,
    onOffsetChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ambientBase = LocalAmbientBaseColor.current
    val container = remember(ambientBase) { lerp(ambientBase, Color(0xFF0A0C10), 0.28f).copy(alpha = 0.95f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = container,
        scrimColor = Color(0x88000000)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Lyrics source",
                style = Typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                color = PureWhite
            )
            Text(
                text = "$trackTitle · $trackArtist",
                style = Typography.bodySmall,
                color = SmokySilver,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x18FFFFFF))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Sync offset", style = Typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = PearlWhite)
                    Text(
                        text = if (timingOffsetMs == 0) "0.0s (Default)" else String.format("%+.1fs", timingOffsetMs / 1000f),
                        style = Typography.bodySmall,
                        color = SoftSilver
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RezonaGlassPill(isActive = false, onClick = { onOffsetChange(timingOffsetMs - 500) }) {
                        Text("-0.5s", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = PearlWhite)
                    }
                    RezonaGlassPill(isActive = timingOffsetMs != 0, onClick = { onOffsetChange(0) }) {
                        Text("Reset", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                    }
                    RezonaGlassPill(isActive = false, onClick = { onOffsetChange(timingOffsetMs + 500) }) {
                        Text("+0.5s", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = PearlWhite)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            RezonaHairlineDivider()

            LyricSourceId.entries.forEach { src ->
                val isSelected = selectedSource == src
                val subtitle = if (src == LyricSourceId.AUTO) {
                    if (activeResolvedSource != LyricSourceId.AUTO) {
                        "Using ${activeResolvedSource.displayName} (fallback ready)"
                    } else {
                        src.description
                    }
                } else {
                    sourceStatuses[src] ?: "Tap to search"
                }
                val isMuted = subtitle == "Not found" || subtitle == "Unavailable"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectSource(src) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(26.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = "Selected",
                                tint = PureWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isMuted) Color(0x44FFFFFF) else Color(0x88FFFFFF))
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = src.displayName,
                            style = Typography.titleMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isMuted && !isSelected) SmokySilver else PureWhite
                        )
                        Text(
                            text = subtitle,
                            style = Typography.bodySmall,
                            color = if (isMuted && !isSelected) Color(0x66FFFFFF) else SmokySilver
                        )
                    }
                }
            }
        }
    }
}

/**
 * Legacy compatibility wrapper if any caller still launches RezonaLyricsStudio as modal.
 */
@Composable
fun RezonaLyricsStudio(
    controller: RezonaPlayerController,
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (!isVisible) return
    var showPoster by remember { mutableStateOf(false) }
    val ambientBase = LocalAmbientBaseColor.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ambientBase)
    ) {
        RezonaInlineLyricsSection(
            controller = controller,
            onCollapseToCover = onDismiss,
            onOpenPosterFromShare = { showPoster = true },
            modifier = Modifier.fillMaxSize()
        )
    }
    if (showPoster) {
        val activeTrack = controller.currentTrack.value
        val lyrics = controller.currentLyrics.value
        if (activeTrack != null) {
            RezonaPosterStudio(
                track = activeTrack,
                allLines = lyrics.map { it.text },
                initialSelectedLines = lyrics.take(3).map { it.text },
                onDismiss = { showPoster = false }
            )
        }
    }
}
