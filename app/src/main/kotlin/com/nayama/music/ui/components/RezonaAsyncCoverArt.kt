package com.nayama.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.nayama.music.ui.util.MockCoverArt

private val YTIMG_THUMB_REGEX = Regex("https?://i\\.ytimg\\.com/vi/([^/]+)/(?:default|mqdefault|hqdefault|sddefault|maxresdefault)\\.jpg.*")
private val GSTATIC_WH_REGEX = Regex("=w\\d+-h\\d+[^&#?]*")
private val GSTATIC_S_REGEX = Regex("=s\\d+[^&#?]*")

/**
 * Upgrades low-res YouTube Music / Googleusercontent thumbnails (120px / 480x360)
 * to crisp 1080p / 720p HD artwork with automatic fallback support in RezonaAsyncCoverArt.
 */
fun upgradeCoverArtCandidates(rawUrl: String?): List<String> {
    val clean = rawUrl?.trim().orEmpty()
    if (clean.isBlank()) return emptyList()

    if (clean.contains("googleusercontent.com") || clean.contains("ggpht.com")) {
        val upgraded = when {
            GSTATIC_WH_REGEX.containsMatchIn(clean) -> clean.replace(GSTATIC_WH_REGEX, "=w1080-h1080-l90-rj")
            GSTATIC_S_REGEX.containsMatchIn(clean) -> clean.replace(GSTATIC_S_REGEX, "=s1080-c-k-c0x00ffffff-no-rj")
            else -> clean
        }
        return listOf(upgraded, clean).distinct()
    }

    val ytMatch = YTIMG_THUMB_REGEX.matchEntire(clean)
    if (ytMatch != null) {
        val vid = ytMatch.groupValues[1]
        return listOf(
            "https://i.ytimg.com/vi/$vid/maxresdefault.jpg",
            "https://i.ytimg.com/vi/$vid/sddefault.jpg",
            "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
        ).distinct()
    }

    return listOf(clean)
}

/**
 * Neutral monochrome shimmer skeleton box for loading states.
 */
@Composable
fun RezonaShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    val transition = rememberInfiniteTransition(label = "rezona_shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = AnimRepeatMode.Restart
        ),
        label = "shimmer_x"
    )

    val shimmerColors = listOf(
        Color(0x0F20242E),
        Color(0x28FFFFFF),
        Color(0x0F20242E)
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim, translateAnim * 0.5f),
        end = Offset(translateAnim + 300f, (translateAnim + 300f) * 0.5f)
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0x181C222E))
            .background(brush)
    )
}

/**
 * Universal artwork loader for Rezona:
 * - Automatically upgrades low-res URLs to HD (maxresdefault / w1080) with graceful fallback chain
 * - Uses Size.ORIGINAL + FilterQuality.High for crisp non-blurry fullscreen covers
 * - Crossfades smoothly and falls back cleanly on network error
 */
@Composable
fun RezonaAsyncCoverArt(
    coverArtUrl: String?,
    fallbackIndex: Int = 0,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    val context = LocalContext.current
    val candidates = remember(coverArtUrl) { upgradeCoverArtCandidates(coverArtUrl) }
    var candidateIdx by remember(coverArtUrl) { mutableIntStateOf(0) }
    val activeUrl = candidates.getOrNull(candidateIdx)

    if (!activeUrl.isNullOrBlank()) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(activeUrl)
                .size(Size.ORIGINAL)
                .crossfade(300)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            modifier = modifier.clip(shape),
            loading = {
                RezonaShimmerBox(modifier = Modifier.fillMaxSize(), shape = shape)
            },
            error = {
                if (candidateIdx + 1 < candidates.size) {
                    candidateIdx++
                    RezonaShimmerBox(modifier = Modifier.fillMaxSize(), shape = shape)
                } else {
                    MockCoverArt(index = fallbackIndex, modifier = Modifier.fillMaxSize())
                }
            }
        )
    } else {
        Box(modifier = modifier.clip(shape)) {
            MockCoverArt(index = fallbackIndex, modifier = Modifier.fillMaxSize())
        }
    }
}
