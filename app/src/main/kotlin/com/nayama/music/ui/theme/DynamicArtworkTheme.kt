package com.nayama.music.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

data class DynamicArtworkPalette(
    val primaryAmbient: Color = Color(0xFF14161C),
    val secondaryAmbient: Color = Color(0xFF090A0D),
    val glassTint: Color = Color(0xB3111318),
    val highlightAccent: Color = PureWhite
)

object ArtworkColorExtractor {

    private val paletteCache = ConcurrentHashMap<String, DynamicArtworkPalette>()

    suspend fun extractPalette(context: Context, imageUrl: String?): DynamicArtworkPalette {
        if (imageUrl.isNullOrBlank()) return DynamicArtworkPalette()
        paletteCache[imageUrl]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val req = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .allowHardware(false)
                    .size(64, 64)
                    .build()
                val res = loader.execute(req)
                val bitmap = (res as? SuccessResult)?.drawable?.let {
                    (it as? BitmapDrawable)?.bitmap
                } ?: return@withContext DynamicArtworkPalette()

                val extracted = computeDominantPalette(bitmap)
                paletteCache[imageUrl] = extracted
                extracted
            } catch (_: Throwable) {
                DynamicArtworkPalette()
            }
        }
    }

    private fun computeDominantPalette(bitmap: Bitmap): DynamicArtworkPalette {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return DynamicArtworkPalette()

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val buckets = HashMap<Int, Float>(64)
        val bucketSums = HashMap<Int, Triple<Long, Long, Long>>(64)
        val bucketCounts = HashMap<Int, Int>(64)

        val hsv = FloatArray(3)
        for (px in pixels) {
            val alpha = (px ushr 24) and 0xFF
            if (alpha < 128) continue
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF

            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val sat = hsv[1]
            val valLum = hsv[2]

            // Skip crushed blacks and blown-out whites
            if (valLum < 0.10f || valLum > 0.96f) continue

            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            val weight = 1f + sat * 1.5f + if (valLum in 0.25f..0.75f) 1.0f else 0f
            buckets[key] = (buckets[key] ?: 0f) + weight

            val cur = bucketSums[key] ?: Triple(0L, 0L, 0L)
            bucketSums[key] = Triple(cur.first + r, cur.second + g, cur.third + b)
            bucketCounts[key] = (bucketCounts[key] ?: 0) + 1
        }

        if (buckets.isEmpty()) return DynamicArtworkPalette()

        val sorted = buckets.entries.sortedByDescending { it.value }
        val topKey = sorted[0].key
        val topCount = bucketCounts[topKey] ?: 1
        val topSum = bucketSums[topKey]!!
        val r1 = (topSum.first / topCount).toInt()
        val g1 = (topSum.second / topCount).toInt()
        val b1 = (topSum.third / topCount).toInt()

        val secondEntry = sorted.firstOrNull { entry ->
            val k = entry.key
            val dr = ((k shr 8) and 0xF) - ((topKey shr 8) and 0xF)
            val dg = ((k shr 4) and 0xF) - ((topKey shr 4) and 0xF)
            val db = (k and 0xF) - (topKey and 0xF)
            (dr * dr + dg * dg + db * db) > 6
        } ?: sorted.getOrNull(1)

        val (r2, g2, b2) = if (secondEntry != null) {
            val c = bucketCounts[secondEntry.key] ?: 1
            val s = bucketSums[secondEntry.key]!!
            Triple((s.first / c).toInt(), (s.second / c).toInt(), (s.third / c).toInt())
        } else {
            Triple((r1 * 0.55f).toInt(), (g1 * 0.55f).toInt(), (b1 * 0.55f).toInt())
        }

        // Strictly desaturate ambient colors to max 20-25% saturation and low luminance
        val primaryAmbient = tuneDesaturatedAmbient(r1, g1, b1, targetMaxLum = 0.20f, minLum = 0.08f)
        val secondaryAmbient = tuneDesaturatedAmbient(r2, g2, b2, targetMaxLum = 0.14f, minLum = 0.05f)
        val glassTint = Color(0xB3111318)

        return DynamicArtworkPalette(
            primaryAmbient = primaryAmbient,
            secondaryAmbient = secondaryAmbient,
            glassTint = glassTint,
            highlightAccent = PureWhite
        )
    }

    private fun tuneDesaturatedAmbient(r: Int, g: Int, b: Int, targetMaxLum: Float, minLum: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(r, g, b, hsv)
        // Clamp saturation to max 22% so the interface reads strictly as monochrome
        hsv[1] = min(0.22f, hsv[1] * 0.45f)
        hsv[2] = hsv[2].coerceIn(minLum, targetMaxLum)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}

@Composable
fun rememberAnimatedArtworkPalette(
    context: Context,
    artworkUrl: String?
): DynamicArtworkPalette {
    var targetPalette by remember { mutableStateOf(DynamicArtworkPalette()) }

    LaunchedEffect(artworkUrl) {
        targetPalette = ArtworkColorExtractor.extractPalette(context, artworkUrl)
    }

    val primary by animateColorAsState(
        targetValue = targetPalette.primaryAmbient,
        animationSpec = tween(700),
        label = "dyn_primary"
    )
    val secondary by animateColorAsState(
        targetValue = targetPalette.secondaryAmbient,
        animationSpec = tween(700),
        label = "dyn_secondary"
    )
    val glass by animateColorAsState(
        targetValue = targetPalette.glassTint,
        animationSpec = tween(700),
        label = "dyn_glass"
    )
    val accent by animateColorAsState(
        targetValue = targetPalette.highlightAccent,
        animationSpec = tween(700),
        label = "dyn_accent"
    )

    return DynamicArtworkPalette(
        primaryAmbient = primary,
        secondaryAmbient = secondary,
        glassTint = glass,
        highlightAccent = accent
    )
}
