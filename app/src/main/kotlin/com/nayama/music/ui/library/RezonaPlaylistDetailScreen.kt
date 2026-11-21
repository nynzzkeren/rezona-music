package com.nayama.music.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.data.repository.RezonaLibraryRepository
import com.nayama.music.data.repository.toRezonaTrack
import com.nayama.music.model.RezonaTrack
import com.nayama.music.ui.components.LocalRezonaHazeState
import com.nayama.music.ui.components.RezonaActionDisc
import com.nayama.music.ui.components.RezonaAsyncCoverArt
import com.nayama.music.ui.components.RezonaGlassButton
import com.nayama.music.ui.components.RezonaGlassDialog
import com.nayama.music.ui.components.RezonaGlassTextField
import com.nayama.music.ui.components.RezonaHairlineDivider
import com.nayama.music.ui.components.rezonaHazeSource
import com.nayama.music.ui.theme.Graphite
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.SpecularCardBorder
import com.nayama.music.ui.theme.Typography
import kotlinx.coroutines.launch

@Composable
fun RezonaPlaylistDetailScreen(
    playlistId: String,
    onBack: () -> Unit,
    onTrackSelected: (Int) -> Unit,
    onPlayTrack: (RezonaTrack) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { RezonaLibraryRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    val hazeState = LocalRezonaHazeState.current

    val playlist by repository.getPlaylist(playlistId).collectAsState(initial = null)
    val tracks by repository.getPlaylistTracks(playlistId).collectAsState(initial = emptyList())

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Transparent root over the shared RezonaContinuousAmbientLayer (zero background seam!)
    Box(
        modifier = modifier
            .fillMaxSize()
            .rezonaHazeSource(hazeState)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Navigation Row
            item(key = "nav_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    RezonaActionDisc(
                        size = 42.dp,
                        onClick = onBack
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                            tint = PearlWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RezonaActionDisc(
                            size = 42.dp,
                            onClick = {
                                if (playlist != null) {
                                    renameText = playlist!!.title
                                    showRenameDialog = true
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_edit),
                                contentDescription = "Rename",
                                tint = SoftSilver,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        RezonaActionDisc(
                            size = 42.dp,
                            onClick = { showDeleteConfirmDialog = true }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Delete",
                                tint = Color(0xFFFF5A6E),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Playlist Hero Card & Cover
            item(key = "playlist_hero") {
                if (playlist != null) {
                    val heroCoverUrl = tracks.firstOrNull()?.toRezonaTrack()?.coverArtUrl
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .border(1.5.dp, SpecularCardBorder, RoundedCornerShape(24.dp))
                        ) {
                            RezonaAsyncCoverArt(
                                coverArtUrl = heroCoverUrl,
                                fallbackIndex = playlist!!.coverIndex,
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = playlist!!.title,
                            style = Typography.displayMedium.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                            color = PearlWhite
                        )

                        if (playlist!!.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = playlist!!.description,
                                style = Typography.bodyMedium,
                                color = SmokySilver,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${tracks.size} tracks · YouTube Music",
                                style = Typography.labelMedium,
                                color = SoftSilver
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RezonaGlassButton(
                                text = "Play All",
                                isPrimary = true,
                                onClick = {
                                    tracks.firstOrNull()?.let { onPlayTrack(it.toRezonaTrack()) }
                                }
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            RezonaActionDisc(
                                size = 46.dp,
                                onClick = {
                                    if (tracks.isNotEmpty()) {
                                        onPlayTrack(tracks.random().toRezonaTrack())
                                    }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_shuffle),
                                    contentDescription = "Shuffle",
                                    tint = PureWhite,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Track List Header
            item(key = "track_list_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tracks",
                        style = Typography.titleLarge,
                        color = PearlWhite
                    )
                    Text(
                        text = "${tracks.size} songs",
                        style = Typography.bodySmall,
                        color = SmokySilver
                    )
                }
            }

            // Unboxed Plain Track Rows with Hairline Separators (rezona_core.md line 26)
            itemsIndexed(
                items = tracks,
                key = { _, t -> "pl_track_${t.id}" }
            ) { index, track ->
                val rezonaTrack = remember(track) { track.toRezonaTrack() }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayTrack(rezonaTrack) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = Typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = SoftSilver,
                            modifier = Modifier.width(28.dp)
                        )

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, SpecularCardBorder, RoundedCornerShape(10.dp))
                        ) {
                            RezonaAsyncCoverArt(
                                coverArtUrl = rezonaTrack.coverArtUrl,
                                fallbackIndex = track.coverIndex,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = Typography.titleMedium.copy(fontSize = 14.sp),
                                color = PearlWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${track.artist} · ${rezonaTrack.duration}",
                                style = Typography.bodySmall.copy(fontSize = 12.sp),
                                color = SmokySilver,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    scope.launch {
                                        repository.removeTrackFromPlaylist(playlistId, track.id)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Remove track",
                                tint = SmokySilver,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    RezonaHairlineDivider(startIndent = 84.dp)
                }
            }
        }
    }

    // Rename Glass Dialog
    if (showRenameDialog) {
        RezonaGlassDialog(
            onDismissRequest = { showRenameDialog = false },
            title = "Rename Playlist",
            confirmText = "Save",
            onConfirm = {
                if (renameText.isNotBlank()) {
                    scope.launch {
                        repository.renamePlaylist(playlistId, renameText)
                        showRenameDialog = false
                    }
                }
            }
        ) {
            RezonaGlassTextField(
                value = renameText,
                onValueChange = { renameText = it },
                placeholder = "Playlist title..."
            )
        }
    }

    // Delete Confirmation Glass Dialog
    if (showDeleteConfirmDialog) {
        RezonaGlassDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = "Delete Playlist?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                scope.launch {
                    repository.deletePlaylist(playlistId)
                    showDeleteConfirmDialog = false
                    onBack()
                }
            }
        ) {
            Text(
                text = "Are you sure you want to delete '${playlist?.title ?: "this playlist"}'? This action cannot be undone.",
                color = SmokySilver,
                style = Typography.bodyMedium
            )
        }
    }
}
