package com.nayama.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.model.Album
import com.nayama.music.ui.theme.DarkCharcoal
import com.nayama.music.ui.theme.ElevatedSurface
import com.nayama.music.ui.theme.Graphite
import com.nayama.music.ui.theme.MediumGray
import com.nayama.music.ui.theme.PureWhite

@Composable
fun AlbumCard(
    album: Album,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "album_card_scale"
    )

    Column(
        modifier = modifier
            .width(145.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        // Album artwork container
        if (!album.artworkUrl.isNullOrBlank()) {
            NayamaImage(
                url = album.artworkUrl,
                contentDescription = album.title,
                modifier = Modifier
                    .size(145.dp)
                    .clip(RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                fallbackText = album.title
            )
        } else {
            Box(
                modifier = Modifier
                    .size(145.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ElevatedSurface)
                    .border(0.5.dp, Graphite, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column {
                    Text(
                        text = album.releaseYear,
                        color = MediumGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = album.title.take(2).uppercase(),
                        color = PureWhite.copy(alpha = 0.9f),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.title,
            color = PureWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "${album.artist} · ${album.releaseYear}",
            color = MediumGray,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
