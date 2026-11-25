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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nayama.music.R
import com.nayama.music.backend.LocalMediaScanner
import com.nayama.music.backend.MusicEngineRepository
import com.nayama.music.data.repository.RezonaLibraryRepository
import com.nayama.music.data.repository.toRezonaTrack
import com.nayama.music.model.RezonaTrack
import com.nayama.music.playback.RezonaPlayerController
import com.nayama.music.ui.components.GlassSurface
import com.nayama.music.ui.components.GlassSurfaceVariant
import com.nayama.music.ui.components.LocalRezonaHazeState
import com.nayama.music.ui.components.RezonaAsyncCoverArt
import com.nayama.music.ui.components.RezonaEqualizerModalSheet
import com.nayama.music.ui.components.RezonaGlassCard
import com.nayama.music.ui.components.RezonaGlassPill
import com.nayama.music.ui.components.RezonaTrackMenuSheet
import com.nayama.music.ui.components.rezonaHazeSource
import com.nayama.music.ui.home.REAL_POPULAR_SEEDS
import com.nayama.music.ui.screens.rememberAutoFoldNestedScrollConnection
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.SpecularCardBorder
import com.nayama.music.ui.theme.SpecularDockBorder
import com.nayama.music.ui.theme.Typography
import com.nayama.music.ui.util.GenerativeMoodPattern
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SONIC_TERRITORIES = listOf(
    Triple("Pop & Viral", "Global chart toppers", 0),
    Triple("Indie & Alt", "Warm guitars & bedroom pop", 3),
    Triple("Lo-fi Study", "Dusty beats & rain", 5),
    Triple("Hip-hop", "808s & sharp bars", 2),
    Triple("R&B Soul", "Velvet late-night vocals", 4),
    Triple("Dangdut & Indo", "Nusantara rhythm", 1),
    Triple("Electronic", "Club & synthwave", 6),
    Triple("Jazz Lounge", "Brass & midnight keys", 7)
)

private val CONTENT_FILTERS = listOf("All", "Songs", "Videos", "Albums", "Artists", "Playlists")

