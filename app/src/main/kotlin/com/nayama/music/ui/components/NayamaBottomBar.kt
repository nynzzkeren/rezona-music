package com.nayama.music.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.navigation.NayamaScreen
import com.nayama.music.ui.theme.CoralAccent
import com.nayama.music.ui.theme.GlassBorder
import com.nayama.music.ui.theme.GlassSurfaceLens
import com.nayama.music.ui.theme.GraphiteSurface
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver

/**
 * Floating "Navbar Lensa" Interaktif (Presisi Sesuai Video & Foto Referensi):
 * 1. "Geser navbar-nya, rasain sendiri" -> Gesture geser horizontal untuk pindah tab dengan haptic spring.
 * 2. "Scroll turun, dia melipat. Mini player ikut nempel" -> Mode lipat otomatis saat scroll.
 * 3. Efek pendaran lens magnifier cembung + chromatic border pada tab yang aktif.
 * 4. 3 Tab Utama: Beranda (Home), Cari (Search), Pustaka (Library).
 */
@Composable
fun NayamaBottomBar(
    currentScreen: NayamaScreen,
    onScreenSelected: (NayamaScreen) -> Unit,
    modifier: Modifier = Modifier,
    isFolded: Boolean = false
) {
    val items = NayamaScreen.bottomNavItems
    val currentIndex = items.indexOf(currentScreen).coerceAtLeast(0)

    val bottomPadding by animateDpAsState(
        targetValue = if (isFolded) 4.dp else 10.dp,
        animationSpec = spring(stiffness = 300f),
        label = "nav_bottom_pad"
    )

    val verticalContentPadding by animateDpAsState(
        targetValue = if (isFolded) 4.dp else 7.dp,
        animationSpec = spring(stiffness = 300f),
        label = "nav_content_pad"
    )

    val horizontalPadding by animateDpAsState(
        targetValue = if (isFolded) 28.dp else 22.dp,
        animationSpec = spring(stiffness = 300f),
        label = "nav_horiz_pad"
    )

    val dockScale by animateFloatAsState(
        targetValue = if (isFolded) 0.94f else 1f,
        animationSpec = spring(stiffness = 300f),
        label = "nav_dock_scale"
    )

    var accumulatedDrag by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = horizontalPadding, vertical = bottomPadding)
            .graphicsLayer {
                scaleX = dockScale
                scaleY = dockScale
            }
            .pointerInput(items) {
                detectHorizontalDragGestures(
                    onDragStart = { accumulatedDrag = 0f },
                    onDragEnd = { accumulatedDrag = 0f },
                    onDragCancel = { accumulatedDrag = 0f }
                ) { change, dragAmount ->
                    change.consume()
                    accumulatedDrag += dragAmount
                    if (accumulatedDrag > 45f) {
                        val prev = (currentIndex - 1).coerceAtLeast(0)
                        onScreenSelected(items[prev])
                        accumulatedDrag = 0f
                    } else if (accumulatedDrag < -45f) {
                        val next = (currentIndex + 1).coerceAtMost(items.lastIndex)
                        onScreenSelected(items[next])
                        accumulatedDrag = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Floating Lens Pill Container
        Row(
            modifier = Modifier
                .shadow(
                    elevation = if (isFolded) 10.dp else 20.dp,
                    shape = RoundedCornerShape(40.dp),
                    ambientColor = Color(0x99000000),
                    spotColor = Color(0xCC000000)
                )
                .clip(RoundedCornerShape(40.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xDD1B202C),
                            Color(0xBB131720),
                            Color(0xF00A0C10)
                        )
                    )
                )
                .border(
                    width = 1.25.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x90FFFFFF),
                            Color(0x30FFFFFF),
                            Color(0x0CFFFFFF)
                        )
                    ),
                    shape = RoundedCornerShape(40.dp)
                )
                .padding(horizontal = 8.dp, vertical = verticalContentPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { screen ->
                LensNavItem(
                    screen = screen,
                    isSelected = currentScreen == screen,
                    isCompact = isFolded,
                    onClick = { onScreenSelected(screen) }
                )
            }
        }
    }
}

@Composable
private fun LensNavItem(
    screen: NayamaScreen,
    isSelected: Boolean,
    isCompact: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else if (isSelected) 1.08f else 1f,
        animationSpec = spring(stiffness = 400f),
        label = "lens_item_scale"
    )

    val label = when (screen) {
        NayamaScreen.Home    -> "Beranda"
        NayamaScreen.Explore -> "Jelajah"
        NayamaScreen.Search  -> "Cari"
        NayamaScreen.Library -> "Pustaka"
        NayamaScreen.Profile -> "Profil"
    }

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) PearlWhite else SmokySilver,
        animationSpec = tween(180),
        label = "lens_icon_color"
    )

    val pillBackground by animateColorAsState(
        targetValue = if (isSelected) GraphiteSurface.copy(alpha = 0.95f) else Color.Transparent,
        animationSpec = tween(180),
        label = "lens_pill_bg"
    )

    val pillBorder by animateColorAsState(
        targetValue = if (isSelected) Color(0x38FFFFFF) else Color.Transparent,
        animationSpec = tween(180),
        label = "lens_pill_border"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(30.dp))
            .background(pillBackground)
            .border(0.75.dp, pillBorder, RoundedCornerShape(30.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClickLabel = label,
                onClick = onClick
            )
            .padding(
                horizontal = if (isSelected && !isCompact) 18.dp else 12.dp,
                vertical = if (isCompact) 6.dp else 8.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        // Optical Chromatic Lens Glow & Prismatic Aberration Rim (Frame 006)
        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(30.dp))
                    .border(
                        width = 1.5.dp,
                        brush = Brush.sweepGradient(
                            listOf(
                                CoralAccent,
                                Color(0xFFFFC069),
                                Color(0xFF67E8F9),
                                Color(0xFFA78BFA),
                                CoralAccent
                            )
                        ),
                        shape = RoundedCornerShape(30.dp)
                    )
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(30.dp))
                    .blur(14.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                CoralAccent.copy(alpha = 0.40f),
                                Color(0x22F4F5F7),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = screen.iconResId),
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(if (isCompact) 18.dp else 22.dp)
                )

                // When selected and not folded, expand typography label
                if (isSelected && !isCompact) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        color = PearlWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Glowing dot indicator under active tab (Frame 006)
            if (isSelected) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(CoralAccent)
                )
            }
        }
    }
}

/**
 * Responsive Navigation Rail for wide screens / tablets (>= 600.dp).
 */
@Composable
fun NayamaNavigationRail(
    currentScreen: NayamaScreen,
    onScreenSelected: (NayamaScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(84.dp)
            .fillMaxHeight()
            .background(GlassSurfaceLens)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        NayamaScreen.bottomNavItems.forEach { screen ->
            LensNavItem(
                screen = screen,
                isSelected = currentScreen == screen,
                onClick = { onScreenSelected(screen) }
            )
        }
    }
}
