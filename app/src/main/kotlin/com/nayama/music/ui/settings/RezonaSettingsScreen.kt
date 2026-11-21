package com.nayama.music.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.backend.OfflineDownloadManager
import com.nayama.music.data.preferences.RezonaPreferences
import com.nayama.music.data.repository.RezonaLibraryRepository
import com.nayama.music.ui.components.GlassIntensity
import com.nayama.music.ui.components.RezonaActionDisc
import com.nayama.music.ui.components.RezonaGlassButton
import com.nayama.music.ui.components.RezonaGlassPill
import com.nayama.music.ui.components.RezonaGlassSlider
import com.nayama.music.ui.components.RezonaGlassToggle
import com.nayama.music.ui.components.RezonaHairlineDivider
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.Typography
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Stage H Complete Settings Screen covering all V2 categories:
 * Sound & playback, Appearance, Language & content, Lyrics & translation,
 * Privacy & history, Storage (cache sliders + personal-use download notice), About & Licenses.
 */
@Composable
fun RezonaSettingsScreen(
    initialSection: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferences = remember { RezonaPreferences(context) }
    val repository = remember { RezonaLibraryRepository.getInstance(context) }
    val downloadMgr = remember { OfflineDownloadManager.getInstance(context) }

    val quality by preferences.audioQualityFlow.collectAsState(initial = "High")
    val crossfade by preferences.crossfadeSecondsFlow.collectAsState(initial = 4)
    val gapless by preferences.gaplessFlow.collectAsState(initial = true)
    val scrubberStyle by preferences.scrubberStyleFlow.collectAsState(initial = "Line")
    val glassIntensity by preferences.glassIntensityFlow.collectAsState(initial = "Low")
    val vividAmbient by preferences.ambientVividPlayerFlow.collectAsState(initial = true)
    val dockFoldOnScroll by preferences.dockFoldOnScrollFlow.collectAsState(initial = true)
    val cacheLimitMb by preferences.cacheLimitMbFlow.collectAsState(initial = 1024)
    val imageCacheLimitMb by preferences.imageCacheLimitMbFlow.collectAsState(initial = 256)
    val translationEnabled by preferences.lyricsTranslationEnabledFlow.collectAsState(initial = true)
    val romanizationEnabled by preferences.lyricsRomanizationEnabledFlow.collectAsState(initial = false)
    val blurUnfocused by preferences.lyricsBlurUnfocusedFlow.collectAsState(initial = true)
    val pauseHistory by preferences.pauseHistoryFlow.collectAsState(initial = false)

    var showLicenses by remember { mutableStateOf(false) }
    var showDebugScreen by remember { mutableStateOf(false) }
    var showGlassGallery by remember { mutableStateOf(false) }

    val offlineBytes = remember(downloadMgr) { downloadMgr.totalStorageBytes() }
    val offlineMb = String.format(Locale.ENGLISH, "%.1f MB", offlineBytes / (1024f * 1024f))

    if (showLicenses) {
        RezonaLicensesScreen(onBack = { showLicenses = false })
        return
    }
    if (showDebugScreen) {
        RezonaDebugScreen(
            onBack = { showDebugScreen = false },
            onOpenGlassGallery = {
                showDebugScreen = false
                showGlassGallery = true
            }
        )
        return
    }
    if (showGlassGallery) {
        RezonaGlassGalleryScreen(onBack = { showGlassGallery = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE007090D))
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RezonaActionDisc(size = 42.dp, onClick = onDismiss) {
                Icon(painter = painterResource(id = R.drawable.ic_arrow_back), contentDescription = "Close Settings", tint = PureWhite, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = initialSection?.let { "Settings · $it" } ?: "Settings",
                    style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = PearlWhite
                )
                Text(text = "YouTube Music engine & on-device audio", style = Typography.bodySmall, color = SmokySilver)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            contentPadding = PaddingValues(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // ── 1. SOUND AND PLAYBACK ────────────────────────────────────────
            item(key = "sec_sound") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsSectionHeader("SOUND AND PLAYBACK")
                    Text("Streaming Quality", style = Typography.titleMedium, color = PearlWhite)
                    Text("Opus / M4A stream preference via NewPipeExtractor", style = Typography.bodySmall, color = SmokySilver)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Standard", "High", "Lossless").forEach { q ->
                            val isSelected = quality == q
                            RezonaGlassPill(isActive = isSelected, onClick = { coroutineScope.launch { preferences.setAudioQuality(q) } }, modifier = Modifier.weight(1f)) {
                                Text(q, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) PureWhite else SoftSilver)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Now Playing Scrubber", style = Typography.titleMedium, color = PearlWhite)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Line", "Waveform").forEach { style ->
                            val isSelected = scrubberStyle == style
                            RezonaGlassPill(isActive = isSelected, onClick = { coroutineScope.launch { preferences.setScrubberStyle(style) } }, modifier = Modifier.weight(1f)) {
                                Text(style, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) PureWhite else SoftSilver)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    RezonaHairlineDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Crossfade Duration", style = Typography.titleMedium, color = PearlWhite)
                            Text("Smooth overlap between tracks", style = Typography.bodySmall, color = SmokySilver)
                        }
                        Text("${crossfade}s", style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                    }
                    RezonaGlassSlider(
                        value = crossfade.toFloat(),
                        onValueChange = { coroutineScope.launch { preferences.setCrossfadeSeconds(it.roundToInt()) } },
                        valueRange = 0f..12f,
                        formatBubble = { "${it.roundToInt()}s" }
                    )

                    SettingsToggleRow(
                        title = "Gapless Playback",
                        subtitle = "Eliminate silence between studio album tracks",
                        checked = gapless,
                        onCheckedChange = { coroutineScope.launch { preferences.setGaplessEnabled(it) } }
                    )
                    RezonaHairlineDivider()
                }
            }

            // ── 2. APPEARANCE ────────────────────────────────────────────────
            item(key = "sec_appearance") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsSectionHeader("APPEARANCE")
                    Text("Liquid Glass Intensity", style = Typography.titleMedium, color = PearlWhite)
                    Text("Real Haze blur on floating surfaces (Low uses gradient scrims)", style = Typography.bodySmall, color = SmokySilver)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("Low", "High").forEach { intensity ->
                            val isSelected = glassIntensity == intensity
                            RezonaGlassPill(
                                isActive = isSelected,
                                onClick = {
                                    coroutineScope.launch {
                                        preferences.setGlassIntensity(intensity)
                                        GlassIntensity.current = if (intensity == "High") GlassIntensity.HIGH else GlassIntensity.LOW
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(intensity, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) PureWhite else SoftSilver)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsToggleRow(
                        title = "Vivid Ambient Player",
                        subtitle = "Higher cover-color bloom behind Now Playing",
                        checked = vividAmbient,
                        onCheckedChange = { coroutineScope.launch { preferences.setAmbientVividPlayer(it) } }
                    )
                    SettingsToggleRow(
                        title = "Collapse Dock on Scroll",
                        subtitle = "Shrink bottom tabs into a round icon alongside Mini Player",
                        checked = dockFoldOnScroll,
                        onCheckedChange = { coroutineScope.launch { preferences.setDockFoldOnScroll(it) } }
                    )
                    RezonaHairlineDivider()
                }
            }

            // ── 3. LANGUAGE AND CONTENT ──────────────────────────────────────
            item(key = "sec_language") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsSectionHeader("LANGUAGE AND CONTENT")
                    SettingsInfoRow("Interface Language", "English (Strict Default)")
                    RezonaHairlineDivider()
                    SettingsInfoRow("Streaming Source", "YouTube Music (InnerTube WEB_REMIX + NewPipe)")
                    RezonaHairlineDivider()
                }
            }

            // ── 4. LYRICS AND TRANSLATION ────────────────────────────────────
            item(key = "sec_lyrics") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsSectionHeader("LYRICS AND TRANSLATION")
                    SettingsInfoRow("Lyrics Priority", "LRCLIB synced LRC primary · YouTube Music fallback")
                    SettingsToggleRow(
                        title = "On-Device Translation",
                        subtitle = "Show translated lyric gloss below lines in device language",
                        checked = translationEnabled,
                        onCheckedChange = { coroutineScope.launch { preferences.setLyricsTranslationEnabled(it) } }
                    )
                    SettingsToggleRow(
                        title = "Romanization",
                        subtitle = "Transliterate Korean, Japanese & Cyrillic lines",
                        checked = romanizationEnabled,
                        onCheckedChange = { coroutineScope.launch { preferences.setLyricsRomanizationEnabled(it) } }
                    )
                    SettingsToggleRow(
                        title = "Blur Unfocused Lines",
                        subtitle = "Progressive optical depth blur on non-active lyrics",
                        checked = blurUnfocused,
                        onCheckedChange = { coroutineScope.launch { preferences.setLyricsBlurUnfocused(it) } }
                    )
                    RezonaHairlineDivider()
                }
            }

            // ── 5. PRIVACY AND HISTORY ───────────────────────────────────────
            item(key = "sec_privacy") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsSectionHeader("PRIVACY AND HISTORY")
                    SettingsToggleRow(
                        title = "Pause Listening History",
                        subtitle = "Do not record new plays to Room Stats or Time Capsule",
                        checked = pauseHistory,
                        onCheckedChange = { coroutineScope.launch { preferences.setPauseHistory(it) } }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        RezonaGlassButton(
                            text = "Clear Play History",
                            isPrimary = false,
                            onClick = {
                                coroutineScope.launch {
                                    repository.clearPlaybackHistory()
                                    Toast.makeText(context, "Playback history cleared", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        RezonaGlassButton(
                            text = "Clear Searches",
                            isPrimary = false,
                            onClick = {
                                coroutineScope.launch {
                                    repository.clearSearchHistory()
                                    Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── 6. STORAGE & OFFLINE AUDIO ───────────────────────────────────
            item(key = "sec_storage") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsSectionHeader("STORAGE")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Playback Audio Cache Limit", style = Typography.titleMedium, color = PearlWhite)
                        Text("$cacheLimitMb MB", style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                    }
                    RezonaGlassSlider(
                        value = cacheLimitMb.toFloat(),
                        onValueChange = { coroutineScope.launch { preferences.setCacheLimitMb((it / 128f).roundToInt() * 128) } },
                        valueRange = 256f..4096f,
                        formatBubble = { "${(it / 128f).roundToInt() * 128} MB" }
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Image Cover Cache Limit", style = Typography.titleMedium, color = PearlWhite)
                        Text("$imageCacheLimitMb MB", style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                    }
                    RezonaGlassSlider(
                        value = imageCacheLimitMb.toFloat(),
                        onValueChange = { coroutineScope.launch { preferences.setImageCacheLimitMb((it / 64f).roundToInt() * 64) } },
                        valueRange = 64f..1024f,
                        formatBubble = { "${(it / 64f).roundToInt() * 64} MB" }
                    )

                    Text(
                        text = "App-private offline copies: $offlineMb (Saved inside Rezona for personal offline playback only; no export or format conversion).",
                        style = Typography.bodySmall,
                        color = SmokySilver
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        RezonaGlassButton(
                            text = "Clear App Cache",
                            isPrimary = false,
                            onClick = {
                                runCatching { context.cacheDir.deleteRecursively() }
                                Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        RezonaGlassButton(
                            text = "Clear Downloads",
                            isPrimary = false,
                            onClick = {
                                coroutineScope.launch {
                                    downloadMgr.clearAllDownloads()
                                    Toast.makeText(context, "Offline copies removed", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── 7. ABOUT & DIAGNOSTICS ───────────────────────────────────────
            item(key = "sec_about") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsSectionHeader("ABOUT")
                    SettingsClickableRow("Open-source licenses", "NewPipeExtractor, InnerTune/Metrolist/SimpMusic (GPLv3), Haze, Media3") {
                        showLicenses = true
                    }
                    RezonaHairlineDivider()
                    SettingsClickableRow("Backend & Search Diagnostics", "Live REAL/MOCK badges, section counts & raw request params") {
                        showDebugScreen = true
                    }
                    RezonaHairlineDivider()
                    SettingsClickableRow("Liquid Glass Gallery", "Inspect all GlassSurface variants over live backdrops") {
                        showGlassGallery = true
                    }
                    RezonaHairlineDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Rezona v2.0 · Monochrome Liquid Glass Audio Player", style = Typography.bodySmall, color = SmokySilver)
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
        color = SoftSilver,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun SettingsToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = Typography.titleMedium, color = PearlWhite)
            Text(text = subtitle, style = Typography.bodySmall, color = SmokySilver)
        }
        RezonaGlassToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsInfoRow(title: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(text = title, style = Typography.titleMedium, color = PearlWhite)
        Text(text = value, style = Typography.bodySmall, color = SmokySilver)
    }
}

@Composable
private fun SettingsClickableRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = Typography.titleMedium, color = PearlWhite)
            Text(text = subtitle, style = Typography.bodySmall, color = SmokySilver)
        }
        Icon(painter = painterResource(id = R.drawable.ic_chevron_right), contentDescription = null, tint = SoftSilver, modifier = Modifier.size(18.dp))
    }
}
