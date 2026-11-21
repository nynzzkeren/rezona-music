package com.nayama.music.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
 * Rezona Floating Liquid Glass Mini Player (Stage A Reference Morph):
 * - When expanded (!isNavFolded): sits cleanly above the 4-tab dock (`bottom = 84.dp`, `start = 16.dp`, `end = 16.dp`)
 * - When folded on scroll down (`isNavFolded`): glides down onto the same bottom row next to the
 *   collapsed round tab button (`bottom = 12.dp`, `start = 84.dp`, `end = 16.dp`) with spring motion!
 * - Horizontal drag skips next/previous track; tap expands to Now Playing.
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
            shape = RoundedCornerShape(30.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Squircle Cover Art
                    Box(
                        modifier = Modifier
                            .size(if (isNavFolded) 40.dp else 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(width = 0.75.dp, brush = SpecularCardBorder, shape = RoundedCornerShape(10.dp))
                    ) {
                        RezonaAsyncCoverArt(
                            coverArtUrl = track.coverArtUrl,
                            fallbackIndex = track.index,
                            contentDescription = track.title,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Title & Artist
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = Typography.titleMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                            color = PearlWhite,
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

                    // Action Controls (Heart + White Play/Pause Circle + Skip Next, >= 48dp accessible wrappers)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isNavFolded) 40.dp else 44.dp)
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
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Bright PureWhite Play/Pause Circle matching library.jpg & home_1.jpg
                        Box(
                            modifier = Modifier
                                .size(if (isNavFolded) 42.dp else 46.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, color = PureWhite),
                                    onClick = { controller.togglePlayPause() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(PureWhite),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color(0xFF08090B),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(if (isNavFolded) 40.dp else 44.dp)
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
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Bottom Hairline Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color(0x1EFFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progressFraction)
                            .height(2.dp)
                            .background(PureWhite)
                    )
                }
            }
        }
    }
}
