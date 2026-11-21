package com.nayama.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nayama.music.ui.theme.Graphite
import com.nayama.music.ui.theme.LightGray
import com.nayama.music.ui.theme.PureWhite
import kotlin.math.sin

@Composable
fun AudioVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 24,
    color: Color = PureWhite,
    inactiveColor: Color = Graphite
) {
    val phase: Float
    if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "visualizer_anim")
        val animatedPhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase"
        )
        phase = animatedPhase
    } else {
        phase = 0f
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
    ) {
        val totalWidth = size.width
        val canvasHeight = size.height
        val barSpacing = 4.dp.toPx()
        val totalSpacing = barSpacing * (barCount - 1)
        val barWidth = ((totalWidth - totalSpacing) / barCount).coerceAtLeast(2.dp.toPx())

        for (i in 0 until barCount) {
            val normalizedIndex = i.toFloat() / barCount.toFloat()
            val left = i * (barWidth + barSpacing)

            val barHeight = if (isPlaying) {
                // Harmonic wave synthesis for natural audio frequency response
                val harmonic1 = sin(phase + i * 0.45f) * 0.5f + 0.5f
                val harmonic2 = sin(phase * 1.5f + i * 0.8f) * 0.3f + 0.3f
                val combined = ((harmonic1 + harmonic2) / 1.6f).coerceIn(0.12f, 1f)
                canvasHeight * combined
            } else {
                canvasHeight * 0.08f // resting idle state
            }

            val top = canvasHeight - barHeight

            drawRoundRect(
                color = if (isPlaying) color else inactiveColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

