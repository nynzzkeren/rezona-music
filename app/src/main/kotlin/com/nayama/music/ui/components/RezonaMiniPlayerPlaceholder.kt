package com.nayama.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nayama.music.R
import com.nayama.music.model.RezonaMockRepository
import com.nayama.music.ui.theme.ObsidianBlack
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.Typography
import com.nayama.music.ui.util.MockCoverArt
import kotlin.math.roundToInt

/**
 * Rezona Floating Mini Player
 * Features:
 * - Liquid glass floating capsule with specular borders
 * - Stacks directly above RezonaBottomBar
 * - Moves downward smoothly with spring physics when dock auto-folds
 * - Hairline progress bar along the bottom edge
 * - Updates cover, title, and artist from active track index
 */
@Composable
fun RezonaMiniPlayerPlaceholder(
    isNavFolded: Boolean,
    currentTrackIndex: Int = 0,
    onExpandPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = remember(currentTrackIndex) { RezonaMockRepository.getTrack(currentTrackIndex) }
    var isPlaying by remember { mutableStateOf(true) }
    var isLiked by remember { mutableStateOf(false) }

    // When dock folds (moves down 92dp), mini player drops down by 64dp to hug the bottom
    val bottomOffsetAnim by animateFloatAsState(
        targetValue = if (isNavFolded) 16f else 82f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
        label = "mini_player_fold_anim"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .offset { IntOffset(0, -bottomOffsetAnim.dp.roundToPx()) }
    ) {
        RezonaFloatingDock(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = PureWhite),
                    onClick = onExpandPlayer
                ),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Track Thumbnail with Subtle Ambient Glow
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                    ) {
                        MockCoverArt(index = currentTrackIndex, modifier = Modifier.fillMaxSize())
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title & Artist in Pearl White / Smoky Silver
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = Typography.titleMedium,
                            color = PearlWhite,
                            maxLines = 1
                        )
                        Text(
                            text = track.artist,
                            style = Typography.bodySmall,
                            color = SmokySilver,
                            maxLines = 1
                        )
                    }

                    // Interactive Controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Heart Like Toggle
                        Icon(
                            painter = painterResource(
                                id = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                            ),
                            contentDescription = "Like",
                            tint = if (isLiked) PureWhite else SoftSilver,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = false, color = PureWhite),
                                    onClick = { isLiked = !isLiked }
                                )
                        )

                        // Circular Play / Pause Button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(PureWhite)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, color = ObsidianBlack),
                                    onClick = { isPlaying = !isPlaying }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                                ),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = ObsidianBlack,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Skip Next
                        Icon(
                            painter = painterResource(id = R.drawable.ic_skip_next),
                            contentDescription = "Next",
                            tint = SoftSilver,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = false, color = PureWhite),
                                    onClick = {}
                                )
                        )
                    }
                }

                // Hairline Progress Bar along the bottom rim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0x20FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.38f)
                            .height(2.dp)
                            .background(PureWhite)
                    )
                }
            }
        }
    }
}
