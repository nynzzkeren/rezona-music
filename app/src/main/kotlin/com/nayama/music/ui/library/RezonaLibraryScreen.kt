package com.nayama.music.ui.library

import android.widget.Toast
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
import com.nayama.music.ui.components.RezonaAsyncCoverArt
import com.nayama.music.ui.components.RezonaEqualizerModalSheet
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
import com.nayama.music.ui.theme.SpecularDockBorder
import com.nayama.music.ui.theme.Typography
import kotlinx.coroutines.launch

private val LIBRARY_TABS = listOf("All", "Playlists", "Songs", "Albums", "Artists", "Podcasts")

/**
 * Stage G Library Screen (`library.jpg` reference):
 * - Title "Library"
 * - Swipeable text tabs with white underline indicator: All, Playlists, Songs, Albums, Artists, Podcasts
 * - Controls row: Sort pill ("Date added" / "A–Z" + arrow), Search & Grid/List icons
 * - Two large square cover cards at top of All: "Top this month" and "Top this week"
 * - Plain grouped Collection rows with chevron: Liked, Downloaded, Cached, On device, My Top 50
 * - Floating glass "+" pill button at bottom-right above mini-player/dock for New playlist / Import / Backup
 * - Empty states using transparent Rezona concentric arcs glyph
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

    val pagerState = rememberPagerState(pageCount = { LIBRARY_TABS.size })

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

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ── 1. HEADER TITLE ──────────────────────────────────────────────
            Text(
                text = "Library",
                style = Typography.displayLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                color = PureWhite,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            // ── 2. SWIPEABLE TEXT TABS WITH UNDERLINE INDICATOR ──────────────
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(LIBRARY_TABS.size) { idx ->
                    val title = LIBRARY_TABS[idx]
                    val isSelected = pagerState.currentPage == idx
                    Column(
                        modifier = Modifier
                            .clickable { scope.launch { pagerState.animateScrollToPage(idx) } }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = title,
                            style = Typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) PureWhite else SmokySilver
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .height(2.dp)
                                .width(if (isSelected) 28.dp else 0.dp)
                                .background(PureWhite)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── 3. CONTROLS ROW (Sort pill + Search & Grid/List toggles) ─────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RezonaGlassPill(
                    isActive = false,
                    onClick = {
                        scope.launch {
                            val next = if (sortOrder == LibrarySortOrder.RECENT) LibrarySortOrder.ALPHABETICAL else LibrarySortOrder.RECENT
                            preferences.setSortOrder(next)
                        }
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (sortOrder == LibrarySortOrder.RECENT) "Date added" else "Alphabetical",
                            style = Typography.labelLarge.copy(fontSize = 12.5.sp),
                            color = PearlWhite
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_sort),
                            contentDescription = "Sort",
                            tint = SoftSilver,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { showInlineSearch = !showInlineSearch },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_rezona_search),
                            contentDescription = "Filter Library",
                            tint = if (showInlineSearch) PureWhite else SoftSilver,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable {
                                scope.launch {
                                    val next = if (layoutMode == LibraryLayoutMode.GRID) LibraryLayoutMode.LIST else LibraryLayoutMode.GRID
                                    preferences.setLayoutMode(next)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_grid),
                            contentDescription = "Toggle Grid/List",
                            tint = SoftSilver,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            if (showInlineSearch) {
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    RezonaGlassTextField(
                        value = inlineSearchQuery,
                        onValueChange = { inlineSearchQuery = it },
                        placeholder = "Filter playlists or songs..."
                    )
                }
            }

            // ── 4. SWIPEABLE PAGER CONTENT ───────────────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (LIBRARY_TABS[page]) {
                    "All" -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollConnection)
                                .rezonaHazeSource(hazeState),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 180.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            // Two large square cover cards: Top this month & Top this week
                            item(key = "lib_top_cards") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    AutoTopPlaylistCard(
                                        title = "Top this month",
                                        count = topMonthTracks.size,
                                        coverUrl = topMonthTracks.firstOrNull()?.coverArtUrl.orEmpty(),
                                        fallbackIndex = 0,
                                        onClick = { activeCollectionModal = "Top this month" to topMonthTracks },
                                        modifier = Modifier.weight(1f)
                                    )
                                    AutoTopPlaylistCard(
                                        title = "Top this week",
                                        count = topWeekTracks.size,
                                        coverUrl = topWeekTracks.firstOrNull()?.coverArtUrl.orEmpty(),
                                        fallbackIndex = 2,
                                        onClick = { activeCollectionModal = "Top this week" to topWeekTracks },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // "Collection" plain unboxed rows
                            item(key = "lib_collection") {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Collection",
                                        style = Typography.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                                        color = PureWhite,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )

                                    CollectionPlainRow(
                                        iconRes = R.drawable.ic_heart_filled,
                                        title = "Liked",
                                        countLabel = "${likedTracks.size}",
                                        onClick = { activeCollectionModal = "Liked" to likedTracks }
                                    )
                                    CollectionPlainRow(
                                        iconRes = R.drawable.ic_download_done,
                                        title = "Downloaded",
                                        countLabel = "${downloadedTracks.size}",
                                        onClick = { activeCollectionModal = "Downloaded" to downloadedTracks }
                                    )
                                    CollectionPlainRow(
                                        iconRes = R.drawable.ic_cloud_done,
                                        title = "Cached",
                                        countLabel = "${allSongs.size}",
                                        onClick = { activeCollectionModal = "Cached" to allSongs }
                                    )
                                    CollectionPlainRow(
                                        iconRes = R.drawable.ic_queue_music,
                                        title = "On device",
                                        countLabel = "${onDeviceTracks.size}",
                                        onClick = { activeCollectionModal = "On device" to onDeviceTracks }
                                    )
                                    CollectionPlainRow(
                                        iconRes = R.drawable.ic_trending,
                                        title = "My Top 50",
                                        countLabel = "${myTop50Tracks.size}",
                                        onClick = { activeCollectionModal = "My Top 50" to myTop50Tracks }
                                    )
                                }
                            }

                            if (sortedPlaylists.isNotEmpty()) {
                                item(key = "lib_playlists_header") {
                                    Text(
                                        text = "Your playlists",
                                        style = Typography.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                                        color = PureWhite
                                    )
                                }
                                items(sortedPlaylists, key = { "pl_${it.id}" }) { pl ->
                                    val tracks by repository.getPlaylistTracks(pl.id).collectAsState(initial = emptyList())
                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedPlaylistId = pl.id }
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RezonaAsyncCoverArt(
                                                coverArtUrl = tracks.firstOrNull()?.toRezonaTrack()?.coverArtUrl.orEmpty(),
                                                fallbackIndex = pl.coverIndex,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.size(50.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(pl.title, style = Typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = PearlWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text("${tracks.size} songs", style = Typography.bodySmall, color = SmokySilver)
                                            }
                                            Icon(painter = painterResource(id = R.drawable.ic_chevron_right), contentDescription = null, tint = SoftSilver, modifier = Modifier.size(18.dp))
                                        }
                                        RezonaHairlineDivider(startIndent = 62.dp)
                                    }
                                }
                            }
                        }
                    }

                    "Playlists" -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().nestedScroll(scrollConnection).rezonaHazeSource(hazeState),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 180.dp)
                        ) {
                            if (sortedPlaylists.isEmpty()) {
                                item { RezonaEmptyLibraryState("No playlists yet", "Tap + to create or import a playlist") }
                            } else {
                                items(sortedPlaylists, key = { "p_tab_${it.id}" }) { pl ->
                                    val tracks by repository.getPlaylistTracks(pl.id).collectAsState(initial = emptyList())
                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedPlaylistId = pl.id }
                                                .padding(vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RezonaAsyncCoverArt(
                                                coverArtUrl = tracks.firstOrNull()?.toRezonaTrack()?.coverArtUrl.orEmpty(),
                                                fallbackIndex = pl.coverIndex,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.size(54.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(pl.title, style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PearlWhite)
                                                Text("${tracks.size} songs · ${pl.sourceBlend}", style = Typography.bodySmall, color = SmokySilver)
                                            }
                                            Icon(painter = painterResource(id = R.drawable.ic_chevron_right), contentDescription = null, tint = SoftSilver, modifier = Modifier.size(18.dp))
                                        }
                                        RezonaHairlineDivider(startIndent = 66.dp)
                                    }
                                }
                            }
                        }
                    }

                    "Songs" -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().nestedScroll(scrollConnection).rezonaHazeSource(hazeState),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 180.dp)
                        ) {
                            if (allSongs.isEmpty()) {
                                item { RezonaEmptyLibraryState("No saved songs", "Play or like tracks on Home or Search") }
                            } else {
                                items(allSongs, key = { "song_${it.id}" }) { track ->
                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onPlayTrack(track) }
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RezonaAsyncCoverArt(track.coverArtUrl, track.index, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(50.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(track.title, style = Typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = PearlWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(track.artist, style = Typography.bodySmall, color = SmokySilver, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Box(
                                                modifier = Modifier.size(44.dp).clip(CircleShape).clickable { menuTrack = track },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(painter = painterResource(id = R.drawable.ic_more_vert), contentDescription = null, tint = SoftSilver, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        RezonaHairlineDivider(startIndent = 62.dp)
                                    }
                                }
                            }
                        }
                    }

                    "Albums" -> {
                        val albums = remember(allSongs) { allSongs.distinctBy { it.album }.filter { it.album != "YouTube Music" } }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().nestedScroll(scrollConnection).rezonaHazeSource(hazeState),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 180.dp)
                        ) {
                            if (albums.isEmpty()) {
                                item { RezonaEmptyLibraryState("No saved albums yet", "Albums from tracks you play appear here") }
                            } else {
                                items(albums, key = { "alb_${it.id}" }) { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { onPlayTrack(item) }.padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RezonaAsyncCoverArt(item.coverArtUrl, item.index, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(52.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.album, style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PearlWhite)
                                            Text(item.artist, style = Typography.bodySmall, color = SmokySilver)
                                        }
                                    }
                                    RezonaHairlineDivider(startIndent = 64.dp)
                                }
                            }
                        }
                    }

                    "Artists" -> {
                        val artists = remember(allSongs) { allSongs.distinctBy { it.artist.substringBefore(",").trim() } }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().nestedScroll(scrollConnection).rezonaHazeSource(hazeState),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 180.dp)
                        ) {
                            if (artists.isEmpty()) {
                                item { RezonaEmptyLibraryState("No artists yet", "Artists you listen to will gather here") }
                            } else {
                                items(artists, key = { "art_${it.artist}" }) { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { onPlayTrack(item) }.padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RezonaAsyncCoverArt(item.coverArtUrl, item.index, shape = CircleShape, modifier = Modifier.size(50.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(item.artist, style = Typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = PearlWhite)
                                    }
                                    RezonaHairlineDivider(startIndent = 62.dp)
                                }
                            }
                        }
                    }

                    else -> { // Podcasts
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().nestedScroll(scrollConnection).rezonaHazeSource(hazeState),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 180.dp)
                        ) {
                            item {
                                RezonaEmptyLibraryState(
                                    title = "No podcasts saved",
                                    subtitle = "Podcast episodes from YouTube Music will appear here when added"
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── FLOATING GLASS "+" BUTTON (library.jpg bottom-right) ─────────────
        val fabBottomOffset = if (isNavFolded) 86.dp else 160.dp
        GlassSurface(
            variant = GlassSurfaceVariant.Floating,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = fabBottomOffset)
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            onClick = { showFabMenuSheet = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(painter = painterResource(id = R.drawable.ic_add), contentDescription = "Create or Import", tint = PureWhite, modifier = Modifier.size(20.dp))
                Icon(painter = painterResource(id = R.drawable.ic_chevron_right), contentDescription = null, tint = SoftSilver, modifier = Modifier.size(16.dp))
            }
        }
    }

    // Floating "+" Action Sheet: New playlist / Import / Backup and restore
    if (showFabMenuSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFabMenuSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xEC101218),
            scrimColor = Color(0x88000000)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text("Add to Library", style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PearlWhite)
                Spacer(modifier = Modifier.height(14.dp))
                RezonaHairlineDivider()
                CollectionPlainRow(iconRes = R.drawable.ic_add, title = "New playlist", countLabel = null) {
                    showFabMenuSheet = false
                    showCreatePlaylistDialog = true
                }
                CollectionPlainRow(iconRes = R.drawable.ic_playlist_add, title = "Import playlist (M3U, CSV, YouTube Music)", countLabel = null) {
                    showFabMenuSheet = false
                    showBackupSheet = true
                }
                CollectionPlainRow(iconRes = R.drawable.ic_history, title = "Backup and restore", countLabel = null) {
                    showFabMenuSheet = false
                    showBackupSheet = true
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        var newTitle by remember { mutableStateOf("") }
        RezonaGlassDialog(
            title = "New Playlist",
            onDismissRequest = { showCreatePlaylistDialog = false },
            confirmText = "Create",
            onConfirm = {
                scope.launch {
                    val createdId = repository.createPlaylist(title = newTitle.ifBlank { "Untitled Mix" })
                    showCreatePlaylistDialog = false
                    selectedPlaylistId = createdId
                }
            },
            dismissText = "Cancel"
        ) {
            RezonaGlassTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                placeholder = "Playlist name..."
            )
        }
    }

    if (activeCollectionModal != null) {
        val (colTitle, colTracks) = activeCollectionModal!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { activeCollectionModal = null },
            sheetState = sheetState,
            containerColor = Color(0xEC101218),
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
                    RezonaEmptyLibraryState("Empty collection", "Tracks matching $colTitle will show here")
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
                                    RezonaAsyncCoverArt(track.coverArtUrl, track.index, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(48.dp))
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
private fun AutoTopPlaylistCard(
    title: String,
    count: Int,
    coverUrl: String,
    fallbackIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        RezonaAsyncCoverArt(
            coverArtUrl = coverUrl,
            fallbackIndex = fallbackIndex,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = title, style = Typography.titleMedium.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold), color = PearlWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = "$count songs", style = Typography.bodySmall.copy(fontSize = 12.sp), color = SmokySilver)
    }
}

@Composable
private fun CollectionPlainRow(
    iconRes: Int,
    title: String,
    countLabel: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painter = painterResource(id = iconRes), contentDescription = null, tint = PureWhite, modifier = Modifier.size(21.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, style = Typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = PearlWhite, modifier = Modifier.weight(1f))
        if (!countLabel.isNullOrBlank()) {
            Text(text = countLabel, style = Typography.bodySmall, color = SmokySilver, modifier = Modifier.padding(end = 6.dp))
        }
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
