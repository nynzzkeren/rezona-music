package com.nayama.music.ui.search

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nayama.music.R
import com.nayama.music.backend.LocalMediaScanner
import com.nayama.music.backend.MusicEngineRepository
import com.nayama.music.backend.innertube.InnerTubeFilter
import com.nayama.music.data.repository.RezonaLibraryRepository
import com.nayama.music.data.repository.toRezonaTrack
import com.nayama.music.model.RezonaTrack
import com.nayama.music.playback.RezonaPlayerController
import com.nayama.music.ui.components.GlassSurface
import com.nayama.music.ui.components.GlassSurfaceVariant
import com.nayama.music.ui.components.LocalRezonaHazeState
import com.nayama.music.ui.components.RezonaAsyncCoverArt
import com.nayama.music.ui.components.RezonaEqualizerModalSheet
import com.nayama.music.ui.components.RezonaGlassButton
import com.nayama.music.ui.components.RezonaGlassCard
import com.nayama.music.ui.components.RezonaGlassPill
import com.nayama.music.ui.components.RezonaHairlineDivider
import com.nayama.music.ui.components.RezonaTrackMenuSheet
import com.nayama.music.ui.components.rezonaHazeSource
import com.nayama.music.ui.home.REAL_POPULAR_SEEDS
import com.nayama.music.ui.screens.rememberAutoFoldNestedScrollConnection
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.SpecularDockBorder
import com.nayama.music.ui.theme.Typography
import com.nayama.music.ui.util.GenerativeMoodPattern
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BROWSE_GENRES = listOf(
    "Pop" to 0,
    "Dangdut" to 1,
    "Hip-hop" to 2,
    "Indie" to 3,
    "R&B" to 4,
    "Lo-fi" to 5,
    "Electronic" to 6,
    "Jazz" to 7
)

private val CONTENT_FILTERS = listOf("All", "Songs", "Videos", "Albums", "Artists", "Playlists")

/**
 * Stage B Search Screen (`search_home.jpg` + `search_results.jpg` reference structure):
 * - Header: back arrow + glass pill search field ("Search songs, albums, artists...") + clear button
 * - Segmented switch: Online | On device (MediaStore with runtime permission & rationale)
 * - Idle state: Recently played covers, Most played numbered rows, Favorite artists circles, Browse all 2-col generative tiles
 * - Typing state: Live suggestions list (magnifier, text, diagonal insert arrow) + unboxed plain result rows (56dp cover,
 *   play overlay on first row, 3-dot menu, NO source badges) + infinite scroll continuation tokens
 * - Floating glass Voice Mic button anchored at bottom-right above dock/mini-player
 */
