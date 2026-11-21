package com.nayama.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.ui.components.RezonaActionDisc
import com.nayama.music.ui.components.RezonaGlassCard
import com.nayama.music.ui.theme.DeepCharcoal
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureBlack
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.Typography

data class OpenSourceLibrary(
    val name: String,
    val author: String,
    val license: String,
    val description: String,
    val notice: String
)

val REZONA_LICENSES = listOf(
    OpenSourceLibrary(
        name = "NewPipeExtractor",
        author = "Team NewPipe",
        license = "GNU General Public License v3.0 (GPLv3)",
        description = "Audio stream extraction library for YouTube and YouTube Music.",
        notice = "This application incorporates NewPipeExtractor licensed under the GNU General Public License v3.0. In accordance with GPLv3, any distribution of binary code must provide corresponding source code under reciprocal copyleft terms. Used strictly for personal on-device streaming."
    ),
    OpenSourceLibrary(
        name = "InnerTune, Metrolist, SimpMusic & AuraMusic References",
        author = "Z-Huang / Mostafa / Maxrave / AuraMusic Contributors",
        license = "GNU General Public License v3.0 (GPLv3)",
        description = "YouTube Music InnerTube WEB_REMIX architecture, pagination, and browse endpoint references.",
        notice = "This application incorporates client endpoint definitions and parsing patterns adapted from InnerTune, Metrolist, SimpMusic (kotlinYtmusicScraper), and AuraMusic, licensed under GPLv3. Distributed strictly for personal on-device listening."
    ),
    OpenSourceLibrary(
        name = "Haze",
        author = "Chris Banes",
        license = "Apache License 2.0",
        description = "Hardware-accelerated live backdrop glass blur engine for Jetpack Compose.",
        notice = "Copyright 2023 Chris Banes. Licensed under the Apache License, Version 2.0."
    ),
    OpenSourceLibrary(
        name = "AndroidX Media3 (ExoPlayer)",
        author = "The Android Open Source Project",
        license = "Apache License 2.0",
        description = "Core high-fidelity audio engine, session services, and playback caching.",
        notice = "Copyright The Android Open Source Project. Licensed under the Apache License, Version 2.0."
    ),
    OpenSourceLibrary(
        name = "Room & DataStore",
        author = "Google LLC",
        license = "Apache License 2.0",
        description = "Type-safe on-device SQLite database and reactive preferences store.",
        notice = "Copyright Google LLC. Licensed under the Apache License, Version 2.0."
    ),
    OpenSourceLibrary(
        name = "Coil",
        author = "Coil Contributors",
        license = "Apache License 2.0",
        description = "Kotlin coroutines-first image loading pipeline for Android.",
        notice = "Copyright Coil Contributors. Licensed under the Apache License, Version 2.0."
    ),
    OpenSourceLibrary(
        name = "InnerTune & AuraMusic Architecture",
        author = "Z-Huang / AuraMusic Contributors",
        license = "GNU General Public License v3.0 (GPLv3)",
        description = "YouTube Music InnerTube WEB_REMIX client architecture reference and API models.",
        notice = "This application incorporates architectural patterns and client endpoint definitions adapted from InnerTune (github.com/z-huang/InnerTune) and AuraMusic, licensed under GPLv3. Distributed for on-device personal music listening."
    ),
    OpenSourceLibrary(
        name = "Satoshi Font Family",
        author = "Indian Type Foundry",
        license = "ITF Free Font License",
        description = "Geometric modernist typography driving Rezona's visual system.",
        notice = "Satoshi typeface designed by Indian Type Foundry. Free for personal and commercial use."
    )
)

@Composable
fun RezonaLicensesScreen(
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD907090D)) // Translucent glass scrim over shared continuous ambient layer
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RezonaActionDisc(size = 40.dp, onClick = onBack) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = "Back",
                    tint = PureWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Open-Source Licenses",
                    style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = PearlWhite
                )
                Text(
                    text = "Third-party libraries & acoustic foundations",
                    style = Typography.bodySmall,
                    color = SmokySilver
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(REZONA_LICENSES) { lib ->
                RezonaGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lib.name,
                                    style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = PearlWhite
                                )
                                Text(
                                    text = lib.author,
                                    style = Typography.bodySmall,
                                    color = SoftSilver,
                                    fontSize = 11.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x18FFFFFF))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = lib.license.split(" ").firstOrNull() ?: "License",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = lib.description,
                            style = Typography.bodySmall,
                            color = SmokySilver
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x0CFFFFFF))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = lib.notice,
                                style = Typography.bodySmall,
                                color = SoftSilver,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
