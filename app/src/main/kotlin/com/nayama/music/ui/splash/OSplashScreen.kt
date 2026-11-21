package com.nayama.music.ui.splash

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nayama.music.R
import com.nayama.music.ui.theme.PureBlack
import com.nayama.music.ui.theme.SmokySilver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Rezona Video Splash Screen
 * Plays the official video entrance animation in full portrait immersion.
 * Completely muted (audio track stripped from container and player volume locked to 0f).
 * Supports tap to skip and auto-transitions on playback completion.
 */
@OptIn(UnstableApi::class)
@Composable
fun OSplashScreen(
    onSplashFinished: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val exitAlpha = remember { Animatable(1f) }
    var isFinishing by remember { mutableStateOf(false) }

    fun finishSplash() {
        if (!isFinishing) {
            isFinishing = true
            coroutineScope.launch {
                exitAlpha.animateTo(0f, tween(350, easing = FastOutSlowInEasing))
                onSplashFinished()
            }
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            // Guarantee complete silence
            volume = 0f
            trackSelectionParameters = trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()

            val rawUri = Uri.parse("android.resource://${context.packageName}/${R.raw.splash_video}")
            setMediaItem(MediaItem.fromUri(rawUri))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        finishSplash()
                    }
                }
            })
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            try {
                exoPlayer.stop()
                exoPlayer.release()
            } catch (_: Throwable) {}
        }
    }

    // Safety fallback: if video stalls or takes longer than 5.5s, proceed automatically
    LaunchedEffect(Unit) {
        delay(5500)
        finishSplash()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(exitAlpha.value)
            .background(PureBlack)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                finishSplash()
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    player = exoPlayer
                }
            }
        )

        // Subtle Glass Skip Pill (Top End)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x33000000))
                .border(
                    width = 1.dp,
                    color = Color(0x25FFFFFF),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable { finishSplash() }
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Skip",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = SmokySilver
            )
        }
    }
}
