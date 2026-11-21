package com.nayama.music.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════
// REZONA — OBSIDIAN SILVER MONOCHROME PALETTE
// Official tokens based on design specification & master brief
// ═══════════════════════════════════════════════════════════════

// Core Palette — Exact Hex Specifications
val ObsidianBlack   = Color(0xFF08090B) // Background utama — Pure OLED root floor
val DeepCharcoal    = Color(0xFF111318) // Surface / card substrate
val Graphite        = Color(0xFF1C1F26) // Elevated surface / pill / chip
val SmokySilver     = Color(0xFF858B96) // Secondary elements / muted labels / timestamps
val PearlWhite      = Color(0xFFF4F5F7) // Text utama / primary headings
val SoftSilver      = Color(0xFFC4C8D0) // Icon dan highlight / secondary controls
val PureWhite       = Color(0xFFFFFFFF) // The ONLY interactive accent, specular glare, scrub thumb

// Translucent Glass Card Backgrounds (Always written with full 8-hex alpha)
val GlassBaseCard   = Color(0x73111318) // ~45% alpha DeepCharcoal — reduced to show Haze blur through
val GlassElevated   = Color(0x731C1F26) // ~45% alpha Graphite — reduced to show Haze blur through
val GlassDock       = Color(0x5908090B) // ~35% alpha ObsidianBlack — dock tint reduced for visible blur
val GlassBorderSubtle = Color(0x1AFFFFFF) // Hairline container border (~10% white)
val GlassBorderSpecular = Color(0x4DFFFFFF) // Specular highlight border (~30% white)

// Directional Specular Light Brushes (Simulates top-down beveled milled glass)
val SpecularCardBorder = Brush.verticalGradient(
    listOf(
        Color(0x38FFFFFF),
        Color(0x08FFFFFF)
    )
)

val SpecularDockBorder = Brush.verticalGradient(
    listOf(
        Color(0x66FFFFFF),
        Color(0x1AFFFFFF),
        Color(0x05FFFFFF)
    )
)

val SpecularModalBorder = Brush.verticalGradient(
    listOf(
        Color(0x80FFFFFF),
        Color(0x20FFFFFF),
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

// Strict Monochrome Semantic Tokens
val NayamaBackground    = ObsidianBlack
val NayamaSurface       = DeepCharcoal
val NayamaSurfaceVariant= Graphite
val NayamaElevated      = Graphite
val NayamaCard          = DeepCharcoal
val NayamaBorder        = Color(0xFF242832)
val NayamaBorderSubtle  = Color(0xFF191C24)

// Semantic Text Tokens
val NayamaTextPrimary   = PearlWhite
val NayamaTextSecondary = SoftSilver
val NayamaTextMuted     = SmokySilver
val NayamaTextDisabled  = Color(0xFF535864)

// Interactive Accent (Strictly PureWhite)
val NayamaAccent        = PureWhite
val NayamaAccentPressed = SoftSilver
val CoralAccent         = PureWhite // Stripped of coral; strictly monochrome PureWhite
val CoralSurface        = Color(0x26FFFFFF)
val CoralHover          = SoftSilver

val NayamaOverlay       = Color(0xD908090B)
val NayamaScrim         = Color(0xB308090B)
val GlassSurface        = GlassBaseCard
val GlassSurfaceLens    = GlassDock
val GlassBorder         = GlassBorderSpecular
val GlassBorderSoft     = GlassBorderSubtle
