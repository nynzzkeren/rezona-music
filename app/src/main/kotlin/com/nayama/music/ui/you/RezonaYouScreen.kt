package com.nayama.music.ui.you

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.nayama.music.auth.GoogleAuthManager
import com.nayama.music.data.local.RezonaDatabase
import com.nayama.music.data.preferences.RezonaPreferences
import com.nayama.music.data.repository.RezonaLibraryRepository
import com.nayama.music.data.repository.toRezonaTrack
import com.nayama.music.navigation.RezonaFeatureFlags
import com.nayama.music.playback.RezonaPlayerController
import com.nayama.music.ui.components.LocalRezonaHazeState
import com.nayama.music.ui.components.RezonaAsyncCoverArt
import com.nayama.music.ui.components.RezonaEqualizerModalSheet
import com.nayama.music.ui.components.RezonaGlassCard
import com.nayama.music.ui.components.RezonaGlassDialog
import com.nayama.music.ui.components.RezonaGlassPill
import com.nayama.music.ui.components.RezonaGlassTextField
import com.nayama.music.ui.components.RezonaHairlineDivider
import com.nayama.music.ui.components.rezonaHazeSource
import kotlinx.coroutines.launch
import com.nayama.music.ui.library.RezonaBackupSheet
import com.nayama.music.ui.screens.rememberAutoFoldNestedScrollConnection
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.SpecularCardBorder
import com.nayama.music.ui.theme.Typography

