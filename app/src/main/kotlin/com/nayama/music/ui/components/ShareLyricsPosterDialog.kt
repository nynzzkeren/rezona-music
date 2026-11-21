package com.nayama.music.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nayama.music.R
import com.nayama.music.model.Lyrics
import com.nayama.music.model.Track
import com.nayama.music.ui.theme.CoralAccent
import com.nayama.music.ui.theme.DeepCharcoal
import com.nayama.music.ui.theme.GlassBorder
import com.nayama.music.ui.theme.GraphiteSurface
import com.nayama.music.ui.theme.ObsidianBlack
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver

enum class PosterBackgroundStyle { GLASS, SOLID, BLUR, GRADIENT }
enum class PosterFormat { SQUARE, STORY }
enum class PosterFontFamily { EDITORIAL, GROTESK, RUBIK, SERIF }
enum class PosterFontWeight { REGULAR, MEDIUM, BOLD, BLACK }
enum class PosterTextAlign { LEFT, CENTER, RIGHT }

/**
 * "Lirik jadi poster" — Kartu bagikan lagu / lirik sesuai video referensi.
 * Menghasilkan poster estetik dengan 4 gaya latar, format Square/Story,
 * pilihan font tipografi, ketebalan, dan perataan teks.
 */
@Composable
fun ShareLyricsPosterDialog(
    track: Track,
    lyrics: Lyrics?,
    initialLyricSnippet: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var bgStyle by remember { mutableStateOf(PosterBackgroundStyle.GLASS) }
    var format by remember { mutableStateOf(PosterFormat.SQUARE) }
    var fontChoice by remember { mutableStateOf(PosterFontFamily.EDITORIAL) }
    var fontWeightChoice by remember { mutableStateOf(PosterFontWeight.BOLD) }
    var textAlignChoice by remember { mutableStateOf(PosterTextAlign.CENTER) }

    // Lyric text to display
    val defaultText = remember(lyrics, track) {
        val snippet = initialLyricSnippet?.ifBlank { null }
            ?: lyrics?.lines?.take(4)?.joinToString("\n") { it.text }?.ifBlank { null }
            ?: "They'll kick you,\nthen they'll beat you,\nthen they'll tell you it's fair"
        snippet
    }
    var lyricsText by remember { mutableStateOf(defaultText) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(28.dp))
                .background(DeepCharcoal)
                .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Share lyrics",
                        color = PearlWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(GraphiteSurface)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Tutup",
                            tint = SoftSilver,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── POSTER PREVIEW CARD ────────────────────────────────────
                val aspectRatio = if (format == PosterFormat.SQUARE) 1f else (9f / 16f)
                val cardFontFamily = when (fontChoice) {
                    PosterFontFamily.EDITORIAL -> FontFamily.SansSerif
                    PosterFontFamily.GROTESK   -> FontFamily.SansSerif
                    PosterFontFamily.RUBIK     -> FontFamily.Default
                    PosterFontFamily.SERIF     -> FontFamily.Serif
                }
                val cardFontWeight = when (fontWeightChoice) {
                    PosterFontWeight.REGULAR -> FontWeight.Normal
                    PosterFontWeight.MEDIUM  -> FontWeight.Medium
                    PosterFontWeight.BOLD    -> FontWeight.Bold
                    PosterFontWeight.BLACK   -> FontWeight.Black
                }
                val cardTextAlign = when (textAlignChoice) {
                    PosterTextAlign.LEFT   -> TextAlign.Start
                    PosterTextAlign.CENTER -> TextAlign.Center
                    PosterTextAlign.RIGHT  -> TextAlign.End
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (format == PosterFormat.SQUARE) 0.95f else 0.72f)
                        .aspectRatio(aspectRatio)
                        .clip(RoundedCornerShape(22.dp))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Background treatment based on bgStyle
                    when (bgStyle) {
                        PosterBackgroundStyle.GLASS -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF232832), Color(0xFF101217))
                                        )
                                    )
                            )
                        }
                        PosterBackgroundStyle.SOLID -> {
                            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F1115)))
                        }
                        PosterBackgroundStyle.BLUR -> {
                            NayamaImage(
                                url = track.artworkUrl,
                                contentDescription = track.title,
                                modifier = Modifier.fillMaxSize().blur(32.dp),
                                shape = RoundedCornerShape(22.dp),
                                fallbackText = track.title,
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xB3000000))
                            )
                        }
                        PosterBackgroundStyle.GRADIENT -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            listOf(CoralAccent.copy(alpha = 0.45f), Color(0xFF0A0C10))
                                        )
                                    )
                            )
                        }
                    }

                    // Content inside Poster
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = when (textAlignChoice) {
                            PosterTextAlign.LEFT   -> Alignment.Start
                            PosterTextAlign.CENTER -> Alignment.CenterHorizontally
                            PosterTextAlign.RIGHT  -> Alignment.End
                        }
                    ) {
                        // Top Song Info
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            NayamaImage(
                                url = track.artworkUrl,
                                contentDescription = track.title,
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                                shape = RoundedCornerShape(8.dp),
                                fallbackText = track.title
                            )
                            Column {
                                Text(
                                    text = track.title,
                                    color = PearlWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = track.artist,
                                    color = SmokySilver,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        // Center Lyrics text
                        Text(
                            text = lyricsText,
                            color = PearlWhite,
                            fontSize = if (format == PosterFormat.SQUARE) 18.sp else 16.sp,
                            fontWeight = cardFontWeight,
                            fontFamily = cardFontFamily,
                            textAlign = cardTextAlign,
                            lineHeight = 26.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        )

                        // Bottom O - Reverie Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(CoralAccent))
                            Text(
                                text = "O  —  R E V E R I E",
                                color = SoftSilver,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── CONTROLS & OPTIONS ─────────────────────────────────────

                // 1. Gaya Latar (Background Style)
                OptionSectionTitle(title = "Background (4 gaya latar)")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    items(PosterBackgroundStyle.values()) { style ->
                        SelectablePill(
                            label = style.name.lowercase().replaceFirstChar { it.uppercase() },
                            isSelected = bgStyle == style,
                            onClick = { bgStyle = style }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Format (Square / Story 9:16)
                OptionSectionTitle(title = "Format")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectablePill(
                        label = "Square",
                        isSelected = format == PosterFormat.SQUARE,
                        onClick = { format = PosterFormat.SQUARE },
                        modifier = Modifier.weight(1f)
                    )
                    SelectablePill(
                        label = "Story 9:16",
                        isSelected = format == PosterFormat.STORY,
                        onClick = { format = PosterFormat.STORY },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Pilihan Font
                OptionSectionTitle(title = "Font")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    items(PosterFontFamily.values()) { font ->
                        SelectablePill(
                            label = font.name.lowercase().replaceFirstChar { it.uppercase() },
                            isSelected = fontChoice == font,
                            onClick = { fontChoice = font }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Ketebalan (Weight)
                OptionSectionTitle(title = "Ketebalan")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    items(PosterFontWeight.values()) { w ->
                        SelectablePill(
                            label = w.name.lowercase().replaceFirstChar { it.uppercase() },
                            isSelected = fontWeightChoice == w,
                            onClick = { fontWeightChoice = w }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 5. Perataan Teks (Alignment)
                OptionSectionTitle(title = "Text alignment")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsPosterTextAlign().forEach { align ->
                        SelectablePill(
                            label = align.name.lowercase().replaceFirstChar { it.uppercase() },
                            isSelected = textAlignChoice == align,
                            onClick = { textAlignChoice = align },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── ACTION BUTTON: BAGIKAN POSTER ─────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(CoralAccent)
                        .clickable {
                            shareLyricsSnippet(context, track, lyricsText)
                        }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Bagikan Poster Lirik",
                        color = PearlWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

private fun itemsPosterTextAlign(): List<PosterTextAlign> = listOf(
    PosterTextAlign.LEFT,
    PosterTextAlign.CENTER,
    PosterTextAlign.RIGHT
)

@Composable
private fun OptionSectionTitle(title: String) {
    Text(
        text = title,
        color = SmokySilver,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
    )
}

@Composable
private fun SelectablePill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) CoralAccent.copy(alpha = 0.22f) else GraphiteSurface,
        animationSpec = tween(150),
        label = "pill_bg"
    )
    val border by animateColorAsState(
        targetValue = if (isSelected) CoralAccent else GlassBorder,
        animationSpec = tween(150),
        label = "pill_border"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) PearlWhite else SoftSilver,
        animationSpec = tween(150),
        label = "pill_text"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

private fun shareLyricsSnippet(context: Context, track: Track, lyricsText: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_SUBJECT,
            "Lirik: ${track.title} - ${track.artist}"
        )
        putExtra(
            Intent.EXTRA_TEXT,
            "\"$lyricsText\"\n\n— ${track.title} oleh ${track.artist}\nDiputar di O - Reverie Music"
        )
    }
    context.startActivity(Intent.createChooser(shareIntent, "Bagikan Poster Lirik"))
}
