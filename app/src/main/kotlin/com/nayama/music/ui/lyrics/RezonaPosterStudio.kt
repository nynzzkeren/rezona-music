package com.nayama.music.ui.lyrics

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nayama.music.data.preferences.RezonaPreferences
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.model.RezonaTrack
import com.nayama.music.ui.components.RezonaGlassCard
import com.nayama.music.ui.components.RezonaGlassPill
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.SpecularCardBorder
import com.nayama.music.ui.theme.SpecularDockBorder
import com.nayama.music.ui.theme.Typography

/**
 * Full-Screen Poster Studio Sheet
 * Live 1080px Canvas rendering with full customization:
 * - Line selection (max 8)
 * - Background: Glass / Solid / Blur / Gradient
 * - Format: 1:1 Square / 9:16 Story
 * - Font: Editorial / Grotesk / Rounded / Serif (Bold / Regular)
 * - Alignment & Toggles (Cover thumbnail, Watermark)
 * - Save to gallery & Native Share via FileProvider
 */
@Composable
fun RezonaPosterStudio(
    track: RezonaTrack,
    allLines: List<String>,
    initialSelectedLines: List<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { RezonaPreferences(context) }
    val savedLyricsStyle by preferences.lyricsStyleFlow.collectAsState(initial = LyricsStyleConfig())

    val selectedLines = remember {
        mutableStateListOf<String>().apply {
            if (initialSelectedLines.isNotEmpty()) {
                addAll(initialSelectedLines.take(8))
            } else {
                addAll(allLines.take(3))
            }
        }
    }

    var format by remember { mutableStateOf(PosterFormat.SQUARE) }
    var background by remember { mutableStateOf(PosterBackground.GLASS) }
    var font by remember { mutableStateOf(PosterFont.EDITORIAL) }
    var useSyncedLyricsFont by remember { mutableStateOf(true) }
    var isBold by remember(savedLyricsStyle.weight) {
        mutableStateOf(savedLyricsStyle.weight != LyricsFontWeightOption.REGULAR)
    }
    var isCentered by remember(savedLyricsStyle.centerAlign) {
        mutableStateOf(savedLyricsStyle.centerAlign)
    }
    var showThumbnail by remember { mutableStateOf(true) }
    var showWatermark by remember { mutableStateOf(true) }

    // Live Rendered Bitmap Preview (inherits Part 5 Lyrics Style bundled font!)
    val previewBitmap: Bitmap? = remember(
        selectedLines.toList(),
        format,
        background,
        font,
        useSyncedLyricsFont,
        savedLyricsStyle,
        isBold,
        isCentered,
        showThumbnail,
        showWatermark
    ) {
        if (selectedLines.isEmpty()) null
        else PosterRenderer.renderPosterBitmap(
            context = context,
            lines = selectedLines,
            trackTitle = track.title,
            trackArtist = track.artist,
            coverIndex = track.index,
            format = format,
            background = background,
            font = font,
            isBold = isBold,
            isCentered = isCentered,
            showThumbnail = showThumbnail,
            showWatermark = showWatermark,
            customLyricsStyle = if (useSyncedLyricsFont) savedLyricsStyle else null
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08090B))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "POSTER STUDIO",
                    style = Typography.titleLarge.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = PearlWhite
                )

                // Close Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x331C1F26))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close",
                        tint = SoftSilver,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                // ── 1. LIVE PREVIEW CANVAS ──────────────────────────────────────
                item(key = "preview_canvas") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewBitmap != null) {
                            val ratio = if (format == PosterFormat.SQUARE) 1f else (9f / 16f)
                            Box(
                                modifier = Modifier
                                    .width(if (format == PosterFormat.SQUARE) 260.dp else 190.dp)
                                    .aspectRatio(ratio)
                                    .shadow(24.dp, RoundedCornerShape(16.dp), spotColor = Color(0x99000000))
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, SpecularDockBorder, RoundedCornerShape(16.dp))
                            ) {
                                Image(
                                    bitmap = previewBitmap.asImageBitmap(),
                                    contentDescription = "Poster Preview",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // ── 2. LINE SELECTION (CHIP ROW) ────────────────────────────────
                item(key = "line_selection") {
                    Column {
                        Text(
                            text = "SELECT LINES (${selectedLines.size}/8)",
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = SmokySilver
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            allLines.take(12).forEach { line ->
                                val isSelected = selectedLines.contains(line)
                                RezonaGlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    onClick = {
                                        if (isSelected) {
                                            selectedLines.remove(line)
                                        } else if (selectedLines.size < 8) {
                                            selectedLines.add(line)
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) PureWhite else Color(0x26FFFFFF)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_check),
                                                    contentDescription = null,
                                                    tint = Color(0xFF08090B),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = line,
                                            style = Typography.bodyMedium.copy(fontSize = 13.sp),
                                            color = if (isSelected) PureWhite else SoftSilver,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 3. FORMAT SELECTOR (SQUARE / STORY) ──────────────────────────
                item(key = "format_selector") {
                    Column {
                        Text(text = "FORMAT", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = SmokySilver)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PosterFormat.values().forEach { fmt ->
                                val active = format == fmt
                                RezonaGlassPill(
                                    isActive = active,
                                    onClick = { format = fmt },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = fmt.label,
                                        style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (active) PureWhite else SoftSilver
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 4. BACKGROUND SELECTOR ──────────────────────────────────────
                item(key = "bg_selector") {
                    Column {
                        Text(text = "BACKGROUND", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = SmokySilver)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(PosterBackground.values()) { bg ->
                                val active = background == bg
                                RezonaGlassPill(
                                    isActive = active,
                                    onClick = { background = bg }
                                ) {
                                    Text(
                                        text = bg.label,
                                        style = Typography.labelMedium,
                                        color = if (active) PureWhite else SoftSilver
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 5. FONT SELECTOR & WEIGHT ───────────────────────────────────
                item(key = "font_selector") {
                    Column {
                        Text(text = "TYPOGRAPHY", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = SmokySilver)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item(key = "poster_lyrics_font_sync") {
                                RezonaGlassPill(
                                    isActive = useSyncedLyricsFont,
                                    onClick = { useSyncedLyricsFont = true }
                                ) {
                                    Text(
                                        text = savedLyricsStyle.fontFamily.displayName,
                                        style = Typography.labelMedium,
                                        color = if (useSyncedLyricsFont) PureWhite else SoftSilver
                                    )
                                }
                            }
                            items(PosterFont.values()) { f ->
                                val active = !useSyncedLyricsFont && font == f
                                RezonaGlassPill(
                                    isActive = active,
                                    onClick = {
                                        useSyncedLyricsFont = false
                                        font = f
                                    }
                                ) {
                                    Text(
                                        text = f.label,
                                        style = Typography.labelMedium,
                                        color = if (active) PureWhite else SoftSilver
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Controls: Bold / Alignment / Toggles
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RezonaGlassPill(
                                isActive = isBold,
                                onClick = { isBold = !isBold },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = if (isBold) "Bold" else "Regular", style = Typography.labelSmall, color = PureWhite)
                            }
                            RezonaGlassPill(
                                isActive = isCentered,
                                onClick = { isCentered = !isCentered },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = if (isCentered) "Center" else "Left", style = Typography.labelSmall, color = PureWhite)
                            }
                            RezonaGlassPill(
                                isActive = showThumbnail,
                                onClick = { showThumbnail = !showThumbnail },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = "Thumb", style = Typography.labelSmall, color = if (showThumbnail) PureWhite else SmokySilver)
                            }
                        }
                    }
                }
            }

            // ── ACTION BUTTONS ROW (SAVE & SHARE) ────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Save to Gallery
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0x331C1F26))
                        .border(1.dp, SpecularCardBorder, RoundedCornerShape(26.dp))
                        .clickable {
                            if (previewBitmap != null) {
                                val saved = PosterRenderer.saveToGallery(context, previewBitmap)
                                Toast.makeText(context, if (saved) "Saved to Pictures/Rezona" else "Failed to save", Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Save to Gallery",
                        style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                        color = PearlWhite
                    )
                }

                // Share Button (ACTION_SEND via FileProvider)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(PureWhite)
                        .border(1.dp, Brush.verticalGradient(listOf(Color.White, Color(0x66FFFFFF))), RoundedCornerShape(26.dp))
                        .clickable {
                            if (previewBitmap != null) {
                                PosterRenderer.sharePoster(context, previewBitmap)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Share Poster",
                        style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                        color = Color(0xFF08090B)
                    )
                }
            }
        }
    }
}
