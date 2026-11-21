package com.nayama.music.ui.lyrics

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.res.ResourcesCompat
import com.nayama.music.R

enum class LyricsFontFamilyOption(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val license: String,
    val resIdRegular: Int,
    val resIdBold: Int = resIdRegular
) {
    SATOSHI(
        id = "SATOSHI",
        displayName = "Satoshi",
        subtitle = "Geometric default",
        license = "ITF Free Font License",
        resIdRegular = R.font.satoshi_regular,
        resIdBold = R.font.satoshi_bold
    ),
    INTER(
        id = "INTER",
        displayName = "Inter",
        subtitle = "Clean neo-grotesk",
        license = "SIL Open Font License 1.1",
        resIdRegular = R.font.inter_regular
    ),
    SPACE_GROTESK(
        id = "SPACE_GROTESK",
        displayName = "Space Grotesk",
        subtitle = "Editorial tech",
        license = "SIL Open Font License 1.1",
        resIdRegular = R.font.space_grotesk_regular
    ),
    PLAYFAIR_DISPLAY(
        id = "PLAYFAIR_DISPLAY",
        displayName = "Playfair Display",
        subtitle = "Classic serif",
        license = "SIL Open Font License 1.1",
        resIdRegular = R.font.playfair_display_regular
    ),
    JETBRAINS_MONO(
        id = "JETBRAINS_MONO",
        displayName = "JetBrains Mono",
        subtitle = "Crisp monospace",
        license = "SIL Open Font License 1.1",
        resIdRegular = R.font.jetbrains_mono_regular
    ),
    NUNITO(
        id = "NUNITO",
        displayName = "Nunito",
        subtitle = "Soft rounded",
        license = "SIL Open Font License 1.1",
        resIdRegular = R.font.nunito_regular
    ),
    CAVEAT(
        id = "CAVEAT",
        displayName = "Caveat",
        subtitle = "Handwriting",
        license = "SIL Open Font License 1.1",
        resIdRegular = R.font.caveat_regular
    ),
    BEBAS_NEUE(
        id = "BEBAS_NEUE",
        displayName = "Bebas Neue",
        subtitle = "Condensed display",
        license = "SIL Open Font License 1.1",
        resIdRegular = R.font.bebas_neue_regular
    );

    fun toComposeFontFamily(): FontFamily = when (this) {
        SATOSHI -> FontFamily(
            Font(R.font.satoshi_regular, FontWeight.Normal),
            Font(R.font.satoshi_medium, FontWeight.Medium),
            Font(R.font.satoshi_bold, FontWeight.Bold),
            Font(R.font.satoshi_bold, FontWeight.Black)
        )
        else -> FontFamily(
            Font(resIdRegular, FontWeight.Normal),
            Font(resIdRegular, FontWeight.Medium),
            Font(resIdRegular, FontWeight.Bold),
            Font(resIdRegular, FontWeight.Black)
        )
    }

    fun loadAndroidTypeface(context: Context, isBold: Boolean, isItalic: Boolean): Typeface {
        val base = runCatching {
            ResourcesCompat.getFont(context, if (isBold) resIdBold else resIdRegular)
        }.getOrNull() ?: Typeface.SANS_SERIF
        val style = when {
            isBold && isItalic -> Typeface.BOLD_ITALIC
            isBold -> Typeface.BOLD
            isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(base, style)
    }

    companion object {
        fun fromId(raw: String?): LyricsFontFamilyOption =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: SATOSHI
    }
}

enum class LyricsFontWeightOption(val id: String, val label: String, val weight: FontWeight) {
    REGULAR("REGULAR", "Regular", FontWeight.Normal),
    MEDIUM("MEDIUM", "Medium", FontWeight.Medium),
    BOLD("BOLD", "Bold", FontWeight.Bold),
    BLACK("BLACK", "Black", FontWeight.Black);

    companion object {
        fun fromId(raw: String?): LyricsFontWeightOption =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: BOLD
    }
}

enum class LyricsLineSpacingOption(val id: String, val label: String, val multiplier: Float, val itemGapDp: Float) {
    TIGHT("TIGHT", "Tight", 1.14f, 14f),
    NORMAL("NORMAL", "Normal", 1.28f, 20f),
    RELAXED("RELAXED", "Relaxed", 1.44f, 26f);

    companion object {
        fun fromId(raw: String?): LyricsLineSpacingOption =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: NORMAL
    }
}

@Immutable
data class LyricsStyleConfig(
    val fontFamily: LyricsFontFamilyOption = LyricsFontFamilyOption.SATOSHI,
    val weight: LyricsFontWeightOption = LyricsFontWeightOption.BOLD,
    val fontSizeSp: Float = 26f,
    val centerAlign: Boolean = false,
    val lineSpacing: LyricsLineSpacingOption = LyricsLineSpacingOption.NORMAL,
    val isItalic: Boolean = false,
    val letterSpacingEm: Float = -0.01f,
    val translationSizeSp: Float = 14f
) {
    val textAlign: TextAlign get() = if (centerAlign) TextAlign.Center else TextAlign.Start
    val fontStyle: FontStyle get() = if (isItalic) FontStyle.Italic else FontStyle.Normal
    val lineHeightSp: Float get() = (fontSizeSp * lineSpacing.multiplier).coerceAtLeast(fontSizeSp + 3f)
}
