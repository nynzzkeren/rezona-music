package com.nayama.music.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.DisplayCutout
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import com.nayama.music.MainActivity
import com.nayama.music.R
import com.nayama.music.backend.LyricLanguageHelper
import com.nayama.music.data.preferences.RezonaPreferences
import com.nayama.music.playback.RepeatMode
import com.nayama.music.playback.RezonaPlayerController
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.rememberAnimatedArtworkPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class CutoutPlacement(
    val xOffsetPx: Int,
    val yOffsetPx: Int,
    val isFloatingPunchHole: Boolean,
    val cutoutWidthPx: Int
)

object OverlayPermissionHelper {
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * 3-tier safe fallback for Xiaomi MIUI/HyperOS, Vivo, Oppo/Realme, Samsung, and Android Go
     * where package-scoped ACTION_MANAGE_OVERLAY_PERMISSION may throw ActivityNotFoundException.
     */
    fun openOverlayPermissionSettings(context: Context) {
        val pkgUri = Uri.parse("package:${context.packageName}")
        val candidates = listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return
        }
        Toast.makeText(
            context,
            "Open Settings -> Apps -> Rezona -> Enable 'Display over other apps'",
            Toast.LENGTH_LONG
        ).show()
    }
}

/**
 * Floating Liquid Glass Dynamic Island overlay service:
 * - Drives view visibility & WindowManager layout updates from [serviceScope] on Main thread
 *   (preventing Android WindowManager VSYNC freeze when transitioning from View.GONE to VISIBLE).
 * - Smart Camera Cutout Engine:
 *   - Floating punch-hole camera ("kamera melayang"): overlaps/wraps directly over the punch-hole!
 *   - Attached notch / waterdrop / bezel camera: sits cleanly right below the camera/status bar!
 */
