package com.nayama.music.ui.profile

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nayama.music.R
import com.nayama.music.data.local.StreamingQuality
import com.nayama.music.model.Track
import com.nayama.music.ui.components.BackupAndRestoreDialog
import com.nayama.music.ui.components.NayamaImage
import com.nayama.music.ui.components.SectionHeader
import com.nayama.music.ui.components.SupportCoffeeDialog
import com.nayama.music.ui.theme.DarkCharcoal
import com.nayama.music.ui.theme.DeepBlack
import com.nayama.music.ui.theme.ElevatedSurface
import com.nayama.music.ui.theme.Graphite
import com.nayama.music.ui.theme.LightGray
import com.nayama.music.ui.theme.MediumGray
import com.nayama.music.ui.theme.PureBlack
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SurfaceBlack

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onTrackSelect: ((Track) -> Unit)? = null,
    viewModel: ProfileViewModel = viewModel()
) {
    val context = LocalContext.current
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    var showQualityDialog by remember { mutableStateOf(false) }
    var showMultiSourceDialog by remember { mutableStateOf(false) }
    var showBackupRestoreDialog by remember { mutableStateOf(false) }
    var showSupportCoffeeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshStats()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentPadding = PaddingValues(bottom = 130.dp)
    ) {
        // ── Profile Top Banner ────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar with premium dual ring
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .background(ElevatedSurface)
                        .border(1.5.dp, PureWhite, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(SurfaceBlack),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "O",
                        color = PureWhite,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Text(
                    text = "O - Reverie",
                    color = PureWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    modifier = Modifier.padding(top = 14.dp)
                )

                // Dynamic Persona badge based on actual listening history
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(50.dp))
                        .background(ElevatedSurface)
                        .border(0.5.dp, Graphite, RoundedCornerShape(50.dp))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = stats.listenerPersona,
                        color = PureWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // ── Real Listening Statistics Cards ───────────────────────────────
        item {
            SectionHeader(
                title = "Listening Insights",
                subtitle = "Telemetri sesi lokal dari riwayat pemutaran Anda"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "TRACKS",
                    value = "${stats.totalTracksPlayed}",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "MINUTES",
                    value = "${stats.totalListeningMinutes}m",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "TOP GENRE",
                    value = stats.topGenre.take(10),
                    modifier = Modifier.weight(1.2f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "TOP ARTIST",
                    value = stats.topArtist.take(14),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "LIKED SONGS",
                    value = "${stats.totalLikedSongs}",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Top 5 Most Played Tracks ──────────────────────────────────────
        if (stats.topPlayedTracks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(
                    title = "Most Played",
                    subtitle = "Lagu yang paling sering Anda putar"
                )
            }

            itemsIndexed(stats.topPlayedTracks, key = { index, track -> "top_${track.id}_$index" }) { index, track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTrackSelect?.invoke(track) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "#${index + 1}",
                        color = PureWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp)
                    )

                    NayamaImage(
                        url = track.artworkUrl,
                        contentDescription = track.title,
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        fallbackText = track.title
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = PureWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            color = MediumGray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Text(
                        text = track.formattedDuration,
                        color = LightGray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // ── Application Settings & Audio Quality ──────────────────────────
        item {
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(
                title = "Audio & Network",
                subtitle = "Konfigurasi streaming & preferensi jaringan"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Streaming Quality Selector
                PreferenceRow(
                    title = "Kualitas Audio Streaming",
                    subtitle = "${preferences.streamingQuality.label} · ${preferences.streamingQuality.bitrateLabel}",
                    actionText = "UBAH",
                    onClick = { showQualityDialog = true }
                )

                // Wi-Fi Only Streaming Toggle
                PreferenceSwitchRow(
                    title = "Hanya Streaming Lewat Wi-Fi",
                    subtitle = "Mencegah pemutaran audio memakai paket data seluler",
                    checked = preferences.wifiOnlyStreaming,
                    onCheckedChange = { viewModel.setWifiOnly(it) }
                )

                // Auto Retry on Network Loss Toggle
                PreferenceSwitchRow(
                    title = "Auto-Reconnect Audio",
                    subtitle = "Otomatis melanjutkan pemutaran saat jaringan pulih",
                    checked = preferences.autoRetryOnNetworkLoss,
                    onCheckedChange = { viewModel.setAutoRetry(it) }
                )

                // Multi-Source Integrations (YT Music · Spotify · SoundCloud)
                val spotifyStatus = if (preferences.spotifyClientId.isNotBlank()) "Spotify API Aktif" else "Spotify Curated (Embed)"
                val scStatus = if (preferences.soundCloudClientId.isNotBlank()) "SC Custom ID" else "SC Auto"
                PreferenceRow(
                    title = "Multi-Source API Keys (Opsional)",
                    subtitle = "YT Music Aktif · $spotifyStatus · $scStatus",
                    actionText = "ATUR",
                    onClick = { showMultiSourceDialog = true }
                )
            }
        }

        // ── Storage & Cache Management ────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(
                title = "Penyimpanan & Cache",
                subtitle = "Manajemen data sementara aplikasi"
            )

            val cacheMb = preferences.cacheSizeBytes / (1024.0 * 1024.0)
            val cacheText = "%.1f MB terpakai".format(cacheMb)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PreferenceRow(
                    title = "Bersihkan Cache Audio & Gambar",
                    subtitle = "$cacheText di penyimpanan internal",
                    actionText = "BERSIHKAN",
                    onClick = {
                        val freed = viewModel.clearCache()
                        val freedMb = freed / (1024.0 * 1024.0)
                        Toast.makeText(context, "Cache dibersihkan (%.1f MB dibebaskan)".format(freedMb), Toast.LENGTH_SHORT).show()
                    }
                )

                PreferenceRow(
                    title = "Database Lokal Persisten",
                    subtitle = "SQLite schema v1 · ${stats.totalTracksPlayed} entri tersimpan",
                    actionText = "AKTIF",
                    onClick = {}
                )

                PreferenceRow(
                    title = "Backup & Impor Playlist",
                    subtitle = "Spotify, Apple Music, format M3U & CSV",
                    actionText = "IMPOR",
                    onClick = { showBackupRestoreDialog = true }
                )

                PreferenceRow(
                    title = "Mau Traktir Kopi? (QRIS)",
                    subtitle = "Dukungan sukarela, semua fitur O - Reverie gratis",
                    actionText = "DUKUNG",
                    onClick = { showSupportCoffeeDialog = true }
                )
            }
        }

        // ── About Nayama Music & Open License Legal Attribution ───────────
        // ── About O - Reverie & Open License Legal Attribution ─────────────
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ElevatedSurface)
                    .border(0.5.dp, Graphite, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Column {
                    Text(
                        text = "O — REVERIE : AURAL DIMENSION",
                        color = PureWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Versi 2.1.0 Obsidian Silver · Liquid Glass Edition",
                        color = MediumGray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "Multi-Source Streaming: YouTube Music, Spotify, SoundCloud, JioSaavn, Audius.\nLirik Sinkronisasi: LRCLIB & YouTube Music.\nAudio Engine: AndroidX Media3 ExoPlayer & Hardware AudioFx.",
                        color = LightGray,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    // ── Quality Selection Dialog ──────────────────────────────────────────
    if (showQualityDialog) {
        Dialog(onDismissRequest = { showQualityDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(ElevatedSurface)
                    .border(1.dp, Graphite, RoundedCornerShape(20.dp))
                    .padding(22.dp)
            ) {
                Column {
                    Text(
                        text = "KUALITAS STREAMING AUDIO",
                        color = PureWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Pilih resolusi bitrate audio yang diinginkan.",
                        color = MediumGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                    )

                    StreamingQuality.entries.forEach { quality ->
                        val isSelected = preferences.streamingQuality == quality
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) SurfaceBlack else Color.Transparent)
                                .clickable {
                                    viewModel.setQuality(quality)
                                    showQualityDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = quality.label,
                                    color = if (isSelected) PureWhite else LightGray,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = quality.bitrateLabel,
                                    color = MediumGray,
                                    fontSize = 11.sp
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = "Terpilih",
                                    tint = PureWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .clickable { showQualityDialog = false }
                            .padding(8.dp)
                    ) {
                        Text(text = "TUTUP", color = MediumGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showMultiSourceDialog) {
            var spId by remember { mutableStateOf(preferences.spotifyClientId) }
            var spSecret by remember { mutableStateOf(preferences.spotifyClientSecret) }
            var scId by remember { mutableStateOf(preferences.soundCloudClientId) }

            Dialog(onDismissRequest = { showMultiSourceDialog = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(ElevatedSurface)
                        .border(1.dp, Graphite, RoundedCornerShape(20.dp))
                        .padding(22.dp)
                ) {
                    Column {
                        Text(
                            text = "MULTI-SOURCE API",
                            color = PureWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "YouTube Music & Spotify Embed berjalan otomatis. Isi kredensial Spotify / SoundCloud di bawah jika ingin pencarian penuh resmi.",
                            color = MediumGray,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                        )

                        OutlinedTextField(
                            value = spId,
                            onValueChange = { spId = it },
                            label = { Text("Spotify Client ID", color = MediumGray, fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite,
                                focusedBorderColor = PureWhite,
                                unfocusedBorderColor = Graphite,
                                focusedContainerColor = SurfaceBlack,
                                unfocusedContainerColor = SurfaceBlack
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = spSecret,
                            onValueChange = { spSecret = it },
                            label = { Text("Spotify Client Secret", color = MediumGray, fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite,
                                focusedBorderColor = PureWhite,
                                unfocusedBorderColor = Graphite,
                                focusedContainerColor = SurfaceBlack,
                                unfocusedContainerColor = SurfaceBlack
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = scId,
                            onValueChange = { scId = it },
                            label = { Text("SoundCloud Client ID (opsional)", color = MediumGray, fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite,
                                focusedBorderColor = PureWhite,
                                unfocusedBorderColor = Graphite,
                                focusedContainerColor = SurfaceBlack,
                                unfocusedContainerColor = SurfaceBlack
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "BATAL",
                                color = MediumGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { showMultiSourceDialog = false }
                                    .padding(8.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "SIMPAN",
                                color = PureWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        viewModel.setSpotifyCredentials(spId, spSecret)
                                        viewModel.setSoundCloudClientId(scId)
                                        showMultiSourceDialog = false
                                        Toast.makeText(context, "Konfigurasi Multi-Source disimpan", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showBackupRestoreDialog) {
            BackupAndRestoreDialog(onDismiss = { showBackupRestoreDialog = false })
        }

        if (showSupportCoffeeDialog) {
            SupportCoffeeDialog(onDismiss = { showSupportCoffeeDialog = false })
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ElevatedSurface)
            .border(0.5.dp, Graphite, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = title,
                color = MediumGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = value,
                color = PureWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun PreferenceRow(
    title: String,
    subtitle: String,
    actionText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ElevatedSurface)
            .border(0.5.dp, Graphite, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = PureWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = MediumGray, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(SurfaceBlack)
                .border(0.5.dp, Graphite, RoundedCornerShape(50.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(text = actionText, color = PureWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ElevatedSurface)
            .border(0.5.dp, Graphite, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = PureWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = MediumGray, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PureBlack,
                checkedTrackColor = PureWhite,
                uncheckedThumbColor = MediumGray,
                uncheckedTrackColor = SurfaceBlack
            )
        )
    }
}
