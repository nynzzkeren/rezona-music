package com.nayama.music.ui.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nayama.music.ui.components.LocalAmbientBaseColor
import com.nayama.music.ui.components.LocalTintedGlassColor
import com.nayama.music.ui.components.RezonaGlassPill
import com.nayama.music.ui.components.RezonaGlassSlider
import com.nayama.music.ui.components.RezonaGlassToggle
import com.nayama.music.ui.components.RezonaHairlineDivider
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.Typography
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Part 5 Lyrics Style Glass Bottom Sheet:
 * - Live preview card updating instantaneously
 * - 8 bundled open-license font families (Satoshi, Inter, Space Grotesk, Playfair Display,
 *   JetBrains Mono, Nunito, Caveat, Bebas Neue)
 * - Weight (Regular, Medium, Bold, Black), Size (S/M/L/XL + 20-40sp slider),
 *   Alignment (Left/Center), Line Spacing (Tight/Normal/Relaxed), Italic, Letter Spacing,
 *   Translation Size, and Reset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RezonaLyricsStyleSheet(
    style: LyricsStyleConfig,
    onStyleChange: (LyricsStyleConfig) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ambientBase = LocalAmbientBaseColor.current
    val tintedGlass = LocalTintedGlassColor.current
    val sheetSurface = remember(ambientBase) {
        lerp(ambientBase, Color(0xFF0A0C10), 0.28f).copy(alpha = 0.95f)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetSurface,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Lyrics Style",
                        style = Typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        color = PureWhite
                    )
                    Text(
                        text = "Applies live to Lyrics & Poster Studio",
                        style = Typography.bodySmall,
                        color = SoftSilver
                    )
                }
                RezonaGlassPill(
                    isActive = false,
                    onClick = onReset,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "Reset",
                        style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = PureWhite
                    )
                }
            }

            // Live Preview Card
            val previewFont = remember(style.fontFamily) { style.fontFamily.toComposeFontFamily() }
            val previewShape = RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(previewShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                lerp(tintedGlass, PureWhite, 0.12f).copy(alpha = 0.44f),
                                tintedGlass.copy(alpha = 0.28f)
                            )
                        )
                    )
                    .border(0.9.dp, Color(0x45FFFFFF), previewShape)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "In the quiet after midnight",
                        style = TextStyle(
                            fontFamily = previewFont,
                            fontWeight = style.weight.weight,
                            fontStyle = style.fontStyle,
                            fontSize = style.fontSizeSp.sp,
                            lineHeight = style.lineHeightSp.sp,
                            letterSpacing = style.letterSpacingEm.em
                        ),
                        color = PureWhite,
                        textAlign = style.textAlign,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Di keheningan selepas tengah malam",
                        style = TextStyle(
                            fontFamily = previewFont,
                            fontWeight = FontWeight.Normal,
                            fontStyle = style.fontStyle,
                            fontSize = style.translationSizeSp.sp,
                            lineHeight = (style.translationSizeSp * 1.3f).sp
                        ),
                        color = Color(0xA6FFFFFF),
                        textAlign = style.textAlign,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Every echo fades into warm light",
                        style = TextStyle(
                            fontFamily = previewFont,
                            fontWeight = style.weight.weight,
                            fontStyle = style.fontStyle,
                            fontSize = style.fontSizeSp.sp,
                            lineHeight = style.lineHeightSp.sp,
                            letterSpacing = style.letterSpacingEm.em
                        ),
                        color = Color(0x66FFFFFF),
                        textAlign = style.textAlign,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Font Family Selector
            StyleSectionLabel("FONT FAMILY")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(LyricsFontFamilyOption.entries, key = { it.id }) { fontOpt ->
                    val isSelected = style.fontFamily == fontOpt
                    val chipFont = remember(fontOpt) { fontOpt.toComposeFontFamily() }
                    val chipShape = RoundedCornerShape(16.dp)
                    Box(
                        modifier = Modifier
                            .heightIn(min = 56.dp)
                            .clip(chipShape)
                            .background(
                                if (isSelected) lerp(tintedGlass, PureWhite, 0.25f).copy(alpha = 0.54f)
                                else tintedGlass.copy(alpha = 0.28f)
                            )
                            .border(
                                width = if (isSelected) 1.25.dp else 0.75.dp,
                                color = if (isSelected) Color(0xBBFFFFFF) else Color(0x38FFFFFF),
                                shape = chipShape
                            )
                            .clickable { onStyleChange(style.copy(fontFamily = fontOpt)) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Column {
                            Text(
                                text = fontOpt.displayName,
                                style = TextStyle(
                                    fontFamily = chipFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = PureWhite
                            )
                            Text(
                                text = fontOpt.subtitle,
                                style = Typography.labelSmall.copy(fontSize = 10.sp),
                                color = SmokySilver
                            )
                        }
                    }
                }
            }

            // Weight
            StyleSectionLabel("WEIGHT")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                LyricsFontWeightOption.entries.forEach { w ->
                    val selected = style.weight == w
                    RezonaGlassPill(
                        isActive = selected,
                        onClick = { onStyleChange(style.copy(weight = w)) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        Text(
                            text = w.label,
                            style = Typography.labelSmall.copy(fontWeight = w.weight),
                            color = if (selected) PureWhite else SoftSilver
                        )
                    }
                }
            }

            // Size Presets (S, M, L, XL) + 20-40sp Slider
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StyleSectionLabel("TEXT SIZE")
                Text(
                    text = "${style.fontSizeSp.roundToInt()} sp",
                    style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = PureWhite
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("S" to 22f, "M" to 26f, "L" to 31f, "XL" to 36f).forEach { (label, spVal) ->
                    val selected = kotlin.math.abs(style.fontSizeSp - spVal) < 1.2f
                    RezonaGlassPill(
                        isActive = selected,
                        onClick = { onStyleChange(style.copy(fontSizeSp = spVal)) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        Text(
                            text = label,
                            style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (selected) PureWhite else SoftSilver
                        )
                    }
                }
            }
            RezonaGlassSlider(
                value = style.fontSizeSp,
                onValueChange = { onStyleChange(style.copy(fontSizeSp = it.roundToInt().toFloat())) },
                valueRange = 20f..40f,
                formatBubble = { "${it.roundToInt()}sp" }
            )

            // Alignment + Line Spacing
            StyleSectionLabel("ALIGNMENT")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                RezonaGlassPill(
                    isActive = !style.centerAlign,
                    onClick = { onStyleChange(style.copy(centerAlign = false)) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Left", style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                }
                RezonaGlassPill(
                    isActive = style.centerAlign,
                    onClick = { onStyleChange(style.copy(centerAlign = true)) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Center", style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                }
            }

            StyleSectionLabel("LINE SPACING")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                LyricsLineSpacingOption.entries.forEach { spOpt ->
                    val selected = style.lineSpacing == spOpt
                    RezonaGlassPill(
                        isActive = selected,
                        onClick = { onStyleChange(style.copy(lineSpacing = spOpt)) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        Text(
                            text = spOpt.label,
                            style = Typography.labelMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                            color = if (selected) PureWhite else SoftSilver
                        )
                    }
                }
            }

            RezonaHairlineDivider()

            // Italic Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Italic lyrics", style = Typography.bodyMedium, color = PearlWhite)
                RezonaGlassToggle(
                    checked = style.isItalic,
                    onCheckedChange = { onStyleChange(style.copy(isItalic = it)) }
                )
            }

            // Letter Spacing Slider
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Letter spacing", style = Typography.bodyMedium, color = PearlWhite)
                Text(
                    text = String.format(Locale.ENGLISH, "%+.2f em", style.letterSpacingEm),
                    style = Typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = PureWhite
                )
            }
            RezonaGlassSlider(
                value = style.letterSpacingEm,
                onValueChange = { onStyleChange(style.copy(letterSpacingEm = (it * 100f).roundToInt() / 100f)) },
                valueRange = -0.04f..0.10f,
                formatBubble = { String.format(Locale.ENGLISH, "%+.2f", it) }
            )

            // Translation Line Size Slider
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Translation line size", style = Typography.bodyMedium, color = PearlWhite)
                Text(
                    text = "${style.translationSizeSp.roundToInt()} sp",
                    style = Typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = PureWhite
                )
            }
            RezonaGlassSlider(
                value = style.translationSizeSp,
                onValueChange = { onStyleChange(style.copy(translationSizeSp = it.roundToInt().toFloat())) },
                valueRange = 11f..20f,
                formatBubble = { "${it.roundToInt()}sp" }
            )
        }
    }
}

@Composable
private fun StyleSectionLabel(text: String) {
    Text(
        text = text,
        style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
        color = SoftSilver
    )
}