@Composable
fun RezonaSearchScreen(
    controller: RezonaPlayerController,
    isNavFolded: Boolean,
    onFoldChanged: (Boolean) -> Unit,
    onBack: () -> Unit = {},
    onTrackSelected: (RezonaTrack) -> Unit = { controller.playTrack(it, autoQueueRadio = true) },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hazeState = LocalRezonaHazeState.current
    val listState = rememberLazyListState()
    val scrollConnection = rememberAutoFoldNestedScrollConnection(onFoldChanged)
    val musicEngine = remember { MusicEngineRepository.getInstance(context) }
    val libraryRepo = remember { RezonaLibraryRepository.getInstance(context) }

    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var isOnDeviceMode by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }

    // Permissions & On-Device Audio
    val requiredAudioPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, requiredAudioPerm) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                searchQuery = spoken
            }
        }
    }

    // Idle history feeds from Room
    val recentEntities by libraryRepo.getRecentHistory(15).collectAsState(initial = emptyList())
    val mostPlayedEntities by libraryRepo.getMostPlayed(8).collectAsState(initial = emptyList())
    val recentlyPlayedTracks = remember(recentEntities) {
        recentEntities.map { it.toRezonaTrack() }.distinctBy { it.id }.ifEmpty { REAL_POPULAR_SEEDS }
    }
    val mostPlayedTracks = remember(mostPlayedEntities, recentlyPlayedTracks) {
        mostPlayedEntities.map { it.toRezonaTrack() }.ifEmpty { recentlyPlayedTracks }.take(6)
    }
    val favoriteArtists = remember(mostPlayedTracks, recentlyPlayedTracks) {
        val pool = (mostPlayedTracks + recentlyPlayedTracks).distinctBy {
            it.artist.substringBefore(" ft.").substringBefore(",").trim()
        }
        pool.take(8)
    }

    // Search & Continuation State
    var remoteResults by remember { mutableStateOf<List<RezonaTrack>>(emptyList()) }
    var continuationToken by remember { mutableStateOf<String?>(null) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var searchSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSearchingRemote by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchRetryTrigger by remember { mutableIntStateOf(0) }

    var onDeviceTracks by remember { mutableStateOf<List<RezonaTrack>>(emptyList()) }
    var menuTrack by remember { mutableStateOf<RezonaTrack?>(null) }
    var showEqualizer by remember { mutableStateOf(false) }

    // 200ms debounce
    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            debouncedQuery = ""
            remoteResults = emptyList()
            continuationToken = null
            searchSuggestions = emptyList()
            isSearchingRemote = false
            searchError = null
        } else {
            delay(200L)
            debouncedQuery = q
        }
    }

    // Load local MediaStore audio in On-Device mode
    LaunchedEffect(isOnDeviceMode, hasAudioPermission, debouncedQuery) {
        if (isOnDeviceMode && hasAudioPermission) {
            onDeviceTracks = LocalMediaScanner.scanDeviceAudio(context, debouncedQuery)
        }
    }

    // Progressive YouTube Music search + suggestions + continuation token
    LaunchedEffect(debouncedQuery, selectedFilter, isOnDeviceMode, searchRetryTrigger) {
        if (!isOnDeviceMode && debouncedQuery.isNotEmpty()) {
            isSearchingRemote = true
            searchError = null
            continuationToken = null

            launch {
                searchSuggestions = runCatching {
                    musicEngine.innerTubeRepo.getSearchSuggestions(debouncedQuery)
                }.getOrDefault(emptyList()).take(6)
            }

            try {
                musicEngine.searchTracksProgressive(debouncedQuery, selectedFilter.uppercase()).collect { update ->
                    remoteResults = update.tracks
                    isSearchingRemote = update.isSearching
                    if (update.continuation != null) {
                        continuationToken = update.continuation
                    }
                }
            } catch (e: Throwable) {
                if (remoteResults.isEmpty()) {
                    searchError = e.message ?: "Search failed"
                }
                isSearchingRemote = false
            }
        }
    }

    // Infinite scroll trigger near bottom
    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 5 && lastVisible >= total - 4
        }
    }
    LaunchedEffect(shouldLoadMore, continuationToken, isOnDeviceMode) {
        val token = continuationToken
        if (!isOnDeviceMode && shouldLoadMore && !token.isNullOrBlank() && !isLoadingMore && debouncedQuery.isNotEmpty()) {
            isLoadingMore = true
            val nextPage = musicEngine.innerTubeRepo.searchContinuation(token)
            if (nextPage.items.isNotEmpty()) {
                val merged = (remoteResults + nextPage.items).distinctBy { it.id }.mapIndexed { idx, t -> t.copy(index = idx) }
                remoteResults = merged
            }
            continuationToken = nextPage.continuation
            isLoadingMore = false
        }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomPad = if (controller.currentTrack.value != null) 175.dp else 112.dp

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollConnection)
                .rezonaHazeSource(hazeState),
            contentPadding = PaddingValues(top = statusTop + 12.dp, bottom = bottomPad),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ── 1. HEADER: Back Arrow + Glass Pill Search Field ──────────────
            item(key = "search_top_bar") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (searchQuery.isNotEmpty()) searchQuery = "" else onBack()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrow_back),
                                contentDescription = "Back",
                                tint = PureWhite,
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        GlassSurface(
                            variant = GlassSurfaceVariant.Field,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            isFocused = searchQuery.isNotEmpty()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_rezona_search),
                                    contentDescription = null,
                                    tint = SoftSilver,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search songs, albums, artists...",
                                            style = Typography.bodyMedium.copy(fontSize = 14.sp),
                                            color = SmokySilver,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = TextStyle(color = PureWhite, fontSize = 14.sp),
                                        cursorBrush = SolidColor(PureWhite),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .clickable { searchQuery = "" },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_close),
                                            contentDescription = "Clear search",
                                            tint = SoftSilver,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── 2. SEGMENTED GLASS SWITCH: Online | On device ────────
                    val switchShape = RoundedCornerShape(24.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(switchShape)
                            .background(Brush.verticalGradient(listOf(Color(0x1CFFFFFF), Color(0x0AFFFFFF))))
                            .border(0.75.dp, SpecularDockBorder, switchShape)
                            .padding(3.dp)
                    ) {
                        SegmentedOption(
                            label = "Online",
                            isSelected = !isOnDeviceMode,
                            onClick = { isOnDeviceMode = false },
                            modifier = Modifier.weight(1f)
                        )
                        SegmentedOption(
                            label = "On device",
                            isSelected = isOnDeviceMode,
                            onClick = { isOnDeviceMode = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (isOnDeviceMode) {
                // ── ON-DEVICE AUDIO MODE (MediaStore + Rationale) ────────────
                if (!hasAudioPermission) {
                    item(key = "on_device_rationale") {
                        RezonaGlassCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Local Audio Access", style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PearlWhite)
                                Text(
                                    text = "Allow Rezona to scan audio files stored on your device so you can play local MP3, FLAC, and M4A tracks alongside your library.",
                                    style = Typography.bodyMedium,
                                    color = SmokySilver
                                )
                                RezonaGlassButton(
                                    text = "Grant Audio Access",
                                    isPrimary = true,
                                    onClick = { permissionLauncher.launch(requiredAudioPerm) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                } else if (onDeviceTracks.isEmpty()) {
                    item(key = "on_device_empty") {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No local audio files found on device.", style = Typography.bodyMedium, color = SmokySilver)
                        }
                    }
                } else {
                    itemsIndexed(onDeviceTracks, key = { _, t -> t.id }) { idx, track ->
                        V2SearchResultRow(
                            track = track,
                            isFirstRow = idx == 0,
                            onPlay = { controller.playTrack(track, queueTitle = "On Device Audio") },
                            onMoreClick = { menuTrack = track },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            } else if (debouncedQuery.isEmpty()) {
                // ── IDLE STATE (`search_home.jpg`) ───────────────────────────
                item(key = "idle_recently_played") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Recently played",
                            style = Typography.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                            color = PureWhite,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(recentlyPlayedTracks.take(10), key = { "rp_${it.id}" }) { track ->
                                Column(
                                    modifier = Modifier
                                        .width(148.dp)
                                        .clickable {
                                            scope.launch { libraryRepo.saveSearchQuery(track.title) }
                                            onTrackSelected(track)
                                        }
                                ) {
                                    RezonaAsyncCoverArt(
                                        coverArtUrl = track.coverArtUrl,
                                        fallbackIndex = track.index,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.size(148.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = track.title, style = Typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = PearlWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(text = track.artist, style = Typography.bodySmall, color = SmokySilver, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                item(key = "idle_most_played") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Most played",
                            style = Typography.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                            color = PureWhite
                        )
                        mostPlayedTracks.forEachIndexed { idx, track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onTrackSelected(track) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = SoftSilver,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(26.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                RezonaAsyncCoverArt(
                                    coverArtUrl = track.coverArtUrl,
                                    fallbackIndex = track.index,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = track.title, style = Typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = PearlWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(text = track.artist, style = Typography.bodySmall, color = SmokySilver, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                item(key = "idle_favorite_artists") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Favorite artists",
                            style = Typography.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                            color = PureWhite,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(favoriteArtists, key = { "fav_art_${it.artist}" }) { item ->
                                val cleanArtist = item.artist.substringBefore(" ft.").substringBefore(",").trim()
                                Column(
                                    modifier = Modifier
                                        .width(86.dp)
                                        .clickable { searchQuery = cleanArtist },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    RezonaAsyncCoverArt(
                                        coverArtUrl = item.coverArtUrl,
                                        fallbackIndex = item.index,
                                        shape = CircleShape,
                                        modifier = Modifier.size(84.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = cleanArtist,
                                        style = Typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                        color = PearlWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "idle_browse_all") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Browse all",
                            style = Typography.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                            color = PureWhite
                        )
                        BROWSE_GENRES.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                pair.forEach { (genre, patternIdx) ->
                                    GlassSurface(
                                        variant = GlassSurfaceVariant.Button,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(68.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        onClick = { searchQuery = "$genre hits" }
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            GenerativeMoodPattern(
                                                index = patternIdx,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .alpha(0.38f)
                                            )
                                            Text(
                                                text = genre,
                                                style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                                                color = PureWhite,
                                                modifier = Modifier
                                                    .align(Alignment.CenterStart)
                                                    .padding(horizontal = 16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ── TYPING & RESULTS STATE (`search_results.jpg`) ────────────
                item(key = "search_filter_chips") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(CONTENT_FILTERS) { filter ->
                            val isActive = selectedFilter == filter
                            RezonaGlassPill(
                                isActive = isActive,
                                onClick = { selectedFilter = filter }
                            ) {
                                Text(
                                    text = filter,
                                    style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isActive) PureWhite else SoftSilver
                                )
                            }
                        }
                    }
                }

                // Live Autocomplete Suggestions (magnifier + text + diagonal insert arrow)
                if (searchSuggestions.isNotEmpty()) {
                    item(key = "live_suggestions") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        ) {
                            searchSuggestions.forEach { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            searchQuery = suggestion
                                            scope.launch { libraryRepo.saveSearchQuery(suggestion) }
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_rezona_search),
                                        contentDescription = null,
                                        tint = SoftSilver,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = suggestion,
                                        style = Typography.bodyLarge.copy(fontSize = 14.5.sp),
                                        color = PearlWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .clickable { searchQuery = suggestion },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_arrow_insert),
                                            contentDescription = "Insert into search field",
                                            tint = SoftSilver,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isSearchingRemote && remoteResults.isEmpty()) {
                    item(key = "searching_state") {
                        Box(modifier = Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                            Text("Searching YouTube Music for \"$debouncedQuery\"...", style = Typography.bodyMedium, color = SmokySilver)
                        }
                    }
                } else if (remoteResults.isEmpty() && searchError != null) {
                    item(key = "error_state") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(searchError ?: "Search failed", style = Typography.bodyMedium, color = PearlWhite)
                            RezonaGlassPill(isActive = true, onClick = { searchRetryTrigger++ }) {
                                Text("Retry", style = Typography.labelLarge, color = PureWhite)
                            }
                        }
                    }
                } else {
                    itemsIndexed(remoteResults, key = { idx, t -> "sr_${t.id}_$idx" }) { idx, track ->
                        V2SearchResultRow(
                            track = track,
                            isFirstRow = idx == 0,
                            onPlay = {
                                scope.launch { libraryRepo.saveSearchQuery(debouncedQuery) }
                                onTrackSelected(track)
                            },
                            onMoreClick = { menuTrack = track },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }

                if (isLoadingMore) {
                    item(key = "loading_more_continuation") {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("Loading more results...", style = Typography.bodySmall, color = SmokySilver)
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(statusTop + 14.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xDC07080C), Color(0x8807080C), Color.Transparent)
                    )
                )
        )

        // ── FLOATING GLASS VOICE SEARCH MIC BUTTON ───────────────────────────
        val hasTrack = controller.currentTrack.value != null
        val bottomMicOffset = when {
            isNavFolded -> 78.dp
            hasTrack -> 160.dp
            else -> 104.dp
        }
        GlassSurface(
            variant = GlassSurfaceVariant.Floating,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = bottomMicOffset)
                .size(52.dp),
            shape = CircleShape,
            onClick = {
                val started = runCatching {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a song, artist, or lyrics...")
                    }
                    voiceLauncher.launch(intent)
                    true
                }.getOrDefault(false)
                if (!started) {
                    Toast.makeText(context, "Voice recognition not available on this device", Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_mic),
                contentDescription = "Voice Search",
                tint = PureWhite,
                modifier = Modifier.size(22.dp)
            )
        }
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
private fun SegmentedOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(21.dp)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .then(
                if (isSelected) {
                    Modifier
                        .background(Brush.verticalGradient(listOf(Color(0x36FFFFFF), Color(0x16FFFFFF))))
                        .border(0.75.dp, Color(0x52FFFFFF), shape)
                } else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = Typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isSelected) PureWhite else SoftSilver
        )
    }
}

/**
 * Unboxed plain result row (`56dp` cover, play overlay on first row, title, artist, 3-dot icon, NO source badge).
 */
@Composable
private fun V2SearchResultRow(
    track: RezonaTrack,
    isFirstRow: Boolean,
    onPlay: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onPlay)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            RezonaAsyncCoverArt(
                coverArtUrl = track.coverArtUrl,
                fallbackIndex = track.index,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxSize()
            )
            if (isFirstRow) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xB8000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_play_arrow),
                        contentDescription = null,
                        tint = PureWhite,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = Typography.titleMedium.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
                color = PearlWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = Typography.bodySmall.copy(fontSize = 12.5.sp),
                color = SmokySilver,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onMoreClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_more_vert),
                contentDescription = "Track Options",
                tint = SoftSilver,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}
