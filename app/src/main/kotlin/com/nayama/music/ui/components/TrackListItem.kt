package com.nayama.music.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.model.Track
import com.nayama.music.ui.theme.ElevatedSurface
import com.nayama.music.ui.theme.Graphite
import com.nayama.music.ui.theme.LightGray
import com.nayama.music.ui.theme.MediumGray
import com.nayama.music.ui.theme.PureBlack
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SurfaceBlack

@Composable
fun TrackListItem(
    track: Track,
    modifier: Modifier = Modifier,
    index: Int? = null,
    isPlaying: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor by animateColorAsState(
        targetValue = if (isPressed) ElevatedSurface else PureBlack,
        animationSpec = tween(80),
        label = "track_bg"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track index or playing indicator
        if (index != null) {
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    MiniWaveVisualizer(
                        isPlaying = true,
                        barCount = 3,
                        maxHeight = 13.dp,
                        color = PureWhite
                    )
                } else {
                    Text(
                        text = "%02d".format(index),
                        color = MediumGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Artwork thumbnail
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            NayamaImage(
                url = track.artworkUrl,
                contentDescription = track.title,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(10.dp),
                fallbackText = track.title
            )
            // Playing indicator overlay
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PureBlack.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    MiniWaveVisualizer(
                        isPlaying = true,
                        barCount = 3,
                        maxHeight = 14.dp,
                        color = PureWhite
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isPlaying) PureWhite else PureWhite,
                fontSize = 14.sp,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} · ${track.album}",
                color = MediumGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x1EFFFFFF))
                .border(0.5.dp, Color(0x28FFFFFF), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = track.source.shortBadge,
                color = LightGray,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = track.formattedDuration,
            color = MediumGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal
        )
    }
}
