package com.nayama.music.ui.lyrics

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
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
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.nayama.music.R
import com.nayama.music.backend.MusicEngineRepository
import com.nayama.music.backend.innertube.InnerTubeFilter
import com.nayama.music.data.repository.RezonaLibraryRepository
import com.nayama.music.data.repository.toRezonaTrack
import com.nayama.music.model.RezonaTrack
import com.nayama.music.ui.components.RezonaActionDisc
import com.nayama.music.ui.components.RezonaAsyncCoverArt
import com.nayama.music.ui.components.RezonaGlassButton
import com.nayama.music.ui.components.RezonaGlassCard
import com.nayama.music.ui.components.RezonaGlassPill
import com.nayama.music.ui.components.RezonaGlassSlider
import com.nayama.music.ui.components.RezonaGlassTextField
import com.nayama.music.ui.home.REAL_POPULAR_SEEDS
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.SpecularCardBorder
import com.nayama.music.ui.theme.Typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 7 Distinct 3D Floating Song Poster Layouts:
 * - Includes the Halo Twin Behind-Subject layout (Video 3), Halo Orbit 5 (Photo 1),
 *   3D Tilt Quad (Photo 2), Sky & Ground 3D (Photo 3), Mirror Duo (User screenshot),
 *   Liquid Prism Arc, and iOS Control Stack.
 */
enum class FloatingPosterLayout(val title: String, val subtitle: String, val slotCount: Int) {
    HALO_WINGS_2("Halo Wings (2 Cards)", "2 large 3D glass cards behind subject shoulders", 2),
    MIRROR_DUO_2("Mirror Duo (Top/Bottom)", "Top-left & bottom-right tilted 3D cards", 2),
    HALO_ORBIT_5("Halo Orbit (5 Cards)", "5 glowing glass cards + blurred depth mini-cards", 5),
    TILT_QUAD_3D("3D Tilt Quad (4 Cards)", "4 dramatic inward 3D perspective corner cards", 4),
    SKY_GROUND_3D("Sky & Ground 3D", "2 upper floating cards + 2 ground-tilted 3D cards", 4),
    PRISM_ARC_5("Liquid Prism Arc", "5 crystal cards in a curved 3D spiral arc", 5),
    IOS_STACK_4("iOS Control Stack", "4 angled iOS Control Center translucent music decks", 4)
}

enum class FloatingCardTheme(val label: String) {
    REZONA_HALO_GLASS("Rezona Halo Glass"),
    GOLDEN_GLASS("Golden Glass Glow"),
    IOS_DARK("iOS Dark Frosted"),
    REZONA_CRYSTAL("Rezona Silver Frost")
}

/**
 * Subject Overlap Mode ("mau nimpa foto gw atau gk"):
 * - BEHIND_SUBJECT: Auto-cuts center subject foreground so floating cards sit BEHIND your body/shoulders (like Video 3!)
 * - SAFE_FRAME: Pushes cards outward so they never overlap the center person
 * - FRONT_OVERLAP: Floating cards render in front of the photo
 */
enum class PosterSubjectOverlapMode(val label: String) {
    BEHIND_SUBJECT("Di Belakang Foto (Auto-Cutout)"),
    SAFE_FRAME("Anti-Nimpa (Pinggir)"),
    FRONT_OVERLAP("Di Depan Foto")
}

enum class PosterTouchDragMode(val label: String) {
    DRAG_CARDS("Geser Kartu Lagu"),
    PAN_PHOTO("Geser Posisi Foto")
}

data class Card3DPose(
    val centerXFrac: Float,
    val centerYFrac: Float,
    val scale: Float,
    val rotXDeg: Float,
    val rotYDeg: Float,
    val rotZDeg: Float,
    val isBlurredMini: Boolean = false
)

/**
 * Rezona 3D Floating Song & Live Video Poster Studio (V2):
 * - Fixed card button bounds: all transport buttons (`|<`, `||`, `>|`) & volume bar stay 100% inside the card.
 * - Interactive Drag on Canvas: Drag any floating song card OR pan/zoom your photo directly on the preview!
 * - Overlap Mode Toggle ("mau nimpa foto gw atau gk"):
 *   1) Di Belakang Foto (Auto-Cutout — cards tuck behind your shoulders/head like the viral video!)
 *   2) Anti-Nimpa (Safe Frame — cards frame the outer edges)
 *   3) Di Depan Foto (Front)
 * - Live 3D Motion Mode (`Live 3D Gerak`): Real-time 3D floating oscillation + export as real `.mp4` video or `.png`!
 */
