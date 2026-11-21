package com.nayama.music.ui.screens

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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nayama.music.R
import com.nayama.music.ui.components.GlassIntensity
import com.nayama.music.ui.components.LocalRezonaHazeState
import com.nayama.music.ui.components.RezonaActionDisc
import com.nayama.music.ui.components.RezonaGlassCard
import com.nayama.music.ui.components.RezonaGlassPill
import com.nayama.music.ui.components.rezonaHazeSource
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.Typography
import com.nayama.music.ui.util.MockCoverArt

// ─────────────────────────────────────────────────────────────────────────────
// Mock data models  (replace with real data models in Phase 3)
// ─────────────────────────────────────────────────────────────────────────────

private data class MockTrack(
    val index: Int,
    val title: String,
    val artist: String,
    val duration: String
)

private val MOCK_TRACKS = listOf(
    MockTrack(0, "Glass Resonance", "Echo Atlas", "4:12"),
    MockTrack(1, "Obsidian Drift", "Null Carrier", "3:47"),
    MockTrack(2, "Lunar Static", "Prism Void", "5:03"),
    MockTrack(3, "Thermal Haze", "Deep Signal", "3:28"),
    MockTrack(4, "Velvet Circuit", "Monochrome Lab", "4:55"),
    MockTrack(5, "Carved from Silence", "Hollow Depths", "6:14"),
    MockTrack(6, "Subsurface Drift", "Null Carrier", "3:50"),
    MockTrack(7, "Phase Inversion", "Echo Atlas", "4:33"),
    MockTrack(8, "Matte Black Sky", "Prism Void", "5:21"),
    MockTrack(9, "Cold Plate Reverb", "Signal Arc", "3:59"),
    MockTrack(10, "Iron Lace", "Monochrome Lab", "4:08"),
    MockTrack(11, "Soft Interference", "Deep Signal", "3:37"),
)

private val MOOD_CHIPS = listOf("Chill", "Focus", "Energy", "Late Night", "Party")
private val QUICK_PICKS_INDICES = listOf(2, 5, 7, 0, 4, 9, 1, 6)

// ─────────────────────────────────────────────────────────────────────────────
// Shared scroll-to-fold nested scroll connection
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun rememberAutoFoldNestedScrollConnection(
    onFoldChanged: (Boolean) -> Unit
): NestedScrollConnection = remember {
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < -14f) onFoldChanged(true)
            else if (available.y > 14f) onFoldChanged(false)
            return Offset.Zero
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 1 — Home (Phase 3 Complete Liquid Glass Screen)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RezonaHomePlaceholder(
    onFoldChanged: (Boolean) -> Unit,
    onTrackIndexChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ctrl = remember { com.nayama.music.playback.RezonaPlayerController.getInstance(ctx.applicationContext) }
    com.nayama.music.ui.home.RezonaHomeScreen(
        controller = ctrl,
        onFoldChanged = onFoldChanged,
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 2 — Search
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RezonaSearchPlaceholder(
    onFoldChanged: (Boolean) -> Unit,
    onTrackSelected: (com.nayama.music.model.RezonaTrack) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ctrl = remember { com.nayama.music.playback.RezonaPlayerController.getInstance(ctx.applicationContext) }
    com.nayama.music.ui.search.RezonaSearchScreen(
        controller = ctrl,
        isNavFolded = false,
        onFoldChanged = onFoldChanged,
        onTrackSelected = onTrackSelected,
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 3 — Studio
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RezonaStudioPlaceholder(
    onFoldChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val hazeState = LocalRezonaHazeState.current
    val scrollConnection = rememberAutoFoldNestedScrollConnection(onFoldChanged)
    val echoRooms = listOf("Intimate Studio", "Glass Hall", "Cathedral", "Deep Void")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollConnection)
            .rezonaHazeSource(hazeState),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 40.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "studio_header", contentType = "header") {
            Column {
                Text(text = "Acoustics & Spatial Labs", style = Typography.bodySmall, color = SmokySilver)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "Studio", style = Typography.displayMedium, color = PearlWhite)
            }
        }
        item(key = "echo_mode", contentType = "feature_card") {
            RezonaGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(text = "Echo Mode", style = Typography.titleLarge, color = PearlWhite)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Virtual acoustics: glass halls, cathedrals, intimate studios.", style = Typography.bodySmall, color = SoftSilver)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        echoRooms.forEach { room ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x331C1F26))
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = room, style = Typography.labelSmall, color = PureWhite, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        item(key = "focus_rooms", contentType = "feature_card") {
            RezonaGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(text = "Focus Rooms", style = Typography.titleLarge, color = PearlWhite)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Layer rain on glass, vinyl crackle, or binaural drone beneath your tracks.", style = Typography.bodySmall, color = SoftSilver)
                }
            }
        }
        item(key = "mood_dial", contentType = "feature_card") {
            RezonaGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(text = "Mood Dial", style = Typography.titleLarge, color = PearlWhite)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Rotate between energy levels. The app adapts tempo, reverb, and crossfade automatically.", style = Typography.bodySmall, color = SoftSilver)
                }
            }
        }
        items(
            items = listOf("Source Blend", "Smart Transitions", "Sound Check", "Queue Time Machine", "Daily Rezona"),
            key = { "studio_$it" },
            contentType = { "list_feature_row" }
        ) { feature ->
            RezonaGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painter = painterResource(id = R.drawable.ic_rezona_studio), contentDescription = null, tint = SoftSilver, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(text = feature, style = Typography.titleMedium, color = PearlWhite)
                        Text(text = "Rezona Signature Feature", style = Typography.bodySmall, color = SmokySilver)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 4 — Library
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RezonaLibraryPlaceholder(
    onFoldChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ctrl = remember { com.nayama.music.playback.RezonaPlayerController.getInstance(ctx.applicationContext) }
    com.nayama.music.ui.library.RezonaLibraryScreen(
        controller = ctrl,
        isNavFolded = false,
        onFoldChanged = onFoldChanged,
        modifier = modifier
    )
}
