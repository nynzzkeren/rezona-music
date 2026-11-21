package com.nayama.music.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class DynamicArtworkPalette(
    val ambientBase: Color = Color(0xFF1A1E24),
    val primaryAmbient: Color = Color(0xFF282F3A),
    val secondaryAmbient: Color = Color(0xFF1E242E),
    val tertiaryAmbient: Color = Color(0xFF151920),
    val glassTint: Color = Color(0x6B2E3540),
    val highlightAccent: Color = PureWhite,
    val needsTopContrastScrim: Boolean = false,
    val needsFadeContrastScrim: Boolean = false
)

/**
 * Pure color math for Cover-to-Background Blend (Part 1).
 * Keeps JVM-testable without Android Color/Bitmap dependencies.
 */
object CoverPaletteMath {

    data class Hsl(val h: Float, val s: Float, val l: Float)

    fun rgbToHsl(rInt: Int, gInt: Int, bInt: Int): Hsl {
        val r = (rInt.coerceIn(0, 255)) / 255f
        val g = (gInt.coerceIn(0, 255)) / 255f
        val b = (bInt.coerceIn(0, 255)) / 255f
        val maxVal = max(r, max(g, b))
        val minVal = min(r, min(g, b))
        val delta = maxVal - minVal
        val l = (maxVal + minVal) * 0.5f
        if (delta < 0.0001f) {
            return Hsl(0f, 0f, l)
        }
        val s = delta / (1f - abs(2f * l - 1f)).coerceAtLeast(0.0001f)
        val rawH = when (maxVal) {
            r -> ((g - b) / delta) % 6f
            g -> ((b - r) / delta) + 2f
            else -> ((r - g) / delta) + 4f
        }
        var h = rawH * 60f
        if (h < 0f) h += 360f
        return Hsl(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
    }

    fun hslToColor(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
        val hue = ((h % 360f) + 360f) % 360f
        val sat = s.coerceIn(0f, 1f)
        val lig = l.coerceIn(0f, 1f)
        val c = (1f - abs(2f * lig - 1f)) * sat
        val x = c * (1f - abs((hue / 60f) % 2f - 1f))
        val m = lig - c * 0.5f
        val (r1, g1, b1) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(
            red = (r1 + m).coerceIn(0f, 1f),
            green = (g1 + m).coerceIn(0f, 1f),
            blue = (b1 + m).coerceIn(0f, 1f),
            alpha = alpha.coerceIn(0f, 1f)
        )
    }

    fun relativeLuminance(color: Color): Float {
        fun lin(c: Float): Float = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * lin(color.red) + 0.7152f * lin(color.green) + 0.0722f * lin(color.blue)
    }

    fun contrastRatio(fg: Color, bg: Color): Float {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val hi = max(l1, l2)
        val lo = min(l1, l2)
        return (hi + 0.05f) / (lo + 0.05f)
    }

    /**
     * Computes cover-matched glass tint: ambientBase lightened 12-18% at ~42% alpha.
     */
    fun computeTintedGlass(ambientBase: Color, alpha: Float = 0.42f): Color {
        val hsl = rgbToHsl(
            (ambientBase.red * 255f).toInt(),
            (ambientBase.green * 255f).toInt(),
            (ambientBase.blue * 255f).toInt()
        )
        return hslToColor(
            h = hsl.h,
            s = (hsl.s * 0.92f).coerceIn(0f, 0.72f),
            l = (hsl.l + 0.15f).coerceIn(0.18f, 0.42f),
            alpha = alpha.coerceIn(0.35f, 0.52f)
        )
    }

    /**
     * Extracts dominant swatch + bottom-25% average from ARGB pixels and computes a seamless
     * cover-family ambientBase (hue preserved, saturation preserved, lightness clamped ~0.11..0.19
     * with >= 4.5:1 contrast against white text). Never forces purple or pitch-black.
     */
    fun computeFromPixels(pixels: IntArray, width: Int, height: Int): DynamicArtworkPalette {
        if (pixels.isEmpty() || width <= 0 || height <= 0) return DynamicArtworkPalette()

        val buckets = HashMap<Int, Float>(64)
        val bucketSums = HashMap<Int, LongArray>(64)
        val bucketCounts = HashMap<Int, Int>(64)

        var bottomR = 0L
        var bottomG = 0L
        var bottomB = 0L
        var bottomCount = 0
        var topLumSum = 0.0
        var topCount = 0
        var bottomLumSum = 0.0

        val bottomStartRow = (height * 0.72f).toInt().coerceIn(0, height - 1)
        val topEndRow = (height * 0.22f).toInt().coerceIn(1, height)

        for (y in 0 until height) {
            val rowOff = y * width
            for (x in 0 until width) {
                val px = pixels[rowOff + x]
                val a = (px ushr 24) and 0xFF
                if (a < 128) continue
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                val hsl = rgbToHsl(r, g, b)

                if (y < topEndRow) {
                    topLumSum += relativeLuminance(Color(r / 255f, g / 255f, b / 255f))
                    topCount++
                }
                if (y >= bottomStartRow) {
                    bottomR += r
                    bottomG += g
                    bottomB += b
                    bottomLumSum += relativeLuminance(Color(r / 255f, g / 255f, b / 255f))
                    bottomCount++
                }

                // Weight chromatic pixels strongly so dominant cover hue wins over white/black margins
                if (hsl.l in 0.07f..0.93f) {
                    val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
                    val satBoost = 0.8f + hsl.s * 3.2f
                    val midBoost = if (hsl.l in 0.18f..0.78f) 1.3f else 0.75f
                    val bottomRowBoost = if (y >= bottomStartRow) 1.35f else 1.0f
                    buckets[key] = (buckets[key] ?: 0f) + (satBoost * midBoost * bottomRowBoost)
                    val arr = bucketSums.getOrPut(key) { LongArray(3) }
                    arr[0] += r
                    arr[1] += g
                    arr[2] += b
                    bucketCounts[key] = (bucketCounts[key] ?: 0) + 1
                }
            }
        }

        val avgBottomRgb = if (bottomCount > 0) {
            Triple((bottomR / bottomCount).toInt(), (bottomG / bottomCount).toInt(), (bottomB / bottomCount).toInt())
        } else {
            Triple(28, 32, 38)
        }
        val bottomHsl = rgbToHsl(avgBottomRgb.first, avgBottomRgb.second, avgBottomRgb.third)

        val sortedBuckets = buckets.entries.sortedByDescending { it.value }
        val dominantRgb = if (sortedBuckets.isNotEmpty()) {
            val k = sortedBuckets[0].key
            val c = (bucketCounts[k] ?: 1).coerceAtLeast(1)
            val s = bucketSums[k]!!
            Triple((s[0] / c).toInt(), (s[1] / c).toInt(), (s[2] / c).toInt())
        } else {
            avgBottomRgb
        }
        val domHsl = rgbToHsl(dominantRgb.first, dominantRgb.second, dominantRgb.third)

        // Secondary distinct accent from cover
        val secondRgb = sortedBuckets.firstOrNull { entry ->
            val topK = sortedBuckets[0].key
            val dr = ((entry.key shr 8) and 0xF) - ((topK shr 8) and 0xF)
            val dg = ((entry.key shr 4) and 0xF) - ((topK shr 4) and 0xF)
            val db = (entry.key and 0xF) - (topK and 0xF)
            (dr * dr + dg * dg + db * db) > 5
        }?.let { e ->
            val c = (bucketCounts[e.key] ?: 1).coerceAtLeast(1)
            val s = bucketSums[e.key]!!
            Triple((s[0] / c).toInt(), (s[1] / c).toInt(), (s[2] / c).toInt())
        } ?: dominantRgb
        val secondHsl = rgbToHsl(secondRgb.first, secondRgb.second, secondRgb.third)

        val isNearNeutral = max(domHsl.s, bottomHsl.s) < 0.12f

        val (baseHue, baseSat, baseLight) = if (isNearNeutral) {
            // Pure black / white / monochrome cover -> neutral deep charcoal-slate with minimal tint (never purple)
            val neutralLight = when {
                bottomHsl.l > 0.72f -> 0.17f
                bottomHsl.l < 0.12f -> 0.11f
                else -> 0.14f
            }
            Triple(210f, 0.06f, neutralLight)
        } else {
            // Blend bottom-strip average and dominant swatch keeping the true cover color family!
            val bottomWeight = if (bottomHsl.s >= 0.15f) 0.58f else 0.25f
            val chosenHue = blendHueDegrees(domHsl.h, bottomHsl.h, bottomWeight)
            val rawSat = domHsl.s * (1f - bottomWeight * 0.5f) + bottomHsl.s * (bottomWeight * 0.5f)
            // Preserve most saturation so orange -> rich warm brown, green -> deep forest, blue -> deep navy
            val tunedSat = rawSat.coerceIn(0.28f, 0.68f)
            // Keep lightness in 0.12..0.19 (warmer/brighter covers slightly lighter within readable range)
            val rawLight = (bottomHsl.l * 0.45f + domHsl.l * 0.55f)
            val tunedLight = (0.115f + rawLight * 0.085f).coerceIn(0.115f, 0.195f)
            Triple(chosenHue, tunedSat, tunedLight)
        }

        // Enforce WCAG >= 4.5:1 contrast with pure white text
        var finalLight = baseLight
        var ambientBase = hslToColor(baseHue, baseSat, finalLight)
        while (contrastRatio(PureWhite, ambientBase) < 4.8f && finalLight > 0.08f) {
            finalLight -= 0.01f
            ambientBase = hslToColor(baseHue, baseSat, finalLight)
        }

        val primaryGlow = if (isNearNeutral) {
            hslToColor(210f, 0.08f, (finalLight + 0.08f).coerceAtMost(0.26f))
        } else {
            hslToColor(domHsl.h, (domHsl.s * 0.85f).coerceIn(0.25f, 0.72f), (finalLight + 0.09f).coerceAtMost(0.28f))
        }
        val secondaryGlow = if (isNearNeutral) {
            hslToColor(210f, 0.05f, (finalLight + 0.04f).coerceAtMost(0.22f))
        } else {
            hslToColor(secondHsl.h, (secondHsl.s * 0.78f).coerceIn(0.22f, 0.66f), (finalLight + 0.05f).coerceAtMost(0.24f))
        }
        val tertiaryGlow = hslToColor(baseHue, (baseSat * 0.85f).coerceIn(0.04f, 0.55f), (finalLight - 0.025f).coerceAtLeast(0.08f))
        val glassTint = computeTintedGlass(ambientBase, alpha = 0.42f)

        val avgTopLum = if (topCount > 0) (topLumSum / topCount).toFloat() else 0.1f
        val avgBottomLum = if (bottomCount > 0) (bottomLumSum / bottomCount).toFloat() else 0.1f
        val topContrast = (1.05f) / (avgTopLum + 0.05f)
        val bottomContrast = (1.05f) / (avgBottomLum + 0.05f)

        return DynamicArtworkPalette(
            ambientBase = ambientBase,
            primaryAmbient = primaryGlow,
            secondaryAmbient = secondaryGlow,
            tertiaryAmbient = tertiaryGlow,
            glassTint = glassTint,
            highlightAccent = PureWhite,
            needsTopContrastScrim = topContrast < 4.5f,
            needsFadeContrastScrim = bottomContrast < 4.5f
        )
    }

    private fun blendHueDegrees(h1: Float, h2: Float, weight2: Float): Float {
        val diff = ((h2 - h1 + 540f) % 360f) - 180f
        return ((h1 + diff * weight2) % 360f + 360f) % 360f
    }
}

object ArtworkColorExtractor {