@Composable
fun RezonaFloatingSongPosterStudio(
    initialTrack: RezonaTrack?,
    queueTracks: List<RezonaTrack> = emptyList(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val musicEngine = remember { MusicEngineRepository.getInstance(context) }
    val libraryRepo = remember { RezonaLibraryRepository.getInstance(context) }
    val likedEntities by libraryRepo.getLikedTracks().collectAsState(initial = emptyList())

    val floatingTracks = remember {
        val seeds = buildList {
            if (initialTrack != null) add(initialTrack)
            addAll(queueTracks)
            addAll(REAL_POPULAR_SEEDS)
        }.distinctBy { it.id }.take(5)
        mutableStateListOf<RezonaTrack>().apply { addAll(seeds) }
    }

    LaunchedEffect(likedEntities) {
        if (floatingTracks.size < 5) {
            val liked = likedEntities.map { it.toRezonaTrack() }
            for (t in liked) {
                if (floatingTracks.none { it.id == t.id } && floatingTracks.size < 5) {
                    floatingTracks.add(t)
                }
            }
        }
    }

    var selectedLayout by remember { mutableStateOf(FloatingPosterLayout.HALO_WINGS_2) }
    var selectedTheme by remember { mutableStateOf(FloatingCardTheme.REZONA_HALO_GLASS) }
    var overlapMode by remember { mutableStateOf(PosterSubjectOverlapMode.BEHIND_SUBJECT) }
    var dragMode by remember { mutableStateOf(PosterTouchDragMode.DRAG_CARDS) }
    var isLive3dMotion by remember { mutableStateOf(true) }
    var cardSizeScale by remember { mutableFloatStateOf(1.0f) }
    var photoZoomScale by remember { mutableFloatStateOf(1.0f) }
    var photoPanXFrac by remember { mutableFloatStateOf(0f) }
    var photoPanYFrac by remember { mutableFloatStateOf(0f) }

    // Per-card user drag offsets (normalized -0.45..+0.45 of canvas width/height)
    val cardOffsetsX = remember { mutableStateMapOf<Int, Float>() }
    val cardOffsetsY = remember { mutableStateMapOf<Int, Float>() }

    var activeSlotIndex by remember { mutableIntStateOf(0) }
    var userPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var presetBackdropIndex by remember { mutableIntStateOf(0) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<RezonaTrack>>(emptyList()) }
    var isExportingVideo by remember { mutableStateOf(false) }

    val coverBitmaps = remember { mutableStateMapOf<String, Bitmap>() }
    var renderTrigger by remember { mutableIntStateOf(0) }

    // Live 3D animation phase (radians) when isLive3dMotion is ON
    var animPhaseRad by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isLive3dMotion) {
        if (!isLive3dMotion) {
            animPhaseRad = 0f
            return@LaunchedEffect
        }
        var startNanos = 0L
        var lastRenderMs = 0L
        while (isActive && isLive3dMotion) {
            withFrameNanos { now ->
                if (startNanos == 0L) startNanos = now
                val elapsedMs = (now - startNanos) / 1_000_000L
                // Throttle canvas bitmap re-render to ~20fps for ultra-lightweight battery & zero UI lag
                if (elapsedMs - lastRenderMs >= 50L) {
                    lastRenderMs = elapsedMs
                    animPhaseRad = (elapsedMs / 1000f) * 2.1f
                }
            }
        }
    }

    val logoBitmap = remember { PosterRenderer.getRezonaLogoBitmap(context) }

    LaunchedEffect(floatingTracks.toList()) {
        for (track in floatingTracks) {
            val key = track.coverArtUrl.ifBlank { track.id }
            if (!coverBitmaps.containsKey(key)) {
                val bmp = withContext(Dispatchers.IO) {
                    PosterRenderer.loadTrackCoverBitmap(context, track)
                }
                if (bmp != null) {
                    coverBitmaps[key] = bmp
                    if (track.coverArtUrl.isNotBlank()) {
                        coverBitmaps[track.coverArtUrl] = bmp
                    }
                    renderTrigger++
                }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val decoded = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }.getOrNull()
                if (decoded != null) {
                    withContext(Dispatchers.Main) {
                        userPhotoBitmap = decoded
                        photoPanXFrac = 0f
                        photoPanYFrac = 0f
                        photoZoomScale = 1f
                        renderTrigger++
                    }
                }
            }
        }
    }

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(
        selectedLayout,
        selectedTheme,
        overlapMode,
        cardSizeScale,
        photoZoomScale,
        photoPanXFrac,
        photoPanYFrac,
        cardOffsetsX.toMap(),
        cardOffsetsY.toMap(),
        userPhotoBitmap,
        presetBackdropIndex,
        floatingTracks.toList(),
        renderTrigger,
        animPhaseRad
    ) {
        val tracksSnapshot = floatingTracks.toList()
        val coversSnapshot = coverBitmaps.toMap()
        val offX = cardOffsetsX.toMap()
        val offY = cardOffsetsY.toMap()
        val phase = animPhaseRad
        val rendered = withContext(Dispatchers.Default) {
            FloatingSongPosterRenderer.render3dPoster(
                width = 540,
                height = 960,
                userPhoto = userPhotoBitmap,
                presetBackdropIndex = presetBackdropIndex,
                layout = selectedLayout,
                theme = selectedTheme,
                overlapMode = overlapMode,
                cardSizeScale = cardSizeScale,
                photoZoomScale = photoZoomScale,
                photoPanXFrac = photoPanXFrac,
                photoPanYFrac = photoPanYFrac,
                cardOffsetsX = offX,
                cardOffsetsY = offY,
                animPhaseRad = phase,
                tracks = tracksSnapshot,
                coverBitmaps = coversSnapshot,
                logoBitmap = logoBitmap
            )
        }
        previewBitmap = rendered
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06080C))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LIVE 3D SONG POSTER STUDIO",
                        style = Typography.titleLarge.copy(
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = PureWhite
                    )
                    Text(
                        text = "Sentuh & geser langsung di gambar untuk atur posisi kartu / foto",
                        style = Typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = SoftSilver
                    )
                }
                RezonaGlassPill(
                    isActive = isLive3dMotion,
                    onClick = { isLive3dMotion = !isLive3dMotion }
                ) {
                    Text(
                        text = if (isLive3dMotion) "Live 3D: ON" else "Live 3D: OFF",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isLive3dMotion) Color(0xFF66FFAA) else SoftSilver
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                RezonaActionDisc(size = 36.dp, onClick = onDismiss) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close",
                        tint = PureWhite,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── 1. INTERACTIVE TOUCH-DRAGGABLE 9:16 LIVE 3D PREVIEW ─────────
                item(key = "poster_3d_preview") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .aspectRatio(9f / 16f)
                                .shadow(24.dp, RoundedCornerShape(24.dp))
                                .clip(RoundedCornerShape(24.dp))
                                .border(1.dp, SpecularCardBorder, RoundedCornerShape(24.dp))
                                .pointerInput(selectedLayout, dragMode, overlapMode) {
                                    detectTapGestures { tapOffset ->
                                        if (dragMode == PosterTouchDragMode.DRAG_CARDS) {
                                            val tx = tapOffset.x / size.width.toFloat().coerceAtLeast(1f)
                                            val ty = tapOffset.y / size.height.toFloat().coerceAtLeast(1f)
                                            val poses = FloatingSongPosterRenderer.getMainPoses(selectedLayout, overlapMode)
                                            var bestIdx = activeSlotIndex
                                            var bestDist = Float.MAX_VALUE
                                            poses.forEachIndexed { idx, p ->
                                                val cx = (p.centerXFrac + (cardOffsetsX[idx] ?: 0f)).coerceIn(0.08f, 0.92f)
                                                val cy = (p.centerYFrac + (cardOffsetsY[idx] ?: 0f)).coerceIn(0.08f, 0.92f)
                                                val d = hypot(tx - cx, ty - cy)
                                                if (d < bestDist) {
                                                    bestDist = d
                                                    bestIdx = idx
                                                }
                                            }
                                            activeSlotIndex = bestIdx.coerceIn(0, selectedLayout.slotCount - 1)
                                        }
                                    }
                                }
                                .pointerInput(selectedLayout, dragMode, activeSlotIndex) {
                                    detectDragGestures(
                                        onDragStart = { startOffset ->
                                            if (dragMode == PosterTouchDragMode.DRAG_CARDS) {
                                                val tx = startOffset.x / size.width.toFloat().coerceAtLeast(1f)
                                                val ty = startOffset.y / size.height.toFloat().coerceAtLeast(1f)
                                                val poses = FloatingSongPosterRenderer.getMainPoses(selectedLayout, overlapMode)
                                                var bestIdx = activeSlotIndex
                                                var bestDist = Float.MAX_VALUE
                                                poses.forEachIndexed { idx, p ->
                                                    val cx = (p.centerXFrac + (cardOffsetsX[idx] ?: 0f)).coerceIn(0.08f, 0.92f)
                                                    val cy = (p.centerYFrac + (cardOffsetsY[idx] ?: 0f)).coerceIn(0.08f, 0.92f)
                                                    val d = hypot(tx - cx, ty - cy)
                                                    if (d < bestDist) {
                                                        bestDist = d
                                                        bestIdx = idx
                                                    }
                                                }
                                                activeSlotIndex = bestIdx.coerceIn(0, selectedLayout.slotCount - 1)
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val dxFrac = dragAmount.x / size.width.toFloat().coerceAtLeast(1f)
                                            val dyFrac = dragAmount.y / size.height.toFloat().coerceAtLeast(1f)
                                            if (dragMode == PosterTouchDragMode.DRAG_CARDS) {
                                                val curX = cardOffsetsX[activeSlotIndex] ?: 0f
                                                val curY = cardOffsetsY[activeSlotIndex] ?: 0f
                                                cardOffsetsX[activeSlotIndex] = (curX + dxFrac).coerceIn(-0.45f, 0.45f)
                                                cardOffsetsY[activeSlotIndex] = (curY + dyFrac).coerceIn(-0.45f, 0.45f)
                                            } else {
                                                photoPanXFrac = (photoPanXFrac + dxFrac).coerceIn(-0.6f, 0.6f)
                                                photoPanYFrac = (photoPanYFrac + dyFrac).coerceIn(-0.6f, 0.6f)
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val bmp = previewBitmap
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "3D Song Poster Preview",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CircularProgressIndicator(color = PureWhite)
                            }
                        }

                        // Drag Mode & Reset Position Controls right below preview
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PosterTouchDragMode.entries.forEach { mode ->
                                val selected = dragMode == mode
                                RezonaGlassPill(
                                    isActive = selected,
                                    onClick = { dragMode = mode },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = mode.label,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (selected) PureWhite else SoftSilver,
                                        maxLines = 1
                                    )
                                }
                            }
                            RezonaGlassPill(
                                isActive = false,
                                onClick = {
                                    cardOffsetsX.clear()
                                    cardOffsetsY.clear()
                                    photoPanXFrac = 0f
                                    photoPanYFrac = 0f
                                    photoZoomScale = 1f
                                    cardSizeScale = 1f
                                }
                            ) {
                                Text("Reset", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = PearlWhite)
                            }
                        }
                    }
                }

                // ── 2. OVERLAP MODE ("MAU NIMPA FOTO GW ATAU GAK") ─────────────
                item(key = "poster_overlap_mode") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "POSISI KARTU TERHADAP FOTO KAMU",
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                            color = SoftSilver
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(PosterSubjectOverlapMode.entries) { mode ->
                                val selected = overlapMode == mode
                                RezonaGlassPill(
                                    isActive = selected,
                                    onClick = { overlapMode = mode }
                                ) {
                                    Text(
                                        text = mode.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (selected) PureWhite else SoftSilver
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 3. CARD SIZE & PHOTO ZOOM SLIDERS ──────────────────────────
                item(key = "poster_size_sliders") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ukuran Kartu Lagu 3D", style = Typography.bodySmall, color = PearlWhite)
                            Text("${(cardSizeScale * 100).toInt()}%", style = Typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                        }
                        RezonaGlassSlider(
                            value = cardSizeScale,
                            onValueChange = { cardSizeScale = it.coerceIn(0.65f, 1.30f) },
                            valueRange = 0.65f..1.30f
                        )
                        if (userPhotoBitmap != null) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Zoom Foto Kamu", style = Typography.bodySmall, color = PearlWhite)
                                Text("${(photoZoomScale * 100).toInt()}%", style = Typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = PureWhite)
                            }
                            RezonaGlassSlider(
                                value = photoZoomScale,
                                onValueChange = { photoZoomScale = it.coerceIn(0.8f, 2.2f) },
                                valueRange = 0.8f..2.2f
                            )
                        }
                    }
                }

                // ── 4. ADD YOUR PHOTO FROM GALLERY OR SWITCH BACKDROP ──────────
                item(key = "poster_photo_picker") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "FOTO LATAR (FOTO KAMU)",
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                            color = SoftSilver
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RezonaGlassButton(
                                text = if (userPhotoBitmap != null) "Ganti Foto Kamu" else "+ Pilih Foto Kamu (Galeri)",
                                isPrimary = true,
                                onClick = { photoPickerLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f)
                            )
                            RezonaGlassButton(
                                text = if (userPhotoBitmap != null) "Hapus Foto" else "Ganti Latar",
                                isPrimary = false,
                                onClick = {
                                    if (userPhotoBitmap != null) {
                                        userPhotoBitmap = null
                                    } else {
                                        presetBackdropIndex = (presetBackdropIndex + 1) % 4
                                    }
                                }
                            )
                        }
                    }
                }

                // ── 5. CHOOSE 3D LAYOUT DESIGN (7 TEMPLATES) ───────────────────
                item(key = "poster_3d_layouts") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "DESAIN TATA LETAK 3D (${FloatingPosterLayout.entries.size} GAYA)",
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                            color = SoftSilver
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(FloatingPosterLayout.entries) { layout ->
                                val isSelected = selectedLayout == layout
                                RezonaGlassPill(
                                    isActive = isSelected,
                                    onClick = {
                                        selectedLayout = layout
                                        cardOffsetsX.clear()
                                        cardOffsetsY.clear()
                                        if (activeSlotIndex >= layout.slotCount) {
                                            activeSlotIndex = 0
                                        }
                                    }
                                ) {
                                    Text(
                                        text = layout.title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) PureWhite else SoftSilver
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 6. CARD GLASS THEME SWITCHER ───────────────────────────────
                item(key = "poster_card_theme") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "GAYA KARTU KACA 3D",
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                            color = SoftSilver
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(FloatingCardTheme.entries) { theme ->
                                val isSelected = selectedTheme == theme
                                RezonaGlassPill(
                                    isActive = isSelected,
                                    onClick = { selectedTheme = theme }
                                ) {
                                    Text(
                                        text = theme.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) PureWhite else SoftSilver
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 7. CUSTOMIZE & SEARCH FLOATING SONGS (SLOT 1..N) ───────────
                item(key = "poster_floating_song_slots") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "GANTI & CARI LAGU MELAYANG (KLIK SLOT LALU SEARCH)",
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                            color = SoftSilver
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(floatingTracks.take(selectedLayout.slotCount)) { idx, slotTrack ->
                                val isSlotSelected = activeSlotIndex == idx
                                RezonaGlassPill(
                                    isActive = isSlotSelected,
                                    onClick = { activeSlotIndex = idx }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "#${idx + 1}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isSlotSelected) Color(0xFF66FFAA) else SoftSilver
                                        )
                                        Text(
                                            text = slotTrack.title.take(16),
                                            fontSize = 12.sp,
                                            fontWeight = if (isSlotSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = PureWhite,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                RezonaGlassTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = "Cari lagu untuk Kartu #${activeSlotIndex + 1}...",
                                    singleLine = true
                                )
                            }
                            RezonaGlassPill(
                                isActive = true,
                                onClick = {
                                    val q = searchQuery.trim()
                                    if (q.isNotEmpty()) {
                                        isSearching = true
                                        scope.launch {
                                            val found = runCatching {
                                                withContext(Dispatchers.IO) {
                                                    musicEngine.innerTubeRepo.search(q, InnerTubeFilter.SONGS)
                                                }
                                            }.getOrDefault(emptyList())
                                            searchResults = found
                                            isSearching = false
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    text = if (isSearching) "..." else "Cari",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PureWhite
                                )
                            }
                        }

                        if (searchResults.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                searchResults.take(5).forEach { resultTrack ->
                                    RezonaGlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        onClick = {
                                            if (activeSlotIndex in floatingTracks.indices) {
                                                floatingTracks[activeSlotIndex] = resultTrack
                                            } else {
                                                floatingTracks.add(resultTrack)
                                            }
                                            Toast.makeText(
                                                context,
                                                "Kartu #${activeSlotIndex + 1} -> ${resultTrack.title}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            if (activeSlotIndex + 1 < selectedLayout.slotCount) {
                                                activeSlotIndex += 1
                                            }
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RezonaAsyncCoverArt(
                                                coverArtUrl = resultTrack.coverArtUrl,
                                                fallbackIndex = resultTrack.index,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.size(38.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = resultTrack.title,
                                                    style = Typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = PureWhite,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = resultTrack.artist,
                                                    style = Typography.bodySmall,
                                                    color = SmokySilver,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = "Pakai di #${activeSlotIndex + 1}",
                                                style = Typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                color = Color(0xFF66FFAA)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Save Photo, Save Live 3D Video (MP4) & Share Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xF20A0C10))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RezonaGlassButton(
                    text = "Simpan Foto",
                    isPrimary = false,
                    onClick = {
                        scope.launch {
                            val hiRes = withContext(Dispatchers.Default) {
                                FloatingSongPosterRenderer.render3dPoster(
                                    width = 1080,
                                    height = 1920,
                                    userPhoto = userPhotoBitmap,
                                    presetBackdropIndex = presetBackdropIndex,
                                    layout = selectedLayout,
                                    theme = selectedTheme,
                                    overlapMode = overlapMode,
                                    cardSizeScale = cardSizeScale,
                                    photoZoomScale = photoZoomScale,
                                    photoPanXFrac = photoPanXFrac,
                                    photoPanYFrac = photoPanYFrac,
                                    cardOffsetsX = cardOffsetsX.toMap(),
                                    cardOffsetsY = cardOffsetsY.toMap(),
                                    animPhaseRad = 0f,
                                    tracks = floatingTracks.toList(),
                                    coverBitmaps = coverBitmaps.toMap()
                                )
                            }
                            val saved = PosterRenderer.saveToGallery(context, hiRes)
                            Toast.makeText(
                                context,
                                if (saved) "Poster 3D tersimpan di Galeri (Pictures/Rezona)" else "Gagal menyimpan",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                RezonaGlassButton(
                    text = if (isExportingVideo) "Merender MP4..." else "Buat Video 3D (MP4)",
                    isPrimary = true,
                    onClick = {
                        if (isExportingVideo) return@RezonaGlassButton
                        isExportingVideo = true
                        Toast.makeText(context, "Merender Video Live 3D Poster (6 detik)...", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            val videoFile = withContext(Dispatchers.Default) {
                                FloatingSongPosterRenderer.exportLive3dVideoMp4(
                                    context = context,
                                    userPhoto = userPhotoBitmap,
                                    presetBackdropIndex = presetBackdropIndex,
                                    layout = selectedLayout,
                                    theme = selectedTheme,
                                    overlapMode = overlapMode,
                                    cardSizeScale = cardSizeScale,
                                    photoZoomScale = photoZoomScale,
                                    photoPanXFrac = photoPanXFrac,
                                    photoPanYFrac = photoPanYFrac,
                                    cardOffsetsX = cardOffsetsX.toMap(),
                                    cardOffsetsY = cardOffsetsY.toMap(),
                                    tracks = floatingTracks.toList(),
                                    coverBitmaps = coverBitmaps.toMap()
                                )
                            }
                            isExportingVideo = false
                            if (videoFile != null) {
                                FloatingSongPosterRenderer.saveAndShareVideoMp4(context, videoFile)
                            } else {
                                Toast.makeText(context, "Gagal membuat video MP4 di perangkat ini", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1.3f)
                )
            }
        }
    }
}

object FloatingSongPosterRenderer {

    fun render3dPoster(
        width: Int,
        height: Int,
        userPhoto: Bitmap?,
        presetBackdropIndex: Int,
        layout: FloatingPosterLayout,
        theme: FloatingCardTheme,
        overlapMode: PosterSubjectOverlapMode = PosterSubjectOverlapMode.BEHIND_SUBJECT,
        cardSizeScale: Float = 1.0f,
        photoZoomScale: Float = 1.0f,
        photoPanXFrac: Float = 0f,
        photoPanYFrac: Float = 0f,
        cardOffsetsX: Map<Int, Float> = emptyMap(),
        cardOffsetsY: Map<Int, Float> = emptyMap(),
        animPhaseRad: Float = 0f,
        tracks: List<RezonaTrack>,
        coverBitmaps: Map<String, Bitmap>,
        logoBitmap: Bitmap? = null
    ): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // 1. Draw Background Photo (with user Pan & Zoom!) or Moody Studio Backdrop
        if (userPhoto != null && !userPhoto.isRecycled) {
            drawPannedZoomedBitmap(canvas, userPhoto, width, height, photoZoomScale, photoPanXFrac, photoPanYFrac)
            val vigPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(0x36000000, 0x08000000, 0x4E000000),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vigPaint)
        } else {
            val palettes = listOf(
                intArrayOf(0xFF282420.toInt(), 0xFF151311.toInt(), 0xFF080706.toInt()),
                intArrayOf(0xFF222E20.toInt(), 0xFF121A11.toInt(), 0xFF070A07.toInt()),
                intArrayOf(0xFF1C2330.toInt(), 0xFF0F141D.toInt(), 0xFF06080C.toInt()),
                intArrayOf(0xFF30202A.toInt(), 0xFF191016.toInt(), 0xFF080507.toInt())
            )
            val pal = palettes[presetBackdropIndex.coerceAtLeast(0) % palettes.size]
            val bgPaint = Paint().apply {
                shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), pal, null, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // Subtle upper spotlight beam like Video 3
            val beamPaint = Paint().apply {
                shader = RadialGradient(
                    width * 0.5f, height * 0.12f, width * 0.72f,
                    intArrayOf(0x44FFFFFF, 0x14FFFFFF, 0x00000000),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), beamPaint)
        }

        val safeTracks = tracks.ifEmpty { REAL_POPULAR_SEEDS }
        val allPoses = getPosesForLayout(layout, overlapMode)
        val cardBaseW = (width * 0.41f * cardSizeScale.coerceIn(0.60f, 1.35f)).coerceAtLeast(140f)
        val cardBaseH = cardBaseW * 1.24f

        // 2. First pass: Draw blurred background depth mini-cards
        for ((idx, pose) in allPoses.withIndex()) {
            if (!pose.isBlurredMini) continue
            val track = safeTracks[idx % safeTracks.size]
            val animatedPose = if (animPhaseRad != 0f) {
                val dy = sin(animPhaseRad + idx * 1.3f) * 0.012f
                val dRotZ = cos(animPhaseRad + idx * 1.1f) * 2.2f
                pose.copy(centerYFrac = pose.centerYFrac + dy, rotZDeg = pose.rotZDeg + dRotZ)
            } else pose

            val cardBmp = renderSingleFloatingCard(
                cardW = (cardBaseW * 0.62f).toInt().coerceAtLeast(100),
                cardH = (cardBaseH * 0.62f).toInt().coerceAtLeast(125),
                track = track,
                coverBmp = coverBitmaps[track.coverArtUrl] ?: coverBitmaps[track.id] ?: logoBitmap,
                theme = theme,
                animPhaseRad = animPhaseRad,
                isBlurredMini = true
            )
            drawBitmapWith3DPose(canvas, cardBmp, width, height, animatedPose)
            cardBmp.recycle()
        }

        // 3. Second pass: Draw main high-detail 3D perspective floating song cards (with user drag offsets & Live 3D motion!)
        var mainIdx = 0
        for (pose in allPoses) {
            if (pose.isBlurredMini) continue
            val slotIdx = mainIdx
            val track = safeTracks[slotIdx % safeTracks.size]
            mainIdx++

            val dragDx = cardOffsetsX[slotIdx] ?: 0f
            val dragDy = cardOffsetsY[slotIdx] ?: 0f
            val hoverDy = if (animPhaseRad != 0f) sin(animPhaseRad + slotIdx * 1.7f) * 0.014f else 0f
            val hoverRotY = if (animPhaseRad != 0f) cos(animPhaseRad + slotIdx * 1.4f) * 3.2f else 0f
            val hoverRotZ = if (animPhaseRad != 0f) sin(animPhaseRad * 0.8f + slotIdx) * 1.8f else 0f

            val effectivePose = pose.copy(
                centerXFrac = (pose.centerXFrac + dragDx).coerceIn(0.05f, 0.95f),
                centerYFrac = (pose.centerYFrac + dragDy + hoverDy).coerceIn(0.05f, 0.95f),
                rotYDeg = pose.rotYDeg + hoverRotY,
                rotZDeg = pose.rotZDeg + hoverRotZ
            )

            val cardBmp = renderSingleFloatingCard(
                cardW = cardBaseW.toInt().coerceAtLeast(160),
                cardH = cardBaseH.toInt().coerceAtLeast(200),
                track = track,
                coverBmp = coverBitmaps[track.coverArtUrl] ?: coverBitmaps[track.id] ?: logoBitmap,
                theme = theme,
                animPhaseRad = animPhaseRad,
                isBlurredMini = false
            )
            drawBitmapWith3DPose(canvas, cardBmp, width, height, effectivePose)
            cardBmp.recycle()
        }

        // 4. Third pass: If overlapMode == BEHIND_SUBJECT and userPhoto is set,
        // composite the center subject foreground silhouette ON TOP OF the cards so cards sit BEHIND the person (like Video 3!)
        if (overlapMode == PosterSubjectOverlapMode.BEHIND_SUBJECT && userPhoto != null && !userPhoto.isRecycled) {
            drawSubjectForegroundCutout(
                canvas = canvas,
                userPhoto = userPhoto,
                width = width,
                height = height,
                zoom = photoZoomScale,
                panXFrac = photoPanXFrac,
                panYFrac = photoPanYFrac
            )
        }

        // 5. Bottom subtle Rezona watermark pill with Official New Rezona R Logo
        drawRezonaWatermarkPill(canvas, width, height, logoBitmap)

        return out
    }

    fun getMainPoses(layout: FloatingPosterLayout, overlapMode: PosterSubjectOverlapMode): List<Card3DPose> =
        getPosesForLayout(layout, overlapMode).filter { !it.isBlurredMini }

    private fun getPosesForLayout(
        layout: FloatingPosterLayout,
        overlapMode: PosterSubjectOverlapMode
    ): List<Card3DPose> {
        val raw = when (layout) {
            FloatingPosterLayout.HALO_WINGS_2 -> listOf(
                // Video 3 exact pose: Left & Right floating cards flanking the center person's shoulders
                Card3DPose(0.24f, 0.45f, 0.96f, 4f, 18f, -6f),
                Card3DPose(0.76f, 0.45f, 0.96f, 4f, -18f, 6f)
            )
            FloatingPosterLayout.MIRROR_DUO_2 -> listOf(
                // User mirror selfie screenshot pose: Top-Left & Bottom-Right
                Card3DPose(0.27f, 0.25f, 0.94f, 5f, 16f, -7f),
                Card3DPose(0.73f, 0.69f, 0.94f, -5f, -16f, -5f)
            )
            FloatingPosterLayout.HALO_ORBIT_5 -> listOf(
                Card3DPose(0.25f, 0.26f, 0.86f, 4f, 14f, -4f),
                Card3DPose(0.76f, 0.25f, 0.80f, 4f, -15f, 5f),
                Card3DPose(0.22f, 0.57f, 0.84f, -3f, 16f, -3f),
                Card3DPose(0.78f, 0.58f, 0.82f, -3f, -16f, 6f),
                Card3DPose(0.45f, 0.84f, 0.84f, 6f, 4f, -2f),
                Card3DPose(0.18f, 0.08f, 0.48f, 8f, 12f, -14f, isBlurredMini = true),
                Card3DPose(0.50f, 0.10f, 0.42f, 6f, -10f, 12f, isBlurredMini = true),
                Card3DPose(0.90f, 0.41f, 0.44f, 0f, -18f, 15f, isBlurredMini = true),
                Card3DPose(0.84f, 0.88f, 0.48f, 8f, -14f, 10f, isBlurredMini = true)
            )
            FloatingPosterLayout.TILT_QUAD_3D -> listOf(
                Card3DPose(0.25f, 0.28f, 0.92f, 6f, 22f, -8f),
                Card3DPose(0.75f, 0.31f, 0.90f, 6f, -22f, 9f),
                Card3DPose(0.25f, 0.69f, 0.92f, -5f, 20f, 6f),
                Card3DPose(0.75f, 0.73f, 0.90f, -5f, -20f, -7f)
            )
            FloatingPosterLayout.SKY_GROUND_3D -> listOf(
                Card3DPose(0.23f, 0.32f, 0.92f, 10f, 18f, -4f),
                Card3DPose(0.78f, 0.32f, 0.92f, 10f, -20f, 8f),
                Card3DPose(0.25f, 0.79f, 0.88f, 32f, 14f, -22f),
                Card3DPose(0.72f, 0.74f, 0.90f, 34f, -16f, 24f)
            )
            FloatingPosterLayout.PRISM_ARC_5 -> listOf(
                Card3DPose(0.23f, 0.18f, 0.80f, 5f, 18f, -10f),
                Card3DPose(0.77f, 0.29f, 0.84f, 4f, -18f, 8f),
                Card3DPose(0.22f, 0.48f, 0.86f, 0f, 20f, -6f),
                Card3DPose(0.78f, 0.64f, 0.84f, -4f, -18f, 8f),
                Card3DPose(0.30f, 0.84f, 0.86f, 8f, 12f, -5f)
            )
            FloatingPosterLayout.IOS_STACK_4 -> listOf(
                Card3DPose(0.24f, 0.24f, 0.90f, 4f, 16f, -6f),
                Card3DPose(0.76f, 0.30f, 0.90f, 4f, -16f, 7f),
                Card3DPose(0.24f, 0.67f, 0.90f, -4f, 16f, -5f),
                Card3DPose(0.76f, 0.75f, 0.90f, -4f, -16f, 6f)
            )
        }

        // In SAFE_FRAME ("Anti-Nimpa") mode, push cards further toward the left/right edges so the center subject is 100% clear
        return if (overlapMode == PosterSubjectOverlapMode.SAFE_FRAME) {
            raw.map { p ->
                val pushedX = if (p.centerXFrac < 0.5f) {
                    (p.centerXFrac - 0.05f).coerceAtLeast(0.18f)
                } else {
                    (p.centerXFrac + 0.05f).coerceAtMost(0.82f)
                }
                p.copy(centerXFrac = pushedX, scale = p.scale * 0.90f)
            }
        } else {
            raw
        }
    }

    /**
     * Renders a single floating 3D song card with guaranteed internal bounds so NO buttons ever clip or overflow!
     */
    private fun renderSingleFloatingCard(
        cardW: Int,
        cardH: Int,
        track: RezonaTrack,
        coverBmp: Bitmap?,
        theme: FloatingCardTheme,
        animPhaseRad: Float = 0f,
        isBlurredMini: Boolean
    ): Bitmap {
        val pad = (cardW * 0.06f).coerceAtLeast(8f)
        val bmp = Bitmap.createBitmap(cardW, cardH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rect = RectF(pad * 0.5f, pad * 0.5f, cardW - pad * 0.5f, cardH - pad * 0.5f)
        val corner = cardW * 0.09f

        // 1. Card glass fill
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = when (theme) {
                FloatingCardTheme.REZONA_HALO_GLASS -> LinearGradient(
                    0f, rect.top, 0f, rect.bottom,
                    intArrayOf(0xD85E5850.toInt(), 0xE634312C.toInt(), 0xF01C1A17.toInt()),
                    null, Shader.TileMode.CLAMP
                )
                FloatingCardTheme.GOLDEN_GLASS -> LinearGradient(
                    0f, rect.top, 0f, rect.bottom,
                    intArrayOf(0xCC5A4D3B.toInt(), 0xD9352D22.toInt(), 0xE6221D16.toInt()),
                    null, Shader.TileMode.CLAMP
                )
                FloatingCardTheme.IOS_DARK -> LinearGradient(
                    0f, rect.top, 0f, rect.bottom,
                    intArrayOf(0xEE2B2F37.toInt(), 0xF21B1E24.toInt(), 0xF4121419.toInt()),
                    null, Shader.TileMode.CLAMP
                )
                FloatingCardTheme.REZONA_CRYSTAL -> LinearGradient(
                    0f, rect.top, 0f, rect.bottom,
                    intArrayOf(0xD8525866.toInt(), 0xE22C303A.toInt(), 0xEC171A20.toInt()),
                    null, Shader.TileMode.CLAMP
                )
            }
            if (isBlurredMini) alpha = 155
        }
        c.drawRoundRect(rect, corner, corner, bgPaint)

        // 2. Outer glowing glass border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (cardW * 0.011f).coerceAtLeast(2f)
            color = when (theme) {
                FloatingCardTheme.REZONA_HALO_GLASS -> 0xB8FFFFFF.toInt()
                FloatingCardTheme.GOLDEN_GLASS -> 0xCCEED9A4.toInt()
                FloatingCardTheme.IOS_DARK -> 0x55FFFFFF
                FloatingCardTheme.REZONA_CRYSTAL -> 0xAAFFFFFF.toInt()
            }
        }
        c.drawRoundRect(rect, corner, corner, borderPaint)

        // 3. Top Header Row inside Card ("Rezona" + "•••" like Video 3)
        val innerX = rect.left + rect.width() * 0.075f
        val innerRight = rect.right - rect.width() * 0.075f
        val innerW = innerRight - innerX

        val brandY = rect.top + rect.height() * 0.062f
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            textSize = cardW * 0.038f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        c.drawText("Rezona", innerX, brandY, brandPaint)
        val dotsW = brandPaint.measureText("•••")
        c.drawText("•••", innerRight - dotsW, brandY, brandPaint)

        // 4. Square Rounded Album Cover Art (strictly sized to leave 38% bottom room for text + controls!)
        val artTop = rect.top + rect.height() * 0.088f
        val artSize = minOf(innerW, rect.height() * 0.52f)
        val artLeft = rect.left + (rect.width() - artSize) / 2f
        val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
        val artCorner = cardW * 0.048f

        if (coverBmp != null && !coverBmp.isRecycled) {
            val roundedArt = getRoundedBitmap(coverBmp, artSize.toInt().coerceAtLeast(32), artCorner)
            c.drawBitmap(roundedArt, artLeft, artTop, null)
            roundedArt.recycle()
        } else {
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    artRect.left, artRect.top, artRect.right, artRect.bottom,
                    intArrayOf(0xFF3E4656.toInt(), 0xFF1A1E26.toInt()),
                    null, Shader.TileMode.CLAMP
                )
            }
            c.drawRoundRect(artRect, artCorner, artCorner, placeholderPaint)
        }

        // 5. Song Title & Artist + Heart / Check Badge
        val titleY = artRect.bottom + rect.height() * 0.056f
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = cardW * 0.058f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB8FFFFFF.toInt()
            textSize = cardW * 0.042f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        val maxTextW = innerW * 0.82f
        val titleStr = TextUtils.ellipsize(track.title, titlePaint, maxTextW, TextUtils.TruncateAt.END).toString()
        val artistStr = TextUtils.ellipsize(track.artist, artistPaint, maxTextW, TextUtils.TruncateAt.END).toString()
        c.drawText(titleStr, innerX, titleY, titlePaint)
        val artistY = titleY + rect.height() * 0.042f
        c.drawText(artistStr, innerX, artistY, artistPaint)

        // Badge on right of title/artist
        val badgeCx = innerRight - cardW * 0.032f
        val badgeCy = titleY + rect.height() * 0.012f
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (theme == FloatingCardTheme.GOLDEN_GLASS) 0xFF1ED760.toInt() else 0x99FFFFFF.toInt()
            style = if (theme == FloatingCardTheme.REZONA_HALO_GLASS) Paint.Style.STROKE else Paint.Style.FILL
            strokeWidth = 2f
        }
        c.drawCircle(badgeCx, badgeCy, cardW * 0.028f, badgePaint)

        // 6. Progress Bar + Timestamps (strictly inside card)
        val barY = artistY + rect.height() * 0.046f
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44FFFFFF
            strokeWidth = (rect.height() * 0.010f).coerceAtLeast(2.5f)
            strokeCap = Paint.Cap.ROUND
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            strokeWidth = (rect.height() * 0.010f).coerceAtLeast(2.5f)
            strokeCap = Paint.Cap.ROUND
        }
        c.drawLine(innerX, barY, innerRight, barY, trackPaint)
        val animProg = (0.42f + (if (animPhaseRad != 0f) (sin(animPhaseRad * 0.5f) * 0.08f) else 0f)).coerceIn(0.15f, 0.85f)
        val progX = innerX + innerW * animProg
        c.drawLine(innerX, barY, progX, barY, fillPaint)
        c.drawCircle(progX, barY, cardW * 0.015f, fillPaint)

        val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()
            textSize = cardW * 0.030f
        }
        val timeY = barY + rect.height() * 0.030f
        c.drawText("2:10", innerX, timeY, timePaint)
        val durText = "-" + track.duration.ifBlank { "2:11" }
        val durW = timePaint.measureText(durText)
        c.drawText(durText, innerRight - durW, timeY, timePaint)

        // 7. Transport Controls Row (Previous · Play/Pause · Next — GUARANTEED INSIDE CARD!)
        val ctrlY = (timeY + rect.height() * 0.056f).coerceAtMost(rect.bottom - rect.height() * 0.072f)
        val centerX = rect.centerX()
        val playRadius = cardW * 0.048f

        val pausePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            strokeWidth = (cardW * 0.020f).coerceAtLeast(3f)
            strokeCap = Paint.Cap.ROUND
        }
        val barOffset = playRadius * 0.34f
        val barHalfH = playRadius * 0.44f
        c.drawLine(centerX - barOffset, ctrlY - barHalfH, centerX - barOffset, ctrlY + barHalfH, pausePaint)
        c.drawLine(centerX + barOffset, ctrlY - barHalfH, centerX + barOffset, ctrlY + barHalfH, pausePaint)

        drawSkipTriangle(c, centerX - cardW * 0.17f, ctrlY, cardW * 0.030f, isNext = false)
        drawSkipTriangle(c, centerX + cardW * 0.17f, ctrlY, cardW * 0.030f, isNext = true)

        // 8. Slim Bottom Volume Bar (like Video 3 bottom slider inside card!)
        val volY = (ctrlY + rect.height() * 0.052f).coerceAtMost(rect.bottom - rect.height() * 0.026f)
        if (volY < rect.bottom - 6f) {
            c.drawLine(innerX + innerW * 0.10f, volY, innerRight - innerW * 0.10f, volY, trackPaint)
            c.drawLine(innerX + innerW * 0.10f, volY, innerX + innerW * 0.64f, volY, fillPaint)
        }

        return bmp
    }

    /**
     * Draws the center human subject foreground cutout ON TOP of the inner edges of the floating 3D cards
     * so the person stands IN FRONT OF the floating cards (like Video 3)!
     */
    private fun drawSubjectForegroundCutout(
        canvas: Canvas,
        userPhoto: Bitmap,
        width: Int,
        height: Int,
        zoom: Float,
        panXFrac: Float,
        panYFrac: Float
    ) {
        val fgLayer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val fgCanvas = Canvas(fgLayer)
        drawPannedZoomedBitmap(fgCanvas, userPhoto, width, height, zoom, panXFrac, panYFrac)

        // Create a feathered human silhouette mask (head + shoulders + torso in center)
        val maskBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskBmp)
        val cx = width * (0.5f + panXFrac * 0.35f)
        val headCy = height * (0.37f + panYFrac * 0.35f)
        val torsoCy = height * (0.68f + panYFrac * 0.35f)

        val featherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            maskFilter = BlurMaskFilter((width * 0.045f).coerceAtLeast(16f), BlurMaskFilter.Blur.NORMAL)
        }
        // Head & halo region
        maskCanvas.drawOval(
            RectF(cx - width * 0.17f, headCy - height * 0.14f, cx + width * 0.17f, headCy + height * 0.13f),
            featherPaint
        )
        // Shoulders & torso region
        maskCanvas.drawOval(
            RectF(cx - width * 0.27f, torsoCy - height * 0.22f, cx + width * 0.27f, height * 0.98f),
            featherPaint
        )

        val xferPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        fgCanvas.drawBitmap(maskBmp, 0f, 0f, xferPaint)
        canvas.drawBitmap(fgLayer, 0f, 0f, null)
        maskBmp.recycle()
        fgLayer.recycle()
    }

    private fun drawRezonaWatermarkPill(canvas: Canvas, width: Int, height: Int, logoBitmap: Bitmap? = null) {
        val pillW = width * 0.30f
        val pillH = height * 0.038f
        val left = (width - pillW) / 2f
        val top = height * 0.89f
        val rect = RectF(left, top, left + pillW, top + pillH)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAA0E121A.toInt()
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            color = 0x66FFFFFF
        }
        canvas.drawRoundRect(rect, pillH / 2f, pillH / 2f, bgPaint)
        canvas.drawRoundRect(rect, pillH / 2f, pillH / 2f, borderPaint)

        val iconSize = pillH * 0.68f
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = pillH * 0.48f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val label = "Rezona"
        val textW = textPaint.measureText(label)
        val gap = pillH * 0.22f
        val totalContentW = iconSize + gap + textW
        val startX = rect.centerX() - totalContentW / 2f

        if (logoBitmap != null && !logoBitmap.isRecycled) {
            val iconTop = rect.centerY() - iconSize / 2f
            val iconRect = RectF(startX, iconTop, startX + iconSize, iconTop + iconSize)
            val roundedLogo = getRoundedBitmap(logoBitmap, iconSize.toInt().coerceAtLeast(16), iconSize * 0.26f)
            canvas.drawBitmap(roundedLogo, startX, iconTop, null)
            roundedLogo.recycle()
        } else {
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
            canvas.drawCircle(startX + iconSize * 0.5f, rect.centerY(), iconSize * 0.35f, dotPaint)
        }

        val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, startX + iconSize + gap, textY, textPaint)
    }

    /**
     * Exports a 6-second 24fps H.264 MP4 Live 3D Floating Poster Video using Android's hardware MediaCodec!
     */
    fun exportLive3dVideoMp4(
        context: Context,
        userPhoto: Bitmap?,
        presetBackdropIndex: Int,
        layout: FloatingPosterLayout,
        theme: FloatingCardTheme,
        overlapMode: PosterSubjectOverlapMode,
        cardSizeScale: Float,
        photoZoomScale: Float,
        photoPanXFrac: Float,
        photoPanYFrac: Float,
        cardOffsetsX: Map<Int, Float>,
        cardOffsetsY: Map<Int, Float>,
        tracks: List<RezonaTrack>,
        coverBitmaps: Map<String, Bitmap>
    ): File? {
        return runCatching {
            val videoW = 720
            val videoH = 1280
            val fps = 20
            val durationSec = 6
            val totalFrames = fps * durationSec

            val outDir = File(context.cacheDir, "posters").apply { mkdirs() }
            val outFile = File(outDir, "Rezona_Live3D_${System.currentTimeMillis()}.mp4")

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoW, videoH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 4_500_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = encoder.createInputSurface()
            encoder.start()

            val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()
            var trackIndex = -1
            var muxerStarted = false

            fun drainEncoder(endOfStream: Boolean) {
                if (endOfStream) {
                    encoder.signalEndOfInputStream()
                }
                while (true) {
                    val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                    if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!endOfStream) break
                    } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    } else if (outIdx >= 0) {
                        val encodedData = encoder.getOutputBuffer(outIdx)
                        if (encodedData != null && bufferInfo.size > 0 && muxerStarted) {
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                            }
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    }
                }
            }

            for (frameIdx in 0 until totalFrames) {
                val phase = (frameIdx.toFloat() / fps.toFloat()) * 2.2f
                val frameBmp = render3dPoster(
                    width = videoW,
                    height = videoH,
                    userPhoto = userPhoto,
                    presetBackdropIndex = presetBackdropIndex,
                    layout = layout,
                    theme = theme,
                    overlapMode = overlapMode,
                    cardSizeScale = cardSizeScale,
                    photoZoomScale = photoZoomScale,
                    photoPanXFrac = photoPanXFrac,
                    photoPanYFrac = photoPanYFrac,
                    cardOffsetsX = cardOffsetsX,
                    cardOffsetsY = cardOffsetsY,
                    animPhaseRad = phase,
                    tracks = tracks,
                    coverBitmaps = coverBitmaps,
                    logoBitmap = PosterRenderer.getRezonaLogoBitmap(context)
                )
                val surfaceCanvas = inputSurface.lockCanvas(null)
                surfaceCanvas.drawBitmap(frameBmp, 0f, 0f, null)
                inputSurface.unlockCanvasAndPost(surfaceCanvas)
                frameBmp.recycle()
                drainEncoder(endOfStream = false)
            }
            drainEncoder(endOfStream = true)

            runCatching {
                if (muxerStarted) muxer.stop()
                muxer.release()
            }
            runCatching {
                encoder.stop()
                encoder.release()
                inputSurface.release()
            }
            outFile
        }.getOrNull()
    }

    fun saveAndShareVideoMp4(context: Context, videoFile: File) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, videoFile.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Rezona")
                }
                val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(videoFile).use { input -> input.copyTo(out) }
                    }
                }
            }
            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                videoFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, "Share Rezona Live 3D Video").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure {
            Toast.makeText(context, "Video tersimpan: ${videoFile.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawSkipTriangle(c: Canvas, cx: Float, cy: Float, r: Float, isNext: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE6FFFFFF.toInt()
            style = Paint.Style.FILL
        }
        val dir = if (isNext) 1f else -1f
        val path = Path().apply {
            moveTo(cx - dir * r * 0.7f, cy - r * 0.75f)
            lineTo(cx + dir * r * 0.7f, cy)
            lineTo(cx - dir * r * 0.7f, cy + r * 0.75f)
            close()
        }
        c.drawPath(path, p)
        p.strokeWidth = (r * 0.28f).coerceAtLeast(2f)
        p.strokeCap = Paint.Cap.ROUND
        val barX = cx + dir * r * 0.78f
        c.drawLine(barX, cy - r * 0.7f, barX, cy + r * 0.7f, p)
    }

    private fun drawBitmapWith3DPose(
        canvas: Canvas,
        cardBmp: Bitmap,
        canvasW: Int,
        canvasH: Int,
        pose: Card3DPose
    ) {
        val matrix = Matrix()
        val camera = Camera()
        camera.save()
        camera.rotateX(pose.rotXDeg)
        camera.rotateY(pose.rotYDeg)
        camera.rotateZ(pose.rotZDeg)
        camera.getMatrix(matrix)
        camera.restore()

        val halfW = cardBmp.width / 2f
        val halfH = cardBmp.height / 2f
        matrix.preTranslate(-halfW, -halfH)
        matrix.postScale(pose.scale, pose.scale)
        matrix.postTranslate(pose.centerXFrac * canvasW, pose.centerYFrac * canvasH)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (pose.isBlurredMini) {
            paint.alpha = 150
            paint.maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(cardBmp, matrix, paint)
    }

    private fun drawPannedZoomedBitmap(
        canvas: Canvas,
        src: Bitmap,
        targetW: Int,
        targetH: Int,
        zoom: Float,
        panXFrac: Float,
        panYFrac: Float
    ) {
        val baseScale = maxOf(targetW.toFloat() / src.width.toFloat(), targetH.toFloat() / src.height.toFloat())
        val scale = baseScale * zoom.coerceIn(0.75f, 2.5f)
        val scaledW = src.width * scale
        val scaledH = src.height * scale
        val left = (targetW - scaledW) / 2f + panXFrac * targetW
        val top = (targetH - scaledH) / 2f + panYFrac * targetH
        val dest = RectF(left, top, left + scaledW, top + scaledH)
        canvas.drawBitmap(src, null, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun getRoundedBitmap(src: Bitmap, targetSize: Int, cornerRadius: Float): Bitmap {
        val out = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat())
        c.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(src, null, rect, paint)
        return out
    }
}
