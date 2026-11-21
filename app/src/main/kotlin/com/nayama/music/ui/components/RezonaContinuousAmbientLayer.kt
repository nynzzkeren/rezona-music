package com.nayama.music.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import com.nayama.music.model.RezonaTrack
import com.nayama.music.ui.util.MOCK_PALETTES
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * Provides the active track's ambient cover color to all glass surfaces
 * so baked glass tints carry the cover color family (rezona_player_v3.md Part 1).
 */
val LocalAmbientAccentColor = compositionLocalOf { Color(0xFF282F3A) }
val LocalAmbientBaseColor = compositionLocalOf { Color(0xFF1A1E24) }
val LocalTintedGlassColor = compositionLocalOf { Color(0x6B2E3540) }

/**
 * Fallback deterministic ambient colors when cover artwork has not loaded yet.
 */
fun deriveAmbientColors(track: RezonaTrack?): Triple<Color, Color, Color> {
    if (track == null) {
        return Triple(
            Color(0xFF1D2430),
            Color(0xFF161C26),
            Color(0xFF12161E)
        )
    }

    val hash = (track.title.hashCode() xor (track.artist.hashCode() * 31)).absoluteValue
    val palette = MOCK_PALETTES[hash % MOCK_PALETTES.size]

    return Triple(
        palette.ambient,
        palette.primary,
        palette.secondary
    )
}

/**
 * Precomputed normalized noise coordinates (0..1) for zero-allocation anti-banding dither.
 */
private val NOISE_POINTS_LIGHT: FloatArray by lazy {
    val rng = Random(0x52455A4F)
    FloatArray(420 * 2) { rng.nextFloat() }
}

private val NOISE_POINTS_DARK: FloatArray by lazy {
    val rng = Random(0x4E415941)
    FloatArray(420 * 2) { rng.nextFloat() }
}

/**
 * Continuous Full-Screen Ambient Layer (Part 1 Cover-to-Background Blend):
 * - Drawn edge-to-edge behind all screens & Now Playing (no clipped boxes, no purple/black blocks)
 * - Uses cover-derived `ambientBase` as the continuous foundation color so the cover fade dissolves
 *   into the exact same color family, enhanced with soft cover-hue radial glows & subtle dither noise.
 * - Crossfades smoothly over 700ms between tracks.
 */
@Composable
fun RezonaContinuousAmbientLayer(
    primaryColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color = Color(0xFF151920),
    ambientBase: Color = primaryColor,
    vividMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val boost = if (vividMode) 1.18f else 0.96f

    val animBase by animateColorAsState(
        targetValue = ambientBase,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "ambient_bg_base"
    )
    val animPrimary by animateColorAsState(
        targetValue = primaryColor,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "ambient_bg_primary"
    )
    val animSecondary by animateColorAsState(
        targetValue = secondaryColor,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "ambient_bg_secondary"
    )
    val animTertiary by animateColorAsState(
        targetValue = tertiaryColor,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "ambient_bg_tertiary"
    )

    val lightDitherOffsets = remember { ArrayList<Offset>(420) }
    val darkDitherOffsets = remember { ArrayList<Offset>(420) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer()
    ) {
        val w = size.width
        val h = size.height
        val maxDim = maxOf(w, h)

        // 1. Solid continuous cover-matched ambientBase foundation (NO forced black or purple!)
        drawRect(color = animBase, size = size)

        // 2. Soft upper radial glow from cover dominant swatch
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    animPrimary.copy(alpha = (0.36f * boost).coerceAtMost(0.54f)),
                    animPrimary.copy(alpha = (0.16f * boost).coerceAtMost(0.28f)),
                    Color.Transparent
                ),
                center = Offset(w * 0.30f, h * 0.28f),
                radius = maxDim * 0.85f
            ),
            size = size
        )

        // 3. Soft lower-right overlapping radial glow from cover secondary swatch
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    animSecondary.copy(alpha = (0.32f * boost).coerceAtMost(0.48f)),
                    animSecondary.copy(alpha = (0.14f * boost).coerceAtMost(0.24f)),
                    Color.Transparent
                ),
                center = Offset(w * 0.78f, h * 0.78f),
                radius = maxDim * 0.90f
            ),
            size = size
        )

        // 4. Subtle deeper lower-left grounding gradient in same hue family
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    animTertiary.copy(alpha = 0.42f),
                    animTertiary.copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = Offset(w * 0.18f, h * 0.88f),
                radius = maxDim * 0.78f
            ),
            size = size
        )

        // 5. Anti-banding micro-dither grain
        if (lightDitherOffsets.isEmpty() && w > 0f && h > 0f) {
            var i = 0
            while (i < NOISE_POINTS_LIGHT.size) {
                lightDitherOffsets.add(Offset(NOISE_POINTS_LIGHT[i] * w, NOISE_POINTS_LIGHT[i + 1] * h))
                darkDitherOffsets.add(Offset(NOISE_POINTS_DARK[i] * w, NOISE_POINTS_DARK[i + 1] * h))
                i += 2
            }
        }
        if (lightDitherOffsets.isNotEmpty()) {
            drawPoints(
                points = lightDitherOffsets,
                pointMode = PointMode.Points,
                color = Color(0x06FFFFFF),
                strokeWidth = 1.5f,
                cap = StrokeCap.Round
            )
            drawPoints(
                points = darkDitherOffsets,
                pointMode = PointMode.Points,
                color = Color(0x06000000),
                strokeWidth = 1.5f,
                cap = StrokeCap.Round
            )
        }
    }
}
