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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import com.nayama.music.ui.components.GlassSurface
import com.nayama.music.ui.components.GlassSurfaceVariant
import com.nayama.music.ui.components.LocalRezonaHazeState
import com.nayama.music.ui.components.RezonaActionDisc
import com.nayama.music.ui.components.RezonaAsyncCoverArt
import com.nayama.music.ui.components.RezonaEqualizerModalSheet
import com.nayama.music.ui.components.RezonaGlassCard
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

private val REZONA_FREQUENCIES = listOf("Relax", "Feel good", "Energize", "Sleep", "Sad", "Workout", "Focus")

/**
 * Rezona Original Spatial Prism Home Screen ("Sonic Horizon & Vinyl Bento"):
 * - Editorial Rezona masthead with instant Equalizer & Settings Liquid Glass discs
 * - Unified Frosted Frequency Capsule Strip
 * - Featured Vinyl-Sleeve Spotlight Deck (peeking vinyl disc + instant Play Mix)
 * - 2-Row Horizontal Speed Dial Glass Bento Capsules
 * - Numbered Frosted Rotation Chart Decks (01..04 per swipeable glass card)
 * - Tall Portrait Sonic Mix Posters with frosted bottom caption plates
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
    var show3dPosterStudio by remember { mutableStateOf(false) }
    var showSuperLab by remember { mutableStateOf(false) }
    var showGuitarChords by remember { mutableStateOf(false) }
    var showLyricsVideoStudio by remember { mutableStateOf(false) }
    var showPcSyncDialog by remember { mutableStateOf(false) }

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

    val sessionSubtitle = remember(historyTracks.size) {
        val period = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 4..11 -> "Morning sonic horizon"
            in 12..17 -> "Afternoon studio deck"
            else -> "After-hours resonance"
        }
        if (historyTracks.isNotEmpty()) "$period · ${historyTracks.size} in rotation" else period
    }

    val spotlightPool = remember(selectedMood, moodFilteredTracks, pinnedSpeedDialEntities, historyTracks, homeSections) {
        when {
            selectedMood != null && moodFilteredTracks.isNotEmpty() -> moodFilteredTracks
            else -> {
                val pinned = pinnedSpeedDialEntities.map { it.toRezonaTrack() }
                val homeItems = homeSections.flatMap { it.items }
                (pinned + historyTracks + homeItems + REAL_POPULAR_SEEDS).distinctBy { it.id }
            }
        }
    }

    val spotlightLead = spotlightPool.firstOrNull() ?: REAL_POPULAR_SEEDS.first()
    val speedDialCards = spotlightPool.drop(1).ifEmpty { REAL_POPULAR_SEEDS }.take(8)

    val curatedPosters = remember(homeSections, songMixTracks, trendingTracks, coversAndRemixes) {
        val a = (homeSections.firstOrNull()?.items ?: songMixTracks).take(8)
        val b = (homeSections.getOrNull(1)?.items ?: trendingTracks).take(8)
        val c = coversAndRemixes.take(8)
        listOfNotNull(
            if (a.isNotEmpty()) Triple("Acoustic Prism", a.first().artist, a) else null,
            if (b.isNotEmpty()) Triple("Midnight Pulse", b.first().artist, b) else null,
            if (c.isNotEmpty()) Triple("Stripped & Reworked", c.first().artist, c) else null
        )
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomPad = if (controller.currentTrack.value != null) 172.dp else 112.dp

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
                contentPadding = PaddingValues(top = statusTop + 14.dp, bottom = bottomPad),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                // ── 1. REZONA EDITORIAL MASTHEAD + ACTION DISCS ──────────────────
                item(key = "home_masthead") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Rezona",
                                style = Typography.displayLarge.copy(
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.6).sp
                                ),
                                color = PureWhite
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = sessionSubtitle,
                                style = Typography.bodySmall.copy(fontSize = 12.5.sp),
                                color = SoftSilver
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RezonaActionDisc(size = 44.dp, onClick = { showEqualizer = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_equalizer),
                                    contentDescription = "Equalizer",
                                    tint = PearlWhite,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            RezonaActionDisc(size = 44.dp, onClick = onOpenSettings) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_settings),
                                    contentDescription = "Settings",
                                    tint = PearlWhite,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }

                // ── 2. UNIFIED FROSTED FREQUENCY CAPSULE BAR ─────────────────────
                item(key = "home_frequency_bar") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "freq_streak_fire") {
                            val streakDays = remember(historyTracks.size) {
                                ((historyTracks.size / 3) + 1).coerceAtLeast(1)
                            }
                            RezonaGlassPill(
                                isActive = true,
                                onClick = { showSuperLab = true }
                            ) {
                                Text(
                                    text = "🔥 $streakDays hari berturut-turut",
                                    style = Typography.labelLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
                                    color = PureWhite
                                )
                            }
                        }
                        item(key = "freq_superlab") {
                            RezonaGlassPill(
                                isActive = true,
                                onClick = { showSuperLab = true }
                            ) {
                                Text(
                                    text = "✨ 24+ SuperLab",
                                    style = Typography.labelLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
                                    color = Color(0xFF70D6FF)
                                )
                            }
                        }
                        item(key = "freq_smart_mix") {
                            RezonaGlassPill(
                                isActive = true,
                                onClick = { controller.playSmartPlaylist() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_heart_filled),
                                        contentDescription = null,
                                        tint = Color(0xFFFF5A6E),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Smart Mix",
                                        style = Typography.labelLarge.copy(fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold),
                                        color = PureWhite
                                    )
                                }
                            }
                        }
                        item(key = "freq_all") {
                            val active = selectedMood == null
                            RezonaGlassPill(
                                isActive = active,
                                onClick = { selectedMood = null }
                            ) {
                                Text(
                                    text = "All Waves",
                                    style = Typography.labelLarge.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                                    color = if (active) PureWhite else SoftSilver
                                )
                            }
                        }
                        items(REZONA_FREQUENCIES, key = { "freq_$it" }) { mood ->
                            val isActive = selectedMood == mood
                            RezonaGlassPill(
                                isActive = isActive,
                                onClick = { selectedMood = if (isActive) null else mood }
                            ) {
                                Text(
                                    text = mood,
                                    style = Typography.labelLarge.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                                    color = if (isActive) PureWhite else SoftSilver
                                )
                            }
                        }
                    }
                }

                // ── 3. FEATURED CRYSTAL BANNER HERO DECK (NO VINYL PLATTER) ──────
                item(key = "home_vinyl_spotlight") {
                    Crossfade(targetState = spotlightLead, label = "hero_spotlight_xfade") { heroTrack ->
                        RezonaGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .clickable {
                                    val queueTitle = selectedMood?.let { "$it Frequency" } ?: "Spotlight Mix"
                                    controller.playQueue(spotlightPool.take(20), 0, queueTitle)
                                },
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Clean Full Rounded-Square Crystal Album Banner
                                RezonaAsyncCoverArt(
                                    coverArtUrl = heroTrack.coverArtUrl,
                                    fallbackIndex = heroTrack.index,
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .size(104.dp)
                                        .border(1.2.dp, SpecularCardBorder, RoundedCornerShape(20.dp))
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = selectedMood?.let { "$it Session" } ?: "Featured Banner",
                                        style = Typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                        color = SoftSilver
                                    )
                                    Text(
                                        text = heroTrack.title,
                                        style = Typography.titleLarge.copy(fontSize = 17.5.sp, fontWeight = FontWeight.ExtraBold),
                                        color = PureWhite,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = heroTrack.artist,
                                        style = Typography.bodySmall.copy(fontSize = 12.5.sp),
                                        color = SmokySilver,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .height(34.dp)
                                                .clip(CircleShape)
                                                .background(PureWhite)
                                                .clickable {
                                                    val queueTitle = selectedMood?.let { "$it Frequency" } ?: "Spotlight Mix"
                                                    controller.playQueue(spotlightPool.take(20), 0, queueTitle)
                                                }
                                                .padding(horizontal = 13.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_play_arrow),
                                                    contentDescription = null,
                                                    tint = Color(0xFF07090D),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Text(
                                                    text = "Play Mix",
                                                    style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = Color(0xFF07090D)
                                                )
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .height(34.dp)
                                                .clip(CircleShape)
                                                .background(Color(0x24FFFFFF))
                                                .border(0.8.dp, Color(0x44FFFFFF), CircleShape)
                                                .clickable { show3dPosterStudio = true }
                                                .padding(horizontal = 11.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "3D Poster",
                                                style = Typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                color = PureWhite
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(Color(0x1FFFFFFF))
                                                .border(0.75.dp, Color(0x38FFFFFF), CircleShape)
                                                .clickable { menuTrack = heroTrack },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_more_vert),
                                                contentDescription = "Options",
                                                tint = PearlWhite,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 4. SPEED DIAL BENTO CAPSULES (2-Row Horizontal Glass Tiles) ──
                item(key = "home_speed_dial_bento") {
                    val pairs = remember(speedDialCards) { speedDialCards.chunked(2) }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionTitleRow(
                            title = "Speed Dial",
                            subtitle = "Pinned & instant-play favorites",
                            actionText = null,
                            onAction = {}
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(pairs.size, key = { "sd_col_$it" }) { colIdx ->
                                val colPair = pairs[colIdx]
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    colPair.forEach { track ->
                                        GlassSurface(
                                            variant = GlassSurfaceVariant.Card,
                                            modifier = Modifier
                                                .width(232.dp)
                                                .height(64.dp),
                                            shape = RoundedCornerShape(20.dp),
                                            onClick = {
                                                controller.playTrack(track, queueTitle = "Speed Dial", autoQueueRadio = true)
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RezonaAsyncCoverArt(
                                                    coverArtUrl = track.coverArtUrl,
                                                    fallbackIndex = track.index,
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.size(46.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = track.title,
                                                        style = Typography.titleMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
                                                        color = PureWhite,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = track.artist,
                                                        style = Typography.bodySmall.copy(fontSize = 11.5.sp),
                                                        color = SmokySilver,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_play_arrow),
                                                    contentDescription = null,
                                                    tint = SoftSilver,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 5. SONIC PORTRAIT MIXES (Tall Glass Posters with Frosted Plate) ─
                if (curatedPosters.isNotEmpty()) {
                    item(key = "home_sonic_posters") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SectionTitleRow(
                                title = "Curated Sonic Sets",
                                subtitle = "Blended radio waves from your taste",
                                actionText = null,
                                onAction = {}
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(curatedPosters, key = { "poster_${it.first}" }) { (title, leadArtist, mixTracks) ->
                                    Box(
                                        modifier = Modifier
                                            .width(168.dp)
                                            .height(216.dp)
                                            .clip(RoundedCornerShape(24.dp))
                                            .border(1.dp, SpecularCardBorder, RoundedCornerShape(24.dp))
                                            .clickable {
                                                controller.playQueue(mixTracks, 0, title)
                                            }
                                    ) {
                                        RezonaAsyncCoverArt(
                                            coverArtUrl = mixTracks.firstOrNull()?.coverArtUrl.orEmpty(),
                                            fallbackIndex = 0,
                                            shape = RoundedCornerShape(24.dp),
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(0.54f)
                                                .align(Alignment.BottomCenter)
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color.Transparent,
                                                            Color(0xCC080A10),
                                                            Color(0xF206080C)
                                                        )
                                                    )
                                                )
                                                .padding(14.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.align(Alignment.BottomStart)
                                            ) {
                                                Text(
                                                    text = title,
                                                    style = Typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                                                    color = PureWhite,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "With $leadArtist",
                                                    style = Typography.bodySmall.copy(fontSize = 11.5.sp),
                                                    color = SoftSilver,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(10.dp)
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(Color(0x9906080C))
                                                .border(0.8.dp, SpecularCardBorder, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_play_arrow),
                                                contentDescription = "Play $title",
                                                tint = PureWhite,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 6. HEAVY ROTATION CHART DECKS (Numbered Frosted Cards) ───────
                item(key = "home_rotation_charts") {
                    val chartPool = remember(staplesSource, songMixTracks) {
                        (staplesSource + songMixTracks).distinctBy { it.id }.take(12)
                    }
                    FrostedChartDeckSection(
                        title = "Heavy Rotation",
                        subtitle = "Your top-spun tracks right now",
                        tracks = chartPool,
                        onPlayAll = { controller.playQueue(chartPool, 0, "Heavy Rotation") },
                        onPlayTrack = { idx -> controller.playQueue(chartPool, idx, "Heavy Rotation") },
                        onOpenMenu = { menuTrack = it }
                    )
                }

                // ── 7. TRENDING WAVES DECK ───────────────────────────────────────
                item(key = "home_trending_deck") {
                    val list = trendingTracks.ifEmpty { REAL_POPULAR_SEEDS }
                    FrostedChartDeckSection(
                        title = "Trending Waves",
                        subtitle = "Breakout tracks across YouTube Music",
                        tracks = list.take(12),
                        onPlayAll = { controller.playQueue(list, 0, "Trending Waves") },
                        onPlayTrack = { idx -> controller.playQueue(list, idx, "Trending Waves") },
                        onOpenMenu = { menuTrack = it }
                    )
                }

                // ── 8. ARTIST ORBIT SECTIONS ─────────────────────────────────────
                similarArtistSections.forEach { (artistName, artistTracks) ->
                    item(key = "home_orbit_$artistName") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SectionTitleRow(
                                title = "Orbiting $artistName",
                                subtitle = " kindred artists & sonic matches",
                                actionText = "Play Radio",
                                onAction = { controller.playQueue(artistTracks, 0, "$artistName Orbit") }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(artistTracks.take(10), key = { "${artistName}_${it.id}" }) { item ->
                                    RezonaGlassCard(
                                        modifier = Modifier
                                            .width(146.dp)
                                            .clickable {
                                                controller.playTrack(item, queueTitle = "$artistName Orbit", autoQueueRadio = true)
                                            },
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            RezonaAsyncCoverArt(
                                                coverArtUrl = item.coverArtUrl,
                                                fallbackIndex = item.index,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier.size(126.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = item.title,
                                                style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = PureWhite,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = item.artist,
                                                style = Typography.bodySmall.copy(fontSize = 11.sp),
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

                // ── 9. COVERS, REMIXES & COMMUNITY CRATES ────────────────────────
                item(key = "home_covers_deck") {
                    val list = coversAndRemixes.ifEmpty { REAL_POPULAR_SEEDS.reversed() }
                    FrostedChartDeckSection(
                        title = "Reworks & Unplugged",
                        subtitle = "Live sessions, acoustic takes & remixes",
                        tracks = list.take(12),
                        onPlayAll = { controller.playQueue(list, 0, "Reworks & Unplugged") },
                        onPlayTrack = { idx -> controller.playQueue(list, idx, "Reworks & Unplugged") },
                        onOpenMenu = { menuTrack = it }
                    )
                }

                if (communityPlaylists.isNotEmpty() || homeSections.size > 1) {
                    item(key = "home_community_crates") {
                        val playlistsPool = communityPlaylists.ifEmpty { homeSections.lastOrNull()?.items.orEmpty() }
                        if (playlistsPool.isNotEmpty()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SectionTitleRow(
                                    title = "Community Crates",
                                    subtitle = "Handpicked late-night & indie playlists",
                                    actionText = null,
                                    onAction = {}
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(playlistsPool.take(10), key = { "cpl_${it.id}" }) { pl ->
                                        RezonaGlassCard(
                                            modifier = Modifier
                                                .width(156.dp)
                                                .clickable {
                                                    controller.playTrack(pl, queueTitle = pl.title, autoQueueRadio = true)
                                                },
                                            shape = RoundedCornerShape(22.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                RezonaAsyncCoverArt(
                                                    coverArtUrl = pl.coverArtUrl,
                                                    fallbackIndex = pl.index,
                                                    shape = RoundedCornerShape(14.dp),
                                                    modifier = Modifier.size(136.dp)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = pl.title,
                                                    style = Typography.titleMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
                                                    color = PearlWhite,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = pl.artist,
                                                    style = Typography.bodySmall.copy(fontSize = 11.5.sp),
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
            onOpenEqualizer = { showEqualizer = true },
            onOpen3dSongPoster = { show3dPosterStudio = true }
        )
    }

    if (show3dPosterStudio) {
        com.nayama.music.ui.lyrics.RezonaFloatingSongPosterStudio(
            initialTrack = controller.currentTrack.value ?: spotlightLead,
            queueTracks = spotlightPool.take(5),
            onDismiss = { show3dPosterStudio = false }
        )
    }

    if (showEqualizer) {
        RezonaEqualizerModalSheet(onDismiss = { showEqualizer = false })
    }

    if (showSuperLab) {
        com.nayama.music.ui.player.RezonaSuperLabSheet(
            track = controller.currentTrack.value ?: spotlightLead,
            controller = controller,
            onDismiss = { showSuperLab = false },
            onOpen3dPoster = { show3dPosterStudio = true },
            onOpenLyricsVideo = { showLyricsVideoStudio = true },
            onOpenGuitarChords = { showGuitarChords = true },
            onOpenPcSync = { showPcSyncDialog = true }
        )
    }

    if (showGuitarChords) {
        com.nayama.music.ui.player.RezonaGuitarChordStudioSheet(
            track = controller.currentTrack.value ?: spotlightLead,
            lyrics = controller.currentLyrics.value,
            currentPositionMs = controller.currentPositionMs.longValue,
            onSeekToMs = { controller.seekToPosition(it) },
            onDismiss = { showGuitarChords = false }
        )
    }

    if (showLyricsVideoStudio) {
        val activeTrack = controller.currentTrack.value ?: spotlightLead
        val allLineTexts = controller.currentLyrics.value.map { it.text }.ifEmpty {
            listOf(activeTrack.title, activeTrack.artist, "Rezona Liquid Glass Audio")
        }
        com.nayama.music.ui.lyrics.RezonaPosterStudio(
            track = activeTrack,
            allLines = allLineTexts,
            initialSelectedLines = allLineTexts.take(3),
            onDismiss = { showLyricsVideoStudio = false }
        )
    }

    if (showPcSyncDialog) {
        com.nayama.music.ui.player.RezonaPcSyncDialog(
            track = controller.currentTrack.value ?: spotlightLead,
            onDismiss = { showPcSyncDialog = false }
        )
    }
}

@Composable
private fun SectionTitleRow(
    title: String,
    subtitle: String,
    actionText: String?,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = Typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold),
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
        if (!actionText.isNullOrBlank()) {
            RezonaGlassPill(isActive = false, onClick = onAction) {
                Text(
                    text = actionText,
                    style = Typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = PearlWhite
                )
            }
        }
    }
}

/**
 * Swipeable Frosted Glass Chart Deck: 4 numbered tracks per glass card (`01`..`04`, `05`..`08`).
 */
