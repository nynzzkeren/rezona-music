package com.nayama.music.ui.lyrics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import com.nayama.music.R
import com.nayama.music.backend.LyricLanguageHelper
import com.nayama.music.backend.LyricSourceId
import com.nayama.music.data.preferences.RezonaPreferences
import com.nayama.music.model.SyncedLyricLine
import com.nayama.music.playback.RezonaPlayerController
import com.nayama.music.ui.components.GlassSurface
import com.nayama.music.ui.components.GlassSurfaceVariant
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Stage F Inline Synced Lyrics Viewport (lyrics.jpg reference):
 * - Header: compact row with rounded cover thumbnail, title, artist, expand-back icon, and 3-dot lyrics options
 * - NO boxes, NO highlight block! Plain large bold text directly on ambient background
 * - Each line optionally shows translation & romanization below in lighter smaller type
 * - Progressive word/character fill on the active line; smooth opacity & hardware blur falloff on distant lines
 * - Instrumental gap pulsing dots (3 dots) before intro or long gaps
 * - Auto-scroll centers active line near upper third (1/3 viewport); user drag pauses follow for 3s; tap seeks cleanly
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
    val centerAlign by preferences.lyricsCenterAlignFlow.collectAsState(initial = false)
    val timingOffsetMs by preferences.lyricsOffsetMsFlow.collectAsState(initial = 0)

    val track = controller.currentTrack.value ?: return
    val lyrics = controller.currentLyrics.value
    val lyricsSource = controller.lyricsSourceLabel.value
    val effectivePosMs = (controller.currentPositionMs.longValue + timingOffsetMs).coerceAtLeast(0L)

    var showOptionsSheet by remember { mutableStateOf(false) }
    var showSourcePickerSheet by remember { mutableStateOf(false) }
    var showSearchLyricsDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val supportsHardwareBlur = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
    val isReducedMotion = remember(context) {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) {
            false
        }
    }

    val activeIndex by remember(effectivePosMs, lyrics) {
        derivedStateOf {
            if (lyrics.isEmpty()) return@derivedStateOf -1
            val firstTime = lyrics.firstOrNull()?.timeMs ?: 0L
            if (effectivePosMs < firstTime) -1 else lyrics.indexOfLast { it.timeMs <= effectivePosMs }
        }
    }

    val isInstrumentalGap by remember(effectivePosMs, lyrics, activeIndex) {
        derivedStateOf {
            if (lyrics.isEmpty()) return@derivedStateOf false
            if (activeIndex < 0) return@derivedStateOf (lyrics.first().timeMs - effectivePosMs) > 2500L
            val cur = lyrics.getOrNull(activeIndex) ?: return@derivedStateOf false
            val next = lyrics.getOrNull(activeIndex + 1) ?: return@derivedStateOf false
            (next.timeMs - cur.timeMs) > 7500L && (effectivePosMs - cur.timeMs) > 3500L && (next.timeMs - effectivePosMs) > 1200L
        }
    }

    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var userPausedAutoScroll by remember(track.id) { mutableStateOf(false) }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            userPausedAutoScroll = true
        }
    }

    // Smooth auto-scroll keeping active line near upper third of the viewport (offset +1 for source chip header)
    LaunchedEffect(activeIndex, userPausedAutoScroll) {
        if (activeIndex !in lyrics.indices || userPausedAutoScroll) return@LaunchedEffect
        val targetLazyIndex = activeIndex + 1
        val viewportHeight = listState.layoutInfo.viewportSize.height
        val targetAnchorY = if (viewportHeight > 0) (viewportHeight * 0.30f).toInt() else 180
        val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetLazyIndex }
        if (visibleItem != null) {
            val delta = visibleItem.offset - targetAnchorY
            if (abs(delta) > 6) {
                listState.animateScrollBy(
                    value = delta.toFloat(),
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 240f)
                )
            }
        } else {
            listState.animateScrollToItem(index = targetLazyIndex, scrollOffset = -targetAnchorY)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        // ── COMPACT LYRICS HEADER (Thumbnail + Title/Artist + Expand + 3-dot) ──
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

            RezonaActionDisc(size = 42.dp, onClick = onCollapseToCover) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_ambient),
                    contentDescription = "Back to Cover View",
                    tint = PearlWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            RezonaActionDisc(size = 42.dp, onClick = { showOptionsSheet = true }) {
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
            contentAlignment = if (centerAlign) Alignment.Center else Alignment.CenterStart
        ) {
            if (isInstrumentalGap) {
                InstrumentalDotsIndicator()
            }
        }

        // ── UNBOXED PLAIN LYRICS VIEWPORT ────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
        ) {
            if (lyrics.isEmpty()) {
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
                            text = "Source: LRCLIB & YouTube Music",
                            style = Typography.bodySmall,
                            color = SmokySilver,
                            textAlign = TextAlign.Center
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RezonaGlassPill(isActive = true, onClick = { controller.refreshLyrics(true) }) {
                                Text("Reload", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                            }
                            RezonaGlassPill(
                                isActive = false,
                                onClick = {
                                    showSourcePickerSheet = true
                                    controller.probeAllLyricsSources()
                                }
                            ) {
                                Text("Sources", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = PearlWhite)
                            }
                            RezonaGlassPill(isActive = false, onClick = { showSearchLyricsDialog = true }) {
                                Text("Search", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = SoftSilver)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 24.dp,
                        top = 12.dp,
                        end = 24.dp,
                        bottom = 140.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    item(key = "v2_lyrics_source_top") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (centerAlign) Arrangement.Center else Arrangement.Start
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        showSourcePickerSheet = true
                                        controller.probeAllLyricsSources()
                                    }
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
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
                    }

                    itemsIndexed(
                        items = lyrics,
                        key = { index, line -> "v2_lyric_${line.timeMs}_$index" },
                        contentType = { _, _ -> "v2_lyric_row" }
                    ) { index, line ->
                        val nextTime = lyrics.getOrNull(index + 1)?.timeMs ?: (line.timeMs + 4000L)
                        V2LyricLineItem(
                            index = index,
                            line = line,
                            nextLineTimeMs = nextTime,
                            activeIndex = activeIndex,
                            currentPositionMs = effectivePosMs,
                            translationEnabled = translationEnabled,
                            romanizationEnabled = romanizationEnabled,
                            blurEnabled = blurUnfocused,
                            centerAlign = centerAlign,
                            isReducedMotion = isReducedMotion,
                            supportsHardwareBlur = supportsHardwareBlur,
                            onSeek = {
                                userPausedAutoScroll = false
                                controller.seekToPosition((line.timeMs - timingOffsetMs).coerceAtLeast(0L))
                            }
                        )
                    }

                    item(key = "v2_lyrics_source") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 24.dp),
                            horizontalArrangement = if (centerAlign) Arrangement.Center else Arrangement.Start
                        ) {
                            Text(
                                text = "$lyricsSource · Switch source",
                                style = Typography.labelSmall.copy(fontSize = 11.sp),
                                color = SmokySilver,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        showSourcePickerSheet = true
                                        controller.probeAllLyricsSources()
                                    }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }

                // Floating "Resync" pill when user manually scrolls away
                androidx.compose.animation.AnimatedVisibility(
                    visible = userPausedAutoScroll && lyrics.isNotEmpty(),
                    enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f),
                    exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.92f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                ) {
                    GlassSurface(
                        variant = GlassSurfaceVariant.Floating,
                        shape = CircleShape,
                        hapticOnPress = true,
                        onClick = {
                            userPausedAutoScroll = false
                            if (activeIndex in lyrics.indices) {
                                scope.launch {
                                    val vh = listState.layoutInfo.viewportSize.height
                                    val targetAnchorY = if (vh > 0) (vh * 0.30f).toInt() else 180
                                    listState.animateScrollToItem(index = activeIndex + 1, scrollOffset = -targetAnchorY)
                                }
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
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
            centerAlign = centerAlign,
            timingOffsetMs = timingOffsetMs,
            onToggleTranslation = { scope.launch { preferences.setLyricsTranslationEnabled(it) } },
            onToggleRomanization = { scope.launch { preferences.setLyricsRomanizationEnabled(it) } },
            onToggleBlur = { scope.launch { preferences.setLyricsBlurUnfocused(it) } },
            onToggleCenterAlign = { scope.launch { preferences.setLyricsCenterAlign(it) } },
            onOffsetChange = { scope.launch { preferences.setLyricsOffsetMs(it) } },
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

@Composable
private fun V2LyricLineItem(
    index: Int,
    line: SyncedLyricLine,
    nextLineTimeMs: Long,
    activeIndex: Int,
    currentPositionMs: Long,
    translationEnabled: Boolean,
    romanizationEnabled: Boolean,
    blurEnabled: Boolean,
    centerAlign: Boolean,
    isReducedMotion: Boolean,
    supportsHardwareBlur: Boolean,
    onSeek: () -> Unit
) {
    val distance = if (activeIndex < 0) index + 1 else abs(index - activeIndex)
    val isActive = activeIndex == index

    val targetAlpha = when (distance) {
        0 -> 1.0f
        1 -> 0.44f
        2 -> 0.28f
        else -> 0.14f
    }
    val targetBlurDp = if (blurEnabled && supportsHardwareBlur && !isReducedMotion) {
        when (distance) {
            0 -> 0f
            1 -> 0.8f
            2 -> 2.0f
            else -> 3.4f
        }
    } else 0f

    val animDuration = if (isReducedMotion) 100 else 320
    val animAlpha by animateFloatAsState(targetAlpha, tween(animDuration, easing = FastOutSlowInEasing), label = "la_$index")
    val animBlur by animateFloatAsState(targetBlurDp, tween(animDuration, easing = FastOutSlowInEasing), label = "lb_$index")
    val animColor by animateColorAsState(
        targetValue = if (isActive) PureWhite else PearlWhite.copy(alpha = 0.82f),
        animationSpec = tween(animDuration),
        label = "lc_$index"
    )

    val translation = remember(line.text, translationEnabled) {
        if (translationEnabled) LyricLanguageHelper.translateToDeviceLanguage(line.text) else null
    }
    val romanization = remember(line.text, romanizationEnabled) {
        if (romanizationEnabled) LyricLanguageHelper.romanize(line.text) else null
    }
    val align = if (centerAlign) TextAlign.Center else TextAlign.Start

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = PureWhite),
                onClick = onSeek
            )
            .padding(vertical = 2.dp)
            .graphicsLayer {
                alpha = animAlpha
                transformOrigin = TransformOrigin(if (centerAlign) 0.5f else 0f, 0.5f)
                if (supportsHardwareBlur && animBlur > 0.05f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val px = animBlur * density
                    renderEffect = android.graphics.RenderEffect.createBlurEffect(
                        px, px, android.graphics.Shader.TileMode.CLAMP
                    ).asComposeRenderEffect()
                } else {
                    renderEffect = null
                }
            }
    ) {
        if (isActive && !line.words.isNullOrEmpty()) {
            // Word-by-word active highlight (and subtle progressive brighten per lyrics.jpg)
            val annotated = buildAnnotatedString {
                line.words.forEachIndexed { wIdx, w ->
                    val wordColor = when {
                        currentPositionMs >= w.endMs -> PureWhite
                        currentPositionMs < w.startMs -> Color(0x78FFFFFF)
                        else -> {
                            val d = (w.endMs - w.startMs).toFloat().coerceAtLeast(1f)
                            val p = ((currentPositionMs - w.startMs).toFloat() / d).coerceIn(0f, 1f)
                            lerp(Color(0x78FFFFFF), PureWhite, p)
                        }
                    }
                    withStyle(SpanStyle(color = wordColor)) { append(w.word) }
                    if (wIdx < line.words.lastIndex) append(" ")
                }
            }
            Text(
                text = annotated,
                style = Typography.displayLarge.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 32.sp,
                    letterSpacing = (-0.3).sp
                ),
                textAlign = align,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (isActive) {
            // Line-level progressive sweep when no word tags exist (brightens from start as line progresses)
            val lineDur = (nextLineTimeMs - line.timeMs).toFloat().coerceAtLeast(1000f)
            val frac = ((currentPositionMs - line.timeMs).toFloat() / lineDur).coerceIn(0.08f, 1f)
            val splitIdx = (line.text.length * frac).toInt().coerceIn(1, line.text.length.coerceAtLeast(1))
            val annotated = buildAnnotatedString {
                withStyle(SpanStyle(color = PureWhite)) { append(line.text.substring(0, splitIdx)) }
                if (splitIdx < line.text.length) {
                    withStyle(SpanStyle(color = Color(0x8AFFFFFF))) { append(line.text.substring(splitIdx)) }
                }
            }
            Text(
                text = annotated,
                style = Typography.displayLarge.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 32.sp,
                    letterSpacing = (-0.3).sp
                ),
                textAlign = align,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = line.text,
                style = Typography.displayLarge.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 32.sp,
                    letterSpacing = (-0.3).sp
                ),
                color = animColor,
                textAlign = align,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!romanization.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = romanization,
                style = Typography.bodySmall.copy(fontSize = 13.sp),
                color = SoftSilver.copy(alpha = 0.72f),
                textAlign = align,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!translation.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = translation,
                style = Typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal),
                color = Color(0x99FFFFFF),
                textAlign = align,
                modifier = Modifier.fillMaxWidth()
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
    onReload: () -> Unit,
    onOpenManualSearch: () -> Unit,
    onCopyAll: () -> Unit,
    onOpenPoster: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xEE101218),
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
                RezonaGlassButton(text = "Reload", isPrimary = false, onClick = onReload, modifier = Modifier.weight(1f))
                RezonaGlassButton(text = "Search", isPrimary = false, onClick = onOpenManualSearch, modifier = Modifier.weight(1f))
                RezonaGlassButton(text = "Copy", isPrimary = false, onClick = onCopyAll, modifier = Modifier.weight(1f))
            }
            RezonaGlassButton(
                text = "Create Lyrics Poster",
                isPrimary = true,
                onClick = onOpenPoster,
                modifier = Modifier.fillMaxWidth()
            )

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
        modifier = Modifier.fillMaxWidth(),
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xEE101218),
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

            // Quick sync offset bar
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
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectSource(src) }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE608090C))
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
