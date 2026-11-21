package com.nayama.music.ui.lyrics

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.nayama.music.ui.util.MOCK_PALETTES
import java.io.File
import java.io.FileOutputStream

enum class PosterFormat(val width: Int, val height: Int, val label: String) {
    SQUARE(1080, 1080, "1:1 Square"),
    STORY(1080, 1920, "9:16 Story")
}

enum class PosterBackground(val label: String) {
    GLASS("Glass"),
    SOLID("Solid"),
    BLUR("Blur"),
    GRADIENT("Gradient")
}

enum class PosterFont(val label: String) {
    EDITORIAL("Editorial"),
    GROTESK("Grotesk"),
    ROUNDED("Rounded"),
    SERIF("Serif")
}

object PosterRenderer {

    fun renderPosterBitmap(
        context: Context,
        lines: List<String>,
        trackTitle: String,
        trackArtist: String,
        coverIndex: Int,
        format: PosterFormat,
        background: PosterBackground,
        font: PosterFont,
        isBold: Boolean,
        isCentered: Boolean,
        showThumbnail: Boolean,
        showWatermark: Boolean,
        customLyricsStyle: LyricsStyleConfig? = null
    ): Bitmap {
        val width = format.width
        val height = format.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val palette = MOCK_PALETTES[coverIndex.coerceAtLeast(0) % MOCK_PALETTES.size]
        val cPrimary = palette.primary.hashCode()
        val cSecondary = palette.secondary.hashCode()
        val cAmbient = palette.ambient.hashCode()

        // 1. Draw Background
        when (background) {
            PosterBackground.SOLID -> {
                canvas.drawColor(Color.parseColor("#08090B"))
            }
            PosterBackground.GRADIENT -> {
                val gradient = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(cPrimary, cSecondary, Color.parseColor("#08090B")),
                    null,
                    Shader.TileMode.CLAMP
                )
                val paint = Paint().apply { shader = gradient }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
            PosterBackground.BLUR, PosterBackground.GLASS -> {
                // Layered radial tint simulating blurred glass
                canvas.drawColor(Color.parseColor("#0B0D12"))
                val glowPaint = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, width.toFloat(), height.toFloat(),
                        intArrayOf(cAmbient, Color.parseColor("#12141A"), Color.parseColor("#08090B")),
                        null,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)

                if (background == PosterBackground.GLASS) {
                    // Glass inner frame with specular rim
                    val framePaint = Paint().apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 3f
                        color = Color.parseColor("#26FFFFFF")
                    }
                    val margin = 50f
                    canvas.drawRoundRect(RectF(margin, margin, width - margin, height - margin), 40f, 40f, framePaint)
                }
            }
        }

        // 2. Resolve Typeface (applies Part 5 LyricsStyleConfig bundled font when set)
        val typeface = if (customLyricsStyle != null) {
            val useBold = isBold || customLyricsStyle.weight == LyricsFontWeightOption.BOLD ||
                customLyricsStyle.weight == LyricsFontWeightOption.BLACK
            customLyricsStyle.fontFamily.loadAndroidTypeface(
                context = context,
                isBold = useBold,
                isItalic = customLyricsStyle.isItalic
            )
        } else {
            val baseTypeface = when (font) {
                PosterFont.EDITORIAL, PosterFont.SERIF -> Typeface.SERIF
                PosterFont.ROUNDED -> Typeface.DEFAULT
                PosterFont.GROTESK -> Typeface.SANS_SERIF
            }
            val typefaceStyle = if (isBold) Typeface.BOLD else Typeface.NORMAL
            Typeface.create(baseTypeface, typefaceStyle)
        }

        // 3. Draw Track Header / Thumbnail
        val contentPadding = 100f
        var currentY = 120f

        if (showThumbnail) {
            val thumbSize = 130f
            val thumbPaint = Paint().apply {
                color = cPrimary
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(RectF(contentPadding, currentY, contentPadding + thumbSize, currentY + thumbSize), 24f, 24f, thumbPaint)

            // Header metadata next to thumb
            val metaPaint = TextPaint().apply {
                color = Color.parseColor("#F4F5F7")
                textSize = 38f
                this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText(trackTitle, contentPadding + thumbSize + 34f, currentY + 54f, metaPaint)

            metaPaint.apply {
                color = Color.parseColor("#858B96")
                textSize = 28f
                this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            canvas.drawText(trackArtist, contentPadding + thumbSize + 34f, currentY + 100f, metaPaint)
            currentY += thumbSize + 70f
        } else {
            val metaPaint = TextPaint().apply {
                color = Color.parseColor("#858B96")
                textSize = 30f
                letterSpacing = 0.08f
                this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("$trackTitle — $trackArtist".uppercase(), contentPadding, currentY + 40f, metaPaint)
            currentY += 100f
        }

        // 4. Draw Lyric Lines
        val availableWidth = (width - (contentPadding * 2)).toInt()
        val textPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = if (format == PosterFormat.STORY) 56f else 50f
            this.typeface = typeface
            isAntiAlias = true
        }

        val alignment = if (isCentered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
        val lyricsBlock = lines.joinToString("\n\n")

        val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(lyricsBlock, 0, lyricsBlock.length, textPaint, availableWidth)
                .setAlignment(alignment)
                .setLineSpacing(14f, 1.2f)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(lyricsBlock, textPaint, availableWidth, alignment, 1.2f, 14f, true)
        }

        canvas.save()
        val textY = if (format == PosterFormat.STORY) {
            (height * 0.35f)
        } else {
            currentY + 40f
        }
        canvas.translate(contentPadding, textY)
        staticLayout.draw(canvas)
        canvas.restore()

        // 5. Draw Watermark & Brand Footer
        if (showWatermark) {
            val footerPaint = TextPaint().apply {
                color = Color.parseColor("#6A717E")
                textSize = 28f
                letterSpacing = 0.15f
                this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            val footerY = height - 90f
            canvas.drawText("REZONA · PURE RESONANCE", contentPadding, footerY, footerPaint)
        }

        return bitmap
    }

    fun sharePoster(context: Context, bitmap: Bitmap) {
        try {
            val cachePath = File(context.cacheDir, "posters")
            cachePath.mkdirs()
            val file = File(cachePath, "rezona_lyrics_poster_${System.currentTimeMillis()}.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()
            stream.close()

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Rezona Poster").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Throwable) {
            // fallback
        }
    }

    fun saveToGallery(context: Context, bitmap: Bitmap): Boolean {
        return try {
            val filename = "Rezona_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Rezona")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return false
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                true
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val rezonaDir = File(picturesDir, "Rezona").apply { mkdirs() }
                val file = File(rezonaDir, filename)
                FileOutputStream(file).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun shareText(context: Context, lines: List<String>, trackTitle: String, trackArtist: String) {
        try {
            val text = buildString {
                lines.forEach { appendLine("\"$it\"") }
                appendLine()
                append("— $trackTitle by $trackArtist (via Rezona)")
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, "Share Lyrics").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Throwable) {
            // fallback
        }
    }
}
