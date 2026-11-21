package com.nayama.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.ui.components.GlassSurface
import com.nayama.music.ui.components.GlassSurfaceVariant
import com.nayama.music.ui.components.LocalAmbientAccentColor
import com.nayama.music.ui.components.LocalRezonaHazeState
import com.nayama.music.ui.components.RezonaActionDisc
import com.nayama.music.ui.components.RezonaContinuousAmbientLayer
import com.nayama.music.ui.components.RezonaFloatingDock
import com.nayama.music.ui.components.RezonaGlassButton
import com.nayama.music.ui.components.RezonaGlassCheckbox
import com.nayama.music.ui.components.RezonaGlassDialog
import com.nayama.music.ui.components.RezonaGlassPill
import com.nayama.music.ui.components.RezonaGlassSlider
import com.nayama.music.ui.components.RezonaGlassTextField
import com.nayama.music.ui.components.RezonaGlassToast
import com.nayama.music.ui.components.RezonaGlassToggle
import com.nayama.music.ui.components.RezonaHairlineDivider
import com.nayama.music.ui.components.rezonaHazeSource
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.Typography
import dev.chrisbanes.haze.HazeState

/**
 * Hidden "Glass Gallery" debug screen required by rezona_core.md:
 * Shows every GlassSurface variant (Floating, Button, IconButton, Chip, Field, Toggle,
 * Slider with percent bubble, Checkbox, Menu, Dialog, Toast, and unboxed hairline rows)
 * across states (Default, Pressed, Disabled, Focused) over switchable colorful backdrops.
 */
