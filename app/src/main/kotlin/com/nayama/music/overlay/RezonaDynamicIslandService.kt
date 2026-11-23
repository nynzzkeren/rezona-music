package com.nayama.music.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import com.nayama.music.MainActivity
import com.nayama.music.R
import com.nayama.music.playback.RezonaPlayerController
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SoftSilver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Floating Liquid Glass Dynamic Island overlay anchored around the top camera cutout.
 * Appears smoothly when Rezona is backgrounded while a track is active, and lets the user
 * tap to expand full playback + Like controls or long-press to jump back into Rezona.
 */
class RezonaDynamicIslandService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
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
            y = dpToPx(8)
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@RezonaDynamicIslandService)
            setViewTreeSavedStateRegistryOwner(this@RezonaDynamicIslandService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val appInForeground by appInForegroundFlow.collectAsState()
                val controller = RezonaPlayerController.getExistingInstance()
                val track = controller?.currentTrack?.value
                val shouldShow = !appInForeground && track != null

                LaunchedEffect(shouldShow) {
                    visibility = if (shouldShow) View.VISIBLE else View.GONE
                }

                if (shouldShow && controller != null && track != null) {
                    DynamicIslandCapsule(
                        controller = controller,
                        onOpenApp = {
                            val intent = Intent(this@RezonaDynamicIslandService, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            startActivity(intent)
                        }
                    )
                }
            }
        }

        composeView = view
        runCatching { wm.addView(view, params) }.onFailure { stopSelf() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        composeView?.let { v ->
            runCatching { windowManager?.removeView(v) }
        }
        composeView = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    companion object {
        private val appInForegroundFlow = MutableStateFlow(true)

        fun setAppInForeground(inForeground: Boolean) {
            appInForegroundFlow.value = inForeground
        }

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, RezonaDynamicIslandService::class.java)
            runCatching { context.startService(intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RezonaDynamicIslandService::class.java)
            runCatching { context.stopService(intent) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DynamicIslandCapsule(
    controller: RezonaPlayerController,
    onOpenApp: () -> Unit
) {
    val track = controller.currentTrack.value ?: return
    val isPlaying = controller.isPlaying.value
    val isLiked = controller.isLiked.value
    val positionMs = controller.currentPositionMs.longValue
    val durationMs = controller.durationMs.longValue.coerceAtLeast(1L)
    val lyrics = controller.currentLyrics.value
    val activeLyricLine = remember(lyrics, positionMs) {
        if (lyrics.isEmpty()) null
        else lyrics.lastOrNull { it.timeMs <= positionMs + 150L }?.text?.takeIf { it.isNotBlank() }
    }

    var isExpanded by remember { mutableStateOf(false) }

    // Auto-collapse back to pill 10 seconds after expanding without interaction
    LaunchedEffect(isExpanded, track.id, isPlaying) {
        if (isExpanded) {
            delay(10000L)
            isExpanded = false
        }
    }

    val collapsedWidth = if (isPlaying && !activeLyricLine.isNullOrBlank()) 236.dp else 172.dp
    val widthDp by animateDpAsState(
        targetValue = if (isExpanded) 340.dp else collapsedWidth,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 360f),
        label = "island_width"
    )
    val cornerDp by animateDpAsState(
        targetValue = if (isExpanded) 28.dp else 22.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "island_corner"
    )

    val shape = RoundedCornerShape(cornerDp)
    val borderColor by animateColorAsState(
        targetValue = if (isPlaying) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.14f),
        animationSpec = tween(300),
        label = "island_border"
    )

    Box(
        modifier = Modifier
            .width(widthDp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xF00F1218),
                        Color(0xEB07080C)
                    )
                )
            )
            .border(0.9.dp, borderColor, shape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { isExpanded = !isExpanded },
                onLongClick = onOpenApp
            )
    ) {
        AnimatedContent(
            targetState = isExpanded,
            transitionSpec = {
                fadeIn(tween(180)) togetherWith fadeOut(tween(140))
            },
            label = "island_content"
        ) { expanded ->
            if (!expanded) {
                CollapsedIslandRow(
                    coverArtUrl = track.coverArtUrl,
                    activeLyricLine = if (isPlaying) activeLyricLine else null,
                    isPlaying = isPlaying
                )
            } else {
                ExpandedIslandCard(
                    title = track.title,
                    artist = track.artist,
                    coverArtUrl = track.coverArtUrl,
                    activeLyricLine = activeLyricLine,
                    isPlaying = isPlaying,
                    isLiked = isLiked,
                    progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                    elapsedText = formatMs(positionMs),
                    totalText = formatMs(durationMs),
                    onOpenApp = onOpenApp,
                    onCollapse = { isExpanded = false },
                    onToggleLike = { controller.toggleLike() },
                    onPrev = { controller.skipPrevious() },
                    onPlayPause = { controller.togglePlayPause() },
                    onNext = { controller.skipNext() }
                )
            }
        }
    }
}

@Composable
private fun CollapsedIslandRow(
    coverArtUrl: String?,
    activeLyricLine: String?,
    isPlaying: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 8.dp),
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
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Center cutout clearance + subtle Rezona waveform on the right
        MiniWaveformBars(isPlaying = isPlaying)
    }
}

@Composable
private fun MiniWaveformBars(isPlaying: Boolean) {
    val infinite = rememberInfiniteTransition(label = "island_wave")
    val bar1 by infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by infinite.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(510, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(370, easing = FastOutSlowInEasing), RepeatMode.Reverse),
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
private fun ExpandedIslandCard(
    title: String,
    artist: String,
    coverArtUrl: String?,
    activeLyricLine: String?,
    isPlaying: Boolean,
    isLiked: Boolean,
    progress: Float,
    elapsedText: String,
    totalText: String,
    onOpenApp: () -> Unit,
    onCollapse: () -> Unit,
    onToggleLike: () -> Unit,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = coverArtUrl,
                contentDescription = "Cover Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1D2028))
                    .clickable(onClick = onOpenApp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenApp)
            ) {
                Text(
                    text = title,
                    color = PureWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    color = SoftSilver,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleLike),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                    ),
                    contentDescription = "Like",
                    tint = if (isLiked) Color(0xFFFF5A6E) else SoftSilver,
                    modifier = Modifier.size(18.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCollapse),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_expand_more),
                    contentDescription = "Collapse",
                    tint = SoftSilver,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Live synced lyric line inside Dynamic Island
        if (!activeLyricLine.isNullOrBlank()) {
            AnimatedContent(
                targetState = activeLyricLine,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                label = "island_lyric"
            ) { lyric ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(0.6.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = lyric,
                        color = PureWhite.copy(alpha = 0.94f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Progress bar + timestamps
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(3.5.dp)
                        .clip(CircleShape)
                        .background(PureWhite)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(elapsedText, color = SoftSilver, fontSize = 10.sp)
                Text(totalText, color = SoftSilver, fontSize = 10.sp)
            }
        }

        // Transport controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPrev),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_skip_previous),
                    contentDescription = "Previous",
                    tint = PearlWhite,
                    modifier = Modifier.size(22.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                    ),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = PureWhite,
                    modifier = Modifier.size(22.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onNext),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_skip_next),
                    contentDescription = "Next",
                    tint = PearlWhite,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%d:%02d".format(mins, secs)
}
