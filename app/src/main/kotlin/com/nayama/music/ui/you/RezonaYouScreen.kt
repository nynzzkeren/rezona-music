package com.nayama.music.ui.you

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import com.nayama.music.auth.FirebaseAuthActionResult
import com.nayama.music.auth.FirebaseAuthManager
import com.nayama.music.data.local.RezonaDatabase
import com.nayama.music.data.preferences.RezonaPreferences
import com.nayama.music.data.repository.RezonaLibraryRepository
import com.nayama.music.data.repository.toRezonaTrack
import com.nayama.music.navigation.RezonaFeatureFlags
import com.nayama.music.playback.RezonaPlayerController
import com.nayama.music.sync.RezonaCloudSyncManager
import com.nayama.music.ui.components.LocalRezonaHazeState
import com.nayama.music.ui.components.RezonaAsyncCoverArt
import com.nayama.music.ui.components.RezonaEqualizerModalSheet
import com.nayama.music.ui.components.RezonaGlassButton
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
    val authManager = remember { FirebaseAuthManager.getInstance(context) }
    val syncManager = remember { RezonaCloudSyncManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val hazeState = LocalRezonaHazeState.current
    val scrollConnection = rememberAutoFoldNestedScrollConnection(onFoldChanged)

    val historyEntities by repository.getRecentHistory(40).collectAsState(initial = emptyList())
    val mostPlayedEntities by repository.getMostPlayed(10).collectAsState(initial = emptyList())
    val historyEntries by db.historyDao().getRecentEntries(300).collectAsState(initial = emptyList())
    val likedCount by repository.getLikedCount().collectAsState(initial = 0)
    val authSession by authManager.session.collectAsState()
    val syncStatus by syncManager.status.collectAsState()

    var showHistorySheet by remember { mutableStateOf(false) }
    var showRecapSheet by remember { mutableStateOf(false) }
    var showListenTogetherStub by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showBackupSheet by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var isAccountBusy by remember { mutableStateOf(false) }

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

            // Account Card (Firebase Authenticated User or Local Guest Mode)
            item(key = "you_guest_card") {
                val signedIn = authSession.isSignedIn && authSession.isEmailVerified
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
                                    .background(if (signedIn) Color(0x2EFFFFFF) else Color(0x1CFFFFFF))
                                    .border(1.dp, SpecularCardBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (signedIn) {
                                    val initial = authSession.displayName.firstOrNull()?.uppercaseChar()?.toString()
                                        ?: authSession.email.firstOrNull()?.uppercaseChar()?.toString()
                                        ?: "R"
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
                                    text = if (signedIn) authSession.displayName else "Guest Listener",
                                    style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = PureWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (signedIn) {
                                        "${authSession.email} · ${authSession.providerLabel}"
                                    } else {
                                        "Local-only library · $likedCount liked · Cloud sync off"
                                    },
                                    style = Typography.bodySmall,
                                    color = SmokySilver,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        RezonaHairlineDivider()

                        if (signedIn) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (syncStatus.pendingOpsCount > 0) {
                                            "Pending upload: ${syncStatus.pendingOpsCount} ops"
                                        } else {
                                            "$likedCount liked · Cloud synced"
                                        },
                                        style = Typography.bodySmall,
                                        color = SoftSilver
                                    )
                                    Text(
                                        text = "UID: ${authSession.uid?.take(10).orEmpty()}...",
                                        style = Typography.labelSmall,
                                        color = SmokySilver
                                    )
                                }
                                RezonaGlassPill(
                                    isActive = false,
                                    onClick = { onOpenSettingsSection("Sync") },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        text = "Sync status",
                                        style = Typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = PearlWhite
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RezonaGlassButton(
                                    text = "Sign out",
                                    isPrimary = false,
                                    onClick = { showSignOutDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                )
                                RezonaGlassButton(
                                    text = "Delete account",
                                    isPrimary = false,
                                    onClick = {
                                        deletePassword = ""
                                        deleteError = null
                                        showDeleteAccountDialog = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
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
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        text = "Sign in / Create Account",
                                        style = Typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = PearlWhite,
                                        maxLines = 1
                                    )
                                }
                                RezonaGlassPill(
                                    isActive = true,
                                    onClick = {
                                        if (isAccountBusy) return@RezonaGlassPill
                                        scope.launch {
                                            isAccountBusy = true
                                            when (val res = authManager.signInWithGoogle(context)) {
                                                is FirebaseAuthActionResult.SignedIn -> {
                                                    Toast.makeText(context, "Signed in as ${res.email}", Toast.LENGTH_SHORT).show()
                                                }
                                                is FirebaseAuthActionResult.AccountCollision -> {
                                                    onOpenAuth()
                                                }
                                                is FirebaseAuthActionResult.Error -> {
                                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                                }
                                                else -> Unit
                                            }
                                            isAccountBusy = false
                                        }
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        text = if (isAccountBusy) "..." else "G  Google",
                                        style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = PureWhite
                                    )
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
                YouPlainRow(R.drawable.ic_cloud_done, "Cloud sync & status", "UID, queued ops, push/pull & force restore") {
                    onOpenSettingsSection("Sync")
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

    if (showSignOutDialog) {
        RezonaGlassDialog(
            title = "Sign Out",
            onDismissRequest = { showSignOutDialog = false },
            dismissText = null,
            onConfirm = null
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Keep local library on this device or clear local data? Your cloud data will remain safely stored.",
                    style = Typography.bodyMedium,
                    color = SoftSilver
                )
                RezonaGlassButton(
                    text = "Keep Local Data",
                    isPrimary = true,
                    onClick = {
                        scope.launch {
                            syncManager.detachListeners()
                            authManager.signOut()
                            showSignOutDialog = false
                            Toast.makeText(context, "Signed out (local library kept)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                )
                RezonaGlassButton(
                    text = "Clear Local Data",
                    isPrimary = false,
                    onClick = {
                        scope.launch {
                            syncManager.detachListeners()
                            syncManager.clearLocalUserLibraryTables()
                            authManager.signOut()
                            showSignOutDialog = false
                            Toast.makeText(context, "Signed out and local data cleared", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                )
                RezonaGlassButton(
                    text = "Cancel",
                    isPrimary = false,
                    onClick = { showSignOutDialog = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                )
            }
        }
    }

    if (showDeleteAccountDialog) {
        RezonaGlassDialog(
            title = "Delete Account Permanently",
            onDismissRequest = {
                if (!isAccountBusy) showDeleteAccountDialog = false
            },
            confirmText = if (isAccountBusy) "Deleting..." else if (authSession.isPasswordUser) "Verify & Delete" else "Re-authenticate with Google",
            onConfirm = {
                if (isAccountBusy) return@RezonaGlassDialog
                val uid = authSession.uid ?: return@RezonaGlassDialog
                scope.launch {
                    isAccountBusy = true
                    deleteError = null
                    val reauth = if (authSession.isPasswordUser) {
                        if (deletePassword.isEmpty()) {
                            Result.failure(IllegalStateException("Enter your password to confirm."))
                        } else {
                            authManager.reauthenticateWithPassword(deletePassword)
                        }
                    } else {
                        authManager.reauthenticateWithGoogle(context)
                    }
                    if (reauth.isFailure) {
                        deleteError = reauth.exceptionOrNull()?.message ?: "Re-authentication failed."
                        isAccountBusy = false
                        return@launch
                    }

                    syncManager.detachListeners()
                    val wipeRes = syncManager.deleteAllCloudDataForUid(uid)
                    if (wipeRes.isFailure) {
                        deleteError = wipeRes.exceptionOrNull()?.message ?: "Failed to delete cloud data."
                        isAccountBusy = false
                        return@launch
                    }

                    val delUser = authManager.deleteAuthenticatedFirebaseUser()
                    if (delUser.isFailure) {
                        deleteError = delUser.exceptionOrNull()?.message ?: "Failed to delete account."
                        isAccountBusy = false
                        return@launch
                    }

                    syncManager.clearLocalUserLibraryTables()
                    preferences.resetSyncAndAccountLocalData()
                    showDeleteAccountDialog = false
                    isAccountBusy = false
                    Toast.makeText(context, "Account and cloud library deleted", Toast.LENGTH_SHORT).show()
                }
            },
            dismissText = "Cancel"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "This permanently deletes your Firebase Auth user, all cloud synced likes, playlists, library items, history, pins, and settings, and wipes local library data on this device.",
                    style = Typography.bodySmall,
                    color = SoftSilver
                )
                if (authSession.isPasswordUser) {
                    RezonaGlassTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it; deleteError = null },
                        placeholder = "Current password to confirm",
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
                if (!deleteError.isNullOrBlank()) {
                    Text(deleteError!!, style = Typography.bodySmall, color = Color(0xFFFF8E96))
                }
            }
        }
    }

    if (showHistorySheet) {
        val tracks = historyEntities.map { it.toRezonaTrack() }.distinctBy { it.id }
        val playCountByTrack = remember(historyEntries) {
            historyEntries.groupingBy { it.trackId }.eachCount()
        }
        val lastPlayedByTrack = remember(historyEntries) {
            historyEntries.groupBy { it.trackId }.mapValues { (_, list) -> list.maxOf { it.playedAt } }
        }
        val timeFmt = remember { java.text.SimpleDateFormat("dd MMM · HH:mm", java.util.Locale.getDefault()) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = sheetState,
            containerColor = Color(0xEC101218),
            scrimColor = Color(0x88000000)
        ) {
            Column(modifier = Modifier.fillMaxWidth().height(500.dp).padding(horizontal = 20.dp)) {
                Text("Listening History", style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PearlWhite)
                Text("${tracks.size} unique tracks · ${historyEntries.size} total plays", style = Typography.bodySmall, color = SmokySilver)
                Spacer(modifier = Modifier.height(12.dp))
                RezonaHairlineDivider()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(tracks, key = { "yh_${it.id}" }) { item ->
                        val plays = playCountByTrack[item.id] ?: 1
                        val lastAt = lastPlayedByTrack[item.id]
                        val playedLabel = if (lastAt != null && lastAt > 0L) {
                            "${plays}× played · ${timeFmt.format(java.util.Date(lastAt))}"
                        } else {
                            "${plays}× played"
                        }
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
                                Text(playedLabel, style = Typography.labelSmall, color = SoftSilver.copy(alpha = 0.8f), maxLines = 1)
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
        val playCountByTrack = remember(historyEntries) {
            historyEntries.groupingBy { it.trackId }.eachCount()
        }
        val durationSecById = remember(historyEntities, mostPlayedEntities) {
            (historyEntities + mostPlayedEntities).associate { entity ->
                val sec = (entity.durationMs / 1000L).takeIf { it > 0L } ?: 210L
                entity.id to sec
            }
        }
        val totalListenSec = historyEntries.sumOf { entry ->
            durationSecById[entry.trackId] ?: 210L
        }
        val totalMins = (totalListenSec / 60L).coerceAtLeast(0L)
        val hours = totalMins / 60L
        val remMins = totalMins % 60L
        val listenTimeLabel = if (hours > 0L) "${hours}h ${remMins}m" else "${remMins}m"

        RezonaGlassDialog(
            title = "Stats & Recap",
            onDismissRequest = { showRecapSheet = false },
            confirmText = "Done",
            onConfirm = { showRecapSheet = false },
            dismissText = null
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$totalPlays total plays · $listenTimeLabel listened", style = Typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                RezonaHairlineDivider()
                Text("Top Tracks (Plays & Time)", style = Typography.labelMedium, color = SoftSilver)
                if (topTracks.isEmpty()) {
                    Text("Play songs to build your local recap.", style = Typography.bodySmall, color = SmokySilver)
                } else {
                    topTracks.take(5).forEachIndexed { idx, t ->
                        val plays = playCountByTrack[t.id] ?: 1
                        val trackMins = ((plays * (durationSecById[t.id] ?: 210L)) / 60L).coerceAtLeast(1L)
                        Text(
                            text = "${idx + 1}. ${t.title} — ${plays}× (${trackMins}m)",
                            style = Typography.bodySmall,
                            color = PearlWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
