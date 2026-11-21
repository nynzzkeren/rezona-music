package com.nayama.music.ui.studio

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.data.preferences.RezonaPreferences
import com.nayama.music.playback.AmbientLayer
import com.nayama.music.playback.AudioEqualizerController
import com.nayama.music.playback.AudioOutputDetector
import com.nayama.music.playback.EchoProfile
import com.nayama.music.playback.RezonaPlayerController
import com.nayama.music.playback.rememberAudioOutputInfo
import com.nayama.music.ui.components.LocalRezonaHazeState
import com.nayama.music.ui.components.RezonaActionDisc
import com.nayama.music.ui.components.RezonaGlassCard
import com.nayama.music.ui.components.rezonaHazeSource
import com.nayama.music.ui.screens.rememberAutoFoldNestedScrollConnection
import com.nayama.music.ui.theme.DeepCharcoal
import com.nayama.music.ui.theme.Graphite
import com.nayama.music.ui.theme.ObsidianBlack
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureBlack
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.Typography
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rezona Studio Screen (Phase 7).
 * Acoustic & Spatial Lab featuring:
 * 1. Echo Mode (Spatial Reverb with 4 profiles & intensity)
 * 2. Sound Check & 5-Band Equalizer with live frequency visualizer & route inspector
 * 3. Rotary Mood Dial (interactive angle from -135° to +135°)
 * 4. Focus Rooms (Procedural real-time ambient mixer with 5 layers & sleep timer)
 * 5. Sessions (Algorithmic infinite radio & Group Resonance card)
 * 6. Smart Transitions (Crossfade & gapless audio settings)
 */