@Composable
private fun FrostedChartDeckSection(
    title: String,
    subtitle: String,
    tracks: List<RezonaTrack>,
    onPlayAll: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onOpenMenu: (RezonaTrack) -> Unit
) {
    val pages = remember(tracks) { tracks.chunked(4) }
    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitleRow(
            title = title,
            subtitle = subtitle,
            actionText = "Play Deck",
            onAction = onPlayAll
        )
        Spacer(modifier = Modifier.height(12.dp))

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(start = 20.dp, end = 36.dp),
            pageSpacing = 14.dp
        ) { pageIdx ->
            val pageRows = pages[pageIdx]
            RezonaGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pageRows.forEachIndexed { localIdx, item ->
                        val globalIdx = (pageIdx * 4) + localIdx
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPlayTrack(globalIdx) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "%02d".format(globalIdx + 1),
                                style = Typography.labelLarge.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                                color = SoftSilver.copy(alpha = 0.78f),
                                modifier = Modifier.width(26.dp)
                            )
                            RezonaAsyncCoverArt(
                                coverArtUrl = item.coverArtUrl,
                                fallbackIndex = item.index,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(46.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = Typography.titleMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                                    color = PureWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.artist,
                                    style = Typography.bodySmall.copy(fontSize = 11.5.sp),
                                    color = SmokySilver,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable { onOpenMenu(item) },
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
                        if (localIdx < pageRows.lastIndex) {
                            RezonaHairlineDivider(startIndent = 84.dp)
                        }
                    }
                }
            }
        }
    }
}
