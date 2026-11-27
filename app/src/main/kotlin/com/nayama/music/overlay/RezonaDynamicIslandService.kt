package com.nayama.music.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import com.nayama.music.MainActivity
import com.nayama.music.R
import com.nayama.music.backend.LyricLanguageHelper
import com.nayama.music.data.preferences.RezonaPreferences
import com.nayama.music.playback.RepeatMode
import com.nayama.music.playback.RezonaPlayerController
import com.nayama.music.playback.SoundPreset
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

    private var sideComposeView: ComposeView? = null
    private var sideWindowParams: WindowManager.LayoutParams? = null
    private val isSideCardOpenFlow = MutableStateFlow(false)

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

        // Drive visibility + cutout positioning via updateViewLayout (never detach/re-attach live ComposeView!)
        serviceScope.launch {
            combine(
                appInForegroundFlow,
                previewUntilMsFlow,
                snapshotFlow { controller.currentTrack.value },
                preferences.dynamicIslandEnabledFlow,
                combine(preferences.dynamicIslandCutoutModeFlow, isExpandedFlow) { c, e -> c to e }
            ) { inForeground, previewUntilMs, track, islandEnabled, (cutoutMode, expanded) ->
                val isPreviewing = System.currentTimeMillis() < previewUntilMs
                val visible = islandEnabled && track != null && (!inForeground || isPreviewing)
                Triple(visible, cutoutMode, expanded)
            }.collectLatest { (visible, cutoutMode, expanded) ->
                val v = composeView ?: return@collectLatest
                val p = windowParams ?: return@collectLatest
                val placement = resolveCutoutPlacement(wm, v, cutoutMode, expanded)
                val targetVis = if (visible) View.VISIBLE else View.GONE
                val desiredFlags = if (visible) {
                    p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                } else {
                    p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                }

                var layoutChanged = false
                if (p.x != placement.xOffsetPx || p.y != placement.yOffsetPx) {
                    p.x = placement.xOffsetPx
                    p.y = placement.yOffsetPx
                    layoutChanged = true
                }
                if (p.flags != desiredFlags) {
                    p.flags = desiredFlags
                    layoutChanged = true
                }
                if (v.visibility != targetVis) {
                    v.visibility = targetVis
                    if (!visible) isExpandedFlow.value = false
                    layoutChanged = true
                }
                if (layoutChanged) {
                    runCatching { wm.updateViewLayout(v, p) }
                }
            }
        }

        // 2. iOS Control Center Side-Edge Pull-Out Music Overlay Window
        val sideParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            x = 0
            y = 0
        }
        sideWindowParams = sideParams

        val sideView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@RezonaDynamicIslandService)
            setViewTreeSavedStateRegistryOwner(this@RezonaDynamicIslandService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val sideOpen by isSideCardOpenFlow.collectAsState()
                if (!sideOpen) {
                    RezonaIosSideEdgeHandle(
                        onOpenCard = { isSideCardOpenFlow.value = true }
                    )
                } else {
                    RezonaIosMusicCardOverlay(
                        controller = controller,
                        isOpen = true,
                        onClose = { isSideCardOpenFlow.value = false }
                    )
                }
            }
        }
        sideComposeView = sideView
        runCatching { wm.addView(sideView, sideParams) }

        serviceScope.launch {
            combine(
                appInForegroundFlow,
                snapshotFlow { controller.currentTrack.value },
                preferences.iosSidePlayerEnabledFlow,
                isSideCardOpenFlow
            ) { inForeground, track, iosEnabled, sideOpen ->
                val visible = iosEnabled && track != null && !inForeground
                visible to sideOpen
            }.collectLatest { (visible, sideOpen) ->
                val sv = sideComposeView ?: return@collectLatest
                val sp = sideWindowParams ?: return@collectLatest
                val targetVis = if (visible) View.VISIBLE else View.GONE
                val desiredW = if (visible && sideOpen) {
                    WindowManager.LayoutParams.MATCH_PARENT
                } else {
                    WindowManager.LayoutParams.WRAP_CONTENT
                }
                val desiredH = if (visible && sideOpen) {
                    WindowManager.LayoutParams.MATCH_PARENT
                } else {
                    WindowManager.LayoutParams.WRAP_CONTENT
                }
                val desiredFlags = if (visible) {
                    sp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                } else {
                    sp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                }

                var changed = false
                if (sp.width != desiredW || sp.height != desiredH) {
                    sp.width = desiredW
                    sp.height = desiredH
                    changed = true
                }
                if (sp.flags != desiredFlags) {
                    sp.flags = desiredFlags
                    changed = true
                }
                if (sv.visibility != targetVis) {
                    sv.visibility = targetVis
                    if (!visible) isSideCardOpenFlow.value = false
                    changed = true
                }
                if (changed) {
                    runCatching { wm.updateViewLayout(sv, sp) }
                }
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
        sideComposeView?.let { sv ->
            runCatching { windowManager?.removeView(sv) }
        }
        sideComposeView = null
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

private val ISLAND_VOICE_PRESETS = listOf(
    SoundPreset.NORMAL,
    SoundPreset.SLOWED_REVERB,
    SoundPreset.LOFI,
    SoundPreset.NIGHTCORE,
    SoundPreset.SPED_UP,
    SoundPreset.EIGHT_D
)

/**
 * Fast software box-blur transformation for Coil so the Dynamic Island background
 * shows the rich, heavily blurred album cover art on ALL Android versions (API 24-35).
 */
private class IslandBlurredCoverTransformation(
    private val passes: Int = 3,
    private val radius: Int = 8
) : Transformation {
    override val cacheKey: String = "island_blur_${passes}_$radius"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = 72
        val h = 72
        val scaled = Bitmap.createScaledBitmap(input, w, h, true)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)

        repeat(passes) {
            // Horizontal pass
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    var r = 0
                    var g = 0
                    var b = 0
                    var count = 0
                    for (dx in -radius..radius) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val c = pixels[row + nx]
                        r += (c shr 16) and 0xFF
                        g += (c shr 8) and 0xFF
                        b += c and 0xFF
                        count++
                    }
                    tmp[row + x] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                }
            }
            // Vertical pass
            for (x in 0 until w) {
                for (y in 0 until h) {
                    var r = 0
                    var g = 0
                    var b = 0
                    var count = 0
                    for (dy in -radius..radius) {
                        val ny = (y + dy).coerceIn(0, h - 1)
                        val c = tmp[ny * w + x]
                        r += (c shr 16) and 0xFF
                        g += (c shr 8) and 0xFF
                        b += c and 0xFF
                        count++
                    }
                    pixels[y * w + x] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                }
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled != input) scaled.recycle()
        return out
    }
}