class RezonaDynamicIslandService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private val isExpandedFlow = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()
        overlayRunningFlow.value = true
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val initialPlacement = resolveCutoutPlacement(wm, null, "Auto", expanded = false)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = initialPlacement.xOffsetPx
            y = initialPlacement.yOffsetPx
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        windowParams = params

        val controller = RezonaPlayerController.getInstance(applicationContext)
        val preferences = RezonaPreferences(applicationContext)

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@RezonaDynamicIslandService)
            setViewTreeSavedStateRegistryOwner(this@RezonaDynamicIslandService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val expanded by isExpandedFlow.collectAsState()
                RezonaDynamicIslandCapsule(
                    controller = controller,
                    isExpanded = expanded,
                    onExpandedChange = { isExpandedFlow.value = it },
                    onOpenApp = {
                        isExpandedFlow.value = false
                        val intent = Intent(this@RezonaDynamicIslandService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                    }
                )
            }
        }

        composeView = view
        val added = runCatching {
            wm.addView(view, params)
            true
        }.getOrDefault(false)

        if (!added) {
            stopSelf()
            return
        }

        // Drive visibility + cutout positioning + topmost Z-order re-assertion from serviceScope
        var wasVisible = false
        serviceScope.launch {
            combine(
                appInForegroundFlow,
                previewUntilMsFlow,
                snapshotFlow { controller.currentTrack.value },
                preferences.dynamicIslandCutoutModeFlow,
                isExpandedFlow
            ) { inForeground, previewUntilMs, track, cutoutMode, expanded ->
                val isPreviewing = System.currentTimeMillis() < previewUntilMs
                val visible = track != null && (!inForeground || isPreviewing)
                Triple(visible, cutoutMode, expanded)
            }.collectLatest { (visible, cutoutMode, expanded) ->
                val v = composeView ?: return@collectLatest
                val p = windowParams ?: return@collectLatest
                val placement = resolveCutoutPlacement(wm, v, cutoutMode, expanded)
                val targetVis = if (visible) View.VISIBLE else View.INVISIBLE
                var layoutChanged = false

                if (p.x != placement.xOffsetPx || p.y != placement.yOffsetPx) {
                    p.x = placement.xOffsetPx
                    p.y = placement.yOffsetPx
                    layoutChanged = true
                }
                if (v.visibility != targetVis) {
                    v.visibility = targetVis
                    if (!visible) isExpandedFlow.value = false
                }
                // Re-attach window to front of WindowManager Z-order stack whenever becoming visible or expanding
                if (visible && (!wasVisible || expanded)) {
                    runCatching {
                        wm.removeViewImmediate(v)
                        wm.addView(v, p)
                    }.onFailure {
                        runCatching { wm.updateViewLayout(v, p) }
                    }
                } else if (layoutChanged) {
                    runCatching { wm.updateViewLayout(v, p) }
                }
                wasVisible = visible
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PREVIEW) {
            previewUntilMsFlow.value = System.currentTimeMillis() + 6500L
            isExpandedFlow.value = true
            serviceScope.launch {
                delay(6600L)
                previewUntilMsFlow.value = 0L
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        overlayRunningFlow.value = false
        serviceScope.cancel()
        composeView?.let { v ->
            runCatching { windowManager?.removeView(v) }
        }
        composeView = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun resolveCutoutPlacement(
        wm: WindowManager,
        view: View?,
        cutoutMode: String,
        expanded: Boolean
    ): CutoutPlacement {
        val dm = resources.displayMetrics
        val screenWidthPx = dm.widthPixels.coerceAtLeast(320)
        val statusBarHeightPx = getStatusBarHeightPx()
        val pillHeightPx = dpToPx(36)

        val cutout: DisplayCutout? = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                runCatching { wm.currentWindowMetrics.windowInsets.displayCutout }.getOrNull()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                runCatching { view?.rootWindowInsets?.displayCutout }.getOrNull()
            else -> null
        }

        val topRect: Rect? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && cutout != null) {
            cutout.boundingRects.firstOrNull { it.height() > 0 && it.top < dpToPx(64) }
        } else {
            null
        }

        // Detect floating punch-hole camera ("kamera melayang"):
        // Either detached from top bezel (top >= 2dp) OR compact circular/pill punch-hole (<= 48dp wide).
        val autoFloatingPunchHole = topRect != null && (
            topRect.top >= dpToPx(2) ||
                (topRect.width() in 1..dpToPx(48) && topRect.height() <= dpToPx(48))
            )

        val useOverlap = when (cutoutMode) {
            "Overlap" -> true
            "Below" -> false
            else -> autoFloatingPunchHole
        }

        return if (useOverlap) {
            if (expanded) {
                val yTop = (topRect?.top ?: dpToPx(6)).coerceAtLeast(dpToPx(6))
                CutoutPlacement(
                    xOffsetPx = 0,
                    yOffsetPx = yTop,
                    isFloatingPunchHole = true,
                    cutoutWidthPx = topRect?.width() ?: 0
                )
            } else {
                val centerY = topRect?.centerY() ?: (statusBarHeightPx / 2).coerceAtLeast(dpToPx(18))
                val yAligned = (centerY - pillHeightPx / 2).coerceAtLeast(dpToPx(2))
                val xAligned = if (topRect != null) {
                    val rawX = topRect.centerX() - (screenWidthPx / 2)
                    // Keep capsule safely within horizontal screen edges
                    val maxShift = (screenWidthPx / 2 - dpToPx(98)).coerceAtLeast(0)
                    rawX.coerceIn(-maxShift, maxShift)
                } else {
                    0
                }
                CutoutPlacement(
                    xOffsetPx = xAligned,
                    yOffsetPx = yAligned,
                    isFloatingPunchHole = true,
                    cutoutWidthPx = topRect?.width() ?: 0
                )
            }
        } else {
            // Non-floating camera (attached notch / waterdrop / bezel camera) -> place cleanly below it!
            val safeTop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && cutout != null) {
                cutout.safeInsetTop.takeIf { it > 0 } ?: statusBarHeightPx
            } else {
                statusBarHeightPx
            }
            CutoutPlacement(
                xOffsetPx = 0,
                yOffsetPx = safeTop + dpToPx(4),
                isFloatingPunchHole = false,
                cutoutWidthPx = 0
            )
        }
    }

    private fun getStatusBarHeightPx(): Int {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else dpToPx(28)
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val ACTION_PREVIEW = "com.nayama.music.overlay.ACTION_PREVIEW_ISLAND"
        private val appInForegroundFlow = MutableStateFlow(true)
        private val previewUntilMsFlow = MutableStateFlow(0L)
        private val overlayRunningFlow = MutableStateFlow(false)

        fun isOverlayRunning(): Boolean = overlayRunningFlow.value

        fun setAppInForeground(inForeground: Boolean) {
            appInForegroundFlow.value = inForeground
        }

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context.applicationContext, RezonaDynamicIslandService::class.java)
            runCatching { context.applicationContext.startService(intent) }
        }

        fun preview(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context.applicationContext, RezonaDynamicIslandService::class.java).apply {
                action = ACTION_PREVIEW
            }
            runCatching { context.applicationContext.startService(intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context.applicationContext, RezonaDynamicIslandService::class.java)
            runCatching { context.applicationContext.stopService(intent) }
        }
    }
}

