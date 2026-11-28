package com.nayama.music.ui.lyrics

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.nayama.music.data.preferences.RezonaPreferences
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Full-Screen Lyrics Video & iOS Player Card Studio Sheet (Upgraded V3):
 * - 3 Style Modes matching Video 1: `Fokus` | `Lirik` | `Kartu Pemutar (iOS Player Card)`
 * - 4 Aspect Ratios: `1:1` | `4:5` | `9:16` | `16:9`
 * - Timing Offset adjustments: `-0.5 dtk` | `+0.5 dtk`
 * - Live Animated Karaoke Preview toggle + H.264 `.mp4` Video Export (`Buat Video MP4`)
 * - Quick Share chips (`Status WA`, `WA Bisnis`, `Instagram Story`, `Bagikan Gambar`)
 */
@Composable
fun RezonaPosterStudio(
    track: RezonaTrack,
    allLines: List<String>,
    initialSelectedLines: List<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    var styleMode by remember { mutableStateOf(PosterStyleMode.PLAYER_CARD) }
    var format by remember { mutableStateOf(PosterFormat.PORTRAIT_4_5) }
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
    var timingOffsetSec by remember { mutableFloatStateOf(0f) }
    var liveKaraokePreview by remember { mutableStateOf(true) }
    var isExportingVideo by remember { mutableStateOf(false) }
    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(track.id, track.coverArtUrl) {
        coverBitmap = withContext(Dispatchers.IO) {
            PosterRenderer.loadTrackCoverBitmap(context, track)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "lyrics_video_preview")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "karaoke_progress"
    )
    val effectiveProgress = if (liveKaraokePreview) (animProgress * 20).toInt() / 20f else 0.42f

    val previewBitmap: Bitmap? = remember(
        selectedLines.toList(),
        styleMode,
        format,
        background,
        font,
        useSyncedLyricsFont,
        savedLyricsStyle,
        isBold,
        isCentered,
        showThumbnail,
        showWatermark,
        coverBitmap,
        effectiveProgress,
        timingOffsetSec
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
            customLyricsStyle = if (useSyncedLyricsFont) savedLyricsStyle else null,
            styleMode = styleMode,
            coverArtBitmap = coverBitmap,
            animProgress = effectiveProgress,
            timingOffsetSec = timingOffsetSec
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08090B))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "STUDIO VIDEO LIRIK & KARTU iOS",
                        style = Typography.titleLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        ),
                        color = PearlWhite
                    )
                    Text(
                        text = "${track.title} · ${selectedLines.size} baris dipilih",
                        style = Typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = SoftSilver
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RezonaGlassPill(
                        isActive = liveKaraokePreview,
                        onClick = { liveKaraokePreview = !liveKaraokePreview }
                    ) {
                        Text(
                            text = if (liveKaraokePreview) "▶ Live: ON" else "⏸ Live: OFF",
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = PureWhite
                        )
                    }
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
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // ── 1. LIVE PREVIEW CANVAS ──────────────────────────────────────
                item(key = "preview_canvas") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewBitmap != null) {
                            val ratio = format.width.toFloat() / format.height.toFloat()
                            val previewWidthDp = when (format) {
                                PosterFormat.SQUARE -> 250.dp
                                PosterFormat.PORTRAIT_4_5 -> 225.dp
                                PosterFormat.STORY -> 185.dp
                                PosterFormat.LANDSCAPE_16_9 -> 300.dp
                            }
                            Box(
                                modifier = Modifier
                                    .width(previewWidthDp)
                                    .aspectRatio(ratio)
                                    .shadow(24.dp, RoundedCornerShape(18.dp), spotColor = Color(0x99000000))
                                    .clip(RoundedCornerShape(18.dp))
                                    .border(1.dp, SpecularDockBorder, RoundedCornerShape(18.dp))
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

                // ── 2. GAYA (STYLE MODE: FOKUS | LIRIK | KARTU PEMUTAR) ─────────
                item(key = "style_mode_selector") {
                    Column {
                        Text(
                            text = "GAYA TAMPILAN",
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = SmokySilver
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PosterStyleMode.values().forEach { mode ->
                                val active = styleMode == mode
                                RezonaGlassPill(
                                    isActive = active,
                                    onClick = { styleMode = mode },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = mode.label,
                                        style = Typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = if (active) PureWhite else SoftSilver
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 3. RASIO (1:1 | 4:5 | 9:16 | 16:9) & TIMING (-0.5s / +0.5s) ─
                item(key = "format_and_timing") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RASIO & TIMING LIRIK",
                                style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = SmokySilver
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RezonaGlassPill(
                                    isActive = false,
                                    onClick = { timingOffsetSec = (timingOffsetSec - 0.5f).coerceIn(-5f, 5f) }
                                ) {
                                    Text("-0.5 dtk", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                                }
                                RezonaGlassPill(
                                    isActive = kotlin.math.abs(timingOffsetSec) >= 0.05f,
                                    onClick = { timingOffsetSec = 0f }
                                ) {
                                    Text(
                                        text = String.format("%+.1fs", timingOffsetSec),
                                        style = Typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = Color(0xFF70D6FF)
                                    )
                                }
                                RezonaGlassPill(
                                    isActive = false,
                                    onClick = { timingOffsetSec = (timingOffsetSec + 0.5f).coerceIn(-5f, 5f) }
                                ) {
                                    Text("+0.5 dtk", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PosterFormat.values().forEach { fmt ->
                                val active = format == fmt
                                RezonaGlassPill(
                                    isActive = active,
                                    onClick = { format = fmt },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = fmt.label,
                                        style = Typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = if (active) PureWhite else SoftSilver
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 4. LATAR (KACA | POLOS | BURAM | GRADASI) ───────────────────
                item(key = "bg_selector") {
                    Column {
                        Text(text = "LATAR BELAKANG", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = SmokySilver)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PosterBackground.values().forEach { bg ->
                                val active = background == bg
                                RezonaGlassPill(
                                    isActive = active,
                                    onClick = { background = bg },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = bg.label,
                                        style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (active) PureWhite else SoftSilver
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 5. LINE SELECTION (PILIH BARIS LIRIK) ───────────────────────
                item(key = "line_selection") {
                    Column {
                        Text(
                            text = "PILIH BARIS LIRIK (${selectedLines.size}/8)",
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = SmokySilver
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            allLines.take(14).forEach { line ->
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
                                            .padding(horizontal = 14.dp, vertical = 9.dp),
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

                // ── 6. FONT SELECTOR & QUICK SHARE TARGETS (WA STATUS / IG) ─────
                item(key = "font_and_quick_share") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = "TIPOGRAFI & BAGIKAN CEPAT", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = SmokySilver)
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

                        // Quick Share Chips: Status WA | Status WA Bisnis | Instagram Story
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RezonaGlassPill(
                                isActive = false,
                                onClick = {
                                    if (previewBitmap != null) {
                                        PosterRenderer.sharePoster(context, previewBitmap, "com.whatsapp")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Status WA", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                            }
                            RezonaGlassPill(
                                isActive = false,
                                onClick = {
                                    if (previewBitmap != null) {
                                        PosterRenderer.sharePoster(context, previewBitmap, "com.whatsapp.w4b")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("WA Bisnis", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                            }
                            RezonaGlassPill(
                                isActive = false,
                                onClick = {
                                    if (previewBitmap != null) {
                                        PosterRenderer.sharePoster(context, previewBitmap, "com.instagram.android")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Instagram", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                            }
                        }
                    }
                }
            }

            // ── BOTTOM ACTION BUTTONS: SIMPAN GAMBAR | BAGIKAN GAMBAR | BUAT VIDEO MP4 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Save PNG
                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(Color(0x331C1F26))
                        .border(1.dp, SpecularCardBorder, RoundedCornerShape(25.dp))
                        .clickable {
                            if (previewBitmap != null) {
                                val saved = PosterRenderer.saveToGallery(context, previewBitmap)
                                Toast.makeText(context, if (saved) "Disimpan ke Pictures/Rezona" else "Gagal menyimpan", Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Simpan PNG",
                        style = Typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                        color = PearlWhite
                    )
                }

                // Share PNG
                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(Color(0x28FFFFFF))
                        .border(1.dp, SpecularDockBorder, RoundedCornerShape(25.dp))
                        .clickable {
                            if (previewBitmap != null) {
                                PosterRenderer.sharePoster(context, previewBitmap)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Share Gambar",
                        style = Typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                        color = PureWhite
                    )
                }

                // Export Animated MP4 Video ("Buat Video")
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(PureWhite)
                        .border(1.dp, Brush.verticalGradient(listOf(Color.White, Color(0x66FFFFFF))), RoundedCornerShape(25.dp))
                        .clickable(enabled = !isExportingVideo) {
                            if (selectedLines.isNotEmpty()) {
                                isExportingVideo = true
                                Toast.makeText(context, "Merender Video Lirik MP4...", Toast.LENGTH_SHORT).show()
                                val linesCopy = selectedLines.toList()
                                scope.launch {
                                    val videoFile = withContext(Dispatchers.Default) {
                                        PosterRenderer.exportLyricsVideoMp4(
                                            context = context,
                                            lines = linesCopy,
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
                                            customLyricsStyle = if (useSyncedLyricsFont) savedLyricsStyle else null,
                                            styleMode = styleMode,
                                            coverArtBitmap = coverBitmap,
                                            timingOffsetSec = timingOffsetSec
                                        )
                                    }
                                    isExportingVideo = false
                                    if (videoFile != null && videoFile.exists()) {
                                        Toast.makeText(context, "Video MP4 disimpan ke Movies/Rezona!", Toast.LENGTH_SHORT).show()
                                        PosterRenderer.shareVideoFile(context, videoFile)
                                    } else {
                                        Toast.makeText(context, "Gagal membuat video MP4", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isExportingVideo) "Merender..." else "Buat Video MP4",
                        style = Typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 13.sp),
                        color = Color(0xFF08090B)
                    )
                }
            }
        }
    }
}
