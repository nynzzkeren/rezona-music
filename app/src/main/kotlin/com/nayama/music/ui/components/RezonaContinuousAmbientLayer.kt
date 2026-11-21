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
 * so baked glass tints carry a faint influence of the cover art (rezona_core.md).
 */
val LocalAmbientAccentColor = compositionLocalOf { Color(0xFF28344A) }

/**
 * Derives ambient background colors from the active track.
 * Respects desaturated atmospheric bleed per MASTER.md and rezona_core.md.
 */
fun deriveAmbientColors(track: RezonaTrack?): Triple<Color, Color, Color> {
    if (track == null) {
        return Triple(
            Color(0xFF1D2838),
            Color(0xFF141A24),
            Color(0xFF0F141E)
        )
    }

    val hash = (track.title.hashCode() xor (track.artist.hashCode() * 31)).absoluteValue
    val palette = MOCK_PALETTES[hash % MOCK_PALETTES.size]

    return Triple(
        palette.ambient.copy(alpha = 0.62f),
        palette.primary.copy(alpha = 0.46f),
        palette.secondary.copy(alpha = 0.34f)
    )
}

/**
 * Precomputed normalized noise coordinates (0..1) for zero-allocation anti-banding dither.
 */
private val NOISE_POINTS_LIGHT: FloatArray by lazy {
    val rng = Random(0x52455A4F)
    FloatArray(360 * 2) { rng.nextFloat() }
}

private val NOISE_POINTS_DARK: FloatArray by lazy {
    val rng = Random(0x4E415941)
    FloatArray(360 * 2) { rng.nextFloat() }
}

/**
 * 0.1 BACKGROUND SEAM FIX:
 * ONE full-screen continuous layer drawn behind everything, edge-to-edge
 * (behind status bar and navigation bar).
 * Base dark gradient + 3 large soft radial gradients of the cover colors
 * with long smooth falloff + subtle dither noise in a single Canvas.
 * Crossfades over 700ms between tracks. Shared by all screens.
 */
@Composable
fun RezonaContinuousAmbientLayer(
    primaryColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color = Color(0xFF10141D),
    vividMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val boost = if (vividMode) 1.22f else 1.0f

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

    val lightDitherOffsets = remember { ArrayList<Offset>(360) }
    val darkDitherOffsets = remember { ArrayList<Offset>(360) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer()
    ) {
        val w = size.width
        val h = size.height
        val maxDim = maxOf(w, h)

        // 1. Deep OLED Obsidian Base Gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0C0E13),
                    Color(0xFF08090C),
                    Color(0xFF050608),
                    Color(0xFF030406)
                ),
                startY = 0f,
                endY = h
            ),
            size = size
        )

        // 2. Large soft radial gradient 1: Top-left / upper-mid aura with long falloff
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    animPrimary.copy(alpha = (0.52f * boost).coerceAtMost(0.75f)),
                    animPrimary.copy(alpha = (0.28f * boost).coerceAtMost(0.45f)),
                    animPrimary.copy(alpha = 0.11f),
                    animPrimary.copy(alpha = 0.03f),
                    Color.Transparent
                ),
                center = Offset(w * 0.34f, h * 0.22f),
                radius = maxDim * 0.92f
            ),
            size = size
        )

        // 3. Large soft radial gradient 2: Lower-right overlapping aura with long falloff
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    animSecondary.copy(alpha = (0.44f * boost).coerceAtMost(0.65f)),
                    animSecondary.copy(alpha = (0.22f * boost).coerceAtMost(0.38f)),
                    animSecondary.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = Offset(w * 0.72f, h * 0.76f),
                radius = maxDim * 0.95f
            ),
            size = size
        )

        // 4. Large soft radial gradient 3: Mid-left bridge aura so top & bottom blend seamlessly
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    animTertiary.copy(alpha = (0.36f * boost).coerceAtMost(0.55f)),
                    animTertiary.copy(alpha = 0.14f),
                    animTertiary.copy(alpha = 0.04f),
                    Color.Transparent
                ),
                center = Offset(w * 0.22f, h * 0.52f),
                radius = maxDim * 0.82f
            ),
            size = size
        )

        // 5. Subtle procedural dither noise grain to prevent 8-bit gradient banding
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
                color = Color(0x08000000),
                strokeWidth = 1.5f,
                cap = StrokeCap.Round
            )
        }
    }
}
