package com.nayama.music

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.nayama.music.data.preferences.RezonaPreferences
import com.nayama.music.overlay.RezonaDynamicIslandService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.ui.RezonaApp
import com.nayama.music.ui.splash.OSplashScreen
import com.nayama.music.ui.theme.DarkCharcoal
import com.nayama.music.ui.theme.Graphite
import com.nayama.music.ui.theme.LightGray
import com.nayama.music.ui.theme.MediumGray
import com.nayama.music.ui.theme.NayamaBackground
import com.nayama.music.ui.theme.RezonaTheme
import com.nayama.music.ui.theme.PureBlack
import com.nayama.music.ui.theme.PureWhite

class MainActivity : ComponentActivity() {

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        try {
            enableEdgeToEdge()
        } catch (e: Throwable) {
            Log.e("MainActivity", "Edge to edge styling fallback", e)
        }

        val prefs = getSharedPreferences("nayama_debug_prefs", Context.MODE_PRIVATE)
        val lastCrash = prefs.getString("last_crash_stacktrace", null)

        if (intent?.getBooleanExtra(com.nayama.music.playback.RezonaMusicAlarmManager.ACTION_TRIGGER_MUSIC_ALARM, false) == true) {
            com.nayama.music.playback.RezonaPlayerController.getInstance(applicationContext)
                .playSmartPlaylist(customToast = "Rezona Music Alarm · Playing Smart Mix")
        }

        setContent {
            RezonaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NayamaBackground
                ) {
                    var crashReportToDisplay by remember { mutableStateOf(lastCrash) }
                    var showSplash by remember { mutableStateOf(true) }

                    if (crashReportToDisplay != null) {
                        CrashReportScreen(
                            stackTrace = crashReportToDisplay!!,
                            onDismiss = {
                                prefs.edit().remove("last_crash_stacktrace").apply()
                                crashReportToDisplay = null
                            }
                        )
                    } else if (showSplash) {
                        OSplashScreen(
                            onSplashFinished = {
                                showSplash = false
                            }
                        )
                    } else {
                        // Rezona Phase 2 Navigation Shell & Liquid Glass Dock
                        RezonaApp()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        RezonaDynamicIslandService.setAppInForeground(true)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val prefs = RezonaPreferences(applicationContext)
            val islandEnabled = prefs.dynamicIslandEnabledFlow.first()
            val iosSideEnabled = prefs.iosSidePlayerEnabledFlow.first()
            if ((islandEnabled || iosSideEnabled) && Settings.canDrawOverlays(this@MainActivity)) {
                RezonaDynamicIslandService.start(applicationContext)
            } else if (!islandEnabled && !iosSideEnabled) {
                RezonaDynamicIslandService.stop(applicationContext)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        RezonaDynamicIslandService.setAppInForeground(false)
    }
}

@Composable
private fun CrashReportScreen(
    stackTrace: String,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "NAYAMA DIAGNOSTIC LOG",
                color = MediumGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "Laporan Error Sesi Sebelumnya",
                color = PureWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkCharcoal)
                    .border(1.dp, Graphite, RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stackTrace,
                    color = LightGray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(PureWhite)
                .clickable { onDismiss() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Lanjutkan ke O - Reverie",
                color = PureBlack,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
