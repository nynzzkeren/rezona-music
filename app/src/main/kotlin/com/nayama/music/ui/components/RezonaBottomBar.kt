package com.nayama.music.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import com.nayama.music.navigation.RezonaTab
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.Typography

/**
 * Rezona Floating Liquid Glass Dock (V2 Specification — Stage A):
 * - 4 tabs: Home, Search, Library, You
 * - Active tab sits inside a frosted glass capsule bubble with icon + label
 * - On scroll down (`isFolded = true`), smoothly morphs into a SINGLE round 60dp glass button
 *   showing only the active tab icon at the bottom-left while the Mini Player widens next to it
 * - Tapping the collapsed circle or scrolling up expands the dock back with a spring
 * - Swiping horizontally across the expanded dock switches tabs
 */
@Composable
fun RezonaBottomBar(
    currentTab: RezonaTab,
    onTabSelected: (RezonaTab) -> Unit,
    isFolded: Boolean,
    onExpandDock: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tabs = RezonaTab.allTabs
    val view = LocalView.current
    var totalDrag by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomStart
    ) {
        val fullWidth = maxWidth
        val targetWidth = if (isFolded) 60.dp else fullWidth
        val targetHeight = if (isFolded) 60.dp else 64.dp

        val morphWidth by animateDpAsState(
            targetValue = targetWidth,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 320f),
            label = "dock_morph_w"
        )
        val morphHeight by animateDpAsState(
            targetValue = targetHeight,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 320f),
            label = "dock_morph_h"
        )

        RezonaFloatingDock(
            modifier = Modifier
                .width(morphWidth)
                .height(morphHeight)
                .pointerInput(currentTab, isFolded) {
                    if (isFolded) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                        onDragEnd = {
                            val currentIndex = tabs.indexOf(currentTab)
                            val dragThreshold = 48f
                            if (totalDrag < -dragThreshold && currentIndex < tabs.size - 1) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onTabSelected(tabs[currentIndex + 1])
                            } else if (totalDrag > dragThreshold && currentIndex > 0) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onTabSelected(tabs[currentIndex - 1])
                            }
                            totalDrag = 0f
                        }
                    )
                },
            shape = RoundedCornerShape(32.dp)
        ) {
            AnimatedContent(
                targetState = isFolded,
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                },
                label = "dock_fold_content"
            ) { folded ->
                if (folded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, color = PureWhite),
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onExpandDock()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = currentTab.iconRes),
                            contentDescription = "Expand navigation (${currentTab.title})",
                            tint = PureWhite,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEach { tab ->
                            val isActive = tab == currentTab

                            val tabScale by animateFloatAsState(
                                targetValue = if (isActive) 1.04f else 1.0f,
                                animationSpec = spring(dampingRatio = 0.65f, stiffness = 380f),
                                label = "dock_tab_scale_${tab.id}"
                            )
                            val tintColor by animateColorAsState(
                                targetValue = if (isActive) PureWhite else SoftSilver.copy(alpha = 0.85f),
                                animationSpec = tween(180),
                                label = "dock_tab_tint_${tab.id}"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .scale(tabScale)
                                    .clip(RoundedCornerShape(27.dp))
                                    .then(
                                        if (isActive) {
                                            Modifier
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(Color(0x2EFFFFFF), Color(0x14FFFFFF))
                                                    )
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    brush = Brush.verticalGradient(
                                                        listOf(Color(0x52FFFFFF), Color(0x12FFFFFF))
                                                    ),
                                                    shape = RoundedCornerShape(27.dp)
                                                )
                                        } else Modifier
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = true, color = PureWhite),
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            onTabSelected(tab)
                                        }
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
                                        tint = tintColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = tab.title,
                                        style = Typography.labelSmall.copy(
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                            letterSpacing = 0.1.sp
                                        ),
                                        color = tintColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
