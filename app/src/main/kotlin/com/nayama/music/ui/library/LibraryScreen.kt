package com.nayama.music.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nayama.music.R
import com.nayama.music.model.Album
import com.nayama.music.model.Playlist
import com.nayama.music.model.Track
import com.nayama.music.ui.components.AlbumCard
import com.nayama.music.ui.components.BackupAndRestoreDialog
import com.nayama.music.ui.components.NayamaImage
import com.nayama.music.ui.components.SectionHeader
import com.nayama.music.ui.components.TrackListItem
import com.nayama.music.ui.theme.DarkCharcoal
import com.nayama.music.ui.theme.DeepBlack
import com.nayama.music.ui.theme.ElevatedSurface
import com.nayama.music.ui.theme.Graphite
import com.nayama.music.ui.theme.LightGray
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.MediumGray
import com.nayama.music.ui.theme.PureBlack
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SurfaceBlack

@Composable
fun LibraryScreen(
    onPlaylistSelect: (Playlist) -> Unit,
    onTrackSelect: (Track) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel()
) {
    val likedSongs by viewModel.likedSongs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val savedAlbums by viewModel.savedAlbums.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val activePlaylist by viewModel.activePlaylist.collectAsStateWithLifecycle()
    val activePlaylistTracks by viewModel.activePlaylistTracks.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showBackupRestoreDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }

    val filters = listOf("All", "Playlists", "Liked", "Albums", "History")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        if (activePlaylist != null) {
            // Detailed View for Active Playlist
            PlaylistDetailView(
                playlist = activePlaylist!!,
                tracks = activePlaylistTracks,
                onBack = { viewModel.closePlaylist() },
                onTrackSelect = onTrackSelect,
                onRemoveTrack = { trackId ->
                    viewModel.removeTrackFromPlaylist(activePlaylist!!.id, trackId)
                },
                onMoveUp = { index ->
                    if (index > 0) {
                        val reordered = activePlaylistTracks.map { it.id }.toMutableList()
                        val temp = reordered[index]
                        reordered[index] = reordered[index - 1]
                        reordered[index - 1] = temp
                        viewModel.reorderPlaylist(activePlaylist!!.id, reordered)
                    }
                },
                onMoveDown = { index ->
                    if (index < activePlaylistTracks.size - 1) {
                        val reordered = activePlaylistTracks.map { it.id }.toMutableList()
                        val temp = reordered[index]
                        reordered[index] = reordered[index + 1]
                        reordered[index + 1] = temp
                        viewModel.reorderPlaylist(activePlaylist!!.id, reordered)
                    }
                },
                onRename = { playlistToRename = activePlaylist },
                onDelete = { playlistToDelete = activePlaylist }
            )
        } else {
            // Main Library View
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 130.dp)
            ) {
                // Header with title and New Playlist button
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "LIBRARY",
                                color = PureWhite,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 3.sp
                            )
                            Text(
                                text = "Personal collections & saved music",
                                color = MediumGray,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Import Playlist button (Spotify / Apple Music / M3U)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(ElevatedSurface)
                                    .border(0.5.dp, Graphite, RoundedCornerShape(50.dp))
                                    .clickable { showBackupRestoreDialog = true }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = "IMPORT",
                                    color = SoftSilver,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }

                            // Create New Playlist button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(ElevatedSurface)
                                    .border(0.5.dp, Graphite, RoundedCornerShape(50.dp))
                                    .clickable { showCreateDialog = true }
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = "+ NEW",
                                    color = PureWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }

                // Filter Chips (Capsules)
                item {
                    LazyRow(
                        modifier = Modifier.padding(bottom = 16.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filters, key = { it }) { filter ->
                            val isSelected = selectedFilter == filter
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(if (isSelected) PureWhite else ElevatedSurface)
                                    .border(0.5.dp, if (isSelected) PureWhite else Graphite, RoundedCornerShape(50.dp))
                                    .clickable { viewModel.setFilter(filter) }
                                    .padding(horizontal = 18.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = filter,
                                    color = if (isSelected) PureBlack else LightGray,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Filter: "All" or "Liked"
                if (selectedFilter == "All" || selectedFilter == "Liked") {
                    item {
                        // Liked Songs Hero Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(ElevatedSurface, DarkCharcoal)
                                    )
                                )
                                .border(0.5.dp, Graphite, RoundedCornerShape(18.dp))
                                .clickable {
                                    if (likedSongs.isNotEmpty()) {
                                        viewModel.setFilter("Liked")
                                    }
                                }
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(PureWhite),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_heart_filled),
                                        contentDescription = "Liked Songs",
                                        tint = PureBlack,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Liked Songs",
                                        color = PureWhite,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${likedSongs.size} tracks saved locally",
                                        color = MediumGray,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }

                                if (likedSongs.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(PureWhite)
                                            .clickable { onTrackSelect(likedSongs.first()) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_play_arrow),
                                            contentDescription = "Play Liked Songs",
                                            tint = PureBlack,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Liked Songs Detailed List when "Liked" is active
                if (selectedFilter == "Liked") {
                    if (likedSongs.isEmpty()) {
                        item {
                            EmptyLibraryNotice(
                                title = "NO LIKED SONGS YET",
                                description = "Explore tracks and tap the heart icon on any track to build your collection."
                            )
                        }
                    } else {
                        itemsIndexed(likedSongs, key = { _, track -> "liked_${track.id}" }) { index, track ->
                            TrackListItem(
                                track = track,
                                index = index + 1,
                                onClick = { onTrackSelect(track) }
                            )
                        }
                    }
                }

                // Filter: "All" or "Playlists"
                if (selectedFilter == "All" || selectedFilter == "Playlists") {
                    item {
                        Spacer(Modifier.height(8.dp))
                        SectionHeader(
                            title = "Your Playlists",
                            actionText = if (selectedFilter == "All") "SEE ALL" else null,
                            onActionClick = { viewModel.setFilter("Playlists") }
                        )
                    }

                    if (playlists.isEmpty()) {
                        item {
                            EmptyLibraryNotice(
                                title = "NO PLAYLISTS CREATED",
                                description = "Tap '+ NEW' at the top to create your first personal playlist."
                            )
                        }
                    } else {
                        items(playlists, key = { "pl_${it.id}" }) { playlist ->
                            PlaylistListItem(
                                playlist = playlist,
                                onClick = { viewModel.openPlaylist(playlist) },
                                onRename = { playlistToRename = playlist },
                                onDelete = { playlistToDelete = playlist }
                            )
                        }
                    }
                }

                // Filter: "All" or "History"
                if (selectedFilter == "All" || selectedFilter == "History") {
                    item {
                        Spacer(Modifier.height(8.dp))
                        SectionHeader(
                            title = "Recently Played",
                            actionText = if (recentlyPlayed.isNotEmpty()) "CLEAR" else null,
                            onActionClick = { viewModel.clearHistory() }
                        )
                    }

                    if (recentlyPlayed.isEmpty()) {
                        item {
                            EmptyLibraryNotice(
                                title = "NO LISTENING HISTORY",
                                description = "Songs you listen to will automatically appear here."
                            )
                        }
                    } else {
                        val displayHistory = if (selectedFilter == "All") recentlyPlayed.take(5) else recentlyPlayed
                        itemsIndexed(displayHistory, key = { index, track -> "hist_${track.id}_$index" }) { index, track ->
                            TrackListItem(
                                track = track,
                                index = index + 1,
                                onClick = { onTrackSelect(track) }
                            )
                        }
                    }
                }

                // Filter: "Albums"
                if (selectedFilter == "Albums") {
                    item {
                        Spacer(Modifier.height(8.dp))
                        SectionHeader(title = "Saved Albums")
                    }
                    if (savedAlbums.isEmpty()) {
                        item {
                            EmptyLibraryNotice(
                                title = "NO SAVED ALBUMS",
                                description = "Albums you save from Explore will be displayed here."
                            )
                        }
                    } else {
                        items(savedAlbums, key = { "alb_${it.id}" }) { album ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NayamaImage(
                                    url = album.artworkUrl,
                                    contentDescription = album.title,
                                    modifier = Modifier.size(56.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    fallbackText = album.title
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = album.title,
                                        color = PureWhite,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${album.artist} · ${album.releaseYear}",
                                        color = MediumGray,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Create Playlist Dialog
        if (showCreateDialog) {
            PlaylistInputDialog(
                title = "CREATE PLAYLIST",
                initialName = "",
                initialDescription = "",
                confirmText = "CREATE",
                onDismiss = { showCreateDialog = false },
                onConfirm = { name, desc ->
                    viewModel.createPlaylist(name, desc)
                    showCreateDialog = false
                }
            )
        }

        // Rename Playlist Dialog
        if (playlistToRename != null) {
            val pl = playlistToRename!!
            PlaylistInputDialog(
                title = "RENAME PLAYLIST",
                initialName = pl.title,
                initialDescription = pl.description,
                confirmText = "SAVE",
                onDismiss = { playlistToRename = null },
                onConfirm = { newName, newDesc ->
                    viewModel.renamePlaylist(pl.id, newName, newDesc)
                    playlistToRename = null
                }
            )
        }

        // Delete Playlist Confirmation Dialog
        if (playlistToDelete != null) {
            val pl = playlistToDelete!!
            Dialog(onDismissRequest = { playlistToDelete = null }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(ElevatedSurface)
                        .border(1.dp, Graphite, RoundedCornerShape(20.dp))
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            text = "DELETE PLAYLIST",
                            color = PureWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Are you sure you want to delete '${pl.title}'? This action cannot be undone.",
                            color = LightGray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 14.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "CANCEL",
                                color = MediumGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { playlistToDelete = null }
                                    .padding(8.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "DELETE",
                                color = PureWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        viewModel.deletePlaylist(pl.id)
                                        playlistToDelete = null
                                    }
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Backup & Restore Dialog
        if (showBackupRestoreDialog) {
            BackupAndRestoreDialog(onDismiss = { showBackupRestoreDialog = false })
        }
    }
}

@Composable
private fun PlaylistListItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        label = "playlist_item_scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ElevatedSurface)
                .border(0.5.dp, Graphite, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.artworkUrl != null) {
                NayamaImage(
                    url = playlist.artworkUrl,
                    contentDescription = playlist.title,
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    fallbackText = playlist.title
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_music_note),
                    contentDescription = null,
                    tint = LightGray,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                color = PureWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${playlist.trackCount} tracks · ${if (playlist.description.isNotBlank()) playlist.description else "Custom Playlist"}",
                color = MediumGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Rename & Delete buttons
        Row {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onRename() }
                    .padding(8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_edit),
                    contentDescription = "Ubah nama",
                    tint = MediumGray,
                    modifier = Modifier.size(16.dp)
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onDelete() }
                    .padding(8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = "Hapus",
                    tint = MediumGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaylistDetailView(
    playlist: Playlist,
    tracks: List<Track>,
    onBack: () -> Unit,
    onTrackSelect: (Track) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentPadding = PaddingValues(bottom = 130.dp)
    ) {
        // Back Navigation Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ElevatedSurface)
                        .border(0.5.dp, Graphite, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_back),
                        contentDescription = "Kembali",
                        tint = PureWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "PLAYLIST",
                    color = LightGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }

        // Playlist Hero Banner
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(ElevatedSurface)
                        .border(1.dp, Graphite, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (playlist.artworkUrl != null) {
                        NayamaImage(
                            url = playlist.artworkUrl,
                            contentDescription = playlist.title,
                            modifier = Modifier.size(170.dp),
                            shape = RoundedCornerShape(20.dp),
                            fallbackText = playlist.title
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_music_note),
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = playlist.title,
                    color = PureWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                if (playlist.description.isNotBlank()) {
                    Text(
                        text = playlist.description,
                        color = MediumGray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Text(
                    text = "${tracks.size} tracks",
                    color = LightGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Actions row: Play All, Rename, Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (tracks.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(PureWhite)
                                .clickable { onTrackSelect(tracks.first()) }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_play_arrow),
                                contentDescription = null,
                                tint = PureBlack,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "PLAY ALL",
                                color = PureBlack,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(ElevatedSurface)
                            .border(0.5.dp, Graphite, RoundedCornerShape(50.dp))
                            .clickable { onRename() }
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        Text(text = "RENAME", color = PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(ElevatedSurface)
                            .border(0.5.dp, Graphite, RoundedCornerShape(50.dp))
                            .clickable { onDelete() }
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        Text(text = "DELETE", color = MediumGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "Tracks In Playlist")
        }

        if (tracks.isEmpty()) {
            item {
                EmptyLibraryNotice(
                    title = "PLAYLIST IS EMPTY",
                    description = "Search or play any track, then tap '+' on Now Playing to add it here."
                )
            }
        } else {
            itemsIndexed(tracks, key = { index, track -> "pl_track_${track.id}_$index" }) { index, track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "%02d".format(index + 1),
                        color = MediumGray,
                        fontSize = 12.sp,
                        modifier = Modifier.width(28.dp)
                    )

                    NayamaImage(
                        url = track.artworkUrl,
                        contentDescription = track.title,
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        fallbackText = track.title
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onTrackSelect(track) }
                    ) {
                        Text(
                            text = track.title,
                            color = PureWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${track.artist} · ${track.formattedDuration}",
                            color = MediumGray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // Reorder & Remove buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onMoveUp(index) }
                                    .padding(6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_expand_more),
                                    contentDescription = "Naikkan",
                                    tint = LightGray,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .rotate(180f)
                                )
                            }
                        }
                        if (index < tracks.size - 1) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onMoveDown(index) }
                                    .padding(6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_expand_more),
                                    contentDescription = "Turunkan",
                                    tint = LightGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onRemoveTrack(track.id) }
                                .padding(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Hapus",
                                tint = MediumGray,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistInputDialog(
    title: String,
    initialName: String,
    initialDescription: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(20.dp))
                .background(ElevatedSurface)
                .border(1.dp, Graphite, RoundedCornerShape(20.dp))
                .padding(22.dp)
        ) {
            Column {
                Text(
                    text = title,
                    color = PureWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(text = "Playlist Name", color = MediumGray, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedBorderColor = PureWhite,
                        unfocusedBorderColor = Graphite,
                        focusedContainerColor = SurfaceBlack,
                        unfocusedContainerColor = SurfaceBlack
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text(text = "Description (optional)", color = MediumGray, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedBorderColor = PureWhite,
                        unfocusedBorderColor = Graphite,
                        focusedContainerColor = SurfaceBlack,
                        unfocusedContainerColor = SurfaceBlack
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "CANCEL",
                        color = MediumGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = confirmText,
                        color = if (name.isNotBlank()) PureWhite else MediumGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(enabled = name.isNotBlank()) {
                                onConfirm(name, description)
                            }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryNotice(
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(ElevatedSurface)
                .border(0.5.dp, Graphite, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_music_note),
                contentDescription = null,
                tint = MediumGray,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            color = PureWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = description,
            color = MediumGray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
