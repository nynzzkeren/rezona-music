package com.nayama.music.ui.util

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate

/**
 * 14 distinct, visible, muted-yet-rich palettes for mock cover art.
 * High luminance contrast so ambient glows and covers are clearly differentiated.
 */
data class CoverPalette(
    val primary: Color,
    val secondary: Color,
    val highlight: Color,
    val ambient: Color
)

val MOCK_PALETTES: List<CoverPalette> = listOf(
    // 0: Deep Cobalt / Obsidian
    CoverPalette(Color(0xFF1D3557), Color(0xFF0F1E32), Color(0xFF64B5F6), Color(0xFF2A5082)),
    // 1: Emerald Jade / Deep Forest
    CoverPalette(Color(0xFF1B4332), Color(0xFF081C15), Color(0xFF52B788), Color(0xFF2D7255)),
    // 2: Amethyst Wine / Crimson
    CoverPalette(Color(0xFF4A1525), Color(0xFF220912), Color(0xFFE57373), Color(0xFF7A243F)),
    // 3: Warm Amber / Golden Honey
    CoverPalette(Color(0xFF4D3814), Color(0xFF261A07), Color(0xFFFFB74D), Color(0xFF8A6524)),
    // 4: Arctic Glacier / Steel Cyan
    CoverPalette(Color(0xFF163E48), Color(0xFF0A1F25), Color(0xFF4DD0E1), Color(0xFF2B6B7C)),
    // 5: Muted Violet / Electric Iris
    CoverPalette(Color(0xFF381F54), Color(0xFF1B0E2A), Color(0xFFBA68C8), Color(0xFF5E358C)),
    // 6: Burnt Terracotta / Solar Rust
    CoverPalette(Color(0xFF542817), Color(0xFF281108), Color(0xFFFF8A65), Color(0xFF8F4628)),
    // 7: Titanium Sage / Olive Dust
    CoverPalette(Color(0xFF2E3D29), Color(0xFF151C12), Color(0xFF81C784), Color(0xFF4D6645)),
    // 8: Dusty Mauve / Twilight Rose
    CoverPalette(Color(0xFF4A253A), Color(0xFF200F19), Color(0xFFF06292), Color(0xFF804064)),
    // 9: Steel Blue / Horizon Mist
    CoverPalette(Color(0xFF243B55), Color(0xFF111D2B), Color(0xFF90CAF9), Color(0xFF3F638E)),
    // 10: Deep Indigo / Midnight Void
    CoverPalette(Color(0xFF23285E), Color(0xFF0E112B), Color(0xFF7986CB), Color(0xFF3E47A3)),
    // 11: Bronze Eclipse / Warm Brass
    CoverPalette(Color(0xFF4A3C22), Color(0xFF211A0D), Color(0xFFFFD54F), Color(0xFF856C3D)),
    // 12: Milled Silver / Diamond Charcoal
    CoverPalette(Color(0xFF3A404D), Color(0xFF16181D), Color(0xFFECEFF1), Color(0xFF5A6375)),
    // 13: Copper Patina / Deep Teal
    CoverPalette(Color(0xFF193D3B), Color(0xFF0A1B1A), Color(0xFF26A69A), Color(0xFF2E6B67))
)

/**
 * Returns the ambient glow color for a given mock track index.
 */
fun mockAmbientColor(index: Int): Color {
    val palette = MOCK_PALETTES[index.coerceAtLeast(0) % MOCK_PALETTES.size]
    return palette.ambient
}

/**
 * Draws an abstract high-contrast gradient/geometric cover art for the given track index.
 * Uses 6 distinct structural generation variants so covers never look like flat dark squares.
 */
@Composable
fun MockCoverArt(
    index: Int,
    modifier: Modifier = Modifier
) {
    val palette = MOCK_PALETTES[index.coerceAtLeast(0) % MOCK_PALETTES.size]
    val variant = (index.coerceAtLeast(0)) % 6

    Canvas(modifier = modifier) {
        // Base Rich Gradient
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(palette.primary, palette.secondary),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )

        when (variant) {
            0 -> drawConcentricMilledRings(palette)
            1 -> drawPrismaticGeometry(palette)
            2 -> drawOrbitalEclipse(palette)
            3 -> drawWaveformContourMesh(palette)
            4 -> drawDiagonalInterlockingBands(palette)
            5 -> drawSubsurfaceLuminousBurst(palette)
        }

        // Top specular rim glare across all variants
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x33FFFFFF), Color.Transparent),
                startY = 0f,
                endY = size.height * 0.35f
            )
        )
    }
}

// ── 0. Concentric Milled Acoustic Rings ──────────────────────────────────────
private fun DrawScope.drawConcentricMilledRings(palette: CoverPalette) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f
    val maxR = size.minDimension * 0.5f

    for (i in 1..6) {
        val r = (maxR / 6) * i
        drawCircle(
            color = palette.highlight.copy(alpha = 0.22f + (i * 0.05f)),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = if (i % 2 == 0) 2.5f else 1.2f)
        )
    }

    // Glowing Central Origin Node
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, palette.highlight, Color.Transparent),
            center = Offset(cx, cy),
            radius = maxR * 0.35f
        ),
        radius = maxR * 0.35f,
        center = Offset(cx, cy)
    )
}