/**
 * Shared Dynamic Island Composable (used both in System WindowManager Overlay AND In-App universal fallback).
 * Implements the exact frosted Liquid Glass design with REZONA header, rounded square cover art,
 * 3-line live synced lyrics, interactive remaining-time scrubber, and full transport + utility bar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RezonaDynamicIslandCapsule(
    controller: RezonaPlayerController,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { RezonaPreferences(context.applicationContext) }
    val translationPref by preferences.lyricsTranslationEnabledFlow.collectAsState(initial = false)

    val track = controller.currentTrack.value ?: return
    val isPlaying = controller.isPlaying.value
    val isLiked = controller.isLiked.value
    val isShuffle = controller.isShuffle.value
    val repeatMode = controller.repeatMode.value
    val sleepMinutes = controller.sleepTimerMinutes.value
    val sleepEndOfTrack = controller.sleepTimerEndOfTrack.value
    val positionMs = controller.currentPositionMs.longValue
    val durationMs = controller.durationMs.longValue.coerceAtLeast(1L)
    val lyrics = controller.currentLyrics.value

    // Extract smooth animated ambient glow from current cover art
    val ambientGlow = rememberAnimatedArtworkPalette(
        context = context,
        artworkUrl = track.coverArtUrl,
        fallbackKey = track.id
    ).primaryAmbient

    // Active lyric index + optional translated line
    val activeIdx = remember(lyrics, positionMs) {
        if (lyrics.isEmpty()) -1
        else lyrics.indexOfLast { it.timeMs <= positionMs + 160L }
    }
    val prevLine = if (activeIdx > 0) lyrics.getOrNull(activeIdx - 1)?.text else null
    val currLine = if (activeIdx >= 0) lyrics.getOrNull(activeIdx)?.text else lyrics.firstOrNull()?.text
    val nextLine = if (activeIdx >= 0) lyrics.getOrNull(activeIdx + 1)?.text else lyrics.getOrNull(1)?.text

    var translatedCurrLine by remember(track.id, currLine, translationPref) {
        mutableStateOf<String?>(null)
    }
    LaunchedEffect(currLine, translationPref) {
        if (!translationPref || currLine.isNullOrBlank()) {
            translatedCurrLine = null
        } else {
            val map = LyricLanguageHelper.translateLyricsBatch(listOf(currLine))
            translatedCurrLine = map[currLine.trim()]
        }
    }

    // Auto-collapse after 12 seconds without interaction
    var interactionTick by remember { mutableStateOf(0) }
    LaunchedEffect(isExpanded, interactionTick, track.id) {
        if (isExpanded) {
            delay(12000L)
            onExpandedChange(false)
        }
    }

    val collapsedWidth = if (isPlaying && !currLine.isNullOrBlank()) 224.dp else 176.dp
    val widthDp by animateDpAsState(
        targetValue = if (isExpanded) 356.dp else collapsedWidth,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 360f),
        label = "island_width"
    )
    val cornerDp by animateDpAsState(
        targetValue = if (isExpanded) 28.dp else 22.dp,
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 380f),
        label = "island_corner"
    )
    val shape = RoundedCornerShape(cornerDp)

    val topBorderBrush = remember {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.34f),
                Color.White.copy(alpha = 0.10f)
            )
        )
    }

    Box(
        modifier = modifier
            .width(widthDp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xF21B1D24),
                        Color(0xF40E1015)
                    ),
                    start = Offset.Zero,
                    end = Offset(0f, 600f)
                )
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        ambientGlow.copy(alpha = if (isExpanded) 0.38f else 0.22f),
                        Color.Transparent
                    ),
                    center = Offset(120f, 160f),
                    radius = if (isExpanded) 460f else 180f
                )
            )
            .border(1.dp, topBorderBrush, shape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    interactionTick++
                    onExpandedChange(!isExpanded)
                },
                onLongClick = onOpenApp
            )
    ) {
        AnimatedContent(
            targetState = isExpanded,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(130)) },
            label = "island_morph"
        ) { expanded ->
            if (!expanded) {
                CollapsedPillContent(
                    coverArtUrl = track.coverArtUrl,
                    activeLyricLine = if (isPlaying) currLine else null,
                    isPlaying = isPlaying
                )
            } else {
                ExpandedIslandGlassCard(
                    title = track.title,
                    artist = track.artist,
                    coverArtUrl = track.coverArtUrl,
                    prevLyric = prevLine,
                    currLyric = currLine,
                    translatedLyric = translatedCurrLine,
                    nextLyric = nextLine,
                    isPlaying = isPlaying,
                    isLiked = isLiked,
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    isTranslationOn = translationPref,
                    isSleepTimerOn = sleepMinutes != null || sleepEndOfTrack,
                    progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                    elapsedText = formatMs(positionMs),
                    remainingText = "-" + formatMs((durationMs - positionMs).coerceAtLeast(0L)),
                    onOpenApp = onOpenApp,
                    onSeekFraction = { frac ->
                        interactionTick++
                        controller.seekToPosition((frac * durationMs).toLong().coerceIn(0L, durationMs))
                    },
                    onToggleShuffle = {
                        interactionTick++
                        controller.toggleShuffle()
                    },
                    onPrev = {
                        interactionTick++
                        controller.skipPrevious()
                    },
                    onPlayPause = {
                        interactionTick++
                        controller.togglePlayPause()
                    },
                    onNext = {
                        interactionTick++
                        controller.skipNext()
                    },
                    onToggleRepeat = {
                        interactionTick++
                        controller.toggleRepeat()
                    },
                    onToggleTranslate = {
                        interactionTick++
                        scope.launch { preferences.setLyricsTranslationEnabled(!translationPref) }
                    },
                    onShowVolumeUi = {
                        interactionTick++
                        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        am?.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_SAME,
                            AudioManager.FLAG_SHOW_UI
                        )
                    },
                    onCycleSleepTimer = {
                        interactionTick++
                        val nextMin = when (sleepMinutes) {
                            null -> 15
                            15 -> 30
                            30 -> 60
                            else -> null
                        }
                        controller.setSleepTimer(nextMin)
                        val msg = if (nextMin != null) "Sleep timer: ${nextMin}m" else "Sleep timer off"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    onToggleLike = {
                        interactionTick++
                        controller.toggleLike()
                    }
                )
            }
        }
    }
}

@Composable
private fun CollapsedPillContent(
    coverArtUrl: String?,
    activeLyricLine: String?,
    isPlaying: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        AsyncImage(
            model = coverArtUrl,
            contentDescription = "Cover Art",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xFF1D2028))
        )

        if (!activeLyricLine.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = activeLyricLine,
                color = PearlWhite.copy(alpha = 0.92f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        MiniWaveformBars(isPlaying = isPlaying)
    }
}

@Composable
private fun MiniWaveformBars(isPlaying: Boolean) {
    val infinite = rememberInfiniteTransition(label = "island_wave")
    val bar1 by infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by infinite.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(510, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(370, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "b3"
    )

    val heights = if (isPlaying) listOf(bar1, bar2, bar3) else listOf(0.28f, 0.28f, 0.28f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(14.dp)
    ) {
        heights.forEach { frac ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height((14f * frac).dp.coerceAtLeast(3.dp))
                    .clip(CircleShape)
                    .background(if (isPlaying) PureWhite else SoftSilver.copy(alpha = 0.6f))
            )
        }
    }
}

@Composable
private fun ExpandedIslandGlassCard(
    title: String,
    artist: String,
    coverArtUrl: String?,
    prevLyric: String?,
    currLyric: String?,
    translatedLyric: String?,
    nextLyric: String?,
    isPlaying: Boolean,
    isLiked: Boolean,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    isTranslationOn: Boolean,
    isSleepTimerOn: Boolean,
    progress: Float,
    elapsedText: String,
    remainingText: String,
    onOpenApp: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleTranslate: () -> Unit,
    onShowVolumeUi: () -> Unit,
    onCycleSleepTimer: () -> Unit,
    onToggleLike: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header Row: Frosted Rezona icon badge + REZONA Title / Artist
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .border(0.6.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenApp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_sound_waves),
                    contentDescription = "Rezona",
                    tint = PureWhite,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "REZONA",
                        color = Color.White.copy(alpha = 0.56f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.08.em
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = title,
                        color = PureWhite,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = artist,
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 2. Middle Row: Rounded-square Cover Art + 3-Line Synced Lyrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = coverArtUrl,
                contentDescription = "Album Cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1C1F26))
                    .border(0.75.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenApp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (!currLyric.isNullOrBlank()) {
                    if (!prevLyric.isNullOrBlank()) {
                        Text(
                            text = prevLyric,
                            color = Color.White.copy(alpha = 0.42f),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    AnimatedContent(
                        targetState = currLyric,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
                        label = "active_lyric_line"
                    ) { activeText ->
                        Text(
                            text = activeText,
                            color = PureWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 20.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!translatedLyric.isNullOrBlank()) {
                        Text(
                            text = translatedLyric,
                            color = PearlWhite.copy(alpha = 0.78f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (!nextLyric.isNullOrBlank()) {
                        Text(
                            text = nextLyric,
                            color = Color.White.copy(alpha = 0.42f),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = title,
                        color = PureWhite,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tap cover to open full player",
                        color = Color.White.copy(alpha = 0.46f),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }

        // 3. Scrubber Row: Elapsed ("1:42") · Interactive Slider with Dot · Remaining ("-2:15")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = elapsedText,
                color = Color.White.copy(alpha = 0.60f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(18.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val frac = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            onSeekFraction(frac)
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                val trackW = maxWidth
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.24f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(2.5.dp)
                        .clip(CircleShape)
                        .background(PureWhite)
                )
                val thumbX = (trackW - 8.dp) * progress
                Box(
                    modifier = Modifier
                        .offset { IntOffset(thumbX.roundToPx(), 0) }
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(PureWhite)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = remainingText,
                color = Color.White.copy(alpha = 0.60f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // 4. Bottom Action Row: Left Transport + Right Quick Utilities & Coral-Red Heart
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left transport cluster
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IslandIconBtn(
                    iconRes = R.drawable.ic_shuffle,
                    tint = if (isShuffle) PureWhite else Color.White.copy(alpha = 0.56f),
                    sizeDp = 16,
                    onClick = onToggleShuffle
                )
                IslandIconBtn(
                    iconRes = R.drawable.ic_skip_previous,
                    tint = PureWhite.copy(alpha = 0.90f),
                    sizeDp = 19,
                    onClick = onPrev
                )
                // Frosted Play/Pause Pill (matching Screenshot 1)
                Box(
                    modifier = Modifier
                        .width(46.dp)
                        .height(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .border(0.8.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                        .clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                        ),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = PureWhite,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IslandIconBtn(
                    iconRes = R.drawable.ic_skip_next,
                    tint = PureWhite.copy(alpha = 0.90f),
                    sizeDp = 19,
                    onClick = onNext
                )
                IslandIconBtn(
                    iconRes = if (repeatMode == RepeatMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
                    tint = if (repeatMode != RepeatMode.OFF) PureWhite else Color.White.copy(alpha = 0.56f),
                    sizeDp = 16,
                    onClick = onToggleRepeat
                )
            }

            // Right utility cluster: A · Volume · Sleep Timer · ••• · Heart
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isTranslationOn) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable(onClick = onToggleTranslate),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "A",
                        color = if (isTranslationOn) PureWhite else Color.White.copy(alpha = 0.62f),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IslandIconBtn(
                    iconRes = R.drawable.ic_volume_high,
                    tint = Color.White.copy(alpha = 0.68f),
                    sizeDp = 17,
                    onClick = onShowVolumeUi
                )
                IslandIconBtn(
                    iconRes = R.drawable.ic_timer,
                    tint = if (isSleepTimerOn) PureWhite else Color.White.copy(alpha = 0.68f),
                    sizeDp = 17,
                    onClick = onCycleSleepTimer
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenApp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "•••",
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                val heartColor by animateColorAsState(
                    targetValue = if (isLiked) Color(0xFFFF5A5F) else Color.White.copy(alpha = 0.72f),
                    animationSpec = tween(200),
                    label = "heart_color"
                )
                IslandIconBtn(
                    iconRes = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
                    tint = heartColor,
                    sizeDp = 18,
                    onClick = onToggleLike
                )
            }
        }
    }
}

@Composable
private fun IslandIconBtn(
    iconRes: Int,
    tint: Color,
    sizeDp: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(sizeDp.dp)
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%d:%02d".format(mins, secs)
}
