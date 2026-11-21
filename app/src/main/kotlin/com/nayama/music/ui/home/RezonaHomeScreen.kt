package com.nayama.music.ui.home

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.backend.MusicEngineRepository
import com.nayama.music.backend.innertube.InnerTubeFilter
import com.nayama.music.backend.innertube.InnerTubeSection
import com.nayama.music.data.local.RezonaDatabase
import com.nayama.music.data.repository.RezonaLibraryRepository
import com.nayama.music.data.repository.toRezonaTrack
import com.nayama.music.model.RezonaTrack
import com.nayama.music.playback.RezonaPlayerController
import com.nayama.music.ui.components.LocalRezonaHazeState
import com.nayama.music.ui.components.RezonaAsyncCoverArt
import com.nayama.music.ui.components.RezonaEqualizerModalSheet
import com.nayama.music.ui.components.RezonaGlassPill
import com.nayama.music.ui.components.RezonaHairlineDivider
import com.nayama.music.ui.components.RezonaTrackMenuSheet
import com.nayama.music.ui.components.rezonaHazeSource
import com.nayama.music.ui.screens.rememberAutoFoldNestedScrollConnection
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.SpecularCardBorder
import com.nayama.music.ui.theme.Typography
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar

val REAL_POPULAR_SEEDS = listOf(
    RezonaTrack(
        id = "yt_5NV6Rdv1a3I",
        index = 0,
        title = "Get Lucky",
        artist = "Daft Punk ft. Pharrell Williams",
        album = "Random Access Memories",
        duration = "4:08",
        source = "YouTube Music",
        coverArtUrl = "https://i.ytimg.com/vi/5NV6Rdv1a3I/hqdefault.jpg",
        streamUrl = "https://www.youtube.com/watch?v=5NV6Rdv1a3I"
    ),
    RezonaTrack(
        id = "yt_4NRXx6U8ABQ",
        index = 1,
        title = "Blinding Lights",
        artist = "The Weeknd",
        album = "After Hours",
        duration = "3:20",
        source = "YouTube Music",
        coverArtUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg",
        streamUrl = "https://www.youtube.com/watch?v=4NRXx6U8ABQ"
    ),
    RezonaTrack(
        id = "yt_TUVcZfQe-Kw",
        index = 2,
        title = "Levitating",
        artist = "Dua Lipa",
        album = "Future Nostalgia",
        duration = "3:23",
        source = "YouTube Music",
        coverArtUrl = "https://i.ytimg.com/vi/TUVcZfQe-Kw/hqdefault.jpg",
        streamUrl = "https://www.youtube.com/watch?v=TUVcZfQe-Kw"
    ),
    RezonaTrack(
        id = "yt_bpOSxM0rNPM",
        index = 3,
        title = "Do I Wanna Know?",
        artist = "Arctic Monkeys",
        album = "AM",
        duration = "4:32",
        source = "YouTube Music",
        coverArtUrl = "https://i.ytimg.com/vi/bpOSxM0rNPM/hqdefault.jpg",
        streamUrl = "https://www.youtube.com/watch?v=bpOSxM0rNPM"
    ),
    RezonaTrack(
        id = "yt_V1Pl8CzNzCw",
        index = 4,
        title = "Birds of a Feather",
        artist = "Billie Eilish",
        album = "HIT ME HARD AND SOFT",
        duration = "3:16",
        source = "YouTube Music",
        coverArtUrl = "https://i.ytimg.com/vi/V1Pl8CzNzCw/hqdefault.jpg",
        streamUrl = "https://www.youtube.com/watch?v=V1Pl8CzNzCw"
    ),
    RezonaTrack(
        id = "yt_T6eK-2OQtew",
        index = 5,
        title = "Not Like Us",
        artist = "Kendrick Lamar",
        album = "Not Like Us",
        duration = "4:34",
        source = "YouTube Music",
        coverArtUrl = "https://i.ytimg.com/vi/T6eK-2OQtew/hqdefault.jpg",
        streamUrl = "https://www.youtube.com/watch?v=T6eK-2OQtew"
    ),
    RezonaTrack(
        id = "yt_wXhTHyIgQ_U",
        index = 6,
        title = "Circles",
        artist = "Post Malone",
        album = "Hollywood's Bleeding",
        duration = "3:35",
        source = "YouTube Music",
        coverArtUrl = "https://i.ytimg.com/vi/wXhTHyIgQ_U/hqdefault.jpg",
        streamUrl = "https://www.youtube.com/watch?v=wXhTHyIgQ_U"
    ),
    RezonaTrack(
        id = "yt_H5v3kku4y6Q",
        index = 7,
        title = "As It Was",
        artist = "Harry Styles",
        album = "Harry's House",
        duration = "2:47",
        source = "YouTube Music",
        coverArtUrl = "https://i.ytimg.com/vi/H5v3kku4y6Q/hqdefault.jpg",
        streamUrl = "https://www.youtube.com/watch?v=H5v3kku4y6Q"
    ),
    RezonaTrack(
        id = "yt_ic8j13piAhQ",
        index = 8,
        title = "Cruel Summer",
        artist = "Taylor Swift",
        album = "Lover",
        duration = "2:58",
        source = "YouTube Music",
        coverArtUrl = "https://i.ytimg.com/vi/ic8j13piAhQ/hqdefault.jpg",
        streamUrl = "https://www.youtube.com/watch?v=ic8j13piAhQ"
    ),
    RezonaTrack(
        id = "yt_1G4isv_Fylg",
        index = 9,
        title = "Yellow",
        artist = "Coldplay",
        album = "Parachutes",
        duration = "4:29",
        source = "YouTube Music",
        coverArtUrl = "https://i.ytimg.com/vi/1G4isv_Fylg/hqdefault.jpg",
        streamUrl = "https://www.youtube.com/watch?v=1G4isv_Fylg"
    )
)