    private val paletteCache = ConcurrentHashMap<String, DynamicArtworkPalette>()

    fun getCachedPalette(imageUrl: String?): DynamicArtworkPalette? {
        if (imageUrl.isNullOrBlank()) return null
        return paletteCache[imageUrl]
    }

    suspend fun prefetch(context: Context, imageUrl: String?) {
        if (imageUrl.isNullOrBlank() || paletteCache.containsKey(imageUrl)) return
        extractPalette(context, imageUrl)
    }

    suspend fun extractPalette(context: Context, imageUrl: String?): DynamicArtworkPalette {
        if (imageUrl.isNullOrBlank()) return DynamicArtworkPalette()
        paletteCache[imageUrl]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val req = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .allowHardware(false)
                    .size(96, 96)
                    .build()
                val res = context.imageLoader.execute(req)
                val bitmap = (res as? SuccessResult)?.drawable?.let {
                    (it as? BitmapDrawable)?.bitmap
                } ?: return@withContext DynamicArtworkPalette()

                val extracted = extractFromBitmap(bitmap)
                paletteCache[imageUrl] = extracted
                extracted
            } catch (_: Throwable) {
                DynamicArtworkPalette()
            }
        }
    }

    fun extractFromBitmap(bitmap: Bitmap): DynamicArtworkPalette {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return DynamicArtworkPalette()
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        return CoverPaletteMath.computeFromPixels(pixels, w, h)
    }
}

