package com.nayama.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.ui.theme.DarkCharcoal
import com.nayama.music.ui.theme.ElevatedSurface
import com.nayama.music.ui.theme.Graphite
import com.nayama.music.ui.theme.PureBlack
import com.nayama.music.ui.theme.PureWhite

/**
 * BitChord-inspired Frosted Glass Container.
 * Provides a translucent dark glassmorphism aesthetic with subtle specular border highlight.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    backgroundColor: Color = Color(0xDD161616),
    borderColor: Color = Color(0x36FFFFFF),
    borderWidth: Dp = 0.75.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.97f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "glass_card_press_scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        backgroundColor.copy(alpha = 0.88f),
                        backgroundColor.copy(alpha = 0.72f),
                        Color(0xE00A0A0C)
                    )
                )
            )
            .border(
                borderWidth,
                Brush.verticalGradient(
                    listOf(
                        borderColor,
                        borderColor.copy(alpha = 0.14f)
                    )
                ),
                shape
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { onClick() }
                } else Modifier
            ),
        content = content
    )
}

/**
 * BitChord-style Liquid Animated Equalizer Bars.
 * Displayed in the MiniPlayer and Full Player when audio is actively playing.
 */
@Composable
fun MiniWaveVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 4,
    color: Color = PureWhite,
    maxHeight: Dp = 16.dp
) {
    val transition = rememberInfiniteTransition(label = "mini_wave")

    val h1 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "w1"
    )
    val h2 by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "w2"
    )
    val h3 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse),
        label = "w3"
    )
    val h4 by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(390, easing = LinearEasing), RepeatMode.Reverse),
        label = "w4"
    )

    val heights = listOf(h1, h2, h3, h4)

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(barCount.coerceAtMost(heights.size)) { index ->
            val fraction = if (isPlaying) heights[index] else 0.2f
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}

/**
 * BitChord Apple Music-style Frosted Pill Chip for filters, tags, and secondary controls.
 */
@Composable
fun GlassPillChip(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "glass_pill_scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50.dp))
            .background(
                if (isSelected) PureWhite
                else Color(0xDD1A1A1A)
            )
            .border(
                0.75.dp,
                if (isSelected) PureWhite else Color(0x33FFFFFF),
                RoundedCornerShape(50.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) PureBlack else PureWhite,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 0.6.sp
        )
    }
}