@Composable
fun RezonaStudioScreen(
    controller: RezonaPlayerController,
    onFoldChanged: (Boolean) -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferences = remember { RezonaPreferences(context) }
    val hazeState = LocalRezonaHazeState.current
    val listState = rememberLazyListState()
    val nestedScrollConnection = rememberAutoFoldNestedScrollConnection(onFoldChanged)

    val audioOutputInfo = rememberAudioOutputInfo(context)
    val eqController = remember { AudioEqualizerController.getInstance(context) }
    val eqState by eqController.equalizerState.collectAsState()
    val echoState by controller.spatialReverb.state.collectAsState()

    var showEqDialog by remember { mutableStateOf(false) }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .rezonaHazeSource(hazeState)
            .nestedScroll(nestedScrollConnection),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── 1. HEADER ────────────────────────────────────────────────────────
        item(key = "studio_header") {
            StudioHeader(
                onSettingsClick = onOpenSettings,
                onSoundCheckClick = { AudioOutputDetector.openSystemAudioOutputPanel(context) }
            )
        }

        // ── 2. ECHO MODE (SPATIAL REVERB) ────────────────────────────────────
        item(key = "studio_echo_mode") {
            EchoModeSection(
                echoState = echoState,
                onProfileSelect = { profile ->
                    controller.spatialReverb.selectProfile(profile)
                    coroutineScope.launch { preferences.setEchoProfile(profile.title) }
                },
                onIntensityChange = { intensity ->
                    controller.spatialReverb.setIntensity(intensity)
                    coroutineScope.launch { preferences.setEchoIntensity(intensity) }
                },
                onToggleEnabled = { enabled ->
                    controller.spatialReverb.setEnabled(enabled)
                }
            )
        }

        // ── 3. SOUND CHECK & EQUALIZER ───────────────────────────────────────
        item(key = "studio_sound_check") {
            SoundCheckCard(
                outputInfo = audioOutputInfo,
                eqState = eqState,
                isPlaying = controller.isPlaying.value,
                onOpenEq = { showEqDialog = !showEqDialog },
                onRouteClick = { AudioOutputDetector.openSystemAudioOutputPanel(context) }
            )
        }

        // Live Equalizer expand block
        if (showEqDialog) {
            item(key = "studio_eq_expanded") {
                EqualizerSection(
                    eqState = eqState,
                    onToggleEnabled = { eqController.setEnabled(it) },
                    onPresetSelected = { eqController.applyPreset(it) },
                    onBandLevelChange = { index, level -> eqController.setBandLevel(index, level) }
                )
            }
        }

        // ── 4. MOOD DIAL ─────────────────────────────────────────────────────
        item(key = "studio_mood_dial") {
            MoodDialSection(
                currentAngle = controller.moodDialAngle.floatValue,
                onAngleChange = { angle ->
                    controller.setMoodDialAngle(angle)
                    coroutineScope.launch { preferences.setMoodDialAngle(angle) }
                },
                onRecalibrateQueue = {
                    controller.skipNext()
                }
            )
        }

        // ── 5. FOCUS ROOMS (PROCEDURAL AMBIENT SOUNDS) ───────────────────────
        item(key = "studio_focus_rooms") {
            FocusRoomsSection(
                ambientEngine = controller.ambientEngine,
                timerMinutesRemaining = controller.focusTimerMinutesRemaining.value,
                onSetTimer = { controller.startFocusTimer(it) }
            )
        }

        // ── 6. SESSIONS & GROUP RESONANCE ────────────────────────────────────
        item(key = "studio_sessions") {
            SessionsSection(
                currentTrackTitle = controller.currentTrack.value?.title ?: "No track playing",
                onLaunchRadio = {
                    controller.currentTrack.value?.let { controller.launchRadioFromTrack(it) }
                }
            )
        }

        // ── 7. SMART TRANSITIONS & CACHE ─────────────────────────────────────
        item(key = "studio_smart_transitions") {
            SmartTransitionsSection(
                crossfadeSeconds = controller.crossfadeSeconds.intValue,
                gaplessEnabled = controller.gaplessEnabled.value,
                onCrossfadeChange = {
                    controller.setCrossfadeSeconds(it)
                    coroutineScope.launch { preferences.setCrossfadeSeconds(it) }
                },
                onGaplessToggle = {
                    controller.setGaplessEnabled(it)
                    coroutineScope.launch { preferences.setGaplessEnabled(it) }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. HEADER COMPONENT
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StudioHeader(
    onSettingsClick: () -> Unit,
    onSoundCheckClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Acoustic & Spatial Lab",
                    style = Typography.bodySmall,
                    color = SmokySilver
                )
                Text(
                    text = "Studio",
                    style = Typography.displayLarge.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = PearlWhite
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RezonaActionDisc(size = 40.dp, onClick = onSoundCheckClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_equalizer),
                        contentDescription = "Sound Check",
                        tint = SoftSilver,
                        modifier = Modifier.size(19.dp)
                    )
                }
                RezonaActionDisc(size = 40.dp, onClick = onSettingsClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings),
                        contentDescription = "Settings",
                        tint = SoftSilver,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. ECHO MODE (SPATIAL REVERB)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EchoModeSection(
    echoState: com.nayama.music.playback.EchoModeState,
    onProfileSelect: (EchoProfile) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    RezonaGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Echo Mode",
                            style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PearlWhite
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (echoState.isEnabled) Color(0x30FFFFFF) else Color(0x15FFFFFF))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (echoState.isEnabled) "ACTIVE" else "STANDBY",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (echoState.isEnabled) PureWhite else SmokySilver
                            )
                        }
                    }
                    Text(
                        text = "Real-time acoustic spatial reverb",
                        style = Typography.bodySmall,
                        color = SmokySilver
                    )
                }

                com.nayama.music.ui.components.RezonaGlassToggle(
                    checked = echoState.isEnabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4 Spatial Profiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EchoProfile.values().forEach { profile ->
                    val isSelected = echoState.activeProfile == profile
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0x28FFFFFF) else Color(0x0CFFFFFF))
                            .border(
                                width = if (isSelected) 1.25.dp else 0.5.dp,
                                brush = Brush.verticalGradient(
                                    if (isSelected) listOf(Color(0x80FFFFFF), Color(0x20FFFFFF))
                                    else listOf(Color(0x20FFFFFF), Color(0x05FFFFFF))
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onProfileSelect(profile) }
                            .padding(vertical = 12.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = profile.title.split(" ").firstOrNull() ?: profile.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) PureWhite else SmokySilver
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${profile.decayTimeMs}ms",
                                fontSize = 10.sp,
                                color = if (isSelected) SoftSilver else Color(0x60FFFFFF)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Active Profile Subtitle
            Text(
                text = echoState.activeProfile.subtitle,
                style = Typography.bodySmall,
                color = SoftSilver,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Intensity Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Intensity", style = Typography.bodySmall, color = SmokySilver)
                Spacer(modifier = Modifier.width(12.dp))
                com.nayama.music.ui.components.RezonaGlassSlider(
                    value = echoState.intensity,
                    onValueChange = onIntensityChange,
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${(echoState.intensity * 100).toInt()}%",
                    style = Typography.bodySmall,
                    color = PearlWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. SOUND CHECK & EQUALIZER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SoundCheckCard(
    outputInfo: com.nayama.music.playback.AudioOutputInfo,
    eqState: com.nayama.music.playback.EqualizerState,
    isPlaying: Boolean,
    onOpenEq: () -> Unit,
    onRouteClick: () -> Unit
) {
    RezonaGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Sound Check",
                        style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PearlWhite
                    )
                    Text(
                        text = "Hardware route & acoustic spectrum",
                        style = Typography.bodySmall,
                        color = SmokySilver
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x18FFFFFF))
                        .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(20.dp))
                        .clickable { onOpenEq() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (eqState.isEnabled) "EQ: ${eqState.currentPreset}" else "EQ: Flat",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Output Device Route Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x10FFFFFF))
                    .border(0.5.dp, Color(0x20FFFFFF), RoundedCornerShape(12.dp))
                    .clickable { onRouteClick() }
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = outputInfo.iconResId),
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = outputInfo.label,
                                style = Typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = PearlWhite
                            )
                            Text(
                                text = if (outputInfo.isExternalDevice) "48.0 kHz · 24-bit PCM" else "Phone Speaker · 44.1 kHz",
                                style = Typography.bodySmall,
                                color = SmokySilver,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x20FFFFFF))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "ROUTE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = SoftSilver
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live 32-Bar Audio Visualizer
            FrequencyVisualizer(isPlaying = isPlaying)
        }
    }
}

