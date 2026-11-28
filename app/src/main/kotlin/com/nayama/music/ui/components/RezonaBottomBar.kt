package com.nayama.music.ui.components

import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.navigation.RezonaTab
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.Typography
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Rezona Floating Liquid Glass Dock with Smooth Interactive Sliding Glass Lens Effect:
 * - 4 tabs: Home, Search, Library, You
 * - Continuous Liquid Glass Lens pill that glides smoothly between tabs and follows your finger
 *   in real time when you swipe/drag horizontally across the navbar!
 * - Magnifies and illuminates the tab currently under the glass lens (`scale` + refractive specular glare).
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
    val density = LocalDensity.current

    val activeIndex = tabs.indexOf(currentTab).coerceAtLeast(0)
    var isDraggingLens by remember { mutableStateOf(false) }
    var dragFloatIndex by remember { mutableFloatStateOf(activeIndex.toFloat()) }
    var lastHapticSlot by remember { mutableIntStateOf(activeIndex) }

    LaunchedEffect(activeIndex, isDraggingLens) {
        if (!isDraggingLens) {
            dragFloatIndex = activeIndex.toFloat()
            lastHapticSlot = activeIndex
        }
    }

    // Smooth spring-driven fractional tab position (0f .. 3f) for the Liquid Glass Lens
    val animatedLensIndex by animateFloatAsState(
        targetValue = if (isDraggingLens) dragFloatIndex else activeIndex.toFloat(),
        animationSpec = if (isDraggingLens) {
            spring(dampingRatio = 0.85f, stiffness = 900f)
        } else {
            spring(dampingRatio = 0.72f, stiffness = 340f)
        },
        label = "lens_slide_index"
    )

    // Subtle refractive lens bulge scale when sliding
    val lensMagnifyScale by animateFloatAsState(
        targetValue = if (isDraggingLens) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "lens_bulge_scale"
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomStart
    ) {
        val fullWidth = maxWidth
        val targetWidth = if (isFolded) 60.dp else fullWidth
        val targetHeight = if (isFolded) 60.dp else 66.dp

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
                .height(morphHeight),
            shape = RoundedCornerShape(33.dp)
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
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp, vertical = 5.dp)
                            .pointerInput(tabs.size) {
                                val totalW = size.width.toFloat().coerceAtLeast(1f)
                                val slotW = totalW / tabs.size.toFloat()
                                detectHorizontalDragGestures(
                                    onDragStart = { startOffset ->
                                        isDraggingLens = true
                                        val startIdx = ((startOffset.x / slotW) - 0.5f)
                                            .coerceIn(0f, (tabs.size - 1).toFloat())
                                        dragFloatIndex = startIdx
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        val deltaSlots = dragAmount / slotW
                                        dragFloatIndex = (dragFloatIndex + deltaSlots)
                                            .coerceIn(0f, (tabs.size - 1).toFloat())
                                        val nearestSlot = dragFloatIndex.roundToInt().coerceIn(0, tabs.lastIndex)
                                        if (nearestSlot != lastHapticSlot) {
                                            lastHapticSlot = nearestSlot
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            onTabSelected(tabs[nearestSlot])
                                        }
                                    },
                                    onDragEnd = {
                                        val finalSlot = dragFloatIndex.roundToInt().coerceIn(0, tabs.lastIndex)
                                        isDraggingLens = false
                                        if (tabs[finalSlot] != currentTab) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            onTabSelected(tabs[finalSlot])
                                        }
                                    },
                                    onDragCancel = {
                                        isDraggingLens = false
                                    }
                                )
                            }
                    ) {
                        val innerWidthPx = with(density) { maxWidth.toPx() }
                        val tabWidthDp = maxWidth / tabs.size.coerceAtLeast(1)
                        val tabWidthPx = innerWidthPx / tabs.size.coerceAtLeast(1)
                        val lensOffsetXPx = (animatedLensIndex * tabWidthPx).roundToInt()

                        // ── 1. SLIDING LIQUID GLASS LENS PILL (REFRACTIVE DROPLET) ──
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(lensOffsetXPx, 0) }
                                .width(tabWidthDp)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    scaleX = lensMagnifyScale
                                    scaleY = lensMagnifyScale
                                }
                                .clip(RoundedCornerShape(28.dp))
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0x48FFFFFF),
                                            Color(0x28D4E6FF),
                                            Color(0x16FFFFFF)
                                        ),
                                        center = Offset(tabWidthPx * 0.5f, 18f),
                                        radius = (tabWidthPx * 0.85f).coerceAtLeast(40f)
                                    )
                                )
                                .border(
                                    width = 1.25.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color(0x99FFFFFF),
                                            Color(0x36D0E4FF),
                                            Color(0x48FFFFFF)
                                        )
                                    ),
                                    shape = RoundedCornerShape(28.dp)
                                )
                        )

                        // ── 2. FOREGROUND TAB ITEMS WITH LENS PROXIMITY MAGNIFICATION ──
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tabs.forEachIndexed { idx, tab ->
                                val distFromLens = abs(animatedLensIndex - idx.toFloat()).coerceIn(0f, 1.5f)
                                val proximity = (1f - distFromLens).coerceIn(0f, 1f)
                                val itemScale = 1f + (proximity * 0.10f)
                                val tintColor by animateColorAsState(
                                    targetValue = if (proximity > 0.45f) PureWhite else SoftSilver.copy(alpha = 0.78f),
                                    animationSpec = tween(120),
                                    label = "dock_tab_tint_${tab.id}"
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .scale(itemScale)
                                        .clip(RoundedCornerShape(28.dp))
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
                                                fontWeight = if (proximity > 0.5f) FontWeight.ExtraBold else FontWeight.Medium,
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
}
