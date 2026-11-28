package com.nayama.music.ui.lyrics

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.nayama.music.R
import com.nayama.music.model.RezonaTrack
import com.nayama.music.ui.util.MOCK_PALETTES
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class PosterFormat(val width: Int, val height: Int, val label: String) {
    SQUARE(1080, 1080, "1:1"),
    PORTRAIT_4_5(1080, 1350, "4:5"),
    STORY(1080, 1920, "9:16"),
    LANDSCAPE_16_9(1280, 720, "16:9")
}

enum class PosterStyleMode(val label: String) {
    FOCUS("Fokus"),
    LYRICS("Lirik"),
    PLAYER_CARD("Kartu Pemutar")
}

enum class PosterBackground(val label: String) {
    GLASS("Kaca"),
    SOLID("Polos"),
    BLUR("Buram"),
    GRADIENT("Gradasi")
}

enum class PosterFont(val label: String) {
    EDITORIAL("Editorial"),
    GROTESK("Grotesk"),
    ROUNDED("Rounded"),
    SERIF("Serif")
}

object PosterRenderer {

    @Volatile
    private var cachedRezonaLogo: Bitmap? = null

    fun getRezonaLogoBitmap(context: Context): Bitmap? {
        cachedRezonaLogo?.let { if (!it.isRecycled) return it }
        return runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_rezona_logo)?.also {
                cachedRezonaLogo = it
            }
        }.getOrNull()
    }

    /**
     * Reliably loads a track's cover art bitmap using:
     * 1. Coil disk/memory cache & loader
     * 2. YouTube fallback thumbnail URL (`https://i.ytimg.com/vi/<id>/hqdefault.jpg`) if coverArtUrl is blank
     * 3. Direct HttpURLConnection with browser User-Agent
     * 4. Styled Rezona R logo fallback so the song icon NEVER renders as an empty box!
     */
    suspend fun loadTrackCoverBitmap(context: Context, track: RezonaTrack): Bitmap? {
        val ytId = track.id.removePrefix("yt_").takeIf { it.length in 8..16 && !it.contains(" ") }
        val candidates = buildList {
            if (track.coverArtUrl.isNotBlank()) add(track.coverArtUrl)
            if (ytId != null) {
                add("https://i.ytimg.com/vi/$ytId/hqdefault.jpg")
                add("https://i.ytimg.com/vi/$ytId/mqdefault.jpg")
            }
        }.distinct()

        for (url in candidates) {
            val fromCoil = runCatching {
                val req = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(480, 480)
                    .build()
                val res = context.imageLoader.execute(req)
                (res as? SuccessResult)?.drawable?.toBitmap(480, 480, Bitmap.Config.ARGB_8888)
            }.getOrNull()
            if (fromCoil != null) return fromCoil

            val fromHttp = runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0")
                }
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            if (fromHttp != null) return fromHttp
        }
        return getRezonaLogoBitmap(context)
    }

    /**
     * Draws a sleek rounded Rezona watermark badge featuring the official new Rezona R icon + text.
     */
    fun drawRezonaBrandWatermark(
        context: Context,
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        pillHeight: Float,
        label: String = "Rezona"
    ) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = pillHeight * 0.46f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.04f
        }
        val textW = textPaint.measureText(label)
        val iconSize = pillHeight * 0.68f
        val gap = pillHeight * 0.22f
        val padH = pillHeight * 0.42f
        val pillW = padH * 2f + iconSize + gap + textW

        val rect = RectF(
            centerX - pillW * 0.5f,
            centerY - pillHeight * 0.5f,
            centerX + pillW * 0.5f,
            centerY + pillHeight * 0.5f
        )

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(145, 14, 18, 26)
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            color = Color.argb(110, 255, 255, 255)
        }
        canvas.drawRoundRect(rect, pillHeight * 0.5f, pillHeight * 0.5f, bgPaint)
        canvas.drawRoundRect(rect, pillHeight * 0.5f, pillHeight * 0.5f, borderPaint)

        // Draw new Rezona R logo icon inside circular/rounded badge
        val iconLeft = rect.left + padH
        val iconTop = centerY - iconSize * 0.5f
        val iconRect = RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
        val logoBmp = getRezonaLogoBitmap(context)
        if (logoBmp != null && !logoBmp.isRecycled) {
            val save = canvas.save()
            val clip = Path().apply { addRoundRect(iconRect, iconSize * 0.26f, iconSize * 0.26f, Path.Direction.CW) }
            canvas.clipPath(clip)
            canvas.drawBitmap(logoBmp, null, iconRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            canvas.restoreToCount(save)
        }

        val textX = iconRect.right + gap
        val textY = centerY - (textPaint.descent() + textPaint.ascent()) * 0.5f
        canvas.drawText(label, textX, textY, textPaint)
    }

    /**
     * Draws a track cover thumbnail into [thumbRect]; if [coverArtBitmap] is null, draws the Rezona logo artwork
     * so the song icon is ALWAYS visible.
     */
    private fun drawCoverOrFallbackLogo(
        context: Context,
        canvas: Canvas,
        thumbRect: RectF,
        cornerRadius: Float,
        coverArtBitmap: Bitmap?,
        cPrimary: Int
    ) {
        val bmp = coverArtBitmap?.takeIf { !it.isRecycled } ?: getRezonaLogoBitmap(context)
        if (bmp != null && !bmp.isRecycled) {
            val save = canvas.save()
            val path = Path().apply { addRoundRect(thumbRect, cornerRadius, cornerRadius, Path.Direction.CW) }
            canvas.clipPath(path)
            canvas.drawBitmap(bmp, null, thumbRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            canvas.restoreToCount(save)
        } else {
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cPrimary }
            canvas.drawRoundRect(thumbRect, cornerRadius, cornerRadius, tp)
        }
    }

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
        customLyricsStyle: LyricsStyleConfig? = null,
        styleMode: PosterStyleMode = PosterStyleMode.LYRICS,
        coverArtBitmap: Bitmap? = null,
        animProgress: Float = 0.42f,
        timingOffsetSec: Float = 0f
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
                canvas.drawColor(Color.parseColor("#0B0D12"))
                val glowPaint = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, width.toFloat(), height.toFloat(),
                        intArrayOf(cAmbient, Color.parseColor("#182030"), Color.parseColor("#08090B")),
                        null,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)

                if (background == PosterBackground.GLASS && styleMode != PosterStyleMode.PLAYER_CARD) {
                    val framePaint = Paint().apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 3f
                        color = Color.parseColor("#2AFFFFFF")
                    }
                    val margin = 48f
                    canvas.drawRoundRect(RectF(margin, margin, width - margin, height - margin), 40f, 40f, framePaint)
                }
            }
        }

        // 2. Resolve Typeface
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

        // 3. Branch by Style Mode: PLAYER_CARD ("Kartu Pemutar" iOS Control Center Card) vs FOCUS / LYRICS
        if (styleMode == PosterStyleMode.PLAYER_CARD) {
            drawIosPlayerCardPoster(
                context = context,
                canvas = canvas,
                width = width,
                height = height,
                lines = lines,
                trackTitle = trackTitle,
                trackArtist = trackArtist,
                cPrimary = cPrimary,
                typeface = typeface,
                coverArtBitmap = coverArtBitmap,
                animProgress = animProgress,
                showWatermark = showWatermark
            )
            return bitmap
        }

        // 3B. Standard / Focus Lyrics Poster
        val contentPadding = if (width >= 1200) 90f else 96f
        var currentY = if (height < 900) 76f else 118f

        if (showThumbnail) {
            val thumbSize = if (height < 900) 96f else 126f
            val thumbRect = RectF(contentPadding, currentY, contentPadding + thumbSize, currentY + thumbSize)
            drawCoverOrFallbackLogo(
                context = context,
                canvas = canvas,
                thumbRect = thumbRect,
                cornerRadius = 24f,
                coverArtBitmap = coverArtBitmap,
                cPrimary = cPrimary
            )

            val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F4F5F7")
                textSize = if (height < 900) 32f else 38f
                this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            canvas.drawText(trackTitle, contentPadding + thumbSize + 32f, currentY + thumbSize * 0.44f, metaPaint)

            metaPaint.apply {
                color = Color.parseColor("#9BA3B0")
                textSize = if (height < 900) 24f else 28f
                this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            canvas.drawText(trackArtist, contentPadding + thumbSize + 32f, currentY + thumbSize * 0.80f, metaPaint)
            currentY += thumbSize + (if (height < 900) 44f else 68f)
        } else {
            val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#9BA3B0")
                textSize = 28f
                letterSpacing = 0.08f
                this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            canvas.drawText("$trackTitle — $trackArtist".uppercase(), contentPadding, currentY + 38f, metaPaint)
            currentY += 92f
        }

        // 4. Draw Lyric Lines (with Focus highlight on active line according to animProgress)
        val availableWidth = (width - (contentPadding * 2)).toInt().coerceAtLeast(200)
        val baseTextSize = when {
            height < 900 -> 40f
            format == PosterFormat.STORY -> 54f
            else -> 48f
        }
        val activeLineIdx = if (lines.isEmpty()) 0 else (animProgress * lines.size).toInt().coerceIn(0, lines.lastIndex)

        var lineY = if (format == PosterFormat.STORY) (height * 0.30f).coerceAtLeast(currentY) else currentY + 24f
        val align = if (isCentered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL

        lines.forEachIndexed { idx, lineText ->
            val isFocusLine = styleMode == PosterStyleMode.FOCUS && idx == activeLineIdx
            val lineAlpha = when {
                styleMode != PosterStyleMode.FOCUS -> 255
                idx == activeLineIdx -> 255
                idx < activeLineIdx -> 135
                else -> 105
            }
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(lineAlpha, 255, 255, 255)
                textSize = if (isFocusLine) baseTextSize * 1.10f else baseTextSize
                this.typeface = typeface
            }
            val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(lineText, 0, lineText.length, textPaint, availableWidth)
                    .setAlignment(align)
                    .setLineSpacing(10f, 1.16f)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(lineText, textPaint, availableWidth, align, 1.16f, 10f, true)
            }
            canvas.save()
            canvas.translate(contentPadding, lineY)
            layout.draw(canvas)
            canvas.restore()
            lineY += layout.height + (if (height < 900) 24f else 36f)
        }

        // 5. Watermark & Brand Footer with Official New Rezona R Logo
        if (showWatermark) {
            val pillH = if (height < 900) 44f else 54f
            val footerCenterY = height - (if (height < 900) 52f else 84f)
            drawRezonaBrandWatermark(
                context = context,
                canvas = canvas,
                centerX = width * 0.5f,
                centerY = footerCenterY,
                pillHeight = pillH,
                label = "Rezona"
            )
        }

        return bitmap
    }

    private fun drawIosPlayerCardPoster(
        context: Context,
        canvas: Canvas,
        width: Int,
        height: Int,
        lines: List<String>,
        trackTitle: String,
        trackArtist: String,
        cPrimary: Int,
        typeface: Typeface,
        coverArtBitmap: Bitmap?,
        animProgress: Float,
        showWatermark: Boolean
    ) {
        val cardW = if (width > height) width * 0.68f else width * 0.84f
        val cardH = if (width > height) height * 0.84f else (cardW * 1.16f).coerceAtMost(height * 0.78f)
        val left = (width - cardW) * 0.5f
        val top = (height - cardH) * 0.5f
        val cardRect = RectF(left, top, left + cardW, top + cardH)
        val radius = cardW * 0.085f

        // Frosted Dark Glass Card Body
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                intArrayOf(
                    Color.argb(225, 36, 42, 54),
                    Color.argb(235, 20, 24, 32),
                    Color.argb(242, 14, 17, 24)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, radius, radius, cardPaint)

        // Specular Rim
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.2f
            shader = LinearGradient(
                cardRect.left, cardRect.top, cardRect.left, cardRect.bottom,
                intArrayOf(Color.argb(175, 255, 255, 255), Color.argb(45, 255, 255, 255)),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, radius, radius, rimPaint)

        val pad = cardW * 0.075f
        var curY = cardRect.top + pad

        // Header Row: Cover Art + Title/Artist + Mini Waveform Bars
        val thumbSize = cardW * 0.15f
        val thumbRect = RectF(cardRect.left + pad, curY, cardRect.left + pad + thumbSize, curY + thumbSize)
        drawCoverOrFallbackLogo(
            context = context,
            canvas = canvas,
            thumbRect = thumbRect,
            cornerRadius = 22f,
            coverArtBitmap = coverArtBitmap,
            cPrimary = cPrimary
        )

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = cardW * 0.044f
            this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 215, 222, 234)
            textSize = cardW * 0.033f
            this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val textStartX = thumbRect.right + cardW * 0.035f
        canvas.drawText(trackTitle.take(24), textStartX, curY + thumbSize * 0.46f, titlePaint)
        canvas.drawText(trackArtist.take(28), textStartX, curY + thumbSize * 0.80f, artistPaint)

        // Waveform equalizer bars on top-right
        val eqPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
        }
        val eqRight = cardRect.right - pad
        val eqCenterY = curY + thumbSize * 0.5f
        for (b in 0..4) {
            val bx = eqRight - (4 - b) * 11f
            val wave = kotlin.math.abs(kotlin.math.sin((animProgress * 6.28f) + b * 0.9f)).toFloat()
            val bh = 8f + wave * 18f
            canvas.drawLine(bx, eqCenterY - bh, bx, eqCenterY + bh, eqPaint)
        }

        curY += thumbSize + cardH * 0.06f

        // Center Lyrics Viewport inside the iOS Player Card (Word-by-word highlight feel)
        val lyricsBoxTop = curY
        val lyricsBoxBottom = cardRect.bottom - cardH * 0.33f
        val availW = (cardW - pad * 2f).toInt().coerceAtLeast(180)
        val displayLines = lines.ifEmpty { listOf(trackTitle) }.take(4)
        val activeIdx = (animProgress * displayLines.size).toInt().coerceIn(0, displayLines.lastIndex)

        var ly = lyricsBoxTop + (lyricsBoxBottom - lyricsBoxTop) * 0.12f
        displayLines.forEachIndexed { idx, line ->
            val isActive = idx == activeIdx
            val lp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isActive) Color.WHITE else Color.argb(115, 220, 226, 238)
                textSize = if (isActive) cardW * 0.056f else cardW * 0.046f
                this.typeface = typeface
            }
            val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(line, 0, line.length, lp, availW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(6f, 1.12f)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(line, lp, availW, Layout.Alignment.ALIGN_NORMAL, 1.12f, 6f, true)
            }
            if (ly + layout.height < lyricsBoxBottom) {
                canvas.save()
                canvas.translate(cardRect.left + pad, ly)
                layout.draw(canvas)
                canvas.restore()
                ly += layout.height + cardH * 0.028f
            }
        }

        // Bottom Controls inside the Card: Progress Bar + Transport + Volume + Rezona Pill
        val barY = cardRect.bottom - cardH * 0.26f
        val barLeft = cardRect.left + pad
        val barRight = cardRect.right - pad
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(75, 255, 255, 255)
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
        }
        val activeBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(barLeft, barY, barRight, barY, trackPaint)
        val fillX = barLeft + (barRight - barLeft) * animProgress.coerceIn(0.08f, 0.94f)
        canvas.drawLine(barLeft, barY, fillX, barY, activeBarPaint)

        // Timestamps
        val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(175, 220, 226, 238)
            textSize = cardW * 0.027f
            this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val totalSec = 215
        val curSec = (totalSec * animProgress).toInt().coerceIn(0, totalSec)
        val remSec = (totalSec - curSec).coerceAtLeast(0)
        canvas.drawText(String.format("%d:%02d", curSec / 60, curSec % 60), barLeft, barY + cardH * 0.036f, timePaint)
        timePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(String.format("-%d:%02d", remSec / 60, remSec % 60), barRight, barY + cardH * 0.036f, timePaint)

        // Transport Row: ◀◀    ||    ▶▶
        val ctrlY = barY + cardH * 0.095f
        val cx = cardRect.centerX()
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val barW = cardW * 0.015f
        val barH = cardW * 0.044f
        canvas.drawRoundRect(RectF(cx - barW * 1.4f, ctrlY - barH, cx - barW * 0.35f, ctrlY + barH), 4f, 4f, iconPaint)
        canvas.drawRoundRect(RectF(cx + barW * 0.35f, ctrlY - barH, cx + barW * 1.4f, ctrlY + barH), 4f, 4f, iconPaint)

        // Prev & Next double triangles
        val prevX = cx - cardW * 0.18f
        val nextX = cx + cardW * 0.18f
        val triS = cardW * 0.026f
        val prevPath = Path().apply {
            moveTo(prevX - triS, ctrlY)
            lineTo(prevX, ctrlY - triS * 0.85f)
            lineTo(prevX, ctrlY + triS * 0.85f)
            close()
            moveTo(prevX, ctrlY)
            lineTo(prevX + triS, ctrlY - triS * 0.85f)
            lineTo(prevX + triS, ctrlY + triS * 0.85f)
            close()
        }
        canvas.drawPath(prevPath, iconPaint)

        val nextPath = Path().apply {
            moveTo(nextX + triS, ctrlY)
            lineTo(nextX, ctrlY - triS * 0.85f)
            lineTo(nextX, ctrlY + triS * 0.85f)
            close()
            moveTo(nextX, ctrlY)
            lineTo(nextX - triS, ctrlY - triS * 0.85f)
            lineTo(nextX - triS, ctrlY + triS * 0.85f)
            close()
        }
        canvas.drawPath(nextPath, iconPaint)

        // Volume Slider Row
        val volY = ctrlY + cardH * 0.075f
        canvas.drawLine(barLeft + 26f, volY, barRight - 26f, volY, trackPaint)
        canvas.drawLine(barLeft + 26f, volY, barLeft + (barRight - barLeft) * 0.72f, volY, activeBarPaint)

        // Bottom Official Rezona R Logo Watermark Pill inside Card
        if (showWatermark) {
            val pillH = cardH * 0.052f
            val pillCenterY = cardRect.bottom - pad * 0.80f - pillH * 0.5f
            drawRezonaBrandWatermark(
                context = context,
                canvas = canvas,
                centerX = cx,
                centerY = pillCenterY,
                pillHeight = pillH,
                label = "Rezona"
            )
        }
    }

    /**
     * Exports an animated Karaoke Lyrics / iOS Player Card MP4 video (H.264 `.mp4`, 4 seconds @ 24fps)
     * and returns the saved File in cache/Movies.
     */
    fun exportLyricsVideoMp4(
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
        customLyricsStyle: LyricsStyleConfig?,
        styleMode: PosterStyleMode,
        coverArtBitmap: Bitmap?,
        timingOffsetSec: Float
    ): File? {
        // Scale down dimensions to even H.264 hardware encoder-friendly resolution (max 720p/1280p)
        val scale = if (maxOf(format.width, format.height) > 1280) 720f / minOf(format.width, format.height) else 1f
        val videoW = ((format.width * scale).toInt() / 16) * 16
        val videoH = ((format.height * scale).toInt() / 16) * 16
        val fps = 24
        val totalFrames = fps * 4 // 4-second smooth animated lyrics video

        val outDir = File(context.cacheDir, "posters").apply { mkdirs() }
        val outFile = File(outDir, "Rezona_Lyrics_Video_${System.currentTimeMillis()}.mp4")

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        return try {
            val mime = MediaFormat.MIMETYPE_VIDEO_AVC
            val mediaFormat = MediaFormat.createVideoFormat(mime, videoW, videoH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, 3_800_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            codec = MediaCodec.createEncoderByType(mime)
            codec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()
            var trackIndex = -1
            var muxerStarted = false

            for (frameIdx in 0 until totalFrames) {
                val progress = 0.12f + (frameIdx.toFloat() / totalFrames.toFloat()) * 0.82f
                val rawBmp = renderPosterBitmap(
                    context = context,
                    lines = lines,
                    trackTitle = trackTitle,
                    trackArtist = trackArtist,
                    coverIndex = coverIndex,
                    format = format,
                    background = background,
                    font = font,
                    isBold = isBold,
                    isCentered = isCentered,
                    showThumbnail = showThumbnail,
                    showWatermark = showWatermark,
                    customLyricsStyle = customLyricsStyle,
                    styleMode = styleMode,
                    coverArtBitmap = coverArtBitmap,
                    animProgress = progress,
                    timingOffsetSec = timingOffsetSec
                )
                val frameBmp = if (rawBmp.width != videoW || rawBmp.height != videoH) {
                    Bitmap.createScaledBitmap(rawBmp, videoW, videoH, true)
                } else rawBmp

                val yuv = bitmapToNV12(frameBmp, videoW, videoH)
                val inIdx = codec.dequeueInputBuffer(15_000L)
                if (inIdx >= 0) {
                    val inBuf = codec.getInputBuffer(inIdx)
                    inBuf?.clear()
                    inBuf?.put(yuv)
                    val ptsUs = (frameIdx * 1_000_000L) / fps
                    codec.queueInputBuffer(inIdx, 0, yuv.size, ptsUs, 0)
                }

                drainCodec(codec, muxer, bufferInfo, trackIndex, muxerStarted) { newTrackIdx, started ->
                    trackIndex = newTrackIdx
                    muxerStarted = started
                }
            }

            val eosIn = codec.dequeueInputBuffer(15_000L)
            if (eosIn >= 0) {
                codec.queueInputBuffer(eosIn, 0, 0, (totalFrames * 1_000_000L) / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drainCodec(codec, muxer, bufferInfo, trackIndex, muxerStarted, awaitEos = true) { newTrackIdx, started ->
                trackIndex = newTrackIdx
                muxerStarted = started
            }

            // Also copy to Gallery Movies/Rezona
            saveVideoToGallery(context, outFile)
            outFile
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    private fun drainCodec(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        currentTrackIndex: Int,
        currentStarted: Boolean,
        awaitEos: Boolean = false,
        onStateUpdate: (Int, Boolean) -> Unit
    ) {
        var trackIdx = currentTrackIndex
        var started = currentStarted
        var loops = 0
        while (loops < 60) {
            loops++
            val outIdx = codec.dequeueOutputBuffer(bufferInfo, if (awaitEos) 10_000L else 0L)
            if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!awaitEos) break
            } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                trackIdx = muxer.addTrack(codec.outputFormat)
                muxer.start()
                started = true
                onStateUpdate(trackIdx, started)
            } else if (outIdx >= 0) {
                val encodedData = codec.getOutputBuffer(outIdx)
                if (encodedData != null && bufferInfo.size > 0 && started) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(trackIdx, encodedData, bufferInfo)
                }
                codec.releaseOutputBuffer(outIdx, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            }
        }
    }

    private fun bitmapToNV12(bmp: Bitmap, width: Int, height: Int): ByteArray {
        val argb = IntArray(width * height)
        bmp.getPixels(argb, 0, width, 0, 0, width, height)
        val yuv = ByteArray(width * height * 3 / 2)
        var yIndex = 0
        var uvIndex = width * height
        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = argb[j * width + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0 && uvIndex + 1 < yuv.size) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }

    private fun saveVideoToGallery(context: Context, videoFile: File): Boolean {
        return try {
            val filename = "Rezona_Lyrics_${System.currentTimeMillis()}.mp4"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Rezona")
                }
                val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    videoFile.inputStream().use { it.copyTo(out) }
                }
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun shareVideoFile(context: Context, videoFile: File, targetPackage: String? = null) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                videoFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (!targetPackage.isNullOrBlank()) {
                    setPackage(targetPackage)
                }
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Video Lirik Rezona").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Throwable) {
            if (targetPackage != null) {
                shareVideoFile(context, videoFile, targetPackage = null)
            }
        }
    }

    fun sharePoster(context: Context, bitmap: Bitmap, targetPackage: String? = null) {
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
                if (!targetPackage.isNullOrBlank()) {
                    setPackage(targetPackage)
                }
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Poster Rezona").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Throwable) {
            if (targetPackage != null) {
                sharePoster(context, bitmap, targetPackage = null)
            }
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
