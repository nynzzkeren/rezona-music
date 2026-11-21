package com.nayama.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.playback.PlaybackState
import com.nayama.music.ui.theme.CoralAccent
import com.nayama.music.ui.theme.DeepCharcoal
import com.nayama.music.ui.theme.GlassBorder
import com.nayama.music.ui.theme.GraphiteSurface
import com.nayama.music.ui.theme.ObsidianBlack
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.rememberAnimatedArtworkPalette

/**
 * Floating Liquid Glass Mini Player (Matching Image 1 Reference).
 * Frosted dark glass container, squircle album cover, track metadata,
 * heart button, circular play/pause button, and skip button.
 */
@Composable
fun MiniPlayer(
    playbackState: PlaybackState,
    modifier: Modifier = Modifier,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onClick: () -> Unit
) {
    val currentTrack = playbackState.currentTrack ?: return
    val context = LocalContext.current
    val palette = rememberAnimatedArtworkPalette(context, currentTrack.artworkUrl)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(120),
        label = "mini_player_scale"
    )

    var isFavorite by remember(currentTrack.id) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Color(0x99000000),
                    spotColor = palette.primaryAmbient.copy(alpha = 0.65f)
                )
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            palette.primaryAmbient.copy(alpha = 0.85f),
                            Color(0xDD141822),
                            Color(0xF50C0E13)
                        )
                    )
                )
                .border(
                    width = 1.25.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0x80FFFFFF),
                            Color(0x28FFFFFF),
                            Color(0x0CFFFFFF)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onClick() }
        ) {
            // Hairline progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color(0x22FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(playbackState.progressFraction.coerceIn(0f, 1f))
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(CoralAccent)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Squircle Track Artwork
                NayamaImage(
                    url = currentTrack.artworkUrl,
                    contentDescription = currentTrack.title,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(0.5.dp, GlassBorder, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    fallbackText = currentTrack.title
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Track title & artist
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack.title,
                        color = PearlWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = currentTrack.artist,
                        color = SmokySilver,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Controls matching Image 1: Heart, Play/Pause Circle, Skip Next
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Heart Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Sukai"
                            ) { isFavorite = !isFavorite },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                            ),
                            contentDescription = "Favorit",
                            tint = if (isFavorite) CoralAccent else SoftSilver,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Circular White Play/Pause Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PearlWhite)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = if (playbackState.isPlaying) "Jeda" else "Putar"
                            ) { onPlayPauseClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (playbackState.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = ObsidianBlack,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                painter = painterResource(
                                    id = if (playbackState.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                                ),
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = ObsidianBlack,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Skip Next Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Lagu selanjutnya"
                            ) { onSkipNextClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_skip_next),
                            contentDescription = "Skip Next",
                            tint = SoftSilver,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
