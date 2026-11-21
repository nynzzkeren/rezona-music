package com.nayama.music.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.SpecularCardBorder
import com.nayama.music.ui.theme.SpecularDockBorder
import com.nayama.music.ui.theme.Typography
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.math.roundToInt

/**
 * Tunable Glass Blur Intensity levels.
 * In LOW intensity, live blur is replaced with a translucent scrim gradient for 60fps on low-end phones.
 */
enum class GlassIntensity(val blurRadiusDp: Float, val label: String) {
    LOW(8f, "Low"),
    MEDIUM(20f, "Medium"),
    HIGH(35f, "High");

    companion object {
        var current: GlassIntensity = MEDIUM
    }
}

/**
 * GlassSurface variants per rezona_core.md Liquid Glass System specification:
 * - Floating: real Haze blur (dock, mini player, sticky headers, sheets) — max 3 simultaneous blur layers
 * - Card, Button, IconButton, Chip, Field, Toggle, Slider, Menu, Dialog, Toast: baked glass
 */
enum class GlassSurfaceVariant {
    Floating,
    Card,
    Button,
    IconButton,
    Chip,
    Field,
    Toggle,
    Slider,
    Menu,
    Dialog,
    Toast
}

val LocalRezonaHazeState = compositionLocalOf<HazeState?> { null }

fun Modifier.rezonaHazeSource(hazeState: HazeState?): Modifier {
    return if (hazeState != null) {
        this.hazeSource(state = hazeState)
    } else {
        this
    }
}

