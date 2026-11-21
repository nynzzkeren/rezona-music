package com.nayama.music.ui.showcase

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.ui.components.LocalRezonaHazeState
import com.nayama.music.ui.components.RezonaActionDisc
import com.nayama.music.ui.components.RezonaFloatingDock
import com.nayama.music.ui.components.RezonaGlassCard
import com.nayama.music.ui.components.RezonaGlassPill
import com.nayama.music.ui.components.rezonaHazeSource
import com.nayama.music.ui.theme.DeepCharcoal
import com.nayama.music.ui.theme.Graphite
import com.nayama.music.ui.theme.ObsidianBlack
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.Typography
import dev.chrisbanes.haze.HazeState

/**
 * Phase 1 Component Showcase Screen
 * Demonstrates:
 * 1. App name "Rezona" & verified Satoshi font family
 * 2. Official logo asset (in-app mark)
 * 3. Exact Obsidian Silver monochrome palette
 * 4. Hardware-accelerated Liquid Glass modifiers & Haze blur
 * 5. Header Action Discs, Mood Pills, Bento Card, Mini Player, and Floating Lens Dock
 */
@Composable
fun RezonaShowcaseScreen(
    hazeState: HazeState = remember { HazeState() }
) {
    var selectedMood by remember { mutableStateOf("Chill") }
    var selectedTab by remember { mutableStateOf("Home") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        // Desaturated ambient background glow behind scrolling content
        Box(
            modifier = Modifier
                .size(360.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x332B3240),
                            Color(0x101C1F26),
                            Color(0x0008090B)
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .rezonaHazeSource(hazeState),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header: Official Rezona Logo & Title
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_rezona_logo),
                            contentDescription = "Rezona Logo",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Rezona",
                                style = Typography.displayMedium,
                                color = PearlWhite
                            )
                            Text(
                                text = "Phase 1 Component Showcase",
                                style = Typography.bodySmall,
                                color = SmokySilver
                            )
                        }
                    }

                    // 4 Header Glass Action Discs
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RezonaActionDisc(size = 38.dp, onClick = {}) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_history),
                                contentDescription = "Time Capsule",
                                tint = SoftSilver,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        RezonaActionDisc(size = 38.dp, onClick = {}) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_equalizer),
                                contentDescription = "Sound Check",
                                tint = SoftSilver,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        RezonaActionDisc(size = 38.dp, onClick = {}) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_community),
                                contentDescription = "Sessions",
                                tint = SoftSilver,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        RezonaActionDisc(size = 38.dp, onClick = {}) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_settings),
                                contentDescription = "Settings",
                                tint = SoftSilver,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Section 1: Typography (Satoshi Geometric)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "1. SATOSHI TYPOGRAPHY", style = Typography.labelSmall, color = SmokySilver)
                    Text(text = "Display 28sp Bold", style = Typography.displayMedium, color = PearlWhite)
                    Text(text = "Headline 20sp Bold", style = Typography.headlineMedium, color = PearlWhite)
                    Text(text = "Section Title 17sp Bold", style = Typography.titleLarge, color = PearlWhite)
                    Text(text = "Body 14sp Regular — Clean, legible modernism", style = Typography.bodyLarge, color = SoftSilver)
                    Text(text = "Caption 11sp Medium — Timestamps & metadata", style = Typography.labelMedium, color = SmokySilver)
                }
            }

            // Section 2: Exact Obsidian Silver Monochrome Palette
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "2. OBSIDIAN SILVER PALETTE", style = Typography.labelSmall, color = SmokySilver)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PaletteSwatch("Obsidian", ObsidianBlack, "#08090B", Modifier.weight(1f))
                        PaletteSwatch("Charcoal", DeepCharcoal, "#111318", Modifier.weight(1f))
                        PaletteSwatch("Graphite", Graphite, "#1C1F26", Modifier.weight(1f))
                        PaletteSwatch("Smoky", SmokySilver, "#858B96", Modifier.weight(1f))
                        PaletteSwatch("Silver", SoftSilver, "#C4C8D0", Modifier.weight(1f))
                        PaletteSwatch("Pearl", PearlWhite, "#F4F5F7", Modifier.weight(1f))
                    }
                }
            }

            // Section 3: Mood Filter Pills
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "3. MOOD FILTER PILLS", style = Typography.labelSmall, color = SmokySilver)
                    val moods = listOf("Chill", "Focus", "Party", "Energy")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(moods) { mood ->
                            RezonaGlassPill(
                                isActive = selectedMood == mood,
                                onClick = { selectedMood = mood }
                            ) {
                                Text(
                                    text = mood,
                                    style = Typography.labelLarge,
                                    color = if (selectedMood == mood) PureWhite else SoftSilver
                                )
                            }
                        }
                    }
                }
            }

            // Section 4: Liquid Glass Bento Card & Live Specular Edge
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "4. LIQUID GLASS CARDS & SPECULAR EDGES", style = Typography.labelSmall, color = SmokySilver)
                    RezonaGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Acoustic Resonance Lab",
                                    style = Typography.titleMedium,
                                    color = PearlWhite
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x33FFFFFF))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "LIVE BLUR",
                                        style = Typography.labelSmall,
                                        color = PureWhite
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Milled specular glass borders rendered with GPU-accelerated Haze backdrop pipeline. Translucent substrate blends with subtle ambient mist.",
                                style = Typography.bodyMedium,
                                color = SoftSilver
                            )
                        }
                    }
                }
            }

            // Section 5: Mini Player Preview
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "5. FLOATING MINI PLAYER CAPSULE", style = Typography.labelSmall, color = SmokySilver)
                    RezonaFloatingDock(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Thumbnail
                                Image(
                                    painter = painterResource(id = R.drawable.ic_rezona_logo),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Dunia Yang Nanti",
                                        style = Typography.titleMedium,
                                        color = PearlWhite,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Raim Laode",
                                        style = Typography.bodySmall,
                                        color = SmokySilver,
                                        maxLines = 1
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_heart_outline),
                                        contentDescription = "Like",
                                        tint = SoftSilver,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(PureWhite),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_play_arrow),
                                            contentDescription = "Play",
                                            tint = ObsidianBlack,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_skip_next),
                                        contentDescription = "Next",
                                        tint = SoftSilver,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Hairline progress bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(Color(0x26FFFFFF))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.42f)
                                        .height(2.dp)
                                        .background(PureWhite)
                                )
                            }
                        }
                    }
                }
            }

            // Section 6: Floating 4-Tab Navbar Lens Dock
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "6. FLOATING NAVBAR LENS DOCK", style = Typography.labelSmall, color = SmokySilver)
                    RezonaFloatingDock(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = RoundedCornerShape(32.dp)
                    ) {
                        val tabs = listOf(
                            Triple("Home", R.drawable.ic_nav_home, "Home"),
                            Triple("Search", R.drawable.ic_nav_search, "Search"),
                            Triple("Studio", R.drawable.ic_equalizer, "Studio"),
                            Triple("Library", R.drawable.ic_nav_library, "Library")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tabs.forEach { (title, iconRes, key) ->
                                val isActive = selectedTab == key
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isActive) Color(0x331C1F26) else Color.Transparent)
                                        .padding(vertical = 8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = iconRes),
                                        contentDescription = title,
                                        tint = if (isActive) PureWhite else SmokySilver,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = title,
                                        style = Typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = if (isActive) PureWhite else SmokySilver
                                    )
                                    if (isActive) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(3.dp)
                                                .clip(CircleShape)
                                                .background(PureWhite)
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

@Composable
private fun PaletteSwatch(
    label: String,
    color: Color,
    hex: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color)
                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = Typography.labelSmall,
            fontSize = 9.sp,
            color = PearlWhite,
            maxLines = 1
        )
        Text(
            text = hex,
            style = Typography.bodySmall,
            fontSize = 8.sp,
            color = SmokySilver,
            maxLines = 1
        )
    }
}

@Preview(name = "Rezona Phase 1 Showcase", showBackground = true, backgroundColor = 0xFF08090B, widthDp = 390, heightDp = 844)
@Composable
fun RezonaShowcaseScreenPreview() {
    com.nayama.music.ui.theme.RezonaTheme {
        RezonaShowcaseScreen()
    }
}

