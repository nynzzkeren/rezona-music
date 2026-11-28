package com.nayama.music.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════
// REZONA — PLATINUM FROST & DEEP SAPPHIRE LIQUID GLASS PALETTE
// Upgraded theme matching the new metallic Rezona R crest
// ═══════════════════════════════════════════════════════════════

// Core Palette — Refined Platinum & Deep Sapphire Slate
val ObsidianBlack   = Color(0xFF07090E) // Root floor — Deep Midnight Sapphire-Obsidian
val DeepCharcoal    = Color(0xFF10141D) // Surface / card substrate with cool metallic slate undertone
val Graphite        = Color(0xFF1A202E) // Elevated surface / pill / chip
val SmokySilver     = Color(0xFF8E97A8) // Secondary elements / muted labels / timestamps
val PearlWhite      = Color(0xFFF5F7FB) // Primary headings with crisp ice-pearl clarity
val SoftSilver      = Color(0xFFC8D0DE) // Icon & secondary highlight in brushed platinum
val PureWhite       = Color(0xFFFFFFFF) // Specular glare, active lens highlight, scrub thumb

// Translucent Glass Card Backgrounds (Always written with full 8-hex alpha)
val GlassBaseCard   = Color(0x75121722) // Cool frosted slate glass card
val GlassElevated   = Color(0x7A1C2333) // Elevated frosted platinum-slate glass
val GlassDock       = Color(0x66090C14) // Floating dock lens substrate
val GlassBorderSubtle = Color(0x22FFFFFF) // Hairline container border
val GlassBorderSpecular = Color(0x55FFFFFF) // Specular highlight border

// Directional Specular Light Brushes (Simulates top-down beveled milled glass)
val SpecularCardBorder = Brush.verticalGradient(
    listOf(
        Color(0x45FFFFFF),
        Color(0x14C8D8F0),
        Color(0x0AFFFFFF)
    )
)

val SpecularDockBorder = Brush.verticalGradient(
    listOf(
        Color(0x78FFFFFF),
        Color(0x26D0E2FF),
        Color(0x0AFFFFFF)
    )
)

val SpecularModalBorder = Brush.verticalGradient(
    listOf(
        Color(0x88FFFFFF),
        Color(0x28D0E2FF),
        Color(0x00FFFFFF)
    )
)

// Legacy Aliases for Seamless Codebase Compatibility
val ObsidianVoid        = ObsidianBlack
val ObsidianCanvas      = DeepCharcoal
val GraphiteSurface     = Graphite
val ElevatedSurface     = Graphite
val CardSurface         = DeepCharcoal
val SurfaceBlack        = DeepCharcoal
val DarkCharcoal        = DeepCharcoal
val LightGray           = SoftSilver
val MediumGray          = SmokySilver
val MidGray             = SmokySilver
val SilverGray          = SoftSilver
val PureBlack           = ObsidianBlack
val DeepBlack           = ObsidianBlack

// Semantic Tokens
val NayamaBackground    = ObsidianBlack
val NayamaSurface       = DeepCharcoal
val NayamaSurfaceVariant= Graphite
val NayamaElevated      = Graphite
val NayamaCard          = DeepCharcoal
val NayamaBorder        = Color(0xFF252D3D)
val NayamaBorderSubtle  = Color(0xFF181E2A)

// Semantic Text Tokens
val NayamaTextPrimary   = PearlWhite
val NayamaTextSecondary = SoftSilver
val NayamaTextMuted     = SmokySilver
val NayamaTextDisabled  = Color(0xFF565E70)

// Interactive Accent
val NayamaAccent        = PureWhite
val NayamaAccentPressed = SoftSilver
val CoralAccent         = PureWhite
val CoralSurface        = Color(0x28FFFFFF)
val CoralHover          = SoftSilver

val NayamaOverlay       = Color(0xD907090E)
val NayamaScrim         = Color(0xB307090E)
val GlassSurface        = GlassBaseCard
val GlassSurfaceLens    = GlassDock
val GlassBorder         = GlassBorderSpecular
val GlassBorderSoft     = GlassBorderSubtle