/**
 * Applies hardware-accelerated Liquid Glass backdrop blur ONLY on large floating surfaces
 * and ONLY when GlassIntensity != LOW. In LOW intensity or API < 31, falls back to translucent scrim.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.rezonaHazeEffect(
    hazeState: HazeState?,
    shape: Shape? = null
): Modifier {
    val supportsHardwareBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useLiveBlur = supportsHardwareBlur && GlassIntensity.current != GlassIntensity.LOW
    return if (hazeState != null && useLiveBlur) {
        this.hazeEffect(
            state = hazeState,
            style = HazeMaterials.ultraThin()
        )
    } else {
        this
    }
}

fun Modifier.rezonaSpecularBorder(
    brush: Brush = SpecularCardBorder,
    strokeWidth: Dp = 1.dp,
    shape: Shape = RoundedCornerShape(18.dp)
): Modifier = this.border(
    width = strokeWidth,
    brush = brush,
    shape = shape
)

/**
 * Unified Liquid Glass Surface API (rezona_core.md).
 * Layers:
 * - Backdrop: real Haze blur ONLY on Floating/Dialog/Menu/Toast when GlassIntensity != LOW;
 *   small controls & cards use "baked glass" (gradient tint + highlights, no per-item blur).
 * - Tint: vertical gradient, white 8-14% at top to 2-5% at bottom, plus a faint cover color influence.
 * - Specular rim: 1dp gradient border, white 35-45% at top fading to 5% at bottom;
 *   thin inner highlight along the top edge; subtle inner shadow at the bottom.
 * - Pressed state: scale 0.96 with spring, brighter tint and rim, quick release overshoot,
 *   haptic tick on primary actions.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun GlassSurface(
    variant: GlassSurfaceVariant = GlassSurfaceVariant.Card,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    isActive: Boolean = false,
    isFocused: Boolean = false,
    enabled: Boolean = true,
    hapticOnPress: Boolean = false,
    forcePressedPreview: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val view = LocalView.current
    val hazeState = LocalRezonaHazeState.current
    val ambientAccent = LocalAmbientAccentColor.current
    val tintedGlass = LocalTintedGlassColor.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressedRaw by interactionSource.collectIsPressedAsState()
    val isPressed = (isPressedRaw || forcePressedPreview) && enabled

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 420f),
        label = "glass_press_scale"
    )

    val supportsHardwareBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val usesRealBlur = (variant == GlassSurfaceVariant.Floating ||
        variant == GlassSurfaceVariant.Dialog ||
        variant == GlassSurfaceVariant.Menu ||
        variant == GlassSurfaceVariant.Toast) &&
        supportsHardwareBlur &&
        GlassIntensity.current != GlassIntensity.LOW

    // Cover-tinted glass: ambientBase lightened 12-18% at 35-50% alpha + white specular rim (no black/purple boxes)
    val topAlpha = when {
        isPressed -> 0.54f
        isActive -> 0.48f
        isFocused -> 0.46f
        variant == GlassSurfaceVariant.Floating || variant == GlassSurfaceVariant.Dialog -> 0.48f
        variant == GlassSurfaceVariant.IconButton -> 0.44f
        else -> 0.38f
    }
    val bottomAlpha = when {
        isPressed -> 0.42f
        isActive -> 0.36f
        variant == GlassSurfaceVariant.Floating || variant == GlassSurfaceVariant.Dialog -> 0.38f
        variant == GlassSurfaceVariant.IconButton -> 0.32f
        else -> 0.28f
    }

    val tintedTop = lerp(tintedGlass, Color.White, 0.14f).copy(alpha = topAlpha)
    val tintedBottom = lerp(tintedGlass, ambientAccent, 0.28f).copy(alpha = bottomAlpha)

    // Specular rim: 1dp gradient border, white 35-45% at top fading to 5% at bottom
    val rimTopAlpha = when {
        isPressed -> 0.65f
        isFocused -> 0.60f
        isActive -> 0.52f
        else -> 0.40f
    }
    val rimBottomAlpha = when {
        isPressed -> 0.16f
        isFocused -> 0.18f
        isActive -> 0.12f
        else -> 0.05f
    }
    val rimBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = rimTopAlpha),
            Color.White.copy(alpha = (rimTopAlpha + rimBottomAlpha) * 0.35f),
            Color.White.copy(alpha = rimBottomAlpha)
        )
    )

    val clickMod = if (onClick != null && enabled) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(color = PureWhite),
            onClick = {
                if (hapticOnPress) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                onClick()
            }
        )
    } else Modifier

    val resolvedAlignment = contentAlignment ?: when (variant) {
        GlassSurfaceVariant.IconButton,
        GlassSurfaceVariant.Button,
        GlassSurfaceVariant.Chip,
        GlassSurfaceVariant.Floating -> Alignment.Center
        else -> Alignment.TopStart
    }

    Box(
        modifier = modifier
            .scale(scale)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .then(
                if (usesRealBlur && hazeState != null) {
                    Modifier.hazeEffect(state = hazeState, style = HazeMaterials.ultraThin())
                } else Modifier
            )
            .background(
                brush = Brush.verticalGradient(listOf(tintedTop, tintedBottom)),
                shape = shape
            )
            .drawWithContent {
                drawContent()
                // Thin inner highlight along top edge & subtle inner shadow along bottom edge
                val strokePx = 1.dp.toPx()
                drawLine(
                    color = Color.White.copy(alpha = if (isPressed) 0.28f else 0.16f),
                    start = Offset(size.width * 0.08f, strokePx * 0.5f),
                    end = Offset(size.width * 0.92f, strokePx * 0.5f),
                    strokeWidth = strokePx
                )
                drawLine(
                    color = Color.Black.copy(alpha = 0.22f),
                    start = Offset(size.width * 0.08f, size.height - strokePx * 0.5f),
                    end = Offset(size.width * 0.92f, size.height - strokePx * 0.5f),
                    strokeWidth = strokePx
                )
            }
            .border(
                width = if (variant == GlassSurfaceVariant.Floating || isFocused) 1.25.dp else 1.dp,
                brush = rimBrush,
                shape = shape
            )
            .then(clickMod),
        contentAlignment = resolvedAlignment,
        content = content
    )
}

/**
 * Standard Rezona Liquid Glass Card (Baked Glass — no per-item Haze blur to preserve 3-layer budget).
 */
@Composable
fun RezonaGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    backgroundColor: Color = Color.Unspecified,
    borderBrush: Brush = SpecularCardBorder,
    strokeWidth: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    GlassSurface(
        variant = GlassSurfaceVariant.Card,
        modifier = modifier,
        shape = shape,
        hapticOnPress = onClick != null,
        onClick = onClick,
        content = content
    )
}

/**
 * Liquid Glass Pill / Chip (for Category Filters & Status Badges)
 */
