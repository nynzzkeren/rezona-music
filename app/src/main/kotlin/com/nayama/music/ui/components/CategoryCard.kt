package com.nayama.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.model.GenreCategory
import com.nayama.music.ui.theme.DarkCharcoal
import com.nayama.music.ui.theme.ElevatedSurface
import com.nayama.music.ui.theme.Graphite
import com.nayama.music.ui.theme.MediumGray
import com.nayama.music.ui.theme.PureWhite

@Composable
fun CategoryCard(
    category: GenreCategory,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "category_card_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(ElevatedSurface)
            .border(0.5.dp, Graphite, RoundedCornerShape(14.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = category.tag,
                color = MediumGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Column {
                Text(
                    text = category.title,
                    color = PureWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = category.subtitle,
                    color = MediumGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
