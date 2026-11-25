package com.nayama.music.ui.library

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.backend.LocalMediaScanner
import com.nayama.music.data.preferences.LibraryLayoutMode
import com.nayama.music.data.preferences.LibrarySortOrder
import com.nayama.music.data.preferences.RezonaPreferences
import com.nayama.music.data.repository.RezonaLibraryRepository
import com.nayama.music.data.repository.toRezonaTrack
import com.nayama.music.model.RezonaTrack
import com.nayama.music.playback.RezonaPlayerController
import com.nayama.music.ui.components.GlassSurface
import com.nayama.music.ui.components.GlassSurfaceVariant
import com.nayama.music.ui.components.LocalRezonaHazeState
import com.nayama.music.ui.components.RezonaActionDisc
import com.nayama.music.ui.components.RezonaAsyncCoverArt
import com.nayama.music.ui.components.RezonaEqualizerModalSheet
import com.nayama.music.ui.components.RezonaGlassCard
import com.nayama.music.ui.components.RezonaGlassDialog
import com.nayama.music.ui.components.RezonaGlassPill
import com.nayama.music.ui.components.RezonaGlassTextField
import com.nayama.music.ui.components.RezonaHairlineDivider
import com.nayama.music.ui.components.RezonaTrackMenuSheet
import com.nayama.music.ui.components.rezonaHazeSource
import com.nayama.music.ui.screens.rememberAutoFoldNestedScrollConnection
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.SpecularCardBorder
import com.nayama.music.ui.theme.SpecularDockBorder
import com.nayama.music.ui.theme.Typography
import kotlinx.coroutines.launch

private val VAULT_SECTIONS = listOf("Vault", "Crates", "Tracks", "Albums", "Artists")