@Composable
private fun FrequencyVisualizer(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "freq_viz")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x08FFFFFF))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        val barCount = 28
        val spacing = 4.dp.toPx()
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = (size.width - totalSpacing) / barCount

        for (i in 0 until barCount) {
            val normI = i.toFloat() / barCount
            val wave = if (isPlaying) {
                val s1 = sin(phase + normI * 4f) * 0.5f + 0.5f
                val s2 = cos(phase * 1.5f + normI * 8f) * 0.3f
                ((s1 + s2) * 0.85f).coerceIn(0.12f, 1.0f)
            } else {
                0.08f
            }
            val barHeight = size.height * wave
            val x = i * (barWidth + spacing)
            val y = size.height - barHeight

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(PureWhite, SoftSilver.copy(alpha = 0.4f)),
                    startY = y,
                    endY = size.height
                ),
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}

@Composable
private fun EqualizerSection(
    eqState: com.nayama.music.playback.EqualizerState,
    onToggleEnabled: (Boolean) -> Unit,
    onPresetSelected: (String) -> Unit,
    onBandLevelChange: (Short, Short) -> Unit
) {
    RezonaGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "5-Band Equalizer",
                    style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PearlWhite
                )

                com.nayama.music.ui.components.RezonaGlassToggle(
                    checked = eqState.isEnabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Preset Pills
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(eqState.presets) { preset ->
                    val isSelected = eqState.currentPreset == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) PureWhite else Color(0x15FFFFFF))
                            .clickable { onPresetSelected(preset) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = preset,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) PureBlack else SoftSilver
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Equalizer Sliders
            val bands = if (eqState.bands.isNotEmpty()) eqState.bands else listOf(
                com.nayama.music.playback.EqualizerBand(0, 60, -1000, 1000, 200),
                com.nayama.music.playback.EqualizerBand(1, 230, -1000, 1000, 150),
                com.nayama.music.playback.EqualizerBand(2, 910, -1000, 1000, 0),
                com.nayama.music.playback.EqualizerBand(3, 3600, -1000, 1000, 300),
                com.nayama.music.playback.EqualizerBand(4, 14000, -1000, 1000, 400)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                bands.forEach { band ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "${band.currentLevel / 100}dB",
                            fontSize = 10.sp,
                            color = PearlWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = band.currentLevel.toFloat(),
                            onValueChange = { onBandLevelChange(band.index, it.toInt().toShort()) },
                            valueRange = band.minLevel.toFloat()..band.maxLevel.toFloat(),
                            modifier = Modifier.height(100.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = PureWhite,
                                activeTrackColor = PearlWhite,
                                inactiveTrackColor = Graphite
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (band.centerFreqHz >= 1000) "${band.centerFreqHz / 1000}k" else "${band.centerFreqHz}",
                            fontSize = 10.sp,
                            color = SmokySilver
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. MOOD DIAL SECTION
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MoodDialSection(
    currentAngle: Float,
    onAngleChange: (Float) -> Unit,
    onRecalibrateQueue: () -> Unit
) {
    RezonaGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Mood Dial",
                        style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PearlWhite
                    )
                    Text(
                        text = "Rotary acoustic energy steering",
                        style = Typography.bodySmall,
                        color = SmokySilver
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x15FFFFFF))
                        .clickable { onRecalibrateQueue() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Seed Queue",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Rotary Knob Canvas
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val touch = change.position
                            val angleRad = atan2(touch.y - center.y, touch.x - center.x)
                            var angleDeg = (angleRad * (180f / PI.toFloat())) + 90f
                            if (angleDeg > 180f) angleDeg -= 360f
                            val clamped = angleDeg.coerceIn(-135f, 135f)
                            onAngleChange(clamped)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f - 14.dp.toPx()
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Outer Track Arc (-135° to +135° -> start at 135° with sweep 270°)
                    drawArc(
                        color = Color(0x20FFFFFF),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Active Arc
                    val activeSweep = ((currentAngle + 135f) / 270f) * 270f
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color(0x60FFFFFF), PureWhite, Color(0x60FFFFFF)),
                            center = center
                        ),
                        startAngle = 135f,
                        sweepAngle = activeSweep.coerceAtLeast(1f),
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Center Glass Disc
                    val discRadius = radius * 0.72f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0x28FFFFFF), Color(0x0CFFFFFF)),
                            center = center,
                            radius = discRadius
                        ),
                        radius = discRadius,
                        center = center
                    )
                    drawCircle(
                        color = Color(0x40FFFFFF),
                        radius = discRadius,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // Indicator Knob Dot
                    val knobRad = (currentAngle - 90f) * (PI.toFloat() / 180f)
                    val knobDist = discRadius * 0.70f
                    val knobPos = Offset(
                        x = center.x + cos(knobRad) * knobDist,
                        y = center.y + sin(knobRad) * knobDist
                    )
                    drawCircle(
                        color = PureWhite,
                        radius = 6.dp.toPx(),
                        center = knobPos
                    )
                }

                // Mood Label in Center
                val moodLabel = when {
                    currentAngle < -60f -> "Calm Obsidian"
                    currentAngle in -60f..60f -> "Balanced Flow"
                    else -> "High Resonance"
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = moodLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                    Text(
                        text = "${((currentAngle + 135f) / 2.7f).toInt()}%",
                        fontSize = 11.sp,
                        color = SmokySilver
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Deep Calm", style = Typography.bodySmall, color = SmokySilver)
                Text(text = "Radiant Energy", style = Typography.bodySmall, color = SmokySilver)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. FOCUS ROOMS (PROCEDURAL AMBIENT MIXER)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FocusRoomsSection(
    ambientEngine: com.nayama.music.playback.ProceduralAmbientEngine,
    timerMinutesRemaining: Int?,
    onSetTimer: (Int?) -> Unit
) {
    RezonaGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Focus Rooms",
                        style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PearlWhite
                    )
                    Text(
                        text = "Procedural soundscape generator",
                        style = Typography.bodySmall,
                        color = SmokySilver
                    )
                }

                // Timer Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (timerMinutesRemaining != null) PureWhite else Color(0x15FFFFFF))
                        .clickable {
                            val next = when (timerMinutesRemaining) {
                                null -> 15
                                15 -> 30
                                30 -> 60
                                else -> null
                            }
                            onSetTimer(next)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (timerMinutesRemaining != null) "${timerMinutesRemaining}m remaining" else "Timer: Off",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (timerMinutesRemaining != null) PureBlack else SoftSilver
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5 Ambient Layer Sliders
            AmbientSliderRow(
                title = "Rain on Glass",
                volume = ambientEngine.rainVolume,
                onVolumeChange = { ambientEngine.setLayerVolume(AmbientLayer.RAIN, it) }
            )
            AmbientSliderRow(
                title = "Acoustic Cafe",
                volume = ambientEngine.cafeVolume,
                onVolumeChange = { ambientEngine.setLayerVolume(AmbientLayer.CAFE, it) }
            )
            AmbientSliderRow(
                title = "Pure Air (Pink)",
                volume = ambientEngine.staticVolume,
                onVolumeChange = { ambientEngine.setLayerVolume(AmbientLayer.STATIC, it) }
            )
            AmbientSliderRow(
                title = "Midnight Ocean",
                volume = ambientEngine.oceanVolume,
                onVolumeChange = { ambientEngine.setLayerVolume(AmbientLayer.OCEAN, it) }
            )
            AmbientSliderRow(
                title = "Vinyl Crackle",
                volume = ambientEngine.vinylVolume,
                onVolumeChange = { ambientEngine.setLayerVolume(AmbientLayer.VINYL, it) }
            )
        }
    }
}

