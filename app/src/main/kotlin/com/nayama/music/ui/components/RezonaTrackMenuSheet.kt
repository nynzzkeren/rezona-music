package com.nayama.music.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.backend.MusicEngineRepository
import com.nayama.music.backend.OfflineDownloadManager
import com.nayama.music.backend.innertube.InnerTubeFilter
import com.nayama.music.data.repository.RezonaLibraryRepository
import com.nayama.music.model.RezonaTrack
import com.nayama.music.playback.MusicPlayerController
import com.nayama.music.playback.RezonaPlayerController
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.SpecularCardBorder
import com.nayama.music.ui.theme.Typography
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Stage E: Track Three-Dot Menu Glass Bottom Sheet (track_menu.jpg reference structure)
 * - Optional Volume Slider Row at top (`showVolumeSlider = true` from Now Playing / Lyrics, false from list rows)
 * - 4 Glass Action Tiles in row-wrapped grid: Start radio, Add to playlist, Copy link, Share song card
 * - Grouped Plain Rows with hairline separators (View artist, Add/Remove from library, Pin to speed dial,
 *   Download offline inside Rezona, Listen together stub, Details, Ambient mode, Equalizer, System equalizer, Advanced)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RezonaTrackMenuSheet(
    track: RezonaTrack,
    controller: RezonaPlayerController,
    showVolumeSlider: Boolean = true,
    onDismiss: () -> Unit,
    onOpenEqualizer: () -> Unit = {},
    onOpenAmbientMode: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val libraryRepo = remember { RezonaLibraryRepository.getInstance(context) }
    val downloadMgr = remember { OfflineDownloadManager.getInstance(context) }

    val isLiked by libraryRepo.isLiked(track.id).collectAsState(initial = false)
    val isPinned by libraryRepo.isPinnedSpeedDial(track.id).collectAsState(initial = false)
    val isDownloaded by libraryRepo.isDownloaded(track.id).collectAsState(initial = false)
    val downloadProgressMap by downloadMgr.progress.collectAsState()
    val activeProgress = downloadProgressMap[track.id]

    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showAdvancedDialog by remember { mutableStateOf(false) }
    var showArtistSheet by remember { mutableStateOf(false) }
    var showListenTogetherStub by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xEC101218),
        scrimColor = Color(0x88000000),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color(0x4DFFFFFF))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            if (showVolumeSlider) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_volume_low),
                        contentDescription = "Volume Low",
                        tint = SoftSilver,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    RezonaGlassSlider(
                        value = controller.volume.floatValue,
                        onValueChange = { controller.setVolume(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_volume_high),
                        contentDescription = "Volume High",
                        tint = PureWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }
                RezonaHairlineDivider()
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RezonaAsyncCoverArt(
                        coverArtUrl = track.coverArtUrl,
                        fallbackIndex = track.index,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(46.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PearlWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = Typography.bodySmall,
                            color = SmokySilver,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                RezonaHairlineDivider()
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 4 Glass Action Tiles (3 in row 1, 1 wrapped in row 2 matching track_menu.jpg)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MenuGlassTile(
                    iconRes = R.drawable.ic_radio,
                    label = "Start radio",
                    onClick = {
                        controller.launchRadioFromTrack(track)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
                MenuGlassTile(
                    iconRes = R.drawable.ic_playlist_add,
                    label = "Add to playlist",
                    onClick = { showPlaylistPicker = true },
                    modifier = Modifier.weight(1f)
                )
                MenuGlassTile(
                    iconRes = R.drawable.ic_link,
                    label = "Copy link",
                    onClick = {
                        val url = track.streamUrl.ifBlank { "https://music.youtube.com/watch?v=${track.id.removePrefix("yt_")}" }
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Rezona Song Link", url))
                        Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MenuGlassTile(
                    iconRes = R.drawable.ic_share,
                    label = "Share song card",
                    onClick = {
                        scope.launch {
                            RezonaSongCardRenderer.shareSongCard(context, track)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))
            RezonaHairlineDivider()

            // Grouped Plain Rows with Hairline Separators (NO card boxes)
            MenuPlainRow(
                iconRes = R.drawable.ic_rezona_you,
                title = "View artist",
                description = track.artist,
                onClick = { showArtistSheet = true }
            )
            MenuPlainRow(
                iconRes = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
                title = if (isLiked) "Remove from library" else "Add to library",
                onClick = {
                    scope.launch { libraryRepo.toggleLikeTrack(track) }
                }
            )
            MenuPlainRow(
                iconRes = R.drawable.ic_add,
                title = if (isPinned) "Unpin from speed dial" else "Pin to speed dial",
                onClick = {
                    scope.launch {
                        libraryRepo.togglePinSpeedDial(track)
                        Toast.makeText(
                            context,
                            if (isPinned) "Removed from Quick picks" else "Pinned to Quick picks",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
            RezonaHairlineDivider()

            MenuPlainRow(
                iconRes = if (isDownloaded) R.drawable.ic_download_done else R.drawable.ic_download,
                title = when {
                    activeProgress != null -> "Downloading (${(activeProgress * 100).toInt()}%)"
                    isDownloaded -> "Downloaded (Remove offline copy)"
                    else -> "Download"
                },
                description = if (isDownloaded) "Saved inside Rezona for offline playback only" else null,
                onClick = {
                    scope.launch {
                        if (isDownloaded) {
                            downloadMgr.removeDownload(track.id)
                            Toast.makeText(context, "Removed offline copy", Toast.LENGTH_SHORT).show()
                        } else if (activeProgress == null) {
                            Toast.makeText(context, "Saving offline audio inside Rezona...", Toast.LENGTH_SHORT).show()
                            val ok = downloadMgr.downloadTrack(track)
                            Toast.makeText(
                                context,
                                if (ok) "Saved for offline playback in Rezona" else "Download failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
            RezonaHairlineDivider()

            MenuPlainRow(
                iconRes = R.drawable.ic_community,
                title = "Listen together",
                description = "Coming soon · Peer-to-peer rooms",
                onClick = { showListenTogetherStub = true }
            )
            RezonaHairlineDivider()

            MenuPlainRow(
                iconRes = R.drawable.ic_info,
                title = "Details",
                description = "View song details",
                onClick = { showDetailsDialog = true }
            )
            MenuPlainRow(
                iconRes = R.drawable.ic_ambient,
                title = "Ambient mode",
                description = "Calm full-screen cover display",
                onClick = {
                    onDismiss()
                    onOpenAmbientMode()
                }
            )
            MenuPlainRow(
                iconRes = R.drawable.ic_equalizer,
                title = "Equalizer",
                description = "Open audio equalizer",
                onClick = {
                    onDismiss()
                    onOpenEqualizer()
                }
            )
            MenuPlainRow(
                iconRes = R.drawable.ic_rezona_studio,
                title = "System equalizer",
                description = "Open built-in Android equalizer",
                onClick = {
                    val launched = runCatching {
                        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        true
                    }.getOrDefault(false)
                    if (!launched) {
                        Toast.makeText(context, "System equalizer not available on this device", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            MenuPlainRow(
                iconRes = R.drawable.ic_tune,
                title = "Advanced",
                description = "Adjust tempo and pitch",
                onClick = { showAdvancedDialog = true }
            )
        }
    }

    if (showPlaylistPicker) {
        RezonaPlaylistPickerDialog(
            track = track,
            onDismiss = { showPlaylistPicker = false }
        )
    }

    if (showDetailsDialog) {
        RezonaGlassDialog(
            title = "Song Details",
            onDismissRequest = { showDetailsDialog = false },
            confirmText = "Done",
            onConfirm = { showDetailsDialog = false },
            dismissText = null
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine("Title", track.title)
                DetailLine("Artist", track.artist)
                DetailLine("Album", track.album)
                DetailLine("Duration", track.duration)
                DetailLine("Source", track.source)
                DetailLine("Audio Output", "${controller.outputDevice.value} (${controller.audioFormatLabel.value})")
            }
        }
    }

    if (showAdvancedDialog) {
        var tempo by remember { mutableFloatStateOf(controller.manualTempo.floatValue) }
        var pitch by remember { mutableFloatStateOf(controller.manualPitch.floatValue) }
        RezonaGlassDialog(
            title = "Tempo & Pitch",
            onDismissRequest = { showAdvancedDialog = false },
            confirmText = "Done",
            onConfirm = { showAdvancedDialog = false },
            dismissText = null
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tempo (Speed)", color = PearlWhite, style = Typography.bodyMedium)
                    Text(String.format(Locale.ENGLISH, "%.2fx", tempo), color = PureWhite, fontWeight = FontWeight.Bold)
                }
                RezonaGlassSlider(
                    value = tempo,
                    onValueChange = {
                        tempo = it
                        controller.setManualTempoAndPitch(tempo, pitch)
                    },
                    valueRange = 0.6f..1.5f,
                    formatBubble = { String.format(Locale.ENGLISH, "%.2fx", it) }
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Pitch", color = PearlWhite, style = Typography.bodyMedium)
                    Text(String.format(Locale.ENGLISH, "%.2fx", pitch), color = PureWhite, fontWeight = FontWeight.Bold)
                }
                RezonaGlassSlider(
                    value = pitch,
                    onValueChange = {
                        pitch = it
                        controller.setManualTempoAndPitch(tempo, pitch)
                    },
                    valueRange = 0.6f..1.5f,
                    formatBubble = { String.format(Locale.ENGLISH, "%.2fx", it) }
                )

                RezonaGlassButton(
                    text = "Reset Normal (1.0x)",
                    isPrimary = false,
                    onClick = {
                        tempo = 1.0f
                        pitch = 1.0f
                        controller.setManualTempoAndPitch(1.0f, 1.0f)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showArtistSheet) {
        RezonaArtistSongsDialog(
            artistName = track.artist,
            onPlayTrack = {
                controller.playTrack(it, queueTitle = track.artist, autoQueueRadio = true)
                showArtistSheet = false
                onDismiss()
            },
            onDismiss = { showArtistSheet = false }
        )
    }

    if (showListenTogetherStub) {
        RezonaGlassDialog(
            title = "Listen Together",
            onDismissRequest = { showListenTogetherStub = false },
            confirmText = "Got it",
            onConfirm = { showListenTogetherStub = false },
            dismissText = null
        ) {
            Text(
                text = "Real-time peer-to-peer listening rooms are planned for a future update. Currently all playback runs locally on-device with no server.",
                style = Typography.bodyMedium,
                color = SoftSilver
            )
        }
    }
}

@Composable
private fun MenuGlassTile(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        variant = GlassSurfaceVariant.Card,
        modifier = modifier.height(82.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = PearlWhite,
                modifier = Modifier.size(21.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = Typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                color = PearlWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun MenuPlainRow(
    iconRes: Int,
    title: String,
    description: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = PureWhite),
                onClick = onClick
            )
            .padding(vertical = 13.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = PearlWhite,
            modifier = Modifier.size(21.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = Typography.titleMedium.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Medium),
                color = PearlWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = Typography.bodySmall.copy(fontSize = 12.sp),
                    color = SmokySilver,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(text = label, style = Typography.labelSmall, color = SmokySilver)
        Text(text = value.ifBlank { "—" }, style = Typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = PearlWhite)
    }
}

@Composable
fun RezonaPlaylistPickerDialog(
    track: RezonaTrack,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val libraryRepo = remember { RezonaLibraryRepository.getInstance(context) }
    val playlists by libraryRepo.getAllPlaylists().collectAsState(initial = emptyList())
    var newName by remember { mutableStateOf("") }

    RezonaGlassDialog(
        title = "Add to Playlist",
        onDismissRequest = onDismiss,
        confirmText = "Create & Add",
        onConfirm = if (newName.isNotBlank()) {
            {
                scope.launch {
                    val plId = libraryRepo.createPlaylist(title = newName.trim())
                    libraryRepo.addTrackToPlaylist(plId, track)
                    Toast.makeText(context, "Added to ${newName.trim()}", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }
        } else null,
        dismissText = "Close"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RezonaGlassTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = "New playlist name..."
            )
            RezonaHairlineDivider()
            LazyColumn(modifier = Modifier.height(210.dp)) {
                items(playlists, key = { it.id }) { pl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    libraryRepo.addTrackToPlaylist(pl.id, track)
                                    Toast.makeText(context, "Added to ${pl.title}", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = pl.title, style = Typography.bodyMedium, color = PearlWhite)
                        Icon(
                            painter = painterResource(id = R.drawable.ic_add),
                            contentDescription = null,
                            tint = SoftSilver,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    RezonaHairlineDivider()
                }
            }
        }
    }
}

@Composable
private fun RezonaArtistSongsDialog(
    artistName: String,
    onPlayTrack: (RezonaTrack) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val engine = remember { MusicEngineRepository.getInstance(context) }
    var tracks by remember { mutableStateOf<List<RezonaTrack>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(artistName) {
        isLoading = true
        tracks = engine.innerTubeRepo.search("$artistName top songs", InnerTubeFilter.SONGS)
        isLoading = false
    }

    RezonaGlassDialog(
        title = artistName,
        onDismissRequest = onDismiss,
        confirmText = "Close",
        onConfirm = onDismiss,
        dismissText = null
    ) {
        if (isLoading) {
            Text("Loading artist catalog...", style = Typography.bodySmall, color = SmokySilver)
        } else if (tracks.isEmpty()) {
            Text("No tracks found for this artist.", style = Typography.bodySmall, color = SmokySilver)
        } else {
            LazyColumn(modifier = Modifier.height(300.dp)) {
                items(tracks.take(15), key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayTrack(item) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RezonaAsyncCoverArt(
                            coverArtUrl = item.coverArtUrl,
                            fallbackIndex = item.index,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item.title, style = Typography.bodyMedium, color = PearlWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = item.duration, style = Typography.bodySmall, color = SmokySilver)
                        }
                    }
                    RezonaHairlineDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RezonaEqualizerModalSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val eqController = remember { MusicPlayerController.getInstance(context).equalizerController }
    val eqState by eqController.equalizerState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xEE101218),
        scrimColor = Color(0x88000000)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("5-Band Equalizer", style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PearlWhite)
                    Text(
                        text = if (eqState.isAvailable) "Hardware AudioFX · ${eqState.currentPreset}" else "Software Preset Curves",
                        style = Typography.bodySmall,
                        color = SmokySilver
                    )
                }
                RezonaGlassToggle(
                    checked = eqState.isEnabled,
                    onCheckedChange = { eqController.setEnabled(it) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(eqState.presets) { preset ->
                    RezonaGlassPill(
                        isActive = eqState.currentPreset == preset,
                        onClick = { eqController.applyPreset(preset) }
                    ) {
                        Text(
                            text = preset,
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (eqState.currentPreset == preset) PureWhite else SoftSilver
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            val bands = if (eqState.bands.isNotEmpty()) eqState.bands else listOf(
                com.nayama.music.playback.EqualizerBand(0, 60, -1500, 1500, 0),
                com.nayama.music.playback.EqualizerBand(1, 230, -1500, 1500, 0),
                com.nayama.music.playback.EqualizerBand(2, 910, -1500, 1500, 0),
                com.nayama.music.playback.EqualizerBand(3, 3600, -1500, 1500, 0),
                com.nayama.music.playback.EqualizerBand(4, 14000, -1500, 1500, 0)
            )
            bands.forEach { band ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (band.centerFreqHz >= 1000) "${band.centerFreqHz / 1000} kHz" else "${band.centerFreqHz} Hz",
                        style = Typography.bodySmall,
                        color = SoftSilver,
                        modifier = Modifier.width(58.dp)
                    )
                    RezonaGlassSlider(
                        value = band.currentLevel.toFloat(),
                        onValueChange = { eqController.setBandLevel(band.index, it.toInt().toShort()) },
                        valueRange = band.minLevel.toFloat()..band.maxLevel.toFloat(),
                        formatBubble = { "${(it / 100f).toInt()} dB" },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${band.currentLevel / 100} dB",
                        style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = PureWhite,
                        modifier = Modifier.width(42.dp)
                    )
                }
            }
        }
    }
}
