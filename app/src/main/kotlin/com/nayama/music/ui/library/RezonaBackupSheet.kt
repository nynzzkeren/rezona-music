package com.nayama.music.ui.library

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.data.importer.BackupPreview
import com.nayama.music.data.importer.ImportResult
import com.nayama.music.data.importer.RezonaImportExportManager
import com.nayama.music.data.repository.RezonaLibraryRepository
import com.nayama.music.ui.components.RezonaActionDisc
import com.nayama.music.ui.components.RezonaGlassCard
import com.nayama.music.ui.components.RezonaGlassPill
import com.nayama.music.ui.theme.Graphite
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.Typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RezonaBackupSheet(
    onDismiss: () -> Unit,
    onPlaylistCreated: (String) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { RezonaLibraryRepository.getInstance(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog states
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkInput by remember { mutableStateOf("") }
    var isReadingPlaylist by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Backup restore states
    var backupPreview by remember { mutableStateOf<BackupPreview?>(null) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }

    // Auto-detect link on clipboard on open
    LaunchedEffect(Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()?.trim() ?: ""
            if (RezonaImportExportManager.isYouTubeMusicPlaylistUrl(text)) {
                linkInput = text
                showLinkDialog = true
                snackbarHostState.showSnackbar("YouTube Music link detected from clipboard")
            }
        }
    }

    // SAF File pickers
    val m3uPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isReadingPlaylist = true
            scope.launch {
                try {
                    val result = RezonaImportExportManager.importM3uFile(context, uri, repository)
                    importResult = result
                } catch (e: Exception) {
                    errorMessage = e.localizedMessage ?: "Failed to import M3U file"
                } finally {
                    isReadingPlaylist = false
                }
            }
        }
    }

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isReadingPlaylist = true
            scope.launch {
                try {
                    val result = RezonaImportExportManager.importCsvFile(context, uri, repository)
                    importResult = result
                } catch (e: Exception) {
                    errorMessage = e.localizedMessage ?: "Failed to import CSV file"
                } finally {
                    isReadingPlaylist = false
                }
            }
        }
    }

    // Export Document Launcher
    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = RezonaImportExportManager.exportBackupJson(context)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                                writer.write(json)
                            }
                        }
                    }
                    snackbarHostState.showSnackbar("Library backup saved successfully")
                } catch (e: Exception) {
                    errorMessage = "Export failed: ${e.localizedMessage}"
                }
            }
        }
    }

    // Restore Document Launcher
    val restoreDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { ins ->
                            BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).readText()
                        }
                    }
                    if (json != null) {
                        val preview = RezonaImportExportManager.inspectBackup(json)
                        pendingRestoreJson = json
                        backupPreview = preview
                    }
                } catch (e: Exception) {
                    errorMessage = "Invalid backup file: ${e.localizedMessage}"
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xF208090B),
        scrimColor = Color(0x99000000)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            SnackbarHost(hostState = snackbarHostState)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Backup & Restore",
                        style = Typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        color = PearlWhite
                    )
                    Text(
                        text = "Import playlists or export your library",
                        style = Typography.bodySmall,
                        color = SmokySilver
                    )
                }

                RezonaActionDisc(size = 36.dp, onClick = onDismiss) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close",
                        tint = SoftSilver,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Option 1: Import YouTube Music Link
            RezonaGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        linkInput = ""
                        showLinkDialog = true
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF0000).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_rezona_search),
                            contentDescription = null,
                            tint = Color(0xFFFF4E4E),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Import YouTube Music playlist", style = Typography.titleMedium, color = PearlWhite)
                        Text(text = "Paste a music.youtube.com/playlist link", style = Typography.bodySmall, color = SmokySilver)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Option 3: Import M3U
            RezonaGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { m3uPickerLauncher.launch("*/*") }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Graphite),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_rezona_library),
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Import an M3U playlist", style = Typography.titleMedium, color = PearlWhite)
                        Text(text = "Select an .m3u or .m3u8 audio file", style = Typography.bodySmall, color = SmokySilver)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Option 4: Import CSV
            RezonaGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { csvPickerLauncher.launch("*/*") }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Graphite),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings),
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Import a CSV playlist", style = Typography.titleMedium, color = PearlWhite)
                        Text(text = "Title, Artist, and Duration columns", style = Typography.bodySmall, color = SmokySilver)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Backup & Restore Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RezonaGlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { exportDocumentLauncher.launch("rezona_backup_${System.currentTimeMillis()}.json") }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_add), contentDescription = null, tint = PearlWhite, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Export Backup", style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = PearlWhite)
                        Text(text = "Save as JSON", style = Typography.bodySmall.copy(fontSize = 10.sp), color = SmokySilver)
                    }
                }

                RezonaGlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { restoreDocumentLauncher.launch(arrayOf("application/json", "*/*")) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_history), contentDescription = null, tint = PearlWhite, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Restore Backup", style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = PearlWhite)
                        Text(text = "Open JSON backup", style = Typography.bodySmall.copy(fontSize = 10.sp), color = SmokySilver)
                    }
                }
            }
        }
    }

    // Link Input Dialog
    if (showLinkDialog) {
        com.nayama.music.ui.components.RezonaGlassDialog(
            title = "Import Playlist Link",
            onDismissRequest = { showLinkDialog = false },
            confirmText = "Import",
            onConfirm = {
                val url = linkInput.trim()
                if (RezonaImportExportManager.isYouTubeMusicPlaylistUrl(url)) {
                    showLinkDialog = false
                    isReadingPlaylist = true
                    scope.launch {
                        try {
                            val res = RezonaImportExportManager.importFromLink(url, repository)
                            importResult = res
                        } catch (e: Exception) {
                            errorMessage = e.localizedMessage ?: "Failed to import link"
                        } finally {
                            isReadingPlaylist = false
                        }
                    }
                } else {
                    errorMessage = "Please enter a valid YouTube Music playlist URL (e.g. music.youtube.com/playlist?list=...)"
                }
            },
            dismissText = "Cancel"
        ) {
            Text(
                text = "Paste a public YouTube Music playlist link. No account login required.",
                style = Typography.bodySmall,
                color = SmokySilver
            )
            Spacer(modifier = Modifier.height(12.dp))
            com.nayama.music.ui.components.RezonaGlassTextField(
                value = linkInput,
                onValueChange = { linkInput = it },
                placeholder = "https://music.youtube.com/playlist?list=...",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Progress Dialog: "Reading playlist..."
    if (isReadingPlaylist) {
        com.nayama.music.ui.components.RezonaGlassDialog(
            title = "Reading playlist...",
            onDismissRequest = {},
            confirmText = "",
            onConfirm = null,
            dismissText = null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(color = PureWhite, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "Matching tracks on YouTube Music...", color = PearlWhite, style = Typography.bodyMedium)
            }
        }
    }

    // Result Dialog
    if (importResult != null) {
        val result = importResult!!
        val closeAndOpen = {
            val plId = result.createdPlaylistId
            importResult = null
            onDismiss()
            onPlaylistCreated(plId)
        }
        com.nayama.music.ui.components.RezonaGlassDialog(
            title = "Playlist Created",
            onDismissRequest = closeAndOpen,
            confirmText = "Open Playlist",
            onConfirm = closeAndOpen,
            dismissText = null
        ) {
            Column {
                if (result.notFoundSongs.isEmpty()) {
                    Text(
                        text = "Playlist created with ${result.songsFoundCount} songs. All songs were found.",
                        color = PearlWhite,
                        style = Typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "Playlist created with ${result.songsFoundCount} songs. ${result.notFoundSongs.size} songs could not be matched:",
                        color = PearlWhite,
                        style = Typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    result.notFoundSongs.forEach { title ->
                        Text(text = "• $title", color = SmokySilver, style = Typography.bodySmall)
                    }
                }
            }
        }
    }

    // Backup Restore Preview Dialog
    if (backupPreview != null && pendingRestoreJson != null) {
        val preview = backupPreview!!
        com.nayama.music.ui.components.RezonaGlassDialog(
            title = "Restore Backup?",
            onDismissRequest = {
                backupPreview = null
                pendingRestoreJson = null
            },
            confirmText = "Restore",
            onConfirm = {
                val json = pendingRestoreJson
                backupPreview = null
                pendingRestoreJson = null
                if (json != null) {
                    scope.launch {
                        try {
                            RezonaImportExportManager.restoreBackup(context, json)
                            snackbarHostState.showSnackbar("Library restored successfully")
                        } catch (e: Exception) {
                            errorMessage = "Restore failed: ${e.localizedMessage}"
                        }
                    }
                }
            },
            dismissText = "Cancel"
        ) {
            Text(
                text = "This backup contains ${preview.playlistCount} playlists and ${preview.likedCount} liked songs.",
                color = PearlWhite,
                style = Typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Would you like to restore these into your library?",
                color = SmokySilver,
                style = Typography.bodySmall
            )
        }
    }

    // Error Dialog
    if (errorMessage != null) {
        com.nayama.music.ui.components.RezonaGlassDialog(
            title = "Notice",
            onDismissRequest = { errorMessage = null },
            confirmText = "OK",
            onConfirm = { errorMessage = null },
            dismissText = null
        ) {
            Text(
                text = errorMessage!!,
                color = SmokySilver,
                style = Typography.bodyMedium
            )
        }
    }
}