@Composable
fun rememberAnimatedArtworkPalette(
    context: Context,
    artworkUrl: String?,
    fallbackKey: String? = null
): DynamicArtworkPalette {
    var targetPalette by remember {
        mutableStateOf(
            ArtworkColorExtractor.getCachedPalette(artworkUrl) ?: DynamicArtworkPalette()
        )
    }

    LaunchedEffect(artworkUrl, fallbackKey) {
        if (!artworkUrl.isNullOrBlank()) {
            ArtworkColorExtractor.getCachedPalette(artworkUrl)?.let {
                targetPalette = it
                return@LaunchedEffect
            }
            targetPalette = ArtworkColorExtractor.extractPalette(context, artworkUrl)
        } else {
            targetPalette = DynamicArtworkPalette()
        }
    }

    val spec = tween<Color>(durationMillis = 700, easing = FastOutSlowInEasing)
    val base by animateColorAsState(targetValue = targetPalette.ambientBase, animationSpec = spec, label = "dyn_base")
    val primary by animateColorAsState(targetValue = targetPalette.primaryAmbient, animationSpec = spec, label = "dyn_primary")
    val secondary by animateColorAsState(targetValue = targetPalette.secondaryAmbient, animationSpec = spec, label = "dyn_secondary")
    val tertiary by animateColorAsState(targetValue = targetPalette.tertiaryAmbient, animationSpec = spec, label = "dyn_tertiary")
    val glass by animateColorAsState(targetValue = targetPalette.glassTint, animationSpec = spec, label = "dyn_glass")
    val accent by animateColorAsState(targetValue = targetPalette.highlightAccent, animationSpec = spec, label = "dyn_accent")

    return DynamicArtworkPalette(
        ambientBase = base,
        primaryAmbient = primary,
        secondaryAmbient = secondary,
        tertiaryAmbient = tertiary,
        glassTint = glass,
        highlightAccent = accent,
        needsTopContrastScrim = targetPalette.needsTopContrastScrim,
        needsFadeContrastScrim = targetPalette.needsFadeContrastScrim
    )
}