/**
 * Stage H: YOU Tab
 * - Account card (Guest state, NO login, NO streak)
 * - Grouped plain rows with hairline separators:
 *   History, Stats and Recap, Listen together (stub), Equalizer, Sound Lab (feature flag),
 *   and direct Settings categories: Sound and playback, Appearance, Language and content,
 *   Lyrics and translation, Privacy and history, Storage, Backup and import, About
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RezonaYouScreen(
    controller: RezonaPlayerController,
    onFoldChanged: (Boolean) -> Unit,
    onOpenSettingsSection: (String?) -> Unit,
    onOpenSoundLab: () -> Unit,
    onOpenAuth: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { RezonaDatabase.getInstance(context) }
    val repository = remember { RezonaLibraryRepository.getInstance(context) }
    val preferences = remember { RezonaPreferences(context) }
    val scope = rememberCoroutineScope()
    val hazeState = LocalRezonaHazeState.current
    val scrollConnection = rememberAutoFoldNestedScrollConnection(onFoldChanged)

    val historyEntities by repository.getRecentHistory(40).collectAsState(initial = emptyList())
    val mostPlayedEntities by repository.getMostPlayed(10).collectAsState(initial = emptyList())
    val historyEntries by db.historyDao().getRecentEntries(300).collectAsState(initial = emptyList())
    val likedCount by repository.getLikedCount().collectAsState(initial = 0)
    val googleAccount by preferences.googleAccountFlow.collectAsState(
        initial = RezonaPreferences.GoogleAccountState()
    )

    var showHistorySheet by remember { mutableStateOf(false) }
    var showRecapSheet by remember { mutableStateOf(false) }
    var showListenTogetherStub by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showBackupSheet by remember { mutableStateOf(false) }
    var showGoogleFallbackDialog by remember { mutableStateOf(false) }
    var manualGoogleEmail by remember { mutableStateOf("") }
    var manualGoogleName by remember { mutableStateOf("") }

    val googlePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked = GoogleAuthManager.parseResult(result.data)
            if (picked != null) {
                scope.launch {
                    preferences.setGoogleAccount(picked.email, picked.displayName)
                }
                Toast.makeText(context, "Signed in as ${picked.email}", Toast.LENGTH_SHORT).show()
            } else {
                showGoogleFallbackDialog = true
            }
        } else if (GoogleAuthManager.getDeviceGoogleAccounts(context).isEmpty()) {
            showGoogleFallbackDialog = true
        }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomPad = if (controller.currentTrack.value != null) 168.dp else 108.dp

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollConnection)
                .rezonaHazeSource(hazeState),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = statusTop + 14.dp, bottom = bottomPad),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "you_header") {
                Text(
                    text = "You",
                    style = Typography.displayLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                    color = PureWhite
                )
            }

            // Account Card (Guest by default or native Android Google Account chooser profile)
            item(key = "you_guest_card") {
                RezonaGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(if (googleAccount.isSignedIn) Color(0x2EFFFFFF) else Color(0x1CFFFFFF))
                                    .border(1.dp, SpecularCardBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (googleAccount.isSignedIn) {
                                    val initial = googleAccount.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "G"
                                    Text(
                                        text = initial,
                                        style = Typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                                        color = PureWhite
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_rezona_logo),
                                        contentDescription = "Guest Listener",
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (googleAccount.isSignedIn) googleAccount.displayName else "Guest Listener",
                                    style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = PureWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (googleAccount.isSignedIn) {
                                        "${googleAccount.email} · $likedCount liked"
                                    } else {
                                        "On-device library · $likedCount liked · No account needed"
                                    },
                                    style = Typography.bodySmall,
                                    color = SmokySilver,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        RezonaHairlineDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (googleAccount.isSignedIn) {
                                Text(
                                    text = if (googleAccount.provider == "email") {
                                        "Email account signed in on device"
                                    } else {
                                        "Google profile linked on device"
                                    },
                                    style = Typography.bodySmall,
                                    color = SoftSilver,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                RezonaGlassPill(
                                    isActive = false,
                                    onClick = {
                                        scope.launch { preferences.clearGoogleAccount() }
                                        Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text(
                                        text = "Sign out",
                                        style = Typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = PearlWhite
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RezonaGlassPill(
                                        isActive = false,
                                        onClick = onOpenAuth,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "Email Sign in / up",
                                            style = Typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = PearlWhite,
                                            maxLines = 1
                                        )
                                    }
                                    RezonaGlassPill(
                                        isActive = true,
                                        onClick = {
                                            val launched = runCatching {
                                                googlePickerLauncher.launch(GoogleAuthManager.createGoogleAccountChooserIntent())
                                                true
                                            }.getOrDefault(false)
                                            if (!launched) {
                                                showGoogleFallbackDialog = true
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = "G  Google",
                                            style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = PureWhite
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

        // Personal Listening Group (Plain grouped rows)
        item(key = "you_personal_group") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "LISTENING & AUDIO",
                    style = Typography.labelSmall.copy(letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold),
                    color = SoftSilver,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                YouPlainRow(R.drawable.ic_history, "History", "${historyEntities.size} recent tracks") {
                    showHistorySheet = true
                }
                RezonaHairlineDivider(startIndent = 38.dp)
                YouPlainRow(R.drawable.ic_stats, "Stats and Recap", "Local listening totals & top tracks") {
                    showRecapSheet = true
                }
                RezonaHairlineDivider(startIndent = 38.dp)
                YouPlainRow(R.drawable.ic_community, "Listen together", "Coming soon") {
                    showListenTogetherStub = true
                }
                RezonaHairlineDivider(startIndent = 38.dp)
                YouPlainRow(R.drawable.ic_equalizer, "Equalizer", "5-band EQ & presets") {
                    showEqualizerSheet = true
                }
                if (RezonaFeatureFlags.ENABLE_STUDIO_IN_YOU) {
                    RezonaHairlineDivider(startIndent = 38.dp)
                    YouPlainRow(R.drawable.ic_rezona_studio, "Sound Lab", "Mood Dial, Focus Rooms & Sound Check") {
                        onOpenSoundLab()
                    }
                }
            }
        }

        // Settings Group (Plain grouped rows)
        item(key = "you_settings_group") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SETTINGS",
                    style = Typography.labelSmall.copy(letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold),
                    color = SoftSilver,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                YouPlainRow(R.drawable.ic_tune, "Sound and playback", "Quality, crossfade, gapless, scrubber") {
                    onOpenSettingsSection("Sound")
                }
                RezonaHairlineDivider(startIndent = 38.dp)
                YouPlainRow(R.drawable.ic_ambient, "Appearance", "Liquid Glass intensity, ambient mode, dock") {
                    onOpenSettingsSection("Appearance")
                }
                RezonaHairlineDivider(startIndent = 38.dp)
                YouPlainRow(R.drawable.ic_rezona_home, "Language and content", "English UI · YouTube Music region") {
                    onOpenSettingsSection("Language")
                }
                RezonaHairlineDivider(startIndent = 38.dp)
                YouPlainRow(R.drawable.ic_lyrics, "Lyrics and translation", "LRCLIB, YouTube Music fallback, translation") {
                    onOpenSettingsSection("Lyrics")
                }
                RezonaHairlineDivider(startIndent = 38.dp)
                YouPlainRow(R.drawable.ic_pin, "Privacy and history", "Pause listening & search history") {
                    onOpenSettingsSection("Privacy")
                }
                RezonaHairlineDivider(startIndent = 38.dp)
                YouPlainRow(R.drawable.ic_cloud_done, "Storage", "Playback & image cache sliders, offline files") {
                    onOpenSettingsSection("Storage")
                }
                RezonaHairlineDivider(startIndent = 38.dp)
                YouPlainRow(R.drawable.ic_playlist_add, "Backup and import", "M3U, CSV, YouTube Music link & JSON backup") {
                    showBackupSheet = true
                }
                RezonaHairlineDivider(startIndent = 38.dp)
                YouPlainRow(R.drawable.ic_info, "About", "Diagnostics, Glass Gallery & Open-source licenses") {
                    onOpenSettingsSection("About")
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

    if (showGoogleFallbackDialog) {
        val deviceAccounts = remember(showGoogleFallbackDialog) {
            GoogleAuthManager.getDeviceGoogleAccounts(context)
        }
        RezonaGlassDialog(
            title = "Sign in with Google",
            onDismissRequest = { showGoogleFallbackDialog = false },
            confirmText = "Save Profile",
            onConfirm = {
                val trimmed = manualGoogleEmail.trim()
                if (trimmed.contains("@") && trimmed.contains(".")) {
                    scope.launch {
                        preferences.setGoogleAccount(trimmed, manualGoogleName.trim())
                    }
                    manualGoogleEmail = ""
                    manualGoogleName = ""
                    showGoogleFallbackDialog = false
                    Toast.makeText(context, "Signed in as $trimmed", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Enter a valid Google email", Toast.LENGTH_SHORT).show()
                }
            },
            dismissText = "Cancel"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (deviceAccounts.isNotEmpty()) {
                    Text(
                        text = "Detected Google accounts on device:",
                        style = Typography.labelMedium,
                        color = SoftSilver
                    )
                    deviceAccounts.forEach { acc ->
                        RezonaGlassPill(
                            isActive = false,
                            onClick = {
                                scope.launch {
                                    preferences.setGoogleAccount(acc.email, acc.displayName)
                                }
                                showGoogleFallbackDialog = false
                                Toast.makeText(context, "Signed in as ${acc.email}", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(
                                text = acc.email,
                                style = Typography.bodySmall,
                                color = PureWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    RezonaHairlineDivider()
                } else {
                    Text(
                        text = "No device Google account was returned. Add one in Android Settings or enter your Google email for local profile display.",
                        style = Typography.bodySmall,
                        color = SoftSilver
                    )
                    RezonaGlassPill(
                        isActive = false,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_ADD_ACCOUNT).apply {
                                        putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                                    }
                                )
                            }
                        }
                    ) {
                        Text(
                            text = "Open Android Add Account",
                            style = Typography.labelMedium,
                            color = PearlWhite
                        )
                    }
                }
                RezonaGlassTextField(
                    value = manualGoogleEmail,
                    onValueChange = { manualGoogleEmail = it },
                    placeholder = "yourname@gmail.com"
                )
                RezonaGlassTextField(
                    value = manualGoogleName,
                    onValueChange = { manualGoogleName = it },
                    placeholder = "Display name (optional)"
                )
            }
        }
    }

    if (showHistorySheet) {
        val tracks = historyEntities.map { it.toRezonaTrack() }.distinctBy { it.id }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = sheetState,
            containerColor = Color(0xEC101218),
            scrimColor = Color(0x88000000)
        ) {
            Column(modifier = Modifier.fillMaxWidth().height(500.dp).padding(horizontal = 20.dp)) {
                Text("Listening History", style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PearlWhite)
                Text("${tracks.size} played tracks", style = Typography.bodySmall, color = SmokySilver)
                Spacer(modifier = Modifier.height(12.dp))
                RezonaHairlineDivider()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(tracks, key = { "yh_${it.id}" }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    controller.playTrack(item, queueTitle = "History", autoQueueRadio = true)
                                    showHistorySheet = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RezonaAsyncCoverArt(item.coverArtUrl, item.index, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(46.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = Typography.titleMedium, color = PearlWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.artist, style = Typography.bodySmall, color = SmokySilver, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        RezonaHairlineDivider(startIndent = 58.dp)
                    }
                }
            }
        }
    }

    if (showRecapSheet) {
        val totalPlays = historyEntries.size
        val topTracks = mostPlayedEntities.map { it.toRezonaTrack() }
        val estMinutes = ((topTracks.sumOf { t ->
            val parts = t.duration.split(":")
            ((parts.getOrNull(0)?.toLongOrNull() ?: 3L) * 60L + (parts.getOrNull(1)?.toLongOrNull() ?: 30L))
        }.coerceAtLeast(180L)) / 60L) + (totalPlays * 3L)

        RezonaGlassDialog(
            title = "Stats & Recap",
            onDismissRequest = { showRecapSheet = false },
            confirmText = "Done",
            onConfirm = { showRecapSheet = false },
            dismissText = null
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$totalPlays total plays · ~$estMinutes mins on-device", style = Typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                RezonaHairlineDivider()
                Text("Top Tracks", style = Typography.labelMedium, color = SoftSilver)
                if (topTracks.isEmpty()) {
                    Text("Play songs to build your local recap.", style = Typography.bodySmall, color = SmokySilver)
                } else {
                    topTracks.take(5).forEachIndexed { idx, t ->
                        Text("${idx + 1}. ${t.title} — ${t.artist}", style = Typography.bodySmall, color = PearlWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    if (showListenTogetherStub) {
        RezonaGlassDialog(
            title = "Listen Together",
            onDismissRequest = { showListenTogetherStub = false },
            confirmText = "OK",
            onConfirm = { showListenTogetherStub = false },
            dismissText = null
        ) {
            Text(
                text = "Peer-to-peer listening sessions are marked Coming Soon. Rezona runs strictly on-device with zero external account server.",
                style = Typography.bodyMedium,
                color = SoftSilver
            )
        }
    }

    if (showEqualizerSheet) {
        RezonaEqualizerModalSheet(onDismiss = { showEqualizerSheet = false })
    }

    if (showBackupSheet) {
        RezonaBackupSheet(
            onDismiss = { showBackupSheet = false },
            onPlaylistCreated = { showBackupSheet = false }
        )
    }
}

@Composable
private fun YouPlainRow(
    iconRes: Int,
    title: String,
    description: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = PureWhite,
            modifier = Modifier.size(21.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = Typography.titleMedium.copy(fontWeight = FontWeight.Medium), color = PearlWhite)
            if (!description.isNullOrBlank()) {
                Text(text = description, style = Typography.bodySmall, color = SmokySilver)
            }
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = SoftSilver,
            modifier = Modifier.size(18.dp)
        )
    }
}