/**
 * Rezona Original Spatial Library Screen ("Your Vault & Record Crates"):
 * - Top Masthead "Vault" + inline "+ Crate" action pill, Search & Sort discs in header
 * - Sculpted Frosted Capsule Switcher (Vault, Crates, Tracks, Albums, Artists)
 * - 2x2 Asymmetric Vault Bento Tiles (Liked Vault, Downloads, On Device, Top 50 Rotation)
 * - Vinyl-Sleeve Monthly & Weekly Time Capsules
 * - Record Crate Playlist Cards with stacked sleeve lip & instant open/import
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RezonaLibraryScreen(
    controller: RezonaPlayerController,
    isNavFolded: Boolean,
    onFoldChanged: (Boolean) -> Unit,
    onTrackSelected: (Int) -> Unit = {},
    onPlayTrack: (RezonaTrack) -> Unit = { controller.playTrack(it) },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { RezonaLibraryRepository.getInstance(context) }
    val preferences = remember { RezonaPreferences(context) }
    val scope = rememberCoroutineScope()
    val hazeState = LocalRezonaHazeState.current
    val scrollConnection = rememberAutoFoldNestedScrollConnection(onFoldChanged)

    val layoutMode by preferences.layoutModeFlow.collectAsState(initial = LibraryLayoutMode.GRID)
    val sortOrder by preferences.sortOrderFlow.collectAsState(initial = LibrarySortOrder.RECENT)

    val playlists by repository.getAllPlaylists().collectAsState(initial = emptyList())
    val likedEntities by repository.getLikedTracks().collectAsState(initial = emptyList())
    val downloadedEntities by repository.getDownloadedTracks().collectAsState(initial = emptyList())
    val allTrackEntities by repository.getAllTracks().collectAsState(initial = emptyList())
    val topMonthEntities by repository.getTopThisMonth(50).collectAsState(initial = emptyList())
    val topWeekEntities by repository.getTopThisWeek(50).collectAsState(initial = emptyList())
    val myTop50Entities by repository.getMostPlayed(50).collectAsState(initial = emptyList())

    var onDeviceTracks by remember { mutableStateOf<List<RezonaTrack>>(emptyList()) }
    LaunchedEffect(Unit) {
        onDeviceTracks = LocalMediaScanner.scanDeviceAudio(context)
    }

    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var activeCollectionModal by remember { mutableStateOf<Pair<String, List<RezonaTrack>>?>(null) }
    var showBackupSheet by remember { mutableStateOf(false) }
    var showFabMenuSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showInlineSearch by remember { mutableStateOf(false) }
    var inlineSearchQuery by remember { mutableStateOf("") }
    var menuTrack by remember { mutableStateOf<RezonaTrack?>(null) }
    var showEqualizer by remember { mutableStateOf(false) }

    if (selectedPlaylistId != null) {
        RezonaPlaylistDetailScreen(
            playlistId = selectedPlaylistId!!,
            onBack = { selectedPlaylistId = null },
            onTrackSelected = onTrackSelected,
            onPlayTrack = onPlayTrack,
            modifier = modifier
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { VAULT_SECTIONS.size })

    val sortedPlaylists = remember(playlists, sortOrder, inlineSearchQuery) {
        val q = inlineSearchQuery.trim().lowercase()
        val filtered = if (q.isEmpty()) playlists else playlists.filter { it.title.lowercase().contains(q) }
        when (sortOrder) {
            LibrarySortOrder.ALPHABETICAL -> filtered.sortedBy { it.title.lowercase() }
            LibrarySortOrder.RECENT -> filtered.sortedByDescending { it.updatedAt }
        }
    }

    val allSongs = remember(allTrackEntities, sortOrder, inlineSearchQuery) {
        val q = inlineSearchQuery.trim().lowercase()
        val mapped = allTrackEntities.map { it.toRezonaTrack() }.filter {
            q.isEmpty() || it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
        if (sortOrder == LibrarySortOrder.ALPHABETICAL) mapped.sortedBy { it.title.lowercase() } else mapped
    }

    val topMonthTracks = remember(topMonthEntities, allSongs) {
        topMonthEntities.map { it.toRezonaTrack() }.ifEmpty { allSongs.take(12) }
    }
    val topWeekTracks = remember(topWeekEntities, allSongs) {
        topWeekEntities.map { it.toRezonaTrack() }.ifEmpty { allSongs.take(8) }
    }
    val likedTracks = remember(likedEntities) { likedEntities.map { it.toRezonaTrack() } }
    val downloadedTracks = remember(downloadedEntities) { downloadedEntities.map { it.toRezonaTrack() } }
    val myTop50Tracks = remember(myTop50Entities, allSongs) {
        myTop50Entities.map { it.toRezonaTrack() }.ifEmpty { allSongs.take(25) }
    }

    val bottomPad = if (controller.currentTrack.value != null) 176.dp else 112.dp

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ── 1. VAULT MASTHEAD + HEADER ACTIONS ───────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Vault",
                        style = Typography.displayLarge.copy(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = PureWhite
                    )
                    Text(
                        text = "${likedTracks.size} liked · ${sortedPlaylists.size} crates · ${downloadedTracks.size} offline",
                        style = Typography.bodySmall.copy(fontSize = 12.sp),
                        color = SoftSilver,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RezonaActionDisc(
                        size = 42.dp,
                        onClick = { showInlineSearch = !showInlineSearch }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_rezona_search),
                            contentDescription = "Filter Vault",
                            tint = if (showInlineSearch) PureWhite else PearlWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    RezonaActionDisc(
                        size = 42.dp,
                        onClick = {
                            scope.launch {
                                val next = if (sortOrder == LibrarySortOrder.RECENT) {
                                    LibrarySortOrder.ALPHABETICAL
                                } else {
                                    LibrarySortOrder.RECENT
                                }
                                preferences.setSortOrder(next)
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_sort),
                            contentDescription = "Sort Order",
                            tint = PearlWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // Primary "+ Crate" pill right in the header
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .clip(CircleShape)
                            .background(PureWhite)
                            .clickable { showFabMenuSheet = true }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_add),
                                contentDescription = "New Crate or Import",
                                tint = Color(0xFF07090D),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Crate",
                                style = Typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color(0xFF07090D)
                            )
                        }
                    }
                }
            }

            if (showInlineSearch) {
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    RezonaGlassTextField(
                        value = inlineSearchQuery,
                        onValueChange = { inlineSearchQuery = it },
                        placeholder = "Filter crates, songs, artists..."
                    )
                }
            }

            // ── 2. FROSTED CAPSULE SECTION SWITCHER ──────────────────────────
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(VAULT_SECTIONS.size) { idx ->
                    val label = VAULT_SECTIONS[idx]
                    val isSelected = pagerState.currentPage == idx
                    RezonaGlassPill(
                        isActive = isSelected,
                        onClick = { scope.launch { pagerState.animateScrollToPage(idx) } }
                    ) {
                        Text(
                            text = label,
                            style = Typography.labelLarge.copy(
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) PureWhite else SoftSilver
                        )
                    }
                }
            }

            // ── 3. VAULT PAGER CONTENT ───────────────────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (VAULT_SECTIONS[page]) {
                    "Vault" -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollConnection)
                                .rezonaHazeSource(hazeState),
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 12.dp,
                                bottom = bottomPad
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 2x2 Asymmetric Vault Bento Tiles
                            item(key = "vault_bento_grid") {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        VaultBentoTile(
                                            iconRes = R.drawable.ic_heart_filled,
                                            iconTint = Color(0xFFFF5A6E),
                                            title = "Liked Songs",
                                            subtitle = "${likedTracks.size} tracks",
                                            accentGlow = Color(0x38FF5A6E),
                                            onClick = { activeCollectionModal = "Liked Songs" to likedTracks },
                                            modifier = Modifier.weight(1.15f)
                                        )
                                        VaultBentoTile(
                                            iconRes = R.drawable.ic_download_done,
                                            iconTint = PureWhite,
                                            title = "Offline",
                                            subtitle = "${downloadedTracks.size} saved",
                                            accentGlow = Color(0x22FFFFFF),
                                            onClick = { activeCollectionModal = "Offline Downloads" to downloadedTracks },
                                            modifier = Modifier.weight(0.85f)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        VaultBentoTile(
                                            iconRes = R.drawable.ic_music_note,
                                            iconTint = PearlWhite,
                                            title = "On Device",
                                            subtitle = "${onDeviceTracks.size} local files",
                                            accentGlow = Color(0x1EFFFFFF),
                                            onClick = { activeCollectionModal = "On Device Audio" to onDeviceTracks },
                                            modifier = Modifier.weight(0.85f)
                                        )
                                        VaultBentoTile(
                                            iconRes = R.drawable.ic_trending,
                                            iconTint = PureWhite,
                                            title = "Top 50 Spins",
                                            subtitle = "${myTop50Tracks.size} heavy rotation",
                                            accentGlow = Color(0x28FFFFFF),
                                            onClick = { activeCollectionModal = "My Top 50" to myTop50Tracks },
                                            modifier = Modifier.weight(1.15f)
                                        )
                                    }
                                }
                            }

                            // Vinyl Time Capsules: Monthly & Weekly
                            item(key = "vault_time_capsules") {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "Time Capsules",
                                        style = Typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                                        color = PureWhite
                                    )
                                    VinylTimeCapsuleCard(
                                        title = "Top This Month",
                                        subtitle = "${topMonthTracks.size} tracks defining your month",
                                        coverUrl = topMonthTracks.firstOrNull()?.coverArtUrl.orEmpty(),
                                        fallbackIndex = 0,
                                        onClick = { activeCollectionModal = "Top This Month" to topMonthTracks },
                                        onPlayNow = {
                                            if (topMonthTracks.isNotEmpty()) {
                                                controller.playQueue(topMonthTracks, 0, "Top This Month")
                                            }
                                        }
                                    )
                                    VinylTimeCapsuleCard(
                                        title = "Top This Week",
                                        subtitle = "${topWeekTracks.size} recent repeat favorites",
                                        coverUrl = topWeekTracks.firstOrNull()?.coverArtUrl.orEmpty(),
                                        fallbackIndex = 1,
                                        onClick = { activeCollectionModal = "Top This Week" to topWeekTracks },
                                        onPlayNow = {
                                            if (topWeekTracks.isNotEmpty()) {
                                                controller.playQueue(topWeekTracks, 0, "Top This Week")
                                            }
                                        }
                                    )
                                }
                            }

                            // User Record Crates (Playlists)
                            item(key = "vault_crates_header") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Your Crates",
                                        style = Typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                                        color = PureWhite
                                    )
                                    RezonaGlassPill(
                                        isActive = false,
                                        onClick = { showBackupSheet = true }
                                    ) {
                                        Text("Import / Backup", style = Typography.labelSmall, color = PearlWhite)
                                    }
                                }
                            }

                            if (sortedPlaylists.isEmpty()) {
                                item(key = "vault_no_crates") {
                                    RezonaEmptyLibraryState(
                                        title = "No custom crates yet",
                                        subtitle = "Tap + Crate at the top to build a playlist or import from YouTube Music / M3U."
                                    )
                                }
                            } else {
                                items(sortedPlaylists, key = { "pl_${it.id}" }) { pl ->
                                    RecordCrateRow(
                                        title = pl.title,
                                        subtitle = pl.description.ifBlank { "Custom Rezona Crate" },
                                        coverUrl = "",
                                        fallbackIndex = pl.coverIndex,
                                        onClick = { selectedPlaylistId = pl.id }
                                    )
                                }
                            }
                        }
                    }

                    "Crates" -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollConnection)
                                .rezonaHazeSource(hazeState),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomPad),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (sortedPlaylists.isEmpty()) {
                                item {
                                    RezonaEmptyLibraryState(
                                        title = "No playlists yet",
                                        subtitle = "Tap + Crate to start your first mix or import an existing playlist."
                                    )
                                }
                            } else if (layoutMode == LibraryLayoutMode.GRID) {
                                val rows = sortedPlaylists.chunked(2)
                                items(rows.size, key = { "crate_grid_$it" }) { rIdx ->
                                    val pair = rows[rIdx]
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        pair.forEach { pl ->
                                            RezonaGlassCard(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { selectedPlaylistId = pl.id },
                                                shape = RoundedCornerShape(22.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    RezonaAsyncCoverArt(
                                                        coverArtUrl = "",
                                                        fallbackIndex = pl.coverIndex,
                                                        shape = RoundedCornerShape(14.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .aspectRatio(1f)
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = pl.title,
                                                        style = Typography.titleMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                                        color = PureWhite,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = pl.description.ifBlank { "Playlist" },
                                                        style = Typography.bodySmall.copy(fontSize = 11.5.sp),
                                                        color = SmokySilver,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            } else {
                                items(sortedPlaylists, key = { "crate_list_${it.id}" }) { pl ->
                                    RecordCrateRow(
                                        title = pl.title,
                                        subtitle = pl.description.ifBlank { "Playlist" },
                                        coverUrl = "",
                                        fallbackIndex = pl.coverIndex,
                                        onClick = { selectedPlaylistId = pl.id }
                                    )
                                }
                            }
                        }
                    }

                    "Tracks" -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollConnection)
                                .rezonaHazeSource(hazeState),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomPad),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (allSongs.isEmpty()) {
                                item { RezonaEmptyLibraryState("No saved tracks yet", "Songs you play or like will appear in your Vault.") }
                            } else {
                                items(allSongs, key = { "song_${it.id}" }) { track ->
                                    GlassSurface(
                                        variant = GlassSurfaceVariant.Button,
                                        shape = RoundedCornerShape(18.dp),
                                        onClick = { onPlayTrack(track) }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RezonaAsyncCoverArt(
                                                coverArtUrl = track.coverArtUrl,
                                                fallbackIndex = track.index,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(track.title, style = Typography.titleMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = PureWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(track.artist, style = Typography.bodySmall.copy(fontSize = 12.sp), color = SmokySilver, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .clickable { menuTrack = track },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(painter = painterResource(id = R.drawable.ic_more_vert), contentDescription = null, tint = SoftSilver, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Albums" -> {
                        val albums = remember(allSongs) {
                            allSongs.filter { it.album.isNotBlank() }.distinctBy { it.album.lowercase() }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollConnection)
                                .rezonaHazeSource(hazeState),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomPad),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (albums.isEmpty()) {
                                item { RezonaEmptyLibraryState("No albums cataloged", "Albums from your library will collect here.") }
                            } else {
                                items(albums, key = { "alb_${it.album}_${it.id}" }) { item ->
                                    RecordCrateRow(
                                        title = item.album,
                                        subtitle = item.artist,
                                        coverUrl = item.coverArtUrl,
                                        onClick = { onPlayTrack(item) }
                                    )
                                }
                            }
                        }
                    }

                    else -> { // Artists
                        val artists = remember(allSongs) { allSongs.distinctBy { it.artist.substringBefore(",").trim() } }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollConnection)
                                .rezonaHazeSource(hazeState),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomPad),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (artists.isEmpty()) {
                                item { RezonaEmptyLibraryState("No artists yet", "Artists you listen to will gather here.") }
                            } else {
                                items(artists, key = { "art_${it.artist}_${it.id}" }) { item ->
                                    GlassSurface(
                                        variant = GlassSurfaceVariant.Button,
                                        shape = RoundedCornerShape(20.dp),
                                        onClick = { onPlayTrack(item) }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RezonaAsyncCoverArt(
                                                coverArtUrl = item.coverArtUrl,
                                                fallbackIndex = item.index,
                                                shape = CircleShape,
                                                modifier = Modifier
                                                    .size(50.dp)
                                                    .border(1.dp, SpecularCardBorder, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.artist, style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PureWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text("Tap to start artist mix", style = Typography.bodySmall.copy(fontSize = 11.5.sp), color = SmokySilver)
                                            }
                                            Icon(painter = painterResource(id = R.drawable.ic_play_arrow), contentDescription = null, tint = SoftSilver, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // "+ Crate" Action Sheet: New playlist / Import / Backup and restore / Toggle Grid
    if (showFabMenuSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFabMenuSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xF2101218),
            scrimColor = Color(0x88000000)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text("Vault Actions", style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PearlWhite)
                Spacer(modifier = Modifier.height(14.dp))
                RezonaHairlineDivider()
                VaultSheetOptionRow(iconRes = R.drawable.ic_add, title = "Create new crate (Playlist)") {
                    showFabMenuSheet = false
                    showCreatePlaylistDialog = true
                }
                VaultSheetOptionRow(iconRes = R.drawable.ic_playlist_add, title = "Import playlist (M3U, CSV, YouTube Music)") {
                    showFabMenuSheet = false
                    showBackupSheet = true
                }
                VaultSheetOptionRow(iconRes = R.drawable.ic_history, title = "Backup and restore JSON") {
                    showFabMenuSheet = false
                    showBackupSheet = true
                }
                VaultSheetOptionRow(
                    iconRes = R.drawable.ic_grid,
                    title = if (layoutMode == LibraryLayoutMode.GRID) "Switch Crates to List view" else "Switch Crates to Grid view"
                ) {
                    showFabMenuSheet = false
                    scope.launch {
                        val next = if (layoutMode == LibraryLayoutMode.GRID) LibraryLayoutMode.LIST else LibraryLayoutMode.GRID
                        preferences.setLayoutMode(next)
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        var newTitle by remember { mutableStateOf("") }
        RezonaGlassDialog(
            title = "New Crate",
            onDismissRequest = { showCreatePlaylistDialog = false },
            confirmText = "Create",
            onConfirm = {
                scope.launch {
                    val createdId = repository.createPlaylist(title = newTitle.ifBlank { "Untitled Crate" })
                    showCreatePlaylistDialog = false
                    selectedPlaylistId = createdId
                }
            },
            dismissText = "Cancel"
        ) {
            RezonaGlassTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                placeholder = "Crate name..."
            )
        }
    }

    if (activeCollectionModal != null) {
        val (colTitle, colTracks) = activeCollectionModal!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { activeCollectionModal = null },
            sheetState = sheetState,
            containerColor = Color(0xF2101218),
            scrimColor = Color(0x88000000)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp)
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(colTitle, style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PearlWhite)
                        Text("${colTracks.size} tracks", style = Typography.bodySmall, color = SmokySilver)
                    }
                    if (colTracks.isNotEmpty()) {
                        RezonaGlassPill(isActive = true, onClick = {
                            controller.playQueue(colTracks, 0, colTitle)
                            activeCollectionModal = null
                        }) {
                            Text("Play all", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                RezonaHairlineDivider()
                if (colTracks.isEmpty()) {
                    RezonaEmptyLibraryState("Empty collection", "Tracks matching $colTitle will show here.")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(colTracks, key = { "col_${colTitle}_${it.id}" }) { track ->
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onPlayTrack(track)
                                            activeCollectionModal = null
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RezonaAsyncCoverArt(track.coverArtUrl, track.index, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(track.title, style = Typography.titleMedium, color = PearlWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(track.artist, style = Typography.bodySmall, color = SmokySilver, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Box(
                                        modifier = Modifier.size(42.dp).clip(CircleShape).clickable { menuTrack = track },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(painter = painterResource(id = R.drawable.ic_more_vert), contentDescription = null, tint = SoftSilver, modifier = Modifier.size(18.dp))
                                    }
                                }
                                RezonaHairlineDivider(startIndent = 60.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBackupSheet) {
        RezonaBackupSheet(
            onDismiss = { showBackupSheet = false },
            onPlaylistCreated = { plId ->
                showBackupSheet = false
                selectedPlaylistId = plId
            }
        )
    }

    if (menuTrack != null) {
        RezonaTrackMenuSheet(
            track = menuTrack!!,
            controller = controller,
            showVolumeSlider = false,
            onDismiss = { menuTrack = null },
            onOpenEqualizer = { showEqualizer = true }
        )
    }

    if (showEqualizer) {
        RezonaEqualizerModalSheet(onDismiss = { showEqualizer = false })
    }
}

@Composable
private fun VaultBentoTile(
    iconRes: Int,
    iconTint: Color,
    title: String,
    subtitle: String,
    accentGlow: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        variant = GlassSurfaceVariant.Card,
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(22.dp),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(accentGlow, Color.Transparent),
                        radius = 220f
                    )
                )
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0x1FFFFFFF))
                    .align(Alignment.TopStart),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = title,
                    style = Typography.titleMedium.copy(fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold),
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = Typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = SoftSilver,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VinylTimeCapsuleCard(
    title: String,
    subtitle: String,
    coverUrl: String,
    fallbackIndex: Int,
    onClick: () -> Unit,
    onPlayNow: () -> Unit
) {
    RezonaGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(74.dp)
                    .height(60.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .offset(x = 18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF141720))
                        .border(0.8.dp, Color(0x38FFFFFF), CircleShape)
                )
                RezonaAsyncCoverArt(
                    coverArtUrl = coverUrl,
                    fallbackIndex = fallbackIndex,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .size(58.dp)
                        .border(0.8.dp, SpecularCardBorder, RoundedCornerShape(14.dp))
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = Typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = Typography.bodySmall.copy(fontSize = 12.sp),
                    color = SmokySilver,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(PureWhite)
                    .clickable(onClick = onPlayNow),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_play_arrow),
                    contentDescription = "Play $title",
                    tint = Color(0xFF07090D),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun RecordCrateRow(
    title: String,
    subtitle: String,
    coverUrl: String,
    fallbackIndex: Int = 0,
    onClick: () -> Unit
) {
    GlassSurface(
        variant = GlassSurfaceVariant.Button,
        shape = RoundedCornerShape(20.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RezonaAsyncCoverArt(
                coverArtUrl = coverUrl,
                fallbackIndex = fallbackIndex,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .size(52.dp)
                    .border(0.8.dp, SpecularCardBorder, RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = Typography.titleMedium.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold),
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = Typography.bodySmall.copy(fontSize = 12.sp),
                    color = SmokySilver,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = SoftSilver,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun VaultSheetOptionRow(
    iconRes: Int,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painter = painterResource(id = iconRes), contentDescription = null, tint = PureWhite, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = title, style = Typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = PearlWhite, modifier = Modifier.weight(1f))
        Icon(painter = painterResource(id = R.drawable.ic_chevron_right), contentDescription = null, tint = SoftSilver, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun RezonaEmptyLibraryState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_rezona_logo),
            contentDescription = "Rezona",
            modifier = Modifier.size(56.dp).alpha(0.75f)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(text = title, style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PearlWhite, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = subtitle, style = Typography.bodySmall, color = SmokySilver, textAlign = TextAlign.Center)
    }
}
