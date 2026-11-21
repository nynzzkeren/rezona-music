package com.nayama.music.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.sync.RezonaCloudSyncManager
import com.nayama.music.ui.components.RezonaActionDisc
import com.nayama.music.ui.components.RezonaGlassButton
import com.nayama.music.ui.components.RezonaGlassCard
import com.nayama.music.ui.components.RezonaHairlineDivider
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.Typography
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RezonaSyncStatusScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncManager = remember { RezonaCloudSyncManager.getInstance(context) }
    val syncStatus by syncManager.status.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE007090D))
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RezonaActionDisc(size = 48.dp, onClick = onBack) {
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
                    text = "Cloud Sync Status",
                    style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = PearlWhite
                )
                Text(
                    text = "Offline-first Firestore sync telemetry & manual recovery",
                    style = Typography.bodySmall,
                    color = SmokySilver
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        LazyColumn(
            contentPadding = PaddingValues(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RezonaGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SyncFieldRow(
                            label = "Firebase SDK",
                            value = if (syncStatus.isFirebaseConfigured) "Configured" else "Not configured (missing google-services.json)"
                        )
                        RezonaHairlineDivider()
                        SyncFieldRow(
                            label = "Signed-in UID",
                            value = syncStatus.uid ?: "Guest (local only)"
                        )
                        RezonaHairlineDivider()
                        SyncFieldRow(
                            label = "Last Successful Push",
                            value = formatTimestamp(syncStatus.lastPushAtMs)
                        )
                        RezonaHairlineDivider()
                        SyncFieldRow(
                            label = "Last Successful Pull",
                            value = formatTimestamp(syncStatus.lastPullAtMs)
                        )
                        RezonaHairlineDivider()
                        SyncFieldRow(
                            label = "Pending Operations",
                            value = "${syncStatus.pendingOpsCount} queued"
                        )
                    }
                }
            }

            if (!syncStatus.lastError.isNullOrBlank()) {
                item {
                    RezonaGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "LAST SYNC ERROR",
                                style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = Color(0xFFFF8E96)
                            )
                            Text(
                                text = syncStatus.lastError!!,
                                style = Typography.bodySmall,
                                color = PearlWhite
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RezonaGlassButton(
                        text = if (syncStatus.isSyncing) "Syncing..." else "Sync now",
                        isPrimary = true,
                        onClick = {
                            if (syncStatus.isSyncing) return@RezonaGlassButton
                            scope.launch {
                                val res = syncManager.syncNow()
                                Toast.makeText(
                                    context,
                                    if (res.isSuccess) "Cloud sync completed" else (res.exceptionOrNull()?.message ?: "Sync failed"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    )

                    RezonaGlassButton(
                        text = "Force restore from cloud",
                        isPrimary = false,
                        onClick = {
                            if (syncStatus.isSyncing) return@RezonaGlassButton
                            scope.launch {
                                val res = syncManager.forceRestoreFromCloud()
                                Toast.makeText(
                                    context,
                                    if (res.isSuccess) "Restored library from cloud" else (res.exceptionOrNull()?.message ?: "Restore failed"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncFieldRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = Typography.labelSmall, color = SmokySilver)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = Typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = PearlWhite
        )
    }
}

private fun formatTimestamp(ms: Long): String {
    if (ms <= 0L) return "Never"
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    return fmt.format(Date(ms))
}
