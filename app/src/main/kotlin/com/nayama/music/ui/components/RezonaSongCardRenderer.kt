package com.nayama.music.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.nayama.music.model.RezonaTrack
import com.nayama.music.ui.util.MOCK_PALETTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Generates and shares a crisp 1080x1350 Liquid Glass Song Card PNG for Stage D & E ("Share song card").
 */
object RezonaSongCardRenderer {

    suspend fun shareSongCard(context: Context, track: RezonaTrack) {
        val file = withContext(Dispatchers.IO) {
            val coverBmp = downloadBitmap(track.coverArtUrl)
            val cardBmp = renderCardBitmap(track, coverBmp)
            val outDir = File(context.cacheDir, "shared_cards").apply { if (!exists()) mkdirs() }
            val outFile = File(outDir, "rezona_card_${track.id.take(12)}.png")
            FileOutputStream(outFile).use { fos ->
                cardBmp.compress(Bitmap.CompressFormat.PNG, 95, fos)
            }
            outFile
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "${track.title} — ${track.artist}\n${track.streamUrl}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Song Card").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun downloadBitmap(urlStr: String): Bitmap? {
        if (urlStr.isBlank()) return null
        return runCatching {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.connect()
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    private fun renderCardBitmap(track: RezonaTrack, coverBitmap: Bitmap?): Bitmap {
        val w = 1080
        val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val palette = MOCK_PALETTES[track.index.coerceAtLeast(0) % MOCK_PALETTES.size]
        val c1 = android.graphics.Color.rgb(
            (palette.ambient.red * 255).toInt(),
            (palette.ambient.green * 255).toInt(),
            (palette.ambient.blue * 255).toInt()
        )
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(c1, Color.parseColor("#0D1017"), Color.parseColor("#08090B")),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // Outer Liquid Glass Card Container
        val cardRect = RectF(84f, 110f, w - 84f, h - 130f)
        val glassFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, cardRect.top, 0f, cardRect.bottom,
                intArrayOf(Color.parseColor("#24FFFFFF"), Color.parseColor("#08FFFFFF")),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, 54f, 54f, glassFill)

        val glassBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#4DFFFFFF")
        }
        canvas.drawRoundRect(cardRect, 54f, 54f, glassBorder)

        // Artwork Square
        val artLeft = 156f
        val artTop = 186f
        val artSize = w - 312f
        val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
        val clipPath = android.graphics.Path().apply { addRoundRect(artRect, 36f, 36f, android.graphics.Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clipPath)
        if (coverBitmap != null) {
            canvas.drawBitmap(coverBitmap, null, artRect, Paint(Paint.ANTI_ALIAS_FLAG))
        } else {
            val fallbackPaint = Paint().apply {
                shader = LinearGradient(
                    artLeft, artTop, artLeft + artSize, artTop + artSize,
                    intArrayOf(Color.parseColor("#2A2F3D"), Color.parseColor("#111318")),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(artRect, fallbackPaint)
        }
        canvas.restore()

        // Title & Artist
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F4F5F7")
            textSize = 54f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val titleLayout = StaticLayout.Builder
            .obtain(track.title, 0, track.title.length, titlePaint, (w - 312).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(2)
            .build()
        canvas.save()
        canvas.translate(artLeft, artRect.bottom + 54f)
        titleLayout.draw(canvas)
        canvas.restore()

        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A8B0BE")
            textSize = 36f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText(
            track.artist,
            artLeft,
            artRect.bottom + 54f + titleLayout.height + 52f,
            artistPaint
        )

        // Brand Footer
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#88FFFFFF")
            textSize = 28f
            letterSpacing = 0.14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("REZONA · LIQUID GLASS AUDIO", artLeft, cardRect.bottom - 52f, brandPaint)

        return bmp
    }
}
