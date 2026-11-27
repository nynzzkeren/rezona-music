package com.nayama.music.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nayama.music.R
import com.nayama.music.playback.AudioOutputDetector
import com.nayama.music.playback.RezonaPlayerController
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.rememberAnimatedArtworkPalette
import kotlin.math.roundToInt

/**
 * Floating iOS Control Center Side-Edge Music Handle (`♫`) + Pull-Out Background Music Card.
 * - Closed state: A sleek frosted glass tab on the right edge of the screen with a music note icon
 *   that can be tapped, swiped left to open, or dragged vertically along the screen edge.
 * - Open state: Exact 1:1 recreation of the iOS Control Center Music Card (large rounded square cover art,
 *   title/artist + mini waveform, capsule progress bar with timestamps below, rewind/play/fast-forward,
 *   capsule volume slider, centered AirPlay pill, and the 3-icon side rail).
 */
@Composable
fun RezonaIosSideEdgeHandle(
    onOpenCard: () -> Unit,
    modifier: Modifier = Modifier
) {
    var verticalDragOffsetPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .offset { IntOffset(0, verticalDragOffsetPx.roundToInt()) }
            .width(34.dp)
            .height(66.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xD83A3F4A),
                        Color(0xE622262E)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.42f),
                        Color.White.copy(alpha = 0.14f)
                    )
                ),
                shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
            )
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    if (dragAmount.x < -18f) {
                        onOpenCard()
                    } else {
                        verticalDragOffsetPx = (verticalDragOffsetPx + dragAmount.y).coerceIn(-420f, 420f)
                    }
                }
            }
            .clickable(onClick = onOpenCard),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(22.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.42f))
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_music_note),
                contentDescription = "Open iOS Music Player",
                tint = PureWhite,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
fun RezonaIosMusicCardOverlay(
    controller: RezonaPlayerController,
    isOpen: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val track = controller.currentTrack.value ?: return
    val isPlaying = controller.isPlaying.value
    val isLiked = controller.isLiked.value
    val positionMs = controller.currentPositionMs.longValue
    val durationMs = controller.durationMs.longValue.coerceAtLeast(1L)
    val volumeFraction = controller.volume.floatValue
    val progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    val ambientColor = rememberAnimatedArtworkPalette(
        context = context,
        artworkUrl = track.coverArtUrl,
        fallbackKey = track.id
    ).primaryAmbient

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(200)) + slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 340f)
        ),
        exit = fadeOut(tween(160)) + slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(180, easing = FastOutSlowInEasing)
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC07090E))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        if (dragAmount > 28f) onClose()
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                // Main iOS Frosted Control Center Music Card (with rich cover-tinted glass & double rim)
                val cardShape = RoundedCornerShape(42.dp)
                Box(
                    modifier = Modifier
                        .width(306.dp)
                        .shadow(32.dp, cardShape)
                        .clip(cardShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xF0565C68),
                                    Color(0xF2383D46),
                                    Color(0xF522262E)
                                )
                            )
                        )
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ambientColor.copy(alpha = 0.42f),
                                    ambientColor.copy(alpha = 0.16f),
                                    Color.Transparent
                                ),
                                radius = 560f
                            )
                        )
                        .border(
                            width = 1.2.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.48f),
                                    Color.White.copy(alpha = 0.14f)
                                )
                            ),
                            shape = cardShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Consume taps inside card so tapping card doesn't dismiss
                        )
                        .padding(horizontal = 22.dp, vertical = 22.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. Large Square Rounded Album Cover Art
                        AsyncImage(
                            model = track.coverArtUrl,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .shadow(14.dp, RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF1F2229))
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // 2. Title & Artist + Small 5-Bar Waveform
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = PureWhite,
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = track.artist,
                                    color = Color.White.copy(alpha = 0.58f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            IosMiniWaveform(isPlaying = isPlaying)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 3. Thick Capsule Progress Bar + Timestamps Underneath ("0.06" ... "-5.20")
                        IosCapsuleSliderBar(
                            fraction = progress,
                            onFractionChange = { frac ->
                                controller.seekToPosition((frac * durationMs).toLong().coerceIn(0L, durationMs))
                            }
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatIosTime(positionMs),
                                color = Color.White.copy(alpha = 0.52f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "-" + formatIosTime((durationMs - positionMs).coerceAtLeast(0L)),
                                color = Color.White.copy(alpha = 0.52f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // 4. iOS Transport Controls Row (Previous · Play/Pause · Next)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable { controller.skipPrevious() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_skip_previous),
                                    contentDescription = "Previous",
                                    tint = Color.White.copy(alpha = 0.72f),
                                    modifier = Modifier.size(34.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .clickable { controller.togglePlayPause() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                                    ),
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = PureWhite,
                                    modifier = Modifier.size(42.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable { controller.skipNext() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_skip_next),
                                    contentDescription = "Next",
                                    tint = PureWhite,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 5. Volume Bar Row (Low Speaker · Thick Capsule Slider · High Speaker)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_volume_low),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.54f),
                                modifier = Modifier.size(16.dp)
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                IosCapsuleSliderBar(
                                    fraction = volumeFraction,
                                    onFractionChange = { controller.setVolume(it) }
                                )
                            }
                            Icon(
                                painter = painterResource(id = R.drawable.ic_volume_high),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.54f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 6. Centered Bottom AirPlay Pill
                        Box(
                            modifier = Modifier
                                .height(30.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f))
                                .clickable {
                                    AudioOutputDetector.openSystemAudioOutputPanel(context)
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_radio),
                                    contentDescription = "AirPlay",
                                    tint = PureWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "AirPlay",
                                    color = PureWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Right-side iOS Control Center Vertical 3-Icon Strip (Heart · Music Note · Connectivity)
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                        ),
                        contentDescription = "Like",
                        tint = if (isLiked) Color(0xFFFF5A6E) else Color.White.copy(alpha = 0.42f),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { controller.toggleLike() }
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.ic_music_note),
                        contentDescription = "Music",
                        tint = PureWhite,
                        modifier = Modifier.size(22.dp)
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.ic_radio),
                        contentDescription = "Audio Output",
                        tint = Color.White.copy(alpha = 0.42f),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { AudioOutputDetector.openSystemAudioOutputPanel(context) }
                    )
                }
            }
        }
    }
}

@Composable
private fun IosCapsuleSliderBar(
    fraction: Float,
    onFractionChange: (Float) -> Unit
) {
    val clamped = fraction.coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                    onFractionChange(frac)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val frac = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                    onFractionChange(frac)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .height(7.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.85f))
        )
    }
}

@Composable
private fun IosMiniWaveform(isPlaying: Boolean) {
    val infinite = rememberInfiniteTransition(label = "ios_wave")
    val b1 by infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "ib1"
    )
    val b2 by infinite.animateFloat(
        initialValue = 0.80f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "ib2"
    )
    val b3 by infinite.animateFloat(
        initialValue = 0.40f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(360, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "ib3"
    )
    val b4 by infinite.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(tween(460, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "ib4"
    )
    val b5 by infinite.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(tween(430, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "ib5"
    )

    val bars = if (isPlaying) listOf(b1, b2, b3, b4, b5) else listOf(0.25f, 0.48f, 0.82f, 0.48f, 0.25f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(14.dp)
    ) {
        bars.forEach { frac ->
            Box(
                modifier = Modifier
                    .width(2.2.dp)
                    .height((13f * frac).dp.coerceAtLeast(3.dp))
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.82f))
            )
        }
    }
}

private fun formatIosTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%d.%02d".format(mins, secs)
}
