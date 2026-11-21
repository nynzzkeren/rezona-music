package com.nayama.music.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nayama.music.ui.theme.DarkCharcoal
import com.nayama.music.ui.theme.ElevatedSurface
import com.nayama.music.ui.theme.Graphite

@Composable
fun NayamaCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = ElevatedSurface,
    borderColor: Color = Graphite,
    borderWidth: Dp = 0.5.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = if (onClick != null && isPressed) 0.98f else 1f

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interactionSource, indication = null) { onClick() }
                } else Modifier
            ),
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Box(content = content)
    }
}
