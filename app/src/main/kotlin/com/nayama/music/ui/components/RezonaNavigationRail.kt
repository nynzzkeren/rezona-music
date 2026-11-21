package com.nayama.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.navigation.RezonaTab
import com.nayama.music.ui.theme.GlassDock
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.Typography
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * Rezona Responsive Navigation Rail for Large Screens (>= 600dp / Foldables & Tablets)
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun RezonaNavigationRail(
    currentTab: RezonaTab,
    onTabSelected: (RezonaTab) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalRezonaHazeState.current
) {
    val tabs = RezonaTab.allTabs

    Box(
        modifier = modifier
            .width(82.dp)
            .fillMaxHeight()
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState, style = HazeMaterials.ultraThin())
                } else Modifier
            )
            .background(GlassDock)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0x05FFFFFF),
                        Color(0x30FFFFFF)
                    )
                ),
                shape = androidx.compose.ui.graphics.RectangleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand Logo Glyph at top of rail
            Image(
                painter = painterResource(id = R.drawable.ic_rezona_logo),
                contentDescription = "Rezona",
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
            )

            // 4 Navigation Tabs
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                tabs.forEach { tab ->
                    val isActive = tab == currentTab

                    val tabScale by animateFloatAsState(
                        targetValue = if (isActive) 1.12f else 1.0f,
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = 380f),
                        label = "rail_lens_${tab.id}"
                    )

                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .scale(tabScale)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isActive) Color(0x331C1F26) else Color.Transparent
                            )
                            .border(
                                width = 1.dp,
                                brush = if (isActive) Brush.verticalGradient(
                                    listOf(Color(0x60FFFFFF), Color(0x15FFFFFF))
                                ) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, color = PureWhite),
                                onClick = { onTabSelected(tab) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = tab.iconRes),
                                contentDescription = tab.title,
                                tint = if (isActive) PureWhite else SmokySilver,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = tab.title,
                                style = Typography.labelSmall,
                                fontSize = 10.sp,
                                color = if (isActive) PearlWhite else SmokySilver
                            )
                            if (isActive) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(PureWhite)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Hardware / Equalizer Indicator Disc at bottom
            RezonaActionDisc(size = 40.dp, onClick = {}) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_equalizer),
                    contentDescription = "Sound Check",
                    tint = SmokySilver,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
