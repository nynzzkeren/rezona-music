package com.nayama.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.playback.RezonaPlayerController
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.SpecularCardBorder
import com.nayama.music.ui.theme.Typography
import kotlin.math.roundToInt

/**
 * Rezona Original Spinning-Vinyl Studio Mini Player:
 * - Top glowing specular progress beam
 * - Spinning circular vinyl-record cover art with center spindle ring
 * - Track Title & Artist + Coral-Red Heart + Frosted Capsule Play/Pause Pill + Skip Next
 */
@Composable
fun RezonaMiniPlayer(
    controller: RezonaPlayerController,
    isNavFolded: Boolean,
    onExpandPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = controller.currentTrack.value ?: return
    val isPlaying = controller.isPlaying.value
    val isLiked = controller.isLiked.value
    val currentPosition = controller.currentPositionMs.longValue
    val duration = controller.durationMs.longValue
    val progressFraction = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    val spinTransition = rememberInfiniteTransition(label = "mini_vinyl_spin")
    val spinDeg by spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "mini_deg"
    )

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
        label = "mini_player_swipe"
    )

    val bottomPaddingAnim by animateDpAsState(
        targetValue = if (isNavFolded) 12.dp else 84.dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 320f),
        label = "mini_player_bottom"
    )
    val startPaddingAnim by animateDpAsState(
        targetValue = if (isNavFolded) 84.dp else 16.dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 320f),
        label = "mini_player_start"
    )
    val barHeightAnim by animateDpAsState(
        targetValue = if (isNavFolded) 60.dp else 64.dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 320f),
        label = "mini_player_height"
    )

    val draggableState = rememberDraggableState { delta ->
        dragOffsetX += delta
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startPaddingAnim, end = 16.dp, bottom = bottomPaddingAnim)
            .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    if (dragOffsetX > 130f) {
                        controller.skipPrevious()
                    } else if (dragOffsetX < -130f) {
                        controller.skipNext()
                    }
                    dragOffsetX = 0f
                }
            )
    ) {
        RezonaFloatingDock(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeightAnim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = PureWhite),
                    onClick = onExpandPlayer
                ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Specular Progress Beam
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color(0x1FFFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progressFraction)
                            .height(2.dp)
                            .background(PureWhite)
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Spinning Circular Vinyl Cover Disc with center spindle ring
                    val discSize = if (isNavFolded) 40.dp else 44.dp
                    Box(
                        modifier = Modifier
                            .size(discSize)
                            .graphicsLayer {
                                rotationZ = if (isPlaying) spinDeg else 0f
                            }
                            .clip(CircleShape)
                            .border(width = 1.dp, brush = SpecularCardBorder, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        RezonaAsyncCoverArt(
                            coverArtUrl = track.coverArtUrl,
                            fallbackIndex = track.index,
                            contentDescription = track.title,
                            shape = CircleShape,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(Color(0xD907090D))
                                .border(0.8.dp, PureWhite.copy(alpha = 0.65f), CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(11.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = Typography.titleMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
                            color = PureWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = Typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = SmokySilver,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isNavFolded) 38.dp else 42.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, color = Color(0xFFFF5A6E)),
                                    onClick = { controller.toggleLike() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline),
                                contentDescription = if (isLiked) "Unlike" else "Like",
                                tint = if (isLiked) Color(0xFFFF5A6E) else SoftSilver,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Signature Frosted Capsule Play/Pause Pill
                        Box(
                            modifier = Modifier
                                .width(if (isNavFolded) 44.dp else 48.dp)
                                .height(32.dp)
                                .clip(CircleShape)
                                .background(Color(0x28FFFFFF))
                                .border(0.8.dp, Color(0x4DFFFFFF), CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, color = PureWhite),
                                    onClick = { controller.togglePlayPause() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = PureWhite,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(if (isNavFolded) 38.dp else 42.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, color = PureWhite),
                                    onClick = { controller.skipNext() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_skip_next),
                                contentDescription = "Next Track",
                                tint = PearlWhite,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
