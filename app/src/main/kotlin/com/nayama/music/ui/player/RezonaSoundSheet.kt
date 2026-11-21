package com.nayama.music.ui.player

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.playback.SoundPreset
import com.nayama.music.playback.SoundPresetCategory
import com.nayama.music.ui.components.LocalAmbientBaseColor
import com.nayama.music.ui.components.LocalTintedGlassColor
import com.nayama.music.ui.components.RezonaGlassPill
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.Typography

/**
 * Part 2 Sound Effects Glass Bottom Sheet:
 * - Category filter chips: All, Tempo, Space, Tone, Fun
 * - 2-column grid of one-tap cover-tinted glass tiles (icon, name, one-line description)
 * - Tap an active preset again to return to Normal; zero sliders here (manual tempo/pitch stays in Advanced).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RezonaSoundEffectsSheet(
    activePreset: SoundPreset,
    onSelectPreset: (SoundPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ambientBase = LocalAmbientBaseColor.current
    val tintedGlass = LocalTintedGlassColor.current
    val view = LocalView.current

    var selectedCategory by remember { mutableStateOf(SoundPresetCategory.ALL) }
    val presets = remember(selectedCategory) { SoundPreset.byCategory(selectedCategory) }

    val sheetSurfaceColor = remember(ambientBase) {
        lerp(ambientBase, Color(0xFF0A0C10), 0.25f).copy(alpha = 0.94f)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetSurfaceColor,
        scrimColor = Color(0x88000000),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color(0x55FFFFFF))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sound Effects",
                        style = Typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        color = PureWhite
                    )
                    Text(
                        text = if (activePreset == SoundPreset.NORMAL) {
                            "Tap any preset · tap again to reset"
                        } else {
                            "Active: ${activePreset.label}"
                        },
                        style = Typography.bodySmall,
                        color = SoftSilver
                    )
                }
                if (activePreset != SoundPreset.NORMAL) {
                    RezonaGlassPill(
                        isActive = false,
                        onClick = { onSelectPreset(SoundPreset.NORMAL) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "Reset",
                            style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = PureWhite
                        )
                    }
                }
            }

            // Category Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(SoundPresetCategory.entries) { cat ->
                    val isSelected = selectedCategory == cat
                    RezonaGlassPill(
                        isActive = isSelected,
                        onClick = { selectedCategory = cat },
                        modifier = Modifier.heightIn(min = 42.dp)
                    ) {
                        Text(
                            text = cat.label,
                            style = Typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) PureWhite else Color(0xCCFFFFFF)
                        )
                    }
                }
            }

            // 2-Column Grid of One-Tap Glass Tiles
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
            ) {
                items(presets, key = { it.id }) { preset ->
                    val isSelected = activePreset == preset
                    val tileShape = RoundedCornerShape(18.dp)
                    val topFill = if (isSelected) {
                        lerp(tintedGlass, PureWhite, 0.28f).copy(alpha = 0.56f)
                    } else {
                        lerp(tintedGlass, PureWhite, 0.08f).copy(alpha = 0.36f)
                    }
                    val bottomFill = if (isSelected) {
                        tintedGlass.copy(alpha = 0.44f)
                    } else {
                        tintedGlass.copy(alpha = 0.24f)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 84.dp)
                            .clip(tileShape)
                            .background(Brush.verticalGradient(listOf(topFill, bottomFill)))
                            .border(
                                width = if (isSelected) 1.3.dp else 0.85.dp,
                                brush = Brush.verticalGradient(
                                    if (isSelected) listOf(Color(0xC8FFFFFF), Color(0x38FFFFFF))
                                    else listOf(Color(0x4AFFFFFF), Color(0x12FFFFFF))
                                ),
                                shape = tileShape
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = PureWhite),
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onSelectPreset(preset)
                                }
                            )
                            .padding(horizontal = 13.dp, vertical = 12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = preset.iconRes),
                                    contentDescription = null,
                                    tint = if (isSelected) PureWhite else Color(0xD8FFFFFF),
                                    modifier = Modifier.size(18.dp)
                                )
                                if (isSelected && preset != SoundPreset.NORMAL) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(PureWhite)
                                    )
                                }
                            }
                            Text(
                                text = preset.label,
                                style = Typography.titleSmall.copy(
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                ),
                                color = PureWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = preset.description,
                                style = Typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                                color = Color(0xB8FFFFFF),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