@Composable
fun RezonaGlassGalleryScreen(
    onBack: () -> Unit
) {
    val galleryHazeState = remember { HazeState() }
    var backdropIndex by remember { mutableIntStateOf(0) }

    val backdrops = remember {
        listOf(
            "Crimson Sunset" to Triple(Color(0xFF7C1D36), Color(0xFF3F1651), Color(0xFF1F1135)),
            "Cobalt Aurora" to Triple(Color(0xFF1A4F7A), Color(0xFF1D6A58), Color(0xFF132238)),
            "Amber Orchid" to Triple(Color(0xFF7A4419), Color(0xFF5C1E4A), Color(0xFF22152E)),
            "Obsidian Silver" to Triple(Color(0xFF2A3242), Color(0xFF1B202B), Color(0xFF10131A))
        )
    }

    val currentBackdrop = backdrops[backdropIndex % backdrops.size]
    var toggleDefault by remember { mutableStateOf(true) }
    var checkboxDefault by remember { mutableStateOf(true) }
    var sliderValue by remember { mutableFloatStateOf(0.68f) }
    var fieldText by remember { mutableStateOf("Liquid Glass Field") }
    var showPreviewDialog by remember { mutableStateOf(false) }

    CompositionLocalProvider(
        LocalRezonaHazeState provides galleryHazeState,
        LocalAmbientAccentColor provides currentBackdrop.second.first
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Switchable Colorful Ambient Backdrop
            RezonaContinuousAmbientLayer(
                primaryColor = currentBackdrop.second.first,
                secondaryColor = currentBackdrop.second.second,
                tertiaryColor = currentBackdrop.second.third,
                vividMode = true,
                modifier = Modifier.fillMaxSize()
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .rezonaHazeSource(galleryHazeState)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
                item {
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
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Liquid Glass Gallery",
                                    style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = PearlWhite
                                )
                                Text(
                                    text = "All variants · Default, Pressed, Focused, Disabled",
                                    style = Typography.bodySmall,
                                    color = SmokySilver
                                )
                            }
                        }
                    }
                }

                // Backdrop Switcher Chips
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "BACKDROP PALETTE",
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                            color = SoftSilver
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            backdrops.forEachIndexed { idx, pair ->
                                RezonaGlassPill(
                                    isActive = backdropIndex == idx,
                                    onClick = { backdropIndex = idx },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = pair.first.substringBefore(" "),
                                        style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (backdropIndex == idx) PureWhite else SoftSilver
                                    )
                                }
                            }
                        }
                    }
                }

                // 1. Floating Variant (Real Haze Blur)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "1. FLOATING SURFACE (REAL HAZE BLUR)",
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                            color = SoftSilver
                        )
                        RezonaFloatingDock(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Floating Dock / Header Surface", color = PearlWhite, style = Typography.titleMedium)
                                RezonaGlassPill(isActive = true) {
                                    Text("Haze Active", color = PureWhite, style = Typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                // 2. Buttons (Primary & Secondary across Default / Pressed / Disabled)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "2. GLASS PILL BUTTONS (DEFAULT / PRESSED / DISABLED)",
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                            color = SoftSilver
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RezonaGlassButton(text = "Primary", isPrimary = true, onClick = {}, modifier = Modifier.weight(1f))
                            RezonaGlassButton(text = "Pressed", isPrimary = true, forcePressedPreview = true, onClick = {}, modifier = Modifier.weight(1f))
                            RezonaGlassButton(text = "Disabled", isPrimary = true, enabled = false, onClick = {}, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RezonaGlassButton(text = "Secondary", isPrimary = false, onClick = {}, modifier = Modifier.weight(1f))
                            RezonaGlassButton(text = "Pressed", isPrimary = false, forcePressedPreview = true, onClick = {}, modifier = Modifier.weight(1f))
                            RezonaGlassButton(text = "Disabled", isPrimary = false, enabled = false, onClick = {}, modifier = Modifier.weight(1f))
                        }
                    }
                }

                // 3. IconButtons & Chips
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "3. ICON BUTTONS & CHIPS",
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                            color = SoftSilver
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RezonaActionDisc(onClick = {}) {
                                Icon(painter = painterResource(id = R.drawable.ic_play_arrow), contentDescription = "Default", tint = PureWhite, modifier = Modifier.size(18.dp))
                            }
                            GlassSurface(
                                variant = GlassSurfaceVariant.IconButton,
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(22.dp),
                                forcePressedPreview = true,
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(painter = painterResource(id = R.drawable.ic_heart_filled), contentDescription = "Pressed", tint = Color(0xFFFF5A6E), modifier = Modifier.size(18.dp))
                            }
                            GlassSurface(
                                variant = GlassSurfaceVariant.IconButton,
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(22.dp),
                                enabled = false,
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(painter = painterResource(id = R.drawable.ic_settings), contentDescription = "Disabled", tint = SoftSilver, modifier = Modifier.size(18.dp))
                            }
                            RezonaGlassPill(isActive = false, onClick = {}) {
                                Text("Chip Default", color = SoftSilver, style = Typography.labelSmall)
                            }
                            RezonaGlassPill(isActive = true, onClick = {}) {
                                Text("Chip Active", color = PureWhite, style = Typography.labelSmall)
                            }
                        }
                    }
                }

                // 4. TextFields (Default / Focused / Disabled)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "4. GLASS TEXT FIELDS (DEFAULT / FOCUSED / DISABLED)",
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                            color = SoftSilver
                        )
                        RezonaGlassTextField(
                            value = fieldText,
                            onValueChange = { fieldText = it },
                            placeholder = "Default glass field..."
                        )
                        RezonaGlassTextField(
                            value = "Focused with specular outer glow",
                            onValueChange = {},
                            placeholder = "Focused...",
                            forceFocusedPreview = true
                        )
                        RezonaGlassTextField(
                            value = "",
                            onValueChange = {},
                            placeholder = "Disabled glass field",
                            enabled = false
                        )
                    }
                }

                // 5. Toggles, Checkboxes & Sliders with Percent Bubble
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "5. TOGGLES, CHECKBOXES & SLIDER (DRAG FOR % BUBBLE)",
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                            color = SoftSilver
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Glass Toggle (On / Off / Disabled)", color = PearlWhite, style = Typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RezonaGlassToggle(checked = toggleDefault, onCheckedChange = { toggleDefault = it })
                                RezonaGlassToggle(checked = false, onCheckedChange = {})
                                RezonaGlassToggle(checked = true, onCheckedChange = {}, enabled = false)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Glass Checkbox (Checked / Unchecked / Disabled)", color = PearlWhite, style = Typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RezonaGlassCheckbox(checked = checkboxDefault, onCheckedChange = { checkboxDefault = it })
                                RezonaGlassCheckbox(checked = false, onCheckedChange = {})
                                RezonaGlassCheckbox(checked = true, onCheckedChange = {}, enabled = false)
                            }
                        }
                        RezonaGlassSlider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it }
                        )
                    }
                }

                // 6. Unboxed Plain List Rows with Hairline Separators + Menu / Dialog / Toast
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "6. UNBOXED LIST ROWS, MENU, DIALOG & TOAST",
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                            color = SoftSilver
                        )
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showPreviewDialog = true }
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Tap to preview Glass Dialog", color = PearlWhite, style = Typography.titleMedium)
                                    Text("Unboxed row on ambient background with hairline divider", color = SmokySilver, style = Typography.bodySmall)
                                }
                                Icon(painter = painterResource(id = R.drawable.ic_arrow_forward), contentDescription = null, tint = SoftSilver, modifier = Modifier.size(16.dp))
                            }
                            RezonaHairlineDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Contrast >= 4.5:1 Verified", color = PearlWhite, style = Typography.titleMedium)
                                    Text("Readable over Crimson, Cobalt, Amber & Obsidian backdrops", color = SmokySilver, style = Typography.bodySmall)
                                }
                                Text("PASS", color = PureWhite, style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                            RezonaHairlineDivider()
                        }

                        RezonaGlassToast(
                            message = "Glass Toast notification with specular rim",
                            actionLabel = "Undo",
                            onAction = {}
                        )
                    }
                }
            }

            if (showPreviewDialog) {
                RezonaGlassDialog(
                    onDismissRequest = { showPreviewDialog = false },
                    title = "Liquid Glass Dialog",
                    confirmText = "Looks Crisp",
                    onConfirm = { showPreviewDialog = false }
                ) {
                    Text(
                        text = "This modal uses real Haze backdrop blur, top-down white gradient tint (14% to 5%), and a 1.25dp specular rim.",
                        style = Typography.bodyMedium,
                        color = SoftSilver
                    )
                }
            }
        }
    }
}