private val V2_MOOD_CHIPS = listOf("Relax", "Feel good", "Energize", "Sleep", "Sad", "Workout", "Focus")

/**
 * Rezona Home Screen (V2 Stage A — home_1/2/3.jpg structure)
 * Strictly NO streak card, NO boxes behind list content, all real YouTube Music + Room data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RezonaHomeScreen(
    controller: RezonaPlayerController,
    onFoldChanged: (Boolean) -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenSoundCheck: () -> Unit = {},
    onOpenSessions: () -> Unit = {},
    onOpenTimeCapsule: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hazeState = LocalRezonaHazeState.current
    val listState = rememberLazyListState()
    val scrollConnection = rememberAutoFoldNestedScrollConnection(onFoldChanged)

    val db = remember { RezonaDatabase.getInstance(context) }
    val libraryRepo = remember { RezonaLibraryRepository.getInstance(context) }
    val musicEngine = remember { MusicEngineRepository.getInstance(context) }

    val historyEntities by db.historyDao().getRecentHistory(40).collectAsState(initial = emptyList())
    val mostPlayedEntities by libraryRepo.getMostPlayed(36).collectAsState(initial = emptyList())
    val pinnedSpeedDialEntities by libraryRepo.getPinnedSpeedDial().collectAsState(initial = emptyList())

    val historyTracks = remember(historyEntities) { historyEntities.map { it.toRezonaTrack() }.distinctBy { it.id } }
    val staplesSource = remember(mostPlayedEntities, historyTracks) {
        val list = mostPlayedEntities.map { it.toRezonaTrack() }.ifEmpty { historyTracks }
        if (list.isNotEmpty()) list else REAL_POPULAR_SEEDS
    }

    var selectedMood by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    var homeSections by remember { mutableStateOf<List<InnerTubeSection>>(emptyList()) }
    var moodFilteredTracks by remember { mutableStateOf<List<RezonaTrack>>(emptyList()) }
    var songMixTracks by remember { mutableStateOf<List<RezonaTrack>>(emptyList()) }
    var trendingTracks by remember { mutableStateOf<List<RezonaTrack>>(emptyList()) }
    var coversAndRemixes by remember { mutableStateOf<List<RezonaTrack>>(emptyList()) }
    var communityPlaylists by remember { mutableStateOf<List<RezonaTrack>>(emptyList()) }
    var similarArtistSections by remember { mutableStateOf<List<Pair<String, List<RezonaTrack>>>>(emptyList()) }

    var menuTrack by remember { mutableStateOf<RezonaTrack?>(null) }
    var showEqualizer by remember { mutableStateOf(false) }

    suspend fun loadHomeData(forceRefresh: Boolean = false) {
        coroutineScope {
            val homeJob = async { runCatching { musicEngine.innerTubeRepo.getHomeSections(forceRefresh) }.getOrDefault(emptyList()) }
            val songMixJob = async { runCatching { musicEngine.innerTubeRepo.search("top acoustic pop chill mix", InnerTubeFilter.SONGS) }.getOrDefault(emptyList()) }
            val trendingJob = async {
                val yr = Calendar.getInstance().get(Calendar.YEAR)
                runCatching { musicEngine.innerTubeRepo.search("trending viral hits $yr", InnerTubeFilter.SONGS) }.getOrDefault(emptyList())
            }
            val coversJob = async { runCatching { musicEngine.innerTubeRepo.search("stripped down acoustic cover remix", InnerTubeFilter.SONGS) }.getOrDefault(emptyList()) }
            val playlistsJob = async { runCatching { musicEngine.innerTubeRepo.search("indie chill late night playlist", InnerTubeFilter.PLAYLISTS) }.getOrDefault(emptyList()) }

            val topArtists = staplesSource
                .map { it.artist.substringBefore(" ft.").substringBefore(",").trim() }
                .filter { it.isNotBlank() && it != "YouTube Music" }
                .distinct()
                .ifEmpty { listOf("Daft Punk", "The Weeknd", "Arctic Monkeys") }
                .take(3)

            val similarJobs = topArtists.map { artist ->
                artist to async {
                    runCatching { musicEngine.innerTubeRepo.search("similar to $artist songs", InnerTubeFilter.SONGS) }.getOrDefault(emptyList())
                }
            }

            homeSections = homeJob.await()
            songMixTracks = songMixJob.await().ifEmpty { REAL_POPULAR_SEEDS }
            trendingTracks = trendingJob.await().ifEmpty { REAL_POPULAR_SEEDS }
            coversAndRemixes = coversJob.await().ifEmpty { REAL_POPULAR_SEEDS.reversed() }
            communityPlaylists = playlistsJob.await()
            similarArtistSections = similarJobs.mapNotNull { (name, deferred) ->
                val list = deferred.await()
                if (list.isNotEmpty()) name to list else null
            }
        }
    }

    LaunchedEffect(Unit) {
        loadHomeData(forceRefresh = false)
    }

    LaunchedEffect(selectedMood) {
        val mood = selectedMood
        if (mood == null) {
            moodFilteredTracks = emptyList()
        } else {
            val q = when (mood) {
                "Relax" -> "relaxing chill acoustic songs"
                "Feel good" -> "feel good upbeat indie pop songs"
                "Energize" -> "energetic workout electronic pop songs"
                "Sleep" -> "deep sleep ambient peaceful piano"
                "Sad" -> "sad melancholic acoustic ballad songs"
                "Workout" -> "high energy cardio gym workout music"
                "Focus" -> "deep focus instrumental ambient beats"
                else -> "$mood music"
            }
            moodFilteredTracks = runCatching {
                musicEngine.innerTubeRepo.search(q, InnerTubeFilter.SONGS)
            }.getOrDefault(emptyList())
        }
    }

    val greetingText = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 4..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val quickPicks = remember(pinnedSpeedDialEntities, historyTracks, homeSections, moodFilteredTracks, selectedMood) {
        val base = when {
            selectedMood != null && moodFilteredTracks.isNotEmpty() -> moodFilteredTracks
            else -> {
                val pinned = pinnedSpeedDialEntities.map { it.toRezonaTrack() }
                val homeItems = homeSections.flatMap { it.items }
                (pinned + historyTracks + homeItems + REAL_POPULAR_SEEDS).distinctBy { it.id }
            }
        }
        base.take(6)
    }

    val continueSection = remember(homeSections, staplesSource) {
        val firstCarousel = homeSections.firstOrNull { it.items.isNotEmpty() }
        val tracks = firstCarousel?.items?.take(12) ?: staplesSource.take(8)
        Triple(
            tracks.firstOrNull(),
            firstCarousel?.title ?: staplesSource.firstOrNull()?.artist ?: "Continue Listening",
            tracks
        )
    }

    val listenerPickMosaics = remember(homeSections, songMixTracks, trendingTracks) {
        val poolA = (homeSections.firstOrNull()?.items ?: songMixTracks).take(4)
        val poolB = (homeSections.getOrNull(1)?.items ?: trendingTracks).take(4)
        listOfNotNull(
            if (poolA.isNotEmpty()) Triple("Acoustic Resonance", "${poolA.firstOrNull()?.artist ?: "Mixed"} & more", poolA) else null,
            if (poolB.isNotEmpty()) Triple("After Hours Mix", "${poolB.firstOrNull()?.artist ?: "YouTube Music"} Radio", poolB) else null
        )
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomPad = if (controller.currentTrack.value != null) 168.dp else 108.dp

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    loadHomeData(forceRefresh = true)
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollConnection)
                    .rezonaHazeSource(hazeState),
                contentPadding = PaddingValues(top = statusTop + 12.dp, bottom = bottomPad),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // ── 1. HEADER: Small "Home" + Big Time-based Greeting ─────────────
                item(key = "home_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                    Text(
                        text = "Home",
                        style = Typography.bodySmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        color = PearlWhite.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = greetingText,
                        style = Typography.displayLarge.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
                        color = PureWhite
                    )
                }
            }

            // ── 2. QUICK PICKS (2-col grid of compact plain rows, NO card background) ──
            item(key = "home_quick_picks") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    quickPicks.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pair.forEach { track ->
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            controller.playTrack(track, queueTitle = "Quick Picks", autoQueueRadio = true)
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RezonaAsyncCoverArt(
                                        coverArtUrl = track.coverArtUrl,
                                        fallbackIndex = track.index,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.size(50.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = track.title,
                                        style = Typography.titleMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                                        color = PearlWhite,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // ── 3. NO STREAK CARD (Skipped intentionally per global rules) ───

            // ── 4. MOOD CHIPS ────────────────────────────────────────────────
            item(key = "home_mood_chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(V2_MOOD_CHIPS) { mood ->
                        val isActive = selectedMood == mood
                        RezonaGlassPill(
                            isActive = isActive,
                            onClick = { selectedMood = if (isActive) null else mood }
                        ) {
                            Text(
                                text = mood,
                                style = Typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                color = if (isActive) PureWhite else SoftSilver
                            )
                        }
                    }
                }
            }

            // Crossfade filtered spotlight when mood is selected
            if (selectedMood != null) {
                item(key = "home_mood_filtered") {
                    Crossfade(targetState = moodFilteredTracks, label = "mood_xfade") { tracks ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            PagedSongMixSection(
                                title = "$selectedMood Mix",
                                tracks = tracks.ifEmpty { REAL_POPULAR_SEEDS },
                                onPlayAll = {
                                    val list = tracks.ifEmpty { REAL_POPULAR_SEEDS }
                                    controller.playQueue(list, 0, "$selectedMood Mix")
                                },
                                onPlayTrack = { t, idx ->
                                    val list = tracks.ifEmpty { REAL_POPULAR_SEEDS }
                                    controller.playQueue(list, idx, "$selectedMood Mix")
                                },
                                onOpenMenu = { menuTrack = it }
                            )
                        }
                    }
                }
            }

            // ── 5. CONTINUE (Large Rounded Cover + Title + "N songs") ────────
            item(key = "home_continue") {
                val (leadTrack, title, continueTracks) = continueSection
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "Continue",
                        style = Typography.titleLarge.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold),
                        color = PureWhite
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .width(172.dp)
                            .clickable {
                                controller.playQueue(continueTracks, 0, title)
                            }
                    ) {
                        RezonaAsyncCoverArt(
                            coverArtUrl = leadTrack?.coverArtUrl.orEmpty(),
                            fallbackIndex = leadTrack?.index ?: 0,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.size(172.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = title,
                            style = Typography.titleMedium.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold),
                            color = PearlWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${continueTracks.size} songs",
                            style = Typography.bodySmall.copy(fontSize = 12.sp),
                            color = SmokySilver
                        )
                    }
                }
            }

            // ── 6. YOUR STAPLES (3-column, 3 rows per page + page dots + first chevron) ──
            item(key = "home_your_staples") {
                val expandedStaples = remember(staplesSource, songMixTracks) {
                    (staplesSource + songMixTracks).distinctBy { it.id }.take(18)
                }
                val pages = remember(expandedStaples) { expandedStaples.chunked(9) }
                val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Your staples",
                        style = Typography.titleLarge.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold),
                        color = PureWhite,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        pageSpacing = 16.dp
                    ) { pageIdx ->
                        val itemsOnPage = pages[pageIdx]
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsOnPage.chunked(3).forEachIndexed { rowIdx, rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowItems.forEachIndexed { colIdx, item ->
                                        val isVeryFirst = pageIdx == 0 && rowIdx == 0 && colIdx == 0
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    controller.playTrack(item, queueTitle = "Your Staples", autoQueueRadio = true)
                                                }
                                        ) {
                                            RezonaAsyncCoverArt(
                                                coverArtUrl = item.coverArtUrl,
                                                fallbackIndex = item.index,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(1f)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = if (isVeryFirst) item.artist.substringBefore(" ft.") else item.title,
                                                    style = Typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                                    color = PearlWhite,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isVeryFirst) {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.ic_chevron_right),
                                                        contentDescription = null,
                                                        tint = SoftSilver,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    repeat(3 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    if (pages.size > 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(pages.size) { idx ->
                                val selected = pagerState.currentPage == idx
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .height(5.dp)
                                        .width(if (selected) 18.dp else 5.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) PureWhite else Color(0x40FFFFFF))
                                )
                            }
                        }
                    }
                }
            }

            // ── 7. LISTENER PICKS (2x2 Mosaic Collages with Play Overlay) ────
            item(key = "home_listener_picks") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Listener picks",
                        style = Typography.titleLarge.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold),
                        color = PureWhite,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(listenerPickMosaics) { (title, subtitle, mosaicTracks) ->
                            Column(
                                modifier = Modifier
                                    .width(196.dp)
                                    .clickable {
                                        controller.playQueue(mosaicTracks, 0, title)
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(196.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                ) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Row(modifier = Modifier.weight(1f)) {
                                            RezonaAsyncCoverArt(
                                                coverArtUrl = mosaicTracks.getOrNull(0)?.coverArtUrl.orEmpty(),
                                                fallbackIndex = 0,
                                                shape = RoundedCornerShape(0.dp),
                                                modifier = Modifier.weight(1f).fillMaxSize()
                                            )
                                            RezonaAsyncCoverArt(
                                                coverArtUrl = mosaicTracks.getOrNull(1)?.coverArtUrl.orEmpty(),
                                                fallbackIndex = 1,
                                                shape = RoundedCornerShape(0.dp),
                                                modifier = Modifier.weight(1f).fillMaxSize()
                                            )
                                        }
                                        Row(modifier = Modifier.weight(1f)) {
                                            RezonaAsyncCoverArt(
                                                coverArtUrl = mosaicTracks.getOrNull(2)?.coverArtUrl.orEmpty(),
                                                fallbackIndex = 2,
                                                shape = RoundedCornerShape(0.dp),
                                                modifier = Modifier.weight(1f).fillMaxSize()
                                            )
                                            RezonaAsyncCoverArt(
                                                coverArtUrl = mosaicTracks.getOrNull(3)?.coverArtUrl.orEmpty(),
                                                fallbackIndex = 3,
                                                shape = RoundedCornerShape(0.dp),
                                                modifier = Modifier.weight(1f).fillMaxSize()
                                            )
                                        }
                                    }
                                    // Floating play circle overlay
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(10.dp)
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xA6000000))
                                            .border(1.dp, SpecularCardBorder, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_play_arrow),
                                            contentDescription = "Play Mix",
                                            tint = PureWhite,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = title, style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PearlWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(text = subtitle, style = Typography.bodySmall, color = SmokySilver, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // ── 8. "SIMILAR TO <ARTIST>" SECTIONS ────────────────────────────
            similarArtistSections.forEachIndexed { secIdx, (artistName, artistTracks) ->
                item(key = "home_similar_$artistName") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .clickable {
                                    controller.playQueue(artistTracks, 0, "Similar to $artistName")
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (secIdx % 2 == 1) {
                                    RezonaAsyncCoverArt(
                                        coverArtUrl = artistTracks.firstOrNull()?.coverArtUrl.orEmpty(),
                                        fallbackIndex = secIdx,
                                        shape = CircleShape,
                                        modifier = Modifier.size(38.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Column {
                                    Text(text = "Similar to", style = Typography.bodySmall.copy(fontSize = 11.5.sp), color = SmokySilver)
                                    Text(text = artistName, style = Typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = PureWhite)
                                }
                            }
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrow_forward),
                                contentDescription = "Play $artistName Mix",
                                tint = PearlWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(artistTracks.take(10), key = { "${artistName}_${it.id}" }) { item ->
                                Column(
                                    modifier = Modifier
                                        .width(138.dp)
                                        .clickable {
                                            controller.playTrack(item, queueTitle = "Similar to $artistName", autoQueueRadio = true)
                                        }
                                ) {
                                    RezonaAsyncCoverArt(
                                        coverArtUrl = item.coverArtUrl,
                                        fallbackIndex = item.index,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.size(138.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = item.title, style = Typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = PearlWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(text = item.artist, style = Typography.bodySmall.copy(fontSize = 11.5.sp), color = SmokySilver, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            // ── 9. SONG MIX (4 plain rows per page with peek & "Play all") ───
            item(key = "home_song_mix") {
                PagedSongMixSection(
                    title = "Song mix",
                    tracks = songMixTracks.ifEmpty { REAL_POPULAR_SEEDS },
                    onPlayAll = {
                        val list = songMixTracks.ifEmpty { REAL_POPULAR_SEEDS }
                        controller.playQueue(list, 0, "Song Mix")
                    },
                    onPlayTrack = { track, idx ->
                        val list = songMixTracks.ifEmpty { REAL_POPULAR_SEEDS }
                        controller.playQueue(list, idx, "Song Mix")
                    },
                    onOpenMenu = { menuTrack = it }
                )
            }

            // ── 10A. TRENDING SONGS FOR YOU ──────────────────────────────────
            item(key = "home_trending") {
                PagedSongMixSection(
                    title = "Trending songs for you",
                    tracks = trendingTracks.ifEmpty { REAL_POPULAR_SEEDS },
                    onPlayAll = {
                        val list = trendingTracks.ifEmpty { REAL_POPULAR_SEEDS }
                        controller.playQueue(list, 0, "Trending for You")
                    },
                    onPlayTrack = { track, idx ->
                        val list = trendingTracks.ifEmpty { REAL_POPULAR_SEEDS }
                        controller.playQueue(list, idx, "Trending for You")
                    },
                    onOpenMenu = { menuTrack = it }
                )
            }

            // ── 10B. COVERS AND REMIXES ──────────────────────────────────────
            item(key = "home_covers_remixes") {
                PagedSongMixSection(
                    title = "Covers and remixes",
                    tracks = coversAndRemixes.ifEmpty { REAL_POPULAR_SEEDS.reversed() },
                    onPlayAll = {
                        val list = coversAndRemixes.ifEmpty { REAL_POPULAR_SEEDS.reversed() }
                        controller.playQueue(list, 0, "Covers & Remixes")
                    },
                    onPlayTrack = { track, idx ->
                        val list = coversAndRemixes.ifEmpty { REAL_POPULAR_SEEDS.reversed() }
                        controller.playQueue(list, idx, "Covers & Remixes")
                    },
                    onOpenMenu = { menuTrack = it }
                )
            }

            // ── 10C. TRENDING COMMUNITY PLAYLISTS ────────────────────────────
            if (communityPlaylists.isNotEmpty() || homeSections.size > 1) {
                item(key = "home_community_playlists") {
                    val playlistsPool = communityPlaylists.ifEmpty { homeSections.lastOrNull()?.items.orEmpty() }
                    if (playlistsPool.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Trending community playlists",
                                style = Typography.titleLarge.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold),
                                color = PureWhite,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(playlistsPool.take(10), key = { "cpl_${it.id}" }) { pl ->
                                    Column(
                                        modifier = Modifier
                                            .width(156.dp)
                                            .clickable {
                                                controller.playTrack(pl, queueTitle = pl.title, autoQueueRadio = true)
                                            }
                                    ) {
                                        RezonaAsyncCoverArt(
                                            coverArtUrl = pl.coverArtUrl,
                                            fallbackIndex = pl.index,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.size(156.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(text = pl.title, style = Typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = PearlWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(text = pl.artist, style = Typography.bodySmall, color = SmokySilver, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
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

/**
 * 4-rows-per-page HorizontalPager with next-page peek & "Play all" header (home_3.jpg).
 * Strictly unboxed plain rows directly on the continuous ambient background.
 */
@Composable
private fun PagedSongMixSection(
    title: String,
    tracks: List<RezonaTrack>,
    onPlayAll: () -> Unit,
    onPlayTrack: (RezonaTrack, Int) -> Unit,
    onOpenMenu: (RezonaTrack) -> Unit
) {
    val pages = remember(tracks) { tracks.chunked(4) }
    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = Typography.titleLarge.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold),
                color = PureWhite,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Play all",
                style = Typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = PearlWhite.copy(alpha = 0.9f),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onPlayAll)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(start = 20.dp, end = 44.dp),
            pageSpacing = 14.dp
        ) { pageIdx ->
            val pageRows = pages[pageIdx]
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                pageRows.forEachIndexed { localIdx, item ->
                    val globalIdx = (pageIdx * 4) + localIdx
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPlayTrack(item, globalIdx) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RezonaAsyncCoverArt(
                            coverArtUrl = item.coverArtUrl,
                            fallbackIndex = item.index,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = Typography.titleMedium.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
                                color = PearlWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.artist,
                                style = Typography.bodySmall.copy(fontSize = 12.sp),
                                color = SmokySilver,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable { onOpenMenu(item) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_more_vert),
                                contentDescription = "More options",
                                tint = SoftSilver,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