@Composable
fun RezonaGlassPill(
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    GlassSurface(
        variant = GlassSurfaceVariant.Chip,
        modifier = modifier.heightIn(min = 40.dp),
        shape = shape,
        isActive = isActive,
        hapticOnPress = onClick != null,
        onClick = onClick,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

/**
 * Circular Glass Action Disc / IconButton (expands hit area to >= 48dp while keeping visual size crisp).
 */
@Composable
fun RezonaActionDisc(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.size(maxOf(size, 48.dp)),
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            variant = GlassSurfaceVariant.IconButton,
            modifier = Modifier.size(size),
            shape = CircleShape,
            hapticOnPress = true,
            onClick = onClick,
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

/**
 * Floating Liquid Glass Dock (Used for Navbar Lensa and Floating Mini Player — Real Haze Blur)
 */
@Composable
fun RezonaFloatingDock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(32.dp),
    content: @Composable BoxScope.() -> Unit
) {
    GlassSurface(
        variant = GlassSurfaceVariant.Floating,
        modifier = modifier,
        shape = shape,
        content = content
    )
}

/**
 * Primary & Secondary Liquid Glass Pill Buttons (rezona_core.md:
 * "Buttons are glass pills: primary = white-tinted glass with dark text, secondary = clear glass with light text.")
 */
@Composable
fun RezonaGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    enabled: Boolean = true,
    forcePressedPreview: Boolean = false
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressedRaw by interactionSource.collectIsPressedAsState()
    val isPressed = (isPressedRaw || forcePressedPreview) && enabled

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 420f),
        label = "glass_btn_scale"
    )

    val shape = RoundedCornerShape(26.dp)
    val bgBrush = if (isPrimary) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (isPressed) 0.96f else 0.88f),
                Color(0xFFE4E7EE).copy(alpha = if (isPressed) 0.90f else 0.78f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (isPressed) 0.22f else 0.13f),
                Color.White.copy(alpha = if (isPressed) 0.10f else 0.04f)
            )
        )
    }

    val rimBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (isPrimary) 0.95f else if (isPressed) 0.60f else 0.42f),
            Color.White.copy(alpha = if (isPrimary) 0.40f else 0.08f)
        )
    )

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .scale(scale)
            .alpha(if (enabled) 1f else 0.42f)
            .clip(shape)
            .background(bgBrush, shape)
            .border(1.dp, rimBrush, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(color = if (isPrimary) Color(0xFF08090B) else PureWhite),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onClick()
                }
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = Typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
            color = if (isPrimary) Color(0xFF08090B) else PearlWhite
        )
    }
}

/**
 * Custom Liquid Glass Toggle replacing Material3 Switch.
 * Minimum 48dp touch area with spring thumb motion & specular glass track.
 */
@Composable
fun RezonaGlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val view = LocalView.current
    val thumbProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 380f),
        label = "glass_toggle_thumb"
    )
    val trackTopColor by animateColorAsState(
        targetValue = if (checked) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.10f),
        animationSpec = tween(180),
        label = "glass_toggle_top"
    )
    val trackBottomColor by animateColorAsState(
        targetValue = if (checked) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.03f),
        animationSpec = tween(180),
        label = "glass_toggle_bottom"
    )

    Box(
        modifier = modifier
            .size(width = 56.dp, height = 48.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onCheckedChange(!checked)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        val pillShape = RoundedCornerShape(16.dp)
        Box(
            modifier = Modifier
                .size(width = 50.dp, height = 28.dp)
                .clip(pillShape)
                .background(Brush.verticalGradient(listOf(trackTopColor, trackBottomColor)), pillShape)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (checked) 0.60f else 0.35f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape = pillShape
                )
                .padding(horizontal = 3.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset((thumbProgress * 22.dp.toPx()).roundToInt(), 0) }
                    .size(22.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(if (checked) PureWhite else SoftSilver)
            )
        }
    }
}

/**
 * Custom Liquid Glass Checkbox replacing Material3 Checkbox (48dp touch area).
 */
