package com.nayama.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nayama.music.R

/**
 * Rezona Satoshi Geometric Font Family
 * Loaded directly from app/src/main/res/font/
 */
val SatoshiFontFamily = FontFamily(
    Font(R.font.satoshi_regular, FontWeight.Normal),
    Font(R.font.satoshi_medium, FontWeight.Medium),
    Font(R.font.satoshi_bold, FontWeight.Bold),
    Font(R.font.satoshi_bold, FontWeight.SemiBold)
)

/**
 * Rezona Typography Scale
 * Built on Satoshi for geometric precision and crisp legibility over liquid glass.
 */
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = SatoshiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
        color = PearlWhite
    ),
    displayMedium = TextStyle(
        fontFamily = SatoshiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
        color = PearlWhite
    ),
    headlineLarge = TextStyle(
        fontFamily = SatoshiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp,
        color = PearlWhite
    ),
    headlineMedium = TextStyle(
        fontFamily = SatoshiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
        color = PearlWhite
    ),
    titleLarge = TextStyle(
        fontFamily = SatoshiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.0.sp,
        color = PearlWhite
    ),
    titleMedium = TextStyle(
        fontFamily = SatoshiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
        color = PearlWhite
    ),
    titleSmall = TextStyle(
        fontFamily = SatoshiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
        color = SoftSilver
    ),
    bodyLarge = TextStyle(
        fontFamily = SatoshiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
        color = SoftSilver
    ),
    bodyMedium = TextStyle(
        fontFamily = SatoshiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
        color = SoftSilver
    ),
    bodySmall = TextStyle(
        fontFamily = SatoshiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
        color = SmokySilver
    ),
    labelLarge = TextStyle(
        fontFamily = SatoshiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = PureWhite
    ),
    labelMedium = TextStyle(
        fontFamily = SatoshiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp,
        color = SmokySilver
    ),
    labelSmall = TextStyle(
        fontFamily = SatoshiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.0.sp,
        color = SmokySilver
    )
)
