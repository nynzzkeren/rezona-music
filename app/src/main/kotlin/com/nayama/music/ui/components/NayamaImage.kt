package com.nayama.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.nayama.music.ui.theme.DarkCharcoal
import com.nayama.music.ui.theme.Graphite
import com.nayama.music.ui.theme.MediumGray
import com.nayama.music.ui.theme.PureBlack

@Composable
fun NayamaImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    contentScale: ContentScale = ContentScale.Crop,
    fallbackText: String = "N"
) {
    val context = LocalContext.current

    if (url.isNullOrBlank()) {
        DefaultPlaceholder(
            modifier = modifier,
            shape = shape,
            fallbackText = fallbackText
        )
    } else {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(200)
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .build(),
            contentDescription = contentDescription,
            modifier = modifier
                .clip(shape)
                .border(1.dp, Graphite, shape),
            contentScale = contentScale,
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkCharcoal),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MediumGray,
                        strokeWidth = 1.5.dp
                    )
                }
            },
            error = {
                DefaultPlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    shape = shape,
                    fallbackText = fallbackText
                )
            }
        )
    }
}

@Composable
private fun DefaultPlaceholder(
    modifier: Modifier,
    shape: Shape,
    fallbackText: String
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(DarkCharcoal)
            .border(1.dp, Graphite, shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = fallbackText.take(1).uppercase(),
            color = MediumGray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