// ── 1. Prismatic Geometry & Angled Bevels ────────────────────────────────────
private fun DrawScope.drawPrismaticGeometry(palette: CoverPalette) {
    rotate(degrees = -30f, pivot = Offset(size.width / 2, size.height / 2)) {
        val bandW = size.width * 0.28f
        for (i in 0..4) {
            val x = -size.width * 0.4f + i * bandW * 1.15f
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        palette.highlight.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.25f),
                        Color.Transparent
                    ),
                    start = Offset(x, 0f),
                    end = Offset(x + bandW, 0f)
                ),
                topLeft = Offset(x, -size.height),
                size = Size(bandW, size.height * 3f)
            )
        }
    }
}

// ── 2. Orbital Eclipse & Radiant Spheres ─────────────────────────────────────
private fun DrawScope.drawOrbitalEclipse(palette: CoverPalette) {
    val cx = size.width * 0.65f
    val cy = size.height * 0.4f
    val r = size.minDimension * 0.38f

    // Radiant outer glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.highlight.copy(alpha = 0.75f), Color.Transparent),
            center = Offset(cx, cy),
            radius = r * 1.6f
        ),
        radius = r * 1.6f,
        center = Offset(cx, cy)
    )

    // Dark solid eclipse body
    drawCircle(
        color = palette.secondary,
        radius = r,
        center = Offset(cx, cy)
    )

    // Sharp specular rim crescent
    drawCircle(
        color = Color.White.copy(alpha = 0.85f),
        radius = r,
        center = Offset(cx, cy),
        style = Stroke(width = 2.5f)
    )

    // Counter-orbit smaller sphere
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.8f), palette.highlight, Color.Transparent),
            center = Offset(size.width * 0.25f, size.height * 0.72f),
            radius = size.minDimension * 0.16f
        ),
        radius = size.minDimension * 0.16f,
        center = Offset(size.width * 0.25f, size.height * 0.72f)
    )
}

// ── 3. Waveform Contour Mesh ────────────────────────────────────────────────
private fun DrawScope.drawWaveformContourMesh(palette: CoverPalette) {
    val count = 8
    val step = size.height / (count + 1)
    for (i in 1..count) {
        val y = step * i
        val waveH = 14f + (i * 2.8f)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, y)
            cubicTo(
                size.width * 0.3f, y - waveH,
                size.width * 0.6f, y + waveH,
                size.width, y - (waveH * 0.4f)
            )
        }
        drawPath(
            path = path,
            color = if (i % 2 == 0) Color.White.copy(alpha = 0.45f) else palette.highlight.copy(alpha = 0.6f),
            style = Stroke(width = 2.0f)
        )
    }
}

// ── 4. Diagonal Interlocking Bands ──────────────────────────────────────────
private fun DrawScope.drawDiagonalInterlockingBands(palette: CoverPalette) {
    rotate(degrees = 45f, pivot = Offset(size.width / 2, size.height / 2)) {
        val thickness = size.width * 0.18f
        for (i in -2..4) {
            val y = i * thickness * 1.5f
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.highlight.copy(alpha = 0.5f),
                        palette.primary.copy(alpha = 0.2f),
                        Color.Transparent
                    )
                ),
                topLeft = Offset(-size.width, y),
                size = Size(size.width * 3f, thickness)
            )
            // Hairline specular edge
            drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(-size.width, y),
                end = Offset(size.width * 2f, y),
                strokeWidth = 1.5f
            )
        }
    }
}

// ── 5. Subsurface Luminous Burst ────────────────────────────────────────────
private fun DrawScope.drawSubsurfaceLuminousBurst(palette: CoverPalette) {
    val cx1 = size.width * 0.35f
    val cy1 = size.height * 0.35f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.7f), palette.highlight.copy(alpha = 0.5f), Color.Transparent),
            center = Offset(cx1, cy1),
            radius = size.minDimension * 0.65f
        ),
        radius = size.minDimension * 0.65f,
        center = Offset(cx1, cy1)
    )

    val cx2 = size.width * 0.75f
    val cy2 = size.height * 0.7f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.highlight.copy(alpha = 0.6f), palette.primary, Color.Transparent),
            center = Offset(cx2, cy2),
            radius = size.minDimension * 0.5f
        ),
        radius = size.minDimension * 0.5f,
        center = Offset(cx2, cy2)
    )
}

/**
 * Generative monochrome acoustic pattern with subtle cover tint.
 * Used for "Made for Your Mood" & "Browse by mood" cards.
 */
@Composable
fun GenerativeMoodPattern(
    index: Int,
    modifier: Modifier = Modifier
) {
    val palette = MOCK_PALETTES[index.coerceAtLeast(0) % MOCK_PALETTES.size]
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF141720),
                    palette.primary.copy(alpha = 0.45f),
                    Color(0xFF08090B)
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )

        val numLines = 8
        val stepY = size.height / (numLines + 1)
        for (i in 1..numLines) {
            val y = stepY * i
            val waveHeight = 14f + (i * 2.5f)
            val strokeAlpha = 0.15f + (i * 0.04f)
            val strokeColor = if (i % 2 == 0) Color.White.copy(alpha = strokeAlpha) else palette.highlight.copy(alpha = strokeAlpha * 1.4f)
            
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, y)
                cubicTo(
                    size.width * 0.25f, y - waveHeight,
                    size.width * 0.5f, y + waveHeight,
                    size.width * 0.75f, y - (waveHeight * 0.5f)
                )
                lineTo(size.width, y)
            }
            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(width = 1.5f)
            )
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.highlight.copy(alpha = 0.5f), Color.Transparent),
                center = Offset(size.width * 0.85f, size.height * 0.25f),
                radius = size.minDimension * 0.6f
            ),
            radius = size.minDimension * 0.6f,
            center = Offset(size.width * 0.85f, size.height * 0.25f)
        )
    }
}