/**
 * Rezona Original Spatial Search Screen ("Frequency Radar & Sonic Bento"):
 * - Top Masthead "Discover" with inline Cloud / Device frosted mode switcher
 * - Unified Command Capsule with Search Input + Clear + Built-in Voice Mic Disc
 * - Idle state: Recent Spins vinyl chips, Artist Portrait Cards, Asymmetric Sonic Territory Bento Grid
 * - Results state: Top Match Hero Spotlight Card + Frosted Result Rows + infinite continuation
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

    val recentEntities by libraryRepo.getRecentHistory(15).collectAsState(initial = emptyList())
    val mostPlayedEntities by libraryRepo.getMostPlayed(8).collectAsState(initial = emptyList())
    val recentlyPlayedTracks = remember(recentEntities) {
        recentEntities.map { it.toRezonaTrack() }.distinctBy { it.id }.ifEmpty { REAL_POPULAR_SEEDS }
    }
    val mostPlayedTracks = remember(mostPlayedEntities, recentlyPlayedTracks) {
        mostPlayedEntities.map { it.toRezonaTrack() }.ifEmpty { recentlyPlayedTracks }.take(6)
    }
    val favoriteArtists = remember(mostPlayedTracks, recentlyPlayedTracks) {
        (mostPlayedTracks + recentlyPlayedTracks).distinctBy {
            it.artist.substringBefore(" ft.").substringBefore(",").trim()
        }.take(8)
    }

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

    LaunchedEffect(isOnDeviceMode, hasAudioPermission, debouncedQuery) {
        if (isOnDeviceMode && hasAudioPermission) {
            onDeviceTracks = LocalMediaScanner.scanDeviceAudio(context, debouncedQuery)
        }
    }

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
            contentPadding = PaddingValues(top = statusTop + 14.dp, bottom = bottomPad),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── 1. COMMAND MASTHEAD + INLINE CLOUD/DEVICE SWITCHER ───────────
            item(key = "search_command_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Discover",
                                style = Typography.displayLarge.copy(
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = PureWhite
                            )
                            Text(
                                text = if (isOnDeviceMode) "Scanning local device storage" else "Global YouTube Music catalog",
                                style = Typography.bodySmall.copy(fontSize = 12.sp),
                                color = SoftSilver
                            )
                        }

                        // Compact Cloud / Device capsule switch
                        Row(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(CircleShape)
                                .background(Color(0x16FFFFFF))
                                .border(0.8.dp, SpecularDockBorder, CircleShape)
                                .padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ModeChip(
                                label = "Cloud",
                                selected = !isOnDeviceMode,
                                onClick = { isOnDeviceMode = false }
                            )
                            ModeChip(
                                label = "Device",
                                selected = isOnDeviceMode,
                                onClick = { isOnDeviceMode = true }
                            )
                        }
                    }

                    // Unified Command Capsule: Search Icon + Input + Clear + Voice Mic Disc
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x22FFFFFF), Color(0x10FFFFFF))
                                )
                            )
                            .border(1.dp, SpecularDockBorder, RoundedCornerShape(22.dp))
                            .padding(start = 16.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_rezona_search),
                            contentDescription = null,
                            tint = SoftSilver,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = if (isOnDeviceMode) "Filter local MP3 / FLAC..." else "Songs, artists, lyrics, moods...",
                                    style = Typography.bodyMedium.copy(fontSize = 14.5.sp),
                                    color = SmokySilver,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = PureWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(PureWhite),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable { searchQuery = "" },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = "Clear search",
                                    tint = SoftSilver,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x24FFFFFF))
                                .border(0.75.dp, Color(0x40FFFFFF), RoundedCornerShape(16.dp))
                                .clickable {
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
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_mic),
                                contentDescription = "Voice Search",
                                tint = PureWhite,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            // ── 2. ON-DEVICE MODE ────────────────────────────────────────────
            if (isOnDeviceMode) {
                if (!hasAudioPermission) {
                    item(key = "device_perm_card") {
                        RezonaGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Unlock Local Audio Files",
                                    style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = PureWhite
                                )
                                Text(
                                    text = "Grant media permission to play offline MP3, FLAC, and WAV tracks stored on your phone.",
                                    style = Typography.bodySmall,
                                    color = SoftSilver
                                )
                                RezonaGlassPill(
                                    isActive = true,
                                    onClick = { permissionLauncher.launch(requiredAudioPerm) }
                                ) {
                                    Text("Allow Audio Access", style = Typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                                }
                            }
                        }
                    }
                } else if (onDeviceTracks.isEmpty()) {
                    item(key = "device_empty") {
                        Box(modifier = Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                            Text("No matching audio files on this device.", style = Typography.bodyMedium, color = SmokySilver)
                        }
                    }
                } else {
                    itemsIndexed(onDeviceTracks, key = { idx, t -> "dev_${t.id}_$idx" }) { _, track ->
                        FrostedResultRow(
                            track = track,
                            onPlay = { onTrackSelected(track) },
                            onMoreClick = { menuTrack = track },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            } else if (debouncedQuery.isEmpty()) {
                // ── 3. IDLE RADAR STATE: Vinyl Spins + Artist Portraits + Genre Bento ─
                item(key = "idle_recent_vinyl_chips") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Recent Spins",
                            style = Typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                            color = PureWhite,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(recentlyPlayedTracks.take(10), key = { "rec_chip_${it.id}" }) { track ->
                                GlassSurface(
                                    variant = GlassSurfaceVariant.Button,
                                    shape = CircleShape,
                                    onClick = { onTrackSelected(track) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .height(46.dp)
                                            .padding(start = 6.dp, end = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RezonaAsyncCoverArt(
                                            coverArtUrl = track.coverArtUrl,
                                            fallbackIndex = track.index,
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .size(34.dp)
                                                .border(0.8.dp, SpecularCardBorder, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(9.dp))
                                        Column {
                                            Text(
                                                text = track.title,
                                                style = Typography.labelLarge.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                                                color = PureWhite,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = track.artist,
                                                style = Typography.labelSmall.copy(fontSize = 10.5.sp),
                                                color = SmokySilver,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (favoriteArtists.isNotEmpty()) {
                    item(key = "idle_artist_portraits") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Artist Frequencies",
                                style = Typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                                color = PureWhite,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(favoriteArtists, key = { "fav_art_${it.id}" }) { item ->
                                    val cleanArtist = item.artist.substringBefore(" ft.").substringBefore(",").trim()
                                    RezonaGlassCard(
                                        modifier = Modifier
                                            .width(114.dp)
                                            .clickable { searchQuery = cleanArtist },
                                        shape = RoundedCornerShape(22.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            RezonaAsyncCoverArt(
                                                coverArtUrl = item.coverArtUrl,
                                                fallbackIndex = item.index,
                                                shape = CircleShape,
                                                modifier = Modifier
                                                    .size(68.dp)
                                                    .border(1.dp, SpecularCardBorder, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = cleanArtist,
                                                style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = PureWhite,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Explore",
                                                style = Typography.labelSmall.copy(fontSize = 10.5.sp),
                                                color = SoftSilver
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Asymmetric Sonic Territory Bento Grid
                item(key = "idle_sonic_bento") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Sonic Territories",
                            style = Typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                            color = PureWhite
                        )

                        // Featured wide bento banner + 2-column sculpted cards
                        val leadTerritory = SONIC_TERRITORIES.first()
                        TerritoryBentoTile(
                            title = leadTerritory.first,
                            subtitle = leadTerritory.second,
                            patternIdx = leadTerritory.third,
                            heightDp = 96,
                            onClick = { searchQuery = "${leadTerritory.first} hits" },
                            modifier = Modifier.fillMaxWidth()
                        )

                        SONIC_TERRITORIES.drop(1).chunked(2).forEachIndexed { rowIdx, pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                pair.forEachIndexed { colIdx, (genre, desc, patIdx) ->
                                    TerritoryBentoTile(
                                        title = genre,
                                        subtitle = desc,
                                        patternIdx = patIdx,
                                        heightDp = if ((rowIdx + colIdx) % 2 == 0) 88 else 82,
                                        onClick = { searchQuery = "$genre music" },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                // ── 4. ACTIVE SEARCH STATE: Filters + Suggestion Pills + Top Match Hero ─
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

                if (searchSuggestions.isNotEmpty()) {
                    item(key = "live_suggestion_pills") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchSuggestions, key = { "sug_$it" }) { suggestion ->
                                GlassSurface(
                                    variant = GlassSurfaceVariant.Button,
                                    shape = CircleShape,
                                    onClick = {
                                        searchQuery = suggestion
                                        scope.launch { libraryRepo.saveSearchQuery(suggestion) }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_arrow_insert),
                                            contentDescription = null,
                                            tint = SoftSilver,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = suggestion,
                                            style = Typography.labelMedium,
                                            color = PearlWhite,
                                            maxLines = 1
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
                            Text("Scanning frequencies for \"$debouncedQuery\"...", style = Typography.bodyMedium, color = SmokySilver)
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
                } else if (remoteResults.isNotEmpty()) {
                    val topMatch = remoteResults.first()
                    item(key = "top_match_hero_${topMatch.id}") {
                        RezonaGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .clickable {
                                    scope.launch { libraryRepo.saveSearchQuery(debouncedQuery) }
                                    onTrackSelected(topMatch)
                                },
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RezonaAsyncCoverArt(
                                    coverArtUrl = topMatch.coverArtUrl,
                                    fallbackIndex = topMatch.index,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .size(72.dp)
                                        .border(1.dp, SpecularCardBorder, RoundedCornerShape(16.dp))
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Top Match",
                                        style = Typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = SoftSilver
                                    )
                                    Text(
                                        text = topMatch.title,
                                        style = Typography.titleLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold),
                                        color = PureWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = topMatch.artist,
                                        style = Typography.bodySmall,
                                        color = SmokySilver,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(PureWhite)
                                        .clickable {
                                            scope.launch { libraryRepo.saveSearchQuery(debouncedQuery) }
                                            onTrackSelected(topMatch)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_play_arrow),
                                        contentDescription = "Play Top Match",
                                        tint = Color(0xFF07090D),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }

                    itemsIndexed(remoteResults.drop(1), key = { idx, t -> "sr_${t.id}_$idx" }) { _, track ->
                        FrostedResultRow(
                            track = track,
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
                            Text("Loading more tracks...", style = Typography.bodySmall, color = SmokySilver)
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
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier
                        .background(Color(0x33FFFFFF))
                        .border(0.75.dp, Color(0x55FFFFFF), CircleShape)
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = Typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) PureWhite else SoftSilver
        )
    }
}

@Composable
private fun TerritoryBentoTile(
    title: String,
    subtitle: String,
    patternIdx: Int,
    heightDp: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        variant = GlassSurfaceVariant.Card,
        modifier = modifier.height(heightDp.dp),
        shape = RoundedCornerShape(22.dp),
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GenerativeMoodPattern(
                index = patternIdx,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.36f)
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            ) {
                Text(
                    text = title,
                    style = Typography.titleMedium.copy(fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold),
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = Typography.bodySmall.copy(fontSize = 11.sp),
                    color = SoftSilver,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FrostedResultRow(
    track: RezonaTrack,
    onPlay: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        variant = GlassSurfaceVariant.Button,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        onClick = onPlay
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
                modifier = Modifier.size(50.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = Typography.titleMedium.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = Typography.bodySmall.copy(fontSize = 12.sp),
                    color = SmokySilver,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMoreClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_more_vert),
                    contentDescription = "Track Options",
                    tint = SoftSilver,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