@Composable
fun RezonaGlassCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val view = LocalView.current
    val scale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "glass_checkbox_scale"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onCheckedChange(!checked)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        val boxShape = RoundedCornerShape(7.dp)
        Box(
            modifier = Modifier
                .size(22.dp)
                .scale(scale)
                .clip(boxShape)
                .background(
                    if (checked) Color.White.copy(alpha = 0.90f)
                    else Color.White.copy(alpha = 0.10f)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (checked) 0.95f else 0.42f),
                            Color.White.copy(alpha = 0.10f)
                        )
                    ),
                    shape = boxShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Canvas(modifier = Modifier.size(12.dp)) {
                    val w = size.width
                    val h = size.height
                    drawLine(
                        color = Color(0xFF08090B),
                        start = Offset(w * 0.18f, h * 0.52f),
                        end = Offset(w * 0.44f, h * 0.78f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFF08090B),
                        start = Offset(w * 0.44f, h * 0.78f),
                        end = Offset(w * 0.84f, h * 0.22f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/**
 * Custom Liquid Glass Slider with floating percent/value bubble while dragging.
 * Replaces Material3 Slider.
 */
@Composable
fun RezonaGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    formatBubble: ((Float) -> String)? = null
) {
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember(fraction) { mutableFloatStateOf(fraction) }
    val activeFraction = if (isDragging) dragFraction else fraction

    val bubbleText = remember(activeFraction, valueRange) {
        val currentVal = valueRange.start + activeFraction * span
        formatBubble?.invoke(currentVal) ?: "${(activeFraction * 100).roundToInt()}%"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val f = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onValueChange(valueRange.start + f * span)
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + dragFraction * span)
                    },
                    onDrag = { change, _ ->
                        dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + dragFraction * span)
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackHeight = 4.dp.toPx()
            val cy = size.height / 2f
            val thumbRadius = 7.dp.toPx()
            val thumbX = (size.width * activeFraction).coerceIn(thumbRadius, (size.width - thumbRadius).coerceAtLeast(thumbRadius))

            // Unplayed glass track
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = Offset(0f, cy - trackHeight / 2f),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            )

            // Played bright glass track
            drawRoundRect(
                color = PureWhite,
                topLeft = Offset(0f, cy - trackHeight / 2f),
                size = Size(thumbX, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            )

            // Soft thumb glow
            drawCircle(
                color = Color.White.copy(alpha = if (isDragging) 0.35f else 0.18f),
                radius = if (isDragging) 14.dp.toPx() else 11.dp.toPx(),
                center = Offset(thumbX, cy)
            )

            // 14dp PureWhite thumb
            drawCircle(
                color = PureWhite,
                radius = thumbRadius,
                center = Offset(thumbX, cy)
            )
        }

        // Percent bubble while dragging
        if (isDragging) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-6).dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xD9161922))
                    .border(1.dp, SpecularCardBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = bubbleText,
                    style = Typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = PureWhite
                )
            }
        }
    }
}

/**
 * Custom Liquid Glass TextField with focus glow rim (replaces OutlinedTextField).
 */
@Composable
fun RezonaGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    forceFocusedPreview: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocusedRaw by interactionSource.collectIsFocusedAsState()
    val isFocused = isFocusedRaw || forceFocusedPreview
    val shape = RoundedCornerShape(18.dp)

    GlassSurface(
        variant = GlassSurfaceVariant.Field,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (isFocused) {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = shape,
                        ambientColor = Color.White.copy(alpha = 0.25f),
                        spotColor = Color.White.copy(alpha = 0.35f)
                    )
                } else Modifier
            ),
        shape = shape,
        isFocused = isFocused,
        enabled = enabled,
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = Typography.bodyMedium.copy(fontSize = 14.sp),
                    color = SmokySilver
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                interactionSource = interactionSource,
                textStyle = TextStyle(
                    color = PearlWhite,
                    fontSize = 14.sp,
                    fontFamily = Typography.bodyMedium.fontFamily
                ),
                cursorBrush = SolidColor(PureWhite),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Custom Liquid Glass Modal Dialog replacing Material3 AlertDialog.
 */
@Composable
fun RezonaGlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmText: String = "Confirm",
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = "Cancel",
    isDestructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassSurface(
            variant = GlassSurfaceVariant.Dialog,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .shadow(24.dp, RoundedCornerShape(26.dp)),
            shape = RoundedCornerShape(26.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Text(
                    text = title,
                    style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp),
                    color = PearlWhite
                )
                Spacer(modifier = Modifier.height(14.dp))
                content()
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dismissText != null) {
                        RezonaGlassButton(
                            text = dismissText,
                            isPrimary = false,
                            onClick = onDismissRequest
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    if (onConfirm != null) {
                        RezonaGlassButton(
                            text = confirmText,
                            isPrimary = !isDestructive,
                            onClick = onConfirm
                        )
                    }
                }
            }
        }
    }
}

/**
 * Custom Liquid Glass Toast / Snackbar banner.
 */
@Composable
fun RezonaGlassToast(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        variant = GlassSurfaceVariant.Toast,
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = Typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                color = PearlWhite,
                modifier = Modifier.weight(1f)
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.width(12.dp))
                RezonaGlassPill(isActive = true, onClick = onAction) {
                    Text(
                        text = actionLabel,
                        style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = PureWhite
                    )
                }
            }
        }
    }
}

/**
 * Hairline separator for unboxed list rows on the continuous ambient background (rezona_core.md line 26).
 */
@Composable
fun RezonaHairlineDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = 0.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(0.5.dp)
            .background(Color(0x1EFFFFFF))
    )
}
