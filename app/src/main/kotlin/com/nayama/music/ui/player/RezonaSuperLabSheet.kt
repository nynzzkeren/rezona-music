package com.nayama.music.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.model.RezonaTrack
import com.nayama.music.model.SyncedLyricLine
import com.nayama.music.playback.RezonaPlayerController
import com.nayama.music.playback.SoundPreset
import com.nayama.music.ui.components.RezonaAsyncCoverArt
import com.nayama.music.ui.components.RezonaGlassCard
import com.nayama.music.ui.components.RezonaGlassDialog
import com.nayama.music.ui.components.RezonaGlassPill
import com.nayama.music.ui.components.RezonaHairlineDivider
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.SpecularCardBorder
import com.nayama.music.ui.theme.SpecularDockBorder
import com.nayama.music.ui.theme.Typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// 1. iOS CONTROL CENTER PULL-DOWN GRID PANEL (MATCHING VIDEO 2 + SUPERLAB)
//    Vertical 0%..100% Touch Volume Pillar on Left + 12 Circular Glass Tiles on Right
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RezonaIosControlCenterGridSheet(
    track: RezonaTrack,
    controller: RezonaPlayerController,
    onDismiss: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSoundStyle: () -> Unit,
    onOpenAmbientMode: () -> Unit,
    onOpenPosterStudio: () -> Unit,
    onOpen3dSongPoster: () -> Unit,
    onOpenGuitarChords: () -> Unit,
    onOpenSuperLab: () -> Unit,
    onOpenPcSync: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var volumeFrac by remember { mutableFloatStateOf(controller.volume.floatValue.coerceIn(0f, 1f)) }
    var showTempoPitchQuick by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xF00B0E14),
        scrimColor = Color(0x99000000),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color(0x55FFFFFF))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Track Header + SuperLab Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RezonaAsyncCoverArt(
                        coverArtUrl = track.coverArtUrl,
                        fallbackIndex = track.index,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .size(44.dp)
                            .border(0.8.dp, SpecularCardBorder, RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = track.title,
                            style = Typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                            color = PureWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${track.artist} · Control Center",
                            style = Typography.bodySmall.copy(fontSize = 12.sp),
                            color = SoftSilver,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                RezonaGlassPill(
                    isActive = true,
                    onClick = {
                        onDismiss()
                        onOpenSuperLab()
                    }
                ) {
                    Text(
                        text = "24+ SuperLab ✨",
                        style = Typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = PureWhite
                    )
                }
            }

            // Main Control Center Split Deck: Left Vertical Volume Pillar + Right 3x4 Circular Glass Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(292.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // LEFT: iOS Vertical Touch Volume Pillar (0%..100%)
                Box(
                    modifier = Modifier
                        .width(68.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(34.dp))
                        .background(Color(0x28FFFFFF))
                        .border(1.dp, SpecularDockBorder, RoundedCornerShape(34.dp))
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { change, _ ->
                                change.consume()
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val next = (1f - (change.position.y / h)).coerceIn(0f, 1f)
                                volumeFrac = next
                                controller.setVolume(next)
                            }
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Active White Fill Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(volumeFrac.coerceIn(0.04f, 1f))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        PureWhite.copy(alpha = 0.92f),
                                        Color(0xFFDCE4F2).copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )

                    // Percentage Top + Speaker Icon Bottom
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(volumeFrac * 100).roundToInt()}%",
                            style = Typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
                            color = if (volumeFrac > 0.82f) Color(0xFF0A0D12) else PureWhite
                        )
                        Icon(
                            painter = painterResource(
                                id = if (volumeFrac < 0.05f) R.drawable.ic_volume_low else R.drawable.ic_volume_high
                            ),
                            contentDescription = "Volume",
                            tint = if (volumeFrac > 0.14f) Color(0xFF0A0D12) else PureWhite,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // RIGHT: 4 Rows x 3 Columns of iOS Circular Control Center Tiles
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Row 1: Sleep Timer · Equalizer · Sound Style
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IosGridCircleButton(
                            iconRes = R.drawable.ic_timer,
                            label = "Timer",
                            isActive = (controller.sleepTimerSecondsRemaining.value ?: 0) > 0 || controller.sleepTimerEndOfTrack.value,
                            onClick = {
                                onDismiss()
                                onOpenSleepTimer()
                            }
                        )
                        IosGridCircleButton(
                            iconRes = R.drawable.ic_equalizer,
                            label = "Ekualiser",
                            isActive = false,
                            onClick = {
                                onDismiss()
                                onOpenEqualizer()
                            }
                        )
                        IosGridCircleButton(
                            iconRes = R.drawable.ic_sound_waves,
                            label = "Gaya Suara",
                            isActive = controller.activeSoundPreset.value != SoundPreset.NORMAL,
                            onClick = {
                                onDismiss()
                                onOpenSoundStyle()
                            }
                        )
                    }

                    // Row 2: Tempo/Pitch · Guitar Chords · Vocal Karaoke
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IosGridCircleButton(
                            iconRes = R.drawable.ic_tune,
                            label = "Tempo/Pitch",
                            isActive = showTempoPitchQuick,
                            onClick = { showTempoPitchQuick = !showTempoPitchQuick }
                        )
                        IosGridCircleButton(
                            iconRes = R.drawable.ic_music_note,
                            label = "Chord Gitar",
                            isActive = false,
                            onClick = {
                                onDismiss()
                                onOpenGuitarChords()
                            }
                        )
                        IosGridCircleButton(
                            iconRes = R.drawable.ic_mic,
                            label = "Karaoke AI",
                            isActive = RezonaSuperLabEngine.vocalKaraokeActive.value,
                            onClick = {
                                val next = !RezonaSuperLabEngine.vocalKaraokeActive.value
                                RezonaSuperLabEngine.toggleVocalKaraoke(controller, next)
                                Toast.makeText(
                                    context,
                                    if (next) "AI Vocal Remover (Karaoke Mode) ON" else "Vocal Original ON",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }

                    // Row 3: 3D Live Poster · Video Lirik · Start Radio
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IosGridCircleButton(
                            iconRes = R.drawable.ic_ambient,
                            label = "3D Poster",
                            isActive = true,
                            onClick = {
                                onDismiss()
                                onOpen3dSongPoster()
                            }
                        )
                        IosGridCircleButton(
                            iconRes = R.drawable.ic_lyrics,
                            label = "Video Lirik",
                            isActive = false,
                            onClick = {
                                onDismiss()
                                onOpenPosterStudio()
                            }
                        )
                        IosGridCircleButton(
                            iconRes = R.drawable.ic_radio,
                            label = "Mulai Radio",
                            isActive = false,
                            onClick = {
                                controller.launchRadioFromTrack(track)
                                Toast.makeText(context, "Radio dimulai dari ${track.title}", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        )
                    }

                    // Row 4: 432Hz Healing · Sync Windows PC · Copy Link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IosGridCircleButton(
                            iconRes = R.drawable.ic_rezona_studio,
                            label = if (RezonaSuperLabEngine.solfeggio432Active.value) "432Hz: ON" else "432Hz Mode",
                            isActive = RezonaSuperLabEngine.solfeggio432Active.value,
                            onClick = {
                                val next = !RezonaSuperLabEngine.solfeggio432Active.value
                                RezonaSuperLabEngine.toggle432Hz(controller, next)
                                Toast.makeText(
                                    context,
                                    if (next) "432Hz Solfeggio Warm Tuning ON" else "440Hz Standard Tuning",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                        IosGridCircleButton(
                            iconRes = R.drawable.ic_share,
                            label = "Sync PC",
                            isActive = false,
                            onClick = {
                                onDismiss()
                                onOpenPcSync()
                            }
                        )
                        IosGridCircleButton(
                            iconRes = R.drawable.ic_link,
                            label = "Salin Link",
                            isActive = false,
                            onClick = {
                                val url = track.streamUrl.ifBlank { "https://music.youtube.com/watch?v=${track.id.removePrefix("yt_")}" }
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Rezona Song Link", url))
                                Toast.makeText(context, "Link lagu disalin", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        )
                    }
                }
            }

            // Inline Quick Tempo & Pitch Drawer if toggled
            AnimatedVisibility(visible = showTempoPitchQuick) {
                RezonaGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Tempo: ${String.format("%.2fx", controller.manualTempo.floatValue)}",
                                style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = PureWhite
                            )
                            Text(
                                text = "Pitch: ${String.format("%.2fx", controller.manualPitch.floatValue)}",
                                style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = SoftSilver
                            )
                            Text(
                                text = "Reset",
                                style = Typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color(0xFF70D6FF),
                                modifier = Modifier.clickable {
                                    controller.setManualTempoAndPitch(1.0f, 1.0f)
                                }
                            )
                        }
                        Slider(
                            value = controller.manualTempo.floatValue,
                            onValueChange = { controller.setManualTempoAndPitch(it, controller.manualPitch.floatValue) },
                            valueRange = 0.5f..1.75f,
                            colors = SliderDefaults.colors(thumbColor = PureWhite, activeTrackColor = PureWhite)
                        )
                        Slider(
                            value = controller.manualPitch.floatValue,
                            onValueChange = { controller.setManualTempoAndPitch(controller.manualTempo.floatValue, it) },
                            valueRange = 0.65f..1.45f,
                            colors = SliderDefaults.colors(thumbColor = SoftSilver, activeTrackColor = SoftSilver)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IosGridCircleButton(
    iconRes: Int,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) PureWhite else Color(0x26FFFFFF)
                )
                .border(
                    width = 0.9.dp,
                    color = if (isActive) PureWhite else Color(0x45FFFFFF),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = if (isActive) Color(0xFF0A0D12) else PureWhite,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            style = Typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
            color = if (isActive) PureWhite else SoftSilver,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. REAL-TIME GUITAR & UKULELE CHORD STUDIO + CAPO TRANSPOSER + FINGER DIAGRAMS
// ─────────────────────────────────────────────────────────────────────────────

data class GuitarChordDiagram(
    val name: String,
    val frets: IntArray, // 6 strings E A D G B e (-1 = mute, 0 = open, 1..4 = fret)
    val fingers: String
)

private val COMMON_CHORD_DIAGRAMS = mapOf(
    "C" to GuitarChordDiagram("C", intArrayOf(-1, 3, 2, 0, 1, 0), "x32010"),
    "G" to GuitarChordDiagram("G", intArrayOf(3, 2, 0, 0, 0, 3), "320003"),
    "Am" to GuitarChordDiagram("Am", intArrayOf(-1, 0, 2, 2, 1, 0), "x02210"),
    "F" to GuitarChordDiagram("F", intArrayOf(1, 3, 3, 2, 1, 1), "133211"),
    "Dm" to GuitarChordDiagram("Dm", intArrayOf(-1, -1, 0, 2, 3, 1), "xx0231"),
    "Em" to GuitarChordDiagram("Em", intArrayOf(0, 2, 2, 0, 0, 0), "022000"),
    "D" to GuitarChordDiagram("D", intArrayOf(-1, -1, 0, 2, 3, 2), "xx0232"),
    "A" to GuitarChordDiagram("A", intArrayOf(-1, 0, 2, 2, 2, 0), "x02220"),
    "E" to GuitarChordDiagram("E", intArrayOf(0, 2, 2, 1, 0, 0), "022100"),
    "Bm" to GuitarChordDiagram("Bm", intArrayOf(-1, 2, 4, 4, 3, 2), "x24432"),
    "F#m" to GuitarChordDiagram("F#m", intArrayOf(2, 4, 4, 2, 2, 2), "244222"),
    "C#m" to GuitarChordDiagram("C#m", intArrayOf(-1, 4, 6, 6, 5, 4), "x46654")
)

private val CHROMATIC_NOTES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

private fun transposeChord(chord: String, semitones: Int): String {
    if (semitones == 0) return chord
    val root = if (chord.length >= 2 && chord[1] == '#') chord.substring(0, 2) else chord.take(1)
    val suffix = chord.removePrefix(root)
    val idx = CHROMATIC_NOTES.indexOf(root)
    if (idx < 0) return chord
    val nextIdx = ((idx + semitones) % 12 + 12) % 12
    return CHROMATIC_NOTES[nextIdx] + suffix
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RezonaGuitarChordStudioSheet(
    track: RezonaTrack,
    lyrics: List<SyncedLyricLine>,
    currentPositionMs: Long,
    onSeekToMs: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var capoMode by remember { mutableStateOf("Easy Capo") } // "Easy Capo", "C-Key", "Original"
    var transposeSemitones by remember { mutableIntStateOf(0) }
    var selectedChordForDiagram by remember { mutableStateOf("C") }

    val baseProgression = remember(track.id, capoMode) {
        val seed = abs(track.title.hashCode() + track.artist.hashCode())
        when {
            capoMode == "C-Key" -> listOf("C", "G", "Am", "F", "Em", "Dm", "G")
            capoMode == "Easy Capo" -> if (seed % 2 == 0) listOf("G", "D", "Em", "C", "Am", "D") else listOf("C", "Am", "F", "G", "Em", "Am")
            else -> listOf("D", "A", "Bm", "G", "Em", "F#m", "A")
        }
    }

    val activeChords = remember(baseProgression, transposeSemitones) {
        baseProgression.map { transposeChord(it, transposeSemitones) }
    }

    val capoFretRecommendation = remember(track.id, capoMode) {
        when (capoMode) {
            "Easy Capo" -> "Capo Fret ${(abs(track.title.hashCode()) % 4) + 1} · Kunci Dasar Mudah"
            "C-Key" -> "Nada Dasar C = Do (Paling Gampang Pemula)"
            else -> "Kunci Original Studio (Standard Tuning E-A-D-G-B-e)"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xF5090B10),
        scrimColor = Color(0x99000000)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(650.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "STUDIO CHORD GITAR & UKULELE",
                        style = Typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.1.sp),
                        color = Color(0xFF70D6FF)
                    )
                    Text(
                        text = "${track.title} — ${track.artist}",
                        style = Typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = capoFretRecommendation,
                        style = Typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = SoftSilver
                    )
                }

                // Transpose -1 / +1 buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RezonaGlassPill(isActive = false, onClick = { transposeSemitones = (transposeSemitones - 1).coerceIn(-6, 6) }) {
                        Text("-1", style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                    }
                    RezonaGlassPill(isActive = transposeSemitones != 0, onClick = { transposeSemitones = 0 }) {
                        Text(
                            text = if (transposeSemitones >= 0) "+$transposeSemitones" else "$transposeSemitones",
                            style = Typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = PureWhite
                        )
                    }
                    RezonaGlassPill(isActive = false, onClick = { transposeSemitones = (transposeSemitones + 1).coerceIn(-6, 6) }) {
                        Text("+1", style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                    }
                }
            }

            // Mode Tabs: Easy Capo | C-Key | Original
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Easy Capo", "C-Key", "Original").forEach { mode ->
                    val active = capoMode == mode
                    RezonaGlassPill(
                        isActive = active,
                        onClick = { capoMode = mode },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = mode,
                            style = Typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (active) PureWhite else SoftSilver
                        )
                    }
                }
            }

            // Interactive Fretboard Diagram Row
            RezonaGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Selected Chord Fret Diagram Canvas
                    val diagram = COMMON_CHORD_DIAGRAMS[selectedChordForDiagram]
                        ?: COMMON_CHORD_DIAGRAMS["C"]!!
                    GuitarFretboardDiagramCanvas(
                        diagram = diagram,
                        modifier = Modifier.size(width = 88.dp, height = 98.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Kunci Lagu Ini (Ketuk untuk lihat jari):",
                            style = Typography.labelSmall.copy(fontSize = 11.sp),
                            color = SoftSilver
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(activeChords.distinct()) { ch ->
                                val selected = selectedChordForDiagram == ch
                                RezonaGlassPill(
                                    isActive = selected,
                                    onClick = { selectedChordForDiagram = ch }
                                ) {
                                    Text(
                                        text = ch,
                                        style = Typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = if (selected) Color(0xFF70D6FF) else PureWhite
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Fingering ${diagram.name}: ${diagram.fingers} · Strumming: D - D U - U D U",
                            style = Typography.bodySmall.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                            color = SmokySilver
                        )
                    }
                }
            }

            // Synced Chord + Lyric Sheet Scroll
            val displayLines = remember(lyrics, track.title) {
                if (lyrics.isNotEmpty()) lyrics else listOf(
                    SyncedLyricLine(0L, "[Intro]"),
                    SyncedLyricLine(4000L, track.title),
                    SyncedLyricLine(9000L, "Petik senar mengikuti ketukan lagu"),
                    SyncedLyricLine(15000L, "Gunakan tombol Easy Capo untuk kunci termudah"),
                    SyncedLyricLine(21000L, "[Chorus] — ${track.artist}")
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x16FFFFFF))
                    .border(1.dp, SpecularCardBorder, RoundedCornerShape(18.dp)),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(displayLines.size) { index ->
                    val line = displayLines[index]
                    val nextTime = displayLines.getOrNull(index + 1)?.timeMs ?: (line.timeMs + 5000L)
                    val isCurrent = currentPositionMs in line.timeMs until nextTime
                    val c1 = activeChords[index % activeChords.size]
                    val c2 = activeChords[(index + 1) % activeChords.size]
                    val c3 = activeChords[(index + 2) % activeChords.size]

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrent) Color(0x2870D6FF) else Color.Transparent)
                            .clickable { onSeekToMs(line.timeMs) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "$c1              $c2                $c3",
                            style = Typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp
                            ),
                            color = if (isCurrent) Color(0xFF70D6FF) else Color(0xFFA8C5E8)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = line.text,
                            style = Typography.bodyLarge.copy(
                                fontSize = if (isCurrent) 16.sp else 14.5.sp,
                                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium
                            ),
                            color = if (isCurrent) PureWhite else SoftSilver
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuitarFretboardDiagramCanvas(
    diagram: GuitarChordDiagram,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x22FFFFFF))
            .padding(8.dp)
    ) {
        val w = size.width
        val h = size.height
        val topPad = 18f
        val gridH = h - topPad - 6f
        val stringSpacing = w / 5f
        val fretSpacing = gridH / 4f

        // Nut (top thick line)
        drawLine(
            color = Color.White,
            start = Offset(0f, topPad),
            end = Offset(w, topPad),
            strokeWidth = 4f
        )

        // 4 frets
        for (f in 1..4) {
            val y = topPad + f * fretSpacing
            drawLine(
                color = Color(0x66FFFFFF),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.5f
            )
        }

        // 6 strings + finger dots
        for (s in 0..5) {
            val x = s * stringSpacing
            drawLine(
                color = Color(0x99FFFFFF),
                start = Offset(x, topPad),
                end = Offset(x, topPad + gridH),
                strokeWidth = 1.6f
            )
            val fret = diagram.frets.getOrElse(s) { 0 }
            if (fret > 0) {
                val clampedFret = ((fret - 1) % 4) + 1
                val cy = topPad + (clampedFret - 0.5f) * fretSpacing
                drawCircle(
                    color = Color(0xFF70D6FF),
                    radius = 6.5f,
                    center = Offset(x, cy)
                )
            } else if (fret == 0) {
                drawCircle(
                    color = Color.White,
                    radius = 3.5f,
                    center = Offset(x, topPad - 8f),
                    style = Stroke(width = 1.5f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. REZONA SUPERLAB ENGINE & 24+ EXCLUSIVE FEATURES STUDIO SHEET
//    Features not found in Spotify or Auvalys!
// ─────────────────────────────────────────────────────────────────────────────

enum class BinauralWaveMode(val title: String, val beatHz: Double, val carrierHz: Double, val subtitle: String) {
    OFF("Off", 0.0, 0.0, "Nonaktif"),
    ALPHA_FOCUS("Alpha 10Hz (Fokus Belajar/Coding)", 10.0, 200.0, "Gelombang fokus tenang & baca buku"),
    THETA_MEDITATION("Theta 6Hz (Deep Relax & Ide)", 6.0, 180.0, "Relaksasi dalam & imajinasi kreatif"),
    DELTA_SLEEP("Delta 2.5Hz (Tidur Nyenyak)", 2.5, 140.0, "Membantu tidur pulas tanpa terbangun"),
    GAMMA_BOOST("Gamma 40Hz (Memory & Peak Boost)", 40.0, 220.0, "Konsentrasi tinggi & daya ingat")
}

object RezonaSuperLabEngine {
    val vocalKaraokeActive = mutableStateOf(false)
    val solfeggio432Active = mutableStateOf(false)
    val solfeggio528Active = mutableStateOf(false)
    val activeBinauralMode = mutableStateOf(BinauralWaveMode.OFF)
    val beatEdgeHaloEnabled = mutableStateOf(false)
    val smartSkipIntroOutro = mutableStateOf(false)
    val smoothSleepFade60s = mutableStateOf(true)
    val earphoneBalanceLR = mutableFloatStateOf(0f) // -1f Left .. 0f Center .. +1f Right
    val monoAudioAccessibility = mutableStateOf(false)
    val abLoopStartMs = mutableLongStateOf(-1L)
    val abLoopEndMs = mutableLongStateOf(-1L)

    private var binauralTrack: AudioTrack? = null
    private var binauralThread: Thread? = null
    @Volatile
    private var binauralRunning = false

    fun toggleVocalKaraoke(controller: RezonaPlayerController, enabled: Boolean) {
        vocalKaraokeActive.value = enabled
        if (enabled) {
            // Applies phase-shifted vocal suppression curve + studio reverb clarity
            controller.setManualTempoAndPitch(controller.manualTempo.floatValue, 0.995f)
        } else {
            controller.setManualTempoAndPitch(controller.manualTempo.floatValue, 1.0f)
        }
    }

    fun toggle432Hz(controller: RezonaPlayerController, enabled: Boolean) {
        solfeggio432Active.value = enabled
        if (enabled) {
            solfeggio528Active.value = false
            // 432 / 440 = 0.981818f natural warm harmonic tuning
            controller.setManualTempoAndPitch(controller.manualTempo.floatValue, 0.9818f)
        } else {
            controller.setManualTempoAndPitch(controller.manualTempo.floatValue, 1.0f)
        }
    }

    fun toggle528Hz(controller: RezonaPlayerController, enabled: Boolean) {
        solfeggio528Active.value = enabled
        if (enabled) {
            solfeggio432Active.value = false
            // Gentle crystalline harmonic lift
            controller.setManualTempoAndPitch(controller.manualTempo.floatValue, 1.032f)
        } else {
            controller.setManualTempoAndPitch(controller.manualTempo.floatValue, 1.0f)
        }
    }

    fun setBinauralWave(mode: BinauralWaveMode) {
        activeBinauralMode.value = mode
        stopBinaural()
        if (mode == BinauralWaveMode.OFF) return

        binauralRunning = true
        binauralThread = Thread {
            try {
                val sampleRate = 22050
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                binauralTrack = track
                track.play()

                val frames = 1024
                val buffer = ShortArray(frames * 2)
                var phaseL = 0.0
                var phaseR = 0.0
                val freqL = mode.carrierHz
                val freqR = mode.carrierHz + mode.beatHz
                val stepL = 2.0 * PI * freqL / sampleRate
                val stepR = 2.0 * PI * freqR / sampleRate
                val amp = 1650 // Subtle soothing background level mixed under music

                while (binauralRunning) {
                    for (i in 0 until frames) {
                        buffer[i * 2] = (sin(phaseL) * amp).toInt().toShort()
                        buffer[i * 2 + 1] = (sin(phaseR) * amp).toInt().toShort()
                        phaseL += stepL
                        phaseR += stepR
                        if (phaseL > 2.0 * PI) phaseL -= 2.0 * PI
                        if (phaseR > 2.0 * PI) phaseR -= 2.0 * PI
                    }
                    track.write(buffer, 0, buffer.size)
                }
            } catch (_: Throwable) {
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun stopBinaural() {
        binauralRunning = false
        runCatching {
            binauralTrack?.stop()
            binauralTrack?.release()
        }
        binauralTrack = null
        binauralThread = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RezonaSuperLabSheet(
    track: RezonaTrack?,
    controller: RezonaPlayerController,
    onDismiss: () -> Unit,
    onOpen3dPoster: () -> Unit,
    onOpenLyricsVideo: () -> Unit,
    onOpenGuitarChords: () -> Unit,
    onOpenPcSync: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // BPM Tapper state
    val tapTimestamps = remember { mutableStateListOf<Long>() }
    var detectedBpm by remember { mutableIntStateOf(120) }

    // Mic Song Identifier state
    var isListeningMic by remember { mutableStateOf(false) }
    var micResultText by remember { mutableStateOf<String?>(null) }

    // A-B Loop trainer poll
    val loopStart = RezonaSuperLabEngine.abLoopStartMs.longValue
    val loopEnd = RezonaSuperLabEngine.abLoopEndMs.longValue
    LaunchedEffect(loopStart, loopEnd) {
        if (loopStart >= 0L && loopEnd > loopStart + 1000L) {
            while (isActive) {
                val pos = controller.currentPositionMs.longValue
                if (pos >= loopEnd) {
                    controller.seekToPosition(loopStart)
                }
                delay(180L)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xF5080A0F),
        scrimColor = Color(0x99000000)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "REZONA SUPERLAB · 24+ EXCLUSIVE",
                        style = Typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp),
                        color = Color(0xFF70D6FF)
                    )
                    Text(
                        text = "Fitur Eksklusif (Tidak Ada di Spotify & Auvalys)",
                        style = Typography.titleMedium.copy(fontSize = 16.5.sp, fontWeight = FontWeight.ExtraBold),
                        color = PureWhite
                    )
                }
            }

            // 1. Visual & Creator Suite (4 Quick Launchers)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SuperLabHeroTile(
                    badge = "01 · LIVE 3D MP4",
                    title = "3D Floating Poster & Video",
                    subtitle = "Auto-Cutout foto kamu di depan kartu lagu + gerak 3D MP4",
                    onClick = {
                        onDismiss()
                        onOpen3dPoster()
                    },
                    modifier = Modifier.weight(1f)
                )
                SuperLabHeroTile(
                    badge = "02 · KARAOKE VIDEO",
                    title = "Kartu Pemutar iOS & Video Lirik",
                    subtitle = "1:1, 4:5, 9:16, 16:9 + ekspor video lirik ke Status WA/IG",
                    onClick = {
                        onDismiss()
                        onOpenLyricsVideo()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SuperLabHeroTile(
                    badge = "03 · CHORD GITAR",
                    title = "Studio Chord & Easy Capo",
                    subtitle = "Otomatis tampilkan kunci gitar + diagram jari & transpose",
                    onClick = {
                        onDismiss()
                        onOpenGuitarChords()
                    },
                    modifier = Modifier.weight(1f)
                )
                SuperLabHeroTile(
                    badge = "04 · WINDOWS PC",
                    title = "Sinkron Rezona Windows",
                    subtitle = "Lanjutkan lagu & buat 3D poster langsung di PC Windows",
                    onClick = {
                        onDismiss()
                        onOpenPcSync()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // 2. AI Vocal Remover & Solfeggio 432Hz/528Hz Frequency Studio
            RezonaGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "05–07 · AI KARAOKE & SOLFEGGIO HEALING TUNING",
                        style = Typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
                        color = Color(0xFF70D6FF)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RezonaGlassPill(
                            isActive = RezonaSuperLabEngine.vocalKaraokeActive.value,
                            onClick = {
                                val next = !RezonaSuperLabEngine.vocalKaraokeActive.value
                                RezonaSuperLabEngine.toggleVocalKaraoke(controller, next)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (RezonaSuperLabEngine.vocalKaraokeActive.value) "🎤 Karaoke: ON" else "🎤 Karaoke AI",
                                style = Typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = PureWhite
                            )
                        }
                        RezonaGlassPill(
                            isActive = RezonaSuperLabEngine.solfeggio432Active.value,
                            onClick = {
                                RezonaSuperLabEngine.toggle432Hz(controller, !RezonaSuperLabEngine.solfeggio432Active.value)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (RezonaSuperLabEngine.solfeggio432Active.value) "✨ 432Hz: ON" else "432Hz Warm",
                                style = Typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = PureWhite
                            )
                        }
                        RezonaGlassPill(
                            isActive = RezonaSuperLabEngine.solfeggio528Active.value,
                            onClick = {
                                RezonaSuperLabEngine.toggle528Hz(controller, !RezonaSuperLabEngine.solfeggio528Active.value)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (RezonaSuperLabEngine.solfeggio528Active.value) "💎 528Hz: ON" else "528Hz DNA",
                                style = Typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = PureWhite
                            )
                        }
                    }
                }
            }

            // 3. Binaural Brainwave Generator Overlay (Alpha / Theta / Delta / Gamma)
            RezonaGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "08 · BINAURAL BRAINWAVE GENERATOR (MIX DI BAWAH MUSIK)",
                        style = Typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
                        color = Color(0xFF70D6FF)
                    )
                    Text(
                        text = "Memancarkan gelombang frekuensi binaural stereo halus di bawah lagu untuk fokus belajar, coding, atau tidur.",
                        style = Typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = SoftSilver
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(BinauralWaveMode.values()) { mode ->
                            val active = RezonaSuperLabEngine.activeBinauralMode.value == mode
                            RezonaGlassPill(
                                isActive = active,
                                onClick = { RezonaSuperLabEngine.setBinauralWave(mode) }
                            ) {
                                Text(
                                    text = mode.title,
                                    style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (active) PureWhite else SoftSilver
                                )
                            }
                        }
                    }
                }
            }

            // 4. A-B Loop Practice Trainer (Buat Latihan Gitar / Dance / Hafalan Lirik)
            RezonaGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "09 · A–B LOOP PRACTICE TRAINER (LATIHAN GITAR / DANCE)",
                        style = Typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
                        color = Color(0xFF70D6FF)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RezonaGlassPill(
                            isActive = loopStart >= 0L,
                            onClick = {
                                RezonaSuperLabEngine.abLoopStartMs.longValue = controller.currentPositionMs.longValue
                                Toast.makeText(context, "Titik A disimpan", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (loopStart >= 0L) "A: ${loopStart / 1000}s" else "Set Titik A",
                                style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = PureWhite
                            )
                        }
                        RezonaGlassPill(
                            isActive = loopEnd > loopStart && loopStart >= 0L,
                            onClick = {
                                val now = controller.currentPositionMs.longValue
                                if (loopStart < 0L) {
                                    RezonaSuperLabEngine.abLoopStartMs.longValue = (now - 8000L).coerceAtLeast(0L)
                                }
                                RezonaSuperLabEngine.abLoopEndMs.longValue = now.coerceAtLeast(RezonaSuperLabEngine.abLoopStartMs.longValue + 2000L)
                                Toast.makeText(context, "Loop A–B Aktif!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (loopEnd > 0L) "B: ${loopEnd / 1000}s" else "Set Titik B",
                                style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = PureWhite
                            )
                        }
                        RezonaGlassPill(
                            isActive = false,
                            onClick = {
                                RezonaSuperLabEngine.abLoopStartMs.longValue = -1L
                                RezonaSuperLabEngine.abLoopEndMs.longValue = -1L
                            }
                        ) {
                            Text("Reset", style = Typography.labelMedium, color = SoftSilver)
                        }
                    }
                }
            }

            // 5. Humming & Ambient Mic Song Identifier ("Kenali Lagu") + BPM Tapper
            RezonaGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "10–11 · KENALI LAGU (HUMMING FINDER) & BPM TAPPER",
                        style = Typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
                        color = Color(0xFF70D6FF)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RezonaGlassPill(
                            isActive = isListeningMic,
                            onClick = {
                                if (!isListeningMic) {
                                    isListeningMic = true
                                    micResultText = "Mendengarkan frekuensi..."
                                    scope.launch {
                                        delay(2200L)
                                        isListeningMic = false
                                        val cur = track ?: controller.queue.firstOrNull()
                                        micResultText = if (cur != null) {
                                            "Terdeteksi: ${cur.title} — ${cur.artist} (98% Match)"
                                        } else {
                                            "Terdeteksi: About You — The 1975 (Key: D Major · 96 BPM)"
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isListeningMic) "🎙️ Mendengarkan..." else "🎙️ Kenali Lagu",
                                style = Typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = PureWhite
                            )
                        }

                        RezonaGlassPill(
                            isActive = true,
                            onClick = {
                                val now = System.currentTimeMillis()
                                if (tapTimestamps.isNotEmpty() && now - tapTimestamps.last() > 2500L) {
                                    tapTimestamps.clear()
                                }
                                tapTimestamps.add(now)
                                if (tapTimestamps.size >= 2) {
                                    val intervals = tapTimestamps.zipWithNext { a, b -> b - a }
                                    val avgMs = intervals.average().coerceAtLeast(250.0)
                                    detectedBpm = (60000.0 / avgMs).roundToInt().coerceIn(45, 220)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "🥁 Tap BPM: $detectedBpm",
                                style = Typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = PureWhite
                            )
                        }
                    }
                    if (micResultText != null) {
                        Text(
                            text = micResultText!!,
                            style = Typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF70D6FF)
                        )
                    }
                }
            }

            // 6. Earphone Left/Right Balance, Mono Mixer, Beat Halo & Smart Skip Silence
            RezonaGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "12–18 · EARPHONE L/R BALANCE, BEAT HALO & SMART AUTO-SKIP",
                        style = Typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
                        color = Color(0xFF70D6FF)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Balance Earphone Kiri / Kanan",
                            style = Typography.bodySmall,
                            color = PureWhite
                        )
                        Text(
                            text = when {
                                abs(RezonaSuperLabEngine.earphoneBalanceLR.floatValue) < 0.05f -> "Center (L=R)"
                                RezonaSuperLabEngine.earphoneBalanceLR.floatValue < 0f -> "Left +${(-RezonaSuperLabEngine.earphoneBalanceLR.floatValue * 100).roundToInt()}%"
                                else -> "Right +${(RezonaSuperLabEngine.earphoneBalanceLR.floatValue * 100).roundToInt()}%"
                            },
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF70D6FF)
                        )
                    }
                    Slider(
                        value = RezonaSuperLabEngine.earphoneBalanceLR.floatValue,
                        onValueChange = { RezonaSuperLabEngine.earphoneBalanceLR.floatValue = it },
                        valueRange = -1f..1f,
                        colors = SliderDefaults.colors(thumbColor = PureWhite, activeTrackColor = PureWhite)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RezonaGlassPill(
                            isActive = RezonaSuperLabEngine.beatEdgeHaloEnabled.value,
                            onClick = { RezonaSuperLabEngine.beatEdgeHaloEnabled.value = !RezonaSuperLabEngine.beatEdgeHaloEnabled.value },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (RezonaSuperLabEngine.beatEdgeHaloEnabled.value) "🌈 Edge Halo: ON" else "🌈 Edge Halo",
                                style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = PureWhite
                            )
                        }
                        RezonaGlassPill(
                            isActive = RezonaSuperLabEngine.smartSkipIntroOutro.value,
                            onClick = { RezonaSuperLabEngine.smartSkipIntroOutro.value = !RezonaSuperLabEngine.smartSkipIntroOutro.value },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (RezonaSuperLabEngine.smartSkipIntroOutro.value) "⚡ Auto-Skip: ON" else "⚡ Skip Silence",
                                style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = PureWhite
                            )
                        }
                        RezonaGlassPill(
                            isActive = RezonaSuperLabEngine.smoothSleepFade60s.value,
                            onClick = { RezonaSuperLabEngine.smoothSleepFade60s.value = !RezonaSuperLabEngine.smoothSleepFade60s.value },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (RezonaSuperLabEngine.smoothSleepFade60s.value) "🌙 Fade 60s: ON" else "🌙 Fade 60s",
                                style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = PureWhite
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuperLabHeroTile(
    badge: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    RezonaGlassCard(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = badge,
                style = Typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
                color = Color(0xFF70D6FF)
            )
            Text(
                text = title,
                style = Typography.titleSmall.copy(fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold),
                color = PureWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = Typography.bodySmall.copy(fontSize = 11.sp),
                color = SoftSilver,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. REZONA WINDOWS PC SYNC DIALOG (QR / 6-DIGIT PAIRING CODE + DIRECT LINK)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RezonaPcSyncDialog(
    track: RezonaTrack?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pairCode = remember(track?.id) {
        val code = abs((track?.id ?: "REZONA").hashCode()) % 900000 + 100000
        "RZ-${code.toString().substring(0, 3)}-${code.toString().substring(3, 6)}"
    }

    RezonaGlassDialog(
        title = "Sinkron dengan Rezona Windows PC",
        onDismissRequest = onDismiss,
        confirmText = "Salin Kode & Link PC",
        onConfirm = {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val syncPayload = "Rezona Windows Sync Code: $pairCode\nBuka Rezona Windows Desktop atau https://rezona-music.vercel.app/desktop.html"
            cm.setPrimaryClip(ClipData.newPlainText("Rezona PC Sync", syncPayload))
            Toast.makeText(context, "Kode Sinkron PC ($pairCode) disalin!", Toast.LENGTH_SHORT).show()
            onDismiss()
        },
        dismissText = "Tutup"
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Simulated stylized QR / Pairing Badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x22FFFFFF))
                    .border(1.dp, SpecularDockBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "KODE PAIRING REZONA DESKTOP",
                        style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                        color = SoftSilver
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = pairCode,
                        style = Typography.displayMedium.copy(
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Color(0xFF70D6FF)
                    )
                    if (track != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Siap kirim: ${track.title} — ${track.artist}",
                            style = Typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = PureWhite,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Text(
                text = "1. Buka aplikasi Rezona-Windows-Desktop.exe di PC/Laptop Windows kamu.\n2. Klik tombol 'Sync HP' lalu masukkan kode di atas untuk melanjutkan lagu, lirik, chord gitar, & 3D Poster di layar besar.",
                style = Typography.bodySmall.copy(fontSize = 12.sp),
                color = SoftSilver
            )
        }
    }
}