/**
 * Shared Dynamic Island Composable (used both in System WindowManager Overlay AND In-App universal fallback).
 * Matches the blurred cover-art Liquid Glass capsule design with smooth sliding lyrics + translation,
 * voice changer (Slowed+Reverb / Lo-fi / Nightcore), Love, Repeat, and transport controls.
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
    val translationPref by preferences.lyricsTranslationEnabledFlow.collectAsState(initial = true)
    var showIslandLyrics by remember { mutableStateOf(true) }

    val track = controller.currentTrack.value ?: return
    val isPlaying = controller.isPlaying.value
    val isLiked = controller.isLiked.value
    val repeatMode = controller.repeatMode.value
    val activePreset = controller.activeSoundPreset.value
    val positionMs = controller.currentPositionMs.longValue
    val durationMs = controller.durationMs.longValue.coerceAtLeast(1L)
    val lyrics = controller.currentLyrics.value

    val ambientGlow = rememberAnimatedArtworkPalette(
        context = context,
        artworkUrl = track.coverArtUrl,
        fallbackKey = track.id
    ).primaryAmbient

    val activeIdx = remember(lyrics, positionMs) {
        if (lyrics.isEmpty()) -1
        else lyrics.indexOfLast { it.timeMs <= positionMs + 160L }
    }
    val currLine = if (activeIdx >= 0) lyrics.getOrNull(activeIdx)?.text else lyrics.firstOrNull()?.text

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

    val collapsedWidth = if (showIslandLyrics && !currLine.isNullOrBlank()) 248.dp else 218.dp
    val widthDp by animateDpAsState(
        targetValue = if (isExpanded) 348.dp else collapsedWidth,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 360f),
        label = "island_width"
    )
    val cornerDp by animateDpAsState(
        targetValue = if (isExpanded) 32.dp else 24.dp,
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 380f),
        label = "island_corner"
    )
    val shape = RoundedCornerShape(cornerDp)

    val topBorderBrush = remember {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.42f),
                Color.White.copy(alpha = 0.14f)
            )
        )
    }

    val blurredCoverRequest = remember(context, track.coverArtUrl) {
        ImageRequest.Builder(context)
            .data(track.coverArtUrl)
            .transformations(IslandBlurredCoverTransformation())
            .crossfade(320)
            .build()
    }

    Box(
        modifier = modifier
            .width(widthDp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        ambientGlow.copy(alpha = 0.85f),
                        Color(0xFF0C1018)
                    ),
                    start = Offset.Zero,
                    end = Offset(400f, 500f)
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
        // Full-bleed heavily blurred album cover art backdrop (matches the 2 reference screenshots)
        AsyncImage(
            model = blurredCoverRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        // Subtle liquid glass tint scrim for crisp white typography contrast
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.Black.copy(alpha = if (isExpanded) 0.28f else 0.20f)
                        )
                    )
                )
        )

        AnimatedContent(
            targetState = isExpanded,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(130)) },
            label = "island_morph"
        ) { expanded ->
            if (!expanded) {
                CollapsedPillContent(
                    coverArtUrl = track.coverArtUrl,
                    activeLyricLine = if (showIslandLyrics) currLine else null,
                    translatedLine = if (showIslandLyrics && translationPref) translatedCurrLine else null,
                    isPlaying = isPlaying
                )
            } else {
                ExpandedIslandGlassCard(
                    title = track.title,
                    artist = track.artist,
                    coverArtUrl = track.coverArtUrl,
                    currLyric = if (showIslandLyrics) currLine else null,
                    translatedLyric = if (showIslandLyrics && translationPref) translatedCurrLine else null,
                    isPlaying = isPlaying,
                    isLiked = isLiked,
                    repeatMode = repeatMode,
                    isLyricsShown = showIslandLyrics,
                    isTranslationOn = translationPref,
                    activePreset = activePreset,
                    progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                    elapsedText = formatMs(positionMs),
                    remainingText = "-" + formatMs((durationMs - positionMs).coerceAtLeast(0L)),
                    onOpenApp = onOpenApp,
                    onSeekFraction = { frac ->
                        interactionTick++
                        controller.seekToPosition((frac * durationMs).toLong().coerceIn(0L, durationMs))
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
                    onToggleLike = {
                        interactionTick++
                        controller.toggleLike()
                    },
                    onCycleLyricsMode = {
                        interactionTick++
                        when {
                            showIslandLyrics && translationPref -> {
                                scope.launch { preferences.setLyricsTranslationEnabled(false) }
                            }
                            showIslandLyrics && !translationPref -> {
                                showIslandLyrics = false
                            }
                            else -> {
                                showIslandLyrics = true
                                scope.launch { preferences.setLyricsTranslationEnabled(true) }
                            }
                        }
                    },
                    onCycleVoiceChanger = {
                        interactionTick++
                        val idx = ISLAND_VOICE_PRESETS.indexOf(activePreset)
                        val nextPreset = ISLAND_VOICE_PRESETS[(idx + 1) % ISLAND_VOICE_PRESETS.size]
                        controller.applySoundPreset(nextPreset, toggleOffIfSame = false)
                        Toast.makeText(context, "Voice Effect: ${nextPreset.label}", Toast.LENGTH_SHORT).show()
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
    translatedLine: String?,
    isPlaying: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        AsyncImage(
            model = coverArtUrl,
            contentDescription = "Cover Art",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1D2028))
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!activeLyricLine.isNullOrBlank()) {
                AnimatedContent(
                    targetState = activeLyricLine to translatedLine,
                    transitionSpec = {
                        (slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(260))) togetherWith
                            (slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { -it / 2 } + fadeOut(tween(160)))
                    },
                    label = "pill_smooth_lyric"
                ) { (line, trans) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = line,
                            color = PureWhite,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        if (!trans.isNullOrBlank()) {
                            Text(
                                text = trans,
                                color = PearlWhite.copy(alpha = 0.80f),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        MiniWaveformBars(isPlaying = isPlaying)
    }
}

@Composable
private fun MiniWaveformBars(isPlaying: Boolean) {
    val infinite = rememberInfiniteTransition(label = "island_wave")
    val bar1 by infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(tween(430, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by infinite.animateFloat(
        initialValue = 0.82f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(510, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by infinite.animateFloat(
        initialValue = 0.40f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(370, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "b3"
    )
    val bar4 by infinite.animateFloat(
        initialValue = 0.78f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(tween(480, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "b4"
    )
    val bar5 by infinite.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.68f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "b5"
    )

    val heights = if (isPlaying) {
        listOf(bar1, bar2, bar3, bar4, bar5)
    } else {
        listOf(0.28f, 0.52f, 0.85f, 0.52f, 0.28f)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(20.dp)
    ) {
        heights.forEach { frac ->
            Box(
                modifier = Modifier
                    .width(2.8.dp)
                    .height((19f * frac).dp.coerceAtLeast(4.dp))
                    .clip(CircleShape)
                    .background(if (isPlaying) PureWhite else PureWhite.copy(alpha = 0.72f))
            )
        }
    }
}

@Composable
private fun ExpandedIslandGlassCard(
    title: String,
    artist: String,
    coverArtUrl: String?,
    currLyric: String?,
    translatedLyric: String?,
    isPlaying: Boolean,
    isLiked: Boolean,
    repeatMode: RepeatMode,
    isLyricsShown: Boolean,
    isTranslationOn: Boolean,
    activePreset: SoundPreset,
    progress: Float,
    elapsedText: String,
    remainingText: String,
    onOpenApp: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    onCycleLyricsMode: () -> Unit,
    onCycleVoiceChanger: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        // 1. Top Row (exact match with Screenshot 1): Rounded-square Cover Art + Title/Artist + 5-Bar Waveform
        // Tapping title/artist or waveform collapses the expanded island cleanly.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenApp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = coverArtUrl,
                contentDescription = "Album Cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFF1C1F26))
                    .border(0.75.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(13.dp))
                    .clickable(onClick = onOpenApp)
            )
            Spacer(modifier = Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = PureWhite,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = artist,
                    color = PureWhite.copy(alpha = 0.72f),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            MiniWaveformBars(isPlaying = isPlaying)
        }

        // 2. Smooth Synced Lyrics + Terjemah (animated vertical slide + crossfade on line change)
        AnimatedVisibility(
            visible = isLyricsShown && !currLyric.isNullOrBlank(),
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.22f))
                    .border(0.6.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = (currLyric.orEmpty()) to translatedLyric,
                    transitionSpec = {
                        (slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(280))) togetherWith
                            (slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { -it / 2 } + fadeOut(tween(180)))
                    },
                    label = "expanded_smooth_lyric"
                ) { (activeLine, transLine) ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = activeLine,
                            color = PureWhite,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        if (isTranslationOn && !transLine.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = transLine,
                                color = PearlWhite.copy(alpha = 0.82f),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // 3. Scrubber Row (exact match with Screenshot 1): Elapsed ("1:06") · Pill Progress Bar · Remaining ("-2:09")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = elapsedText,
                color = PureWhite.copy(alpha = 0.78f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(10.dp))
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.28f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(4.5.dp)
                        .clip(CircleShape)
                        .background(PureWhite)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = remainingText,
                color = PureWhite.copy(alpha = 0.78f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // 4. Transport Controls Row: Repeat (circular arrow) · Previous · Play/Pause · Next · Love (Heart)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IslandIconBtn(
                iconRes = if (repeatMode == RepeatMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
                tint = if (repeatMode != RepeatMode.OFF) PureWhite else PureWhite.copy(alpha = 0.62f),
                sizeDp = 19,
                onClick = onToggleRepeat
            )
            IslandIconBtn(
                iconRes = R.drawable.ic_skip_previous,
                tint = PureWhite,
                sizeDp = 26,
                onClick = onPrev
            )
            IslandIconBtn(
                iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                tint = PureWhite,
                sizeDp = 30,
                onClick = onPlayPause
            )
            IslandIconBtn(
                iconRes = R.drawable.ic_skip_next,
                tint = PureWhite,
                sizeDp = 26,
                onClick = onNext
            )
            val heartColor by animateColorAsState(
                targetValue = if (isLiked) Color(0xFFFF5A5F) else PureWhite.copy(alpha = 0.72f),
                animationSpec = tween(200),
                label = "heart_color"
            )
            IslandIconBtn(
                iconRes = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
                tint = heartColor,
                sizeDp = 21,
                onClick = onToggleLike
            )
        }

        // 5. Focused Feature Pills: ONLY Lyrics + Terjemah AND Voice Changer (Slowed / Lo-Fi / Nightcore)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val lyricsLabel = when {
                isLyricsShown && isTranslationOn -> "Lyrics + Terjemah"
                isLyricsShown -> "Lyrics Only"
                else -> "Lyrics Off"
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isLyricsShown) Color.White.copy(alpha = 0.22f)
                        else Color.Black.copy(alpha = 0.22f)
                    )
                    .border(0.7.dp, Color.White.copy(alpha = 0.26f), CircleShape)
                    .clickable(onClick = onCycleLyricsMode),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lyricsLabel,
                    color = PureWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            val isFxActive = activePreset != SoundPreset.NORMAL
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isFxActive) Color.White.copy(alpha = 0.25f)
                        else Color.Black.copy(alpha = 0.22f)
                    )
                    .border(0.7.dp, Color.White.copy(alpha = 0.26f), CircleShape)
                    .clickable(onClick = onCycleVoiceChanger),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isFxActive) "FX: ${activePreset.label}" else "FX: Slowed / Lo-Fi",
                    color = PureWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
            .size(34.dp)
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