@Composable
private fun AmbientSliderRow(
    title: String,
    volume: Float,
    onVolumeChange: (Float) -> Unit
) {
    var localVol by remember(volume) { mutableFloatStateOf(volume) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = Typography.bodySmall,
            color = if (localVol > 0.01f) PureWhite else SmokySilver,
            modifier = Modifier.width(100.dp)
        )
        com.nayama.music.ui.components.RezonaGlassSlider(
            value = localVol,
            onValueChange = {
                localVol = it
                onVolumeChange(it)
            },
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${(localVol * 100).toInt()}%",
            fontSize = 10.sp,
            color = if (localVol > 0.01f) PureWhite else Color(0x60FFFFFF),
            modifier = Modifier.width(32.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. SESSIONS & GROUP RESONANCE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SessionsSection(
    currentTrackTitle: String,
    onLaunchRadio: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Algorithmic Radio Launcher
        RezonaGlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLaunchRadio() }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Infinite Acoustic Session",
                        style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PearlWhite
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Seed algorithmic radio from \"$currentTrackTitle\"",
                        style = Typography.bodySmall,
                        color = SmokySilver
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(PureWhite)
                        .padding(10.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_play_arrow),
                        contentDescription = "Launch Session",
                        tint = PureBlack,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Group Resonance (Listen Together) Coming Soon Card
        RezonaGlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x15FFFFFF))
                        .border(1.dp, Color(0x25FFFFFF), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_community),
                        contentDescription = null,
                        tint = SoftSilver,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Group Resonance",
                            style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PearlWhite
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x25FFFFFF))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "COMING SOON",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = SoftSilver
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Synchronized low-latency peer-to-peer listening sessions",
                        style = Typography.bodySmall,
                        color = SmokySilver,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. SMART TRANSITIONS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SmartTransitionsSection(
    crossfadeSeconds: Int,
    gaplessEnabled: Boolean,
    onCrossfadeChange: (Int) -> Unit,
    onGaplessToggle: (Boolean) -> Unit
) {
    RezonaGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Smart Transitions",
                style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PearlWhite
            )
            Text(
                text = "Media3 crossfade and seamless audio continuity",
                style = Typography.bodySmall,
                color = SmokySilver
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Crossfade
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Crossfade",
                    style = Typography.bodyMedium,
                    color = PearlWhite,
                    modifier = Modifier.width(80.dp)
                )
                com.nayama.music.ui.components.RezonaGlassSlider(
                    value = crossfadeSeconds.toFloat(),
                    onValueChange = { onCrossfadeChange(it.toInt()) },
                    valueRange = 0f..12f,
                    formatBubble = { "${it.toInt()}s" },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${crossfadeSeconds}s",
                    style = Typography.bodySmall,
                    color = PearlWhite,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Gapless Playback Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Gapless Playback",
                        style = Typography.bodyMedium,
                        color = PearlWhite
                    )
                    Text(
                        text = "Eliminate silence between studio album tracks",
                        style = Typography.bodySmall,
                        color = SmokySilver,
                        fontSize = 11.sp
                    )
                }

                com.nayama.music.ui.components.RezonaGlassToggle(
                    checked = gaplessEnabled,
                    onCheckedChange = onGaplessToggle
                )
            }
        }
    }
}
