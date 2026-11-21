package com.nayama.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RezonaMonochromeScheme = darkColorScheme(
    primary                 = PureWhite,
    onPrimary               = ObsidianBlack,
    primaryContainer        = DeepCharcoal,
    onPrimaryContainer      = PearlWhite,
    secondary               = SoftSilver,
    onSecondary             = ObsidianBlack,
    secondaryContainer      = Graphite,
    onSecondaryContainer    = PearlWhite,
    tertiary                = SmokySilver,
    onTertiary              = ObsidianBlack,
    background              = ObsidianBlack,
    onBackground            = PearlWhite,
    surface                 = DeepCharcoal,
    onSurface               = PearlWhite,
    surfaceVariant          = DeepCharcoal,
    onSurfaceVariant        = SoftSilver,
    surfaceContainer        = Graphite,
    surfaceContainerHigh    = DeepCharcoal,
    surfaceContainerHighest = Graphite,
    outline                 = Graphite,
    outlineVariant          = NayamaBorderSubtle,
    inverseSurface          = PearlWhite,
    inverseOnSurface        = ObsidianBlack,
    inversePrimary          = ObsidianBlack,
    scrim                   = NayamaScrim
)

@Composable
fun RezonaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RezonaMonochromeScheme,
        typography  = Typography,
        content     = content
    )
}

// Backward Compatibility Alias
@Composable
fun NayamaMusicTheme(
    content: @Composable () -> Unit
) {
    RezonaTheme(content = content)
}
