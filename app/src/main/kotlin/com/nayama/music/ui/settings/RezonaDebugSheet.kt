package com.nayama.music.ui.settings

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.backend.BackendDebugTracker
import com.nayama.music.ui.components.RezonaActionDisc
import com.nayama.music.ui.components.RezonaGlassButton
import com.nayama.music.ui.components.RezonaGlassCard
import com.nayama.music.ui.components.RezonaHairlineDivider
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.Typography

/**
 * 0.6 DEBUG SCREEN:
 * Shows REAL/MOCK badges per provider, live per-endpoint status
 * (InnerTube Search, Suggestions, Home Browse, Radio Queue, NewPipe Stream, LRCLIB Lyrics),
 * stream resolution logs, and a shortcut to the hidden Liquid Glass Gallery.
 */
@Composable
fun RezonaDebugScreen(
    onBack: () -> Unit,
    onOpenGlassGallery: () -> Unit = {},
    onOpenSyncStatus: () -> Unit = {}
) {
    val logs by BackendDebugTracker.logs.collectAsState()
    val endpointsMap by BackendDebugTracker.endpoints.collectAsState()
    val searchTelemetry by BackendDebugTracker.searchTelemetry.collectAsState()
    val endpoints = endpointsMap.values.toList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD907090D)) // Translucent glass scrim over shared continuous ambient layer
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RezonaActionDisc(size = 42.dp, onClick = onBack) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        tint = PureWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Acoustic Stream Inspector",
                        style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = PearlWhite
                    )
                    Text(
                        text = "REAL/MOCK badges & per-endpoint diagnostics",
                        style = Typography.bodySmall,
                        color = SmokySilver
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            contentPadding = PaddingValues(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. PROVIDER REALITY STATUS (REAL vs MOCK BADGES) ─────────────
            item {
                Column {
                    Text(
                        text = "PROVIDER REALITY STATUS",
                        style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                        color = SoftSilver,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProviderStatusBadgeCard(
                            name = "YouTube Music",
                            isReal = true,
                            badgeText = "REAL",
                            detail = "InnerTube + NewPipe Opus",
                            modifier = Modifier.weight(1f)
                        )
                        ProviderStatusBadgeCard(
                            name = "LRCLIB Lyrics",
                            isReal = true,
                            badgeText = "REAL",
                            detail = "Synced LRC · Active",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── 2. PER-ENDPOINT STATUS TABLE (UNBOXED ROWS WITH HAIRLINES) ──
            item {
                Column {
                    Text(
                        text = "PER-ENDPOINT STATUS (${endpoints.size})",
                        style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                        color = SoftSilver,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    endpoints.forEach { ep ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ep.endpointName,
                                    style = Typography.titleMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                                    color = PearlWhite
                                )
                                Text(
                                    text = ep.lastDetail,
                                    style = Typography.bodySmall.copy(fontSize = 11.sp),
                                    color = SmokySilver
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (ep.isHealthy) Color(0x284CAF50) else Color(0x28FF5252))
                                    .border(
                                        width = 1.dp,
                                        color = if (ep.isHealthy) Color(0x664CAF50) else Color(0x66FF5252),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = ep.statusBadge,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (ep.isHealthy) Color(0xFF81C784) else Color(0xFFFF8A80)
                                )
                            }
                        }
                        RezonaHairlineDivider()
                    }
                }
            }

            // ── 2B. SEARCH RESULT COUNTS PER SECTION & RAW REQUEST PARAMS ────
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "LAST SEARCH TELEMETRY",
                        style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                        color = SoftSilver,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        text = "Raw params: ${searchTelemetry.rawRequestParams}",
                        style = Typography.bodySmall,
                        color = PearlWhite
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (searchTelemetry.sectionCounts.isEmpty()) {
                        Text("Run any search to populate section counts.", style = Typography.bodySmall, color = SmokySilver)
                    } else {
                        searchTelemetry.sectionCounts.forEach { (section, count) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(section, style = Typography.bodySmall, color = SoftSilver)
                                Text("$count items", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    RezonaHairlineDivider()
                }
            }

            // ── 3. CLOUD SYNC STATUS & LIQUID GLASS GALLERY SHORTCUT ─────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RezonaGlassButton(
                        text = "Cloud Sync Status",
                        isPrimary = true,
                        onClick = onOpenSyncStatus,
                        modifier = Modifier.fillMaxWidth()
                    )
                    RezonaGlassButton(
                        text = "Open Liquid Glass Gallery",
                        isPrimary = false,
                        onClick = onOpenGlassGallery,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── 4. STREAM RESOLUTION LOGS ────────────────────────────────────
            item {
                Text(
                    text = "STREAM RESOLUTION LOGS (${logs.size})",
                    style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                    color = SoftSilver
                )
            }

            if (logs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No stream resolutions logged yet",
                                style = Typography.bodyMedium,
                                color = SmokySilver
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Play a track from Search or Home to record resolution metrics",
                                style = Typography.bodySmall,
                                color = SoftSilver,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            } else {
                items(logs) { entry ->
                    RezonaGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (entry.isSuccess) Color(0x30FFFFFF) else Color(0x40FF4444))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (entry.isSuccess) "OK" else "FAILED",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PureWhite
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = entry.selectedProvider,
                                        style = Typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = PearlWhite
                                    )
                                }
                                Text(
                                    text = entry.timestamp,
                                    fontSize = 10.sp,
                                    color = SmokySilver
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "${entry.trackTitle} · ${entry.artist}",
                                style = Typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = PearlWhite
                            )

                            if (entry.bitrateKbps != null || entry.streamFormat != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Format: ${entry.streamFormat ?: "Unknown"} · Bitrate: ${entry.bitrateKbps ?: 0} kbps · Latency: ${entry.resolutionDurationMs}ms",
                                    fontSize = 10.sp,
                                    color = SoftSilver
                                )
                            }

                            if (entry.fallbackReason != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0x10FFFFFF))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "Fallback: ${entry.fallbackReason}",
                                        fontSize = 10.sp,
                                        color = SoftSilver
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

@Composable
private fun ProviderStatusBadgeCard(
    name: String,
    isReal: Boolean,
    badgeText: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    RezonaGlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = PearlWhite,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isReal) Color(0x334CAF50) else Color(0x33FF5722)
                        )
                        .border(
                            1.dp,
                            if (isReal) Color(0x664CAF50) else Color(0x66FF5722),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isReal) Color(0xFF81C784) else Color(0xFFFF8A65)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = detail,
                fontSize = 9.5.sp,
                color = SmokySilver,
                maxLines = 1
            )
        }
    }
}
