package com.nayama.music.ui.lyrics

import android.content.Context
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
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.nayama.music.ui.components.RezonaGlassTextField
import com.nayama.music.ui.home.REAL_POPULAR_SEEDS
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.SpecularCardBorder
import com.nayama.music.ui.theme.Typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 6 Distinct 3D Floating Song Poster Layouts:
 * - Includes the 3 viral reference designs uploaded by the user + 3 Rezona exclusive 3D layouts.
 */
enum class FloatingPosterLayout(val title: String, val subtitle: String, val slotCount: Int) {
    HALO_ORBIT_5("Halo Orbit (5 Cards)", "Glowing glass cards + blurred depth mini-cards", 5),
    TILT_QUAD_3D("3D Tilt Quad (4 Cards)", "Dramatic inward 3D perspective corner cards", 4),
    SKY_GROUND_3D("Sky & Ground 3D", "2 upper floating cards + 2 ground-tilted 3D cards", 4),
    PRISM_ARC_5("Liquid Prism Arc", "5 crystal cards in a curved 3D spiral arc", 5),
    IOS_STACK_4("iOS Control Stack", "4 angled iOS Control Center translucent music decks", 4),
    DUO_SPOTLIGHT("Duo Spotlight", "2 oversized 3D glass cards framing center photo", 2)
}

enum class FloatingCardTheme(val label: String) {
    GOLDEN_GLASS("Golden Glass Glow"),
    IOS_DARK("iOS Dark Frosted"),
    REZONA_CRYSTAL("Rezona Silver Frost")
}

private data class Card3DPose(
    val centerXFrac: Float,
    val centerYFrac: Float,
    val scale: Float,
    val rotXDeg: Float,
    val rotYDeg: Float,
    val rotZDeg: Float,
    val isBlurredMini: Boolean = false
)

/**
 * Rezona 3D Floating Song Poster Studio:
 * - Let users upload/pick their own portrait photo from Gallery ("add foto kita")
 * - Search ANY song on YouTube Music or pick from Library to customize every floating 3D song card
 * - Choose from 6 3D perspective layouts & 3 glass card themes
 * - Export 1080x1920 high-res poster to Gallery or Share directly to Instagram/TikTok/WA
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

    // Build initial 5 floating tracks from currentTrack + queue + liked + popular seeds
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

    var selectedLayout by remember { mutableStateOf(FloatingPosterLayout.HALO_ORBIT_5) }
    var selectedTheme by remember { mutableStateOf(FloatingCardTheme.GOLDEN_GLASS) }
    var activeSlotIndex by remember { mutableIntStateOf(0) }
    var userPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var presetBackdropIndex by remember { mutableIntStateOf(0) }

    // Live song search state for replacing any floating card slot
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<RezonaTrack>>(emptyList()) }

    // Cache of loaded album cover bitmaps keyed by coverArtUrl
    val coverBitmaps = remember { mutableStateMapOf<String, Bitmap>() }
    var renderTrigger by remember { mutableIntStateOf(0) }

    // Load cover art bitmaps asynchronously whenever floatingTracks change
    LaunchedEffect(floatingTracks.toList()) {
        for (track in floatingTracks) {
            val url = track.coverArtUrl
            if (url.isNotBlank() && !coverBitmaps.containsKey(url)) {
                val bmp = withContext(Dispatchers.IO) {
                    runCatching {
                        val req = ImageRequest.Builder(context)
                            .data(url)
                            .allowHardware(false)
                            .size(360, 360)
                            .build()
                        val res = context.imageLoader.execute(req)
                        (res as? SuccessResult)?.drawable?.toBitmap(360, 360, Bitmap.Config.ARGB_8888)
                    }.getOrNull()
                }
                if (bmp != null) {
                    coverBitmaps[url] = bmp
                    renderTrigger++
                }
            }
        }
    }

    // Photo picker from user's phone gallery
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
        userPhotoBitmap,
        presetBackdropIndex,
        floatingTracks.toList(),
        renderTrigger
    ) {
        val tracksSnapshot = floatingTracks.toList()
        val coversSnapshot = coverBitmaps.toMap()
        val rendered = withContext(Dispatchers.Default) {
            FloatingSongPosterRenderer.render3dPoster(
                width = 720,
                height = 1280,
                userPhoto = userPhotoBitmap,
                presetBackdropIndex = presetBackdropIndex,
                layout = selectedLayout,
                theme = selectedTheme,
                tracks = tracksSnapshot,
                coverBitmaps = coversSnapshot
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
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "3D FLOATING SONG POSTER",
                        style = Typography.titleLarge.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = PureWhite
                    )
                    Text(
                        text = "Add your photo & customize floating 3D songs",
                        style = Typography.bodySmall,
                        color = SoftSilver
                    )
                }
                RezonaActionDisc(size = 38.dp, onClick = onDismiss) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close",
                        tint = PureWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // ── 1. LIVE 9:16 3D POSTER PREVIEW ─────────────────────────────
                item(key = "poster_3d_preview") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        val bmp = previewBitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "3D Song Poster Preview",
                                modifier = Modifier
                                    .fillMaxWidth(0.74f)
                                    .aspectRatio(9f / 16f)
                                    .shadow(24.dp, RoundedCornerShape(24.dp))
                                    .clip(RoundedCornerShape(24.dp))
                                    .border(1.dp, SpecularCardBorder, RoundedCornerShape(24.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.74f)
                                    .aspectRatio(9f / 16f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color(0xFF10131A)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = PureWhite)
                            }
                        }
                    }
                }

                // ── 2. ADD YOUR PHOTO FROM GALLERY OR SWITCH BACKDROP ──────────
                item(key = "poster_photo_picker") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "1. BACKGROUND PHOTO (FOTO KAMU)",
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                            color = SoftSilver
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RezonaGlassButton(
                                text = if (userPhotoBitmap != null) "Change Your Photo" else "+ Add Your Photo (Gallery)",
                                isPrimary = true,
                                onClick = { photoPickerLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f)
                            )
                            if (userPhotoBitmap != null) {
                                RezonaGlassButton(
                                    text = "Use Preset",
                                    isPrimary = false,
                                    onClick = {
                                        userPhotoBitmap = null
                                        presetBackdropIndex = (presetBackdropIndex + 1) % 4
                                    }
                                )
                            } else {
                                RezonaGlassButton(
                                    text = "Next Backdrop",
                                    isPrimary = false,
                                    onClick = { presetBackdropIndex = (presetBackdropIndex + 1) % 4 }
                                )
                            }
                        }
                    }
                }

                // ── 3. CHOOSE 3D LAYOUT DESIGN (6 TEMPLATES) ───────────────────
                item(key = "poster_3d_layouts") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "2. 3D FLOATING LAYOUT (${FloatingPosterLayout.entries.size} DESIGNS)",
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

                // ── 4. CARD GLASS THEME SWITCHER ───────────────────────────────
                item(key = "poster_card_theme") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "3. FLOATING CARD STYLE",
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

                // ── 5. CUSTOMIZE & SEARCH FLOATING SONGS (SLOT 1..N) ───────────
                item(key = "poster_floating_song_slots") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "4. CUSTOMIZE FLOATING SONGS (TAP SLOT THEN SEARCH)",
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

                        // Search bar to replace the active floating card slot with ANY song
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                RezonaGlassTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = "Search song for Card #${activeSlotIndex + 1} (e.g. Hindia, Tulus, Travis)...",
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
                                    text = if (isSearching) "..." else "Search",
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
                                                "Card #${activeSlotIndex + 1} -> ${resultTrack.title}",
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
                                                text = "Use in #${activeSlotIndex + 1}",
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

            // Bottom Save & Share Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xF20A0C10))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RezonaGlassButton(
                    text = "Save to Gallery",
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
                                    tracks = floatingTracks.toList(),
                                    coverBitmaps = coverBitmaps.toMap()
                                )
                            }
                            val saved = PosterRenderer.saveToGallery(context, hiRes)
                            Toast.makeText(
                                context,
                                if (saved) "3D Song Poster saved to Pictures/Rezona" else "Could not save poster",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                RezonaGlassButton(
                    text = "Share 3D Poster",
                    isPrimary = true,
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
                                    tracks = floatingTracks.toList(),
                                    coverBitmaps = coverBitmaps.toMap()
                                )
                            }
                            PosterRenderer.sharePoster(context, hiRes)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Pure Android Canvas + 3D Camera Perspective Matrix renderer for the 3D Floating Song Poster.
 */
object FloatingSongPosterRenderer {

    fun render3dPoster(
        width: Int,
        height: Int,
        userPhoto: Bitmap?,
        presetBackdropIndex: Int,
        layout: FloatingPosterLayout,
        theme: FloatingCardTheme,
        tracks: List<RezonaTrack>,
        coverBitmaps: Map<String, Bitmap>
    ): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // 1. Draw User Photo (Center-Cropped) OR Moody Editorial Studio Backdrop
        if (userPhoto != null && !userPhoto.isRecycled) {
            drawCenterCropBitmap(canvas, userPhoto, width, height)
            // Subtle cinematic vignette so floating 3D cards pop like the 3 reference photos
            val vigPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(0x38000000, 0x10000000, 0x48000000),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vigPaint)
        } else {
            val palettes = listOf(
                intArrayOf(0xFF3E2F23.toInt(), 0xFF231B14.toInt(), 0xFF110D0A.toInt()), // Warm Urban Sandstone (Photo 1)
                intArrayOf(0xFF283424.toInt(), 0xFF172215.toInt(), 0xFF0A100A.toInt()), // Emerald Courtyard (Photo 2 & 3)
                intArrayOf(0xFF1E2634.toInt(), 0xFF111620.toInt(), 0xFF07090E.toInt()), // Midnight Studio
                intArrayOf(0xFF34222E.toInt(), 0xFF1D131B.toInt(), 0xFF0B070A.toInt())  // Sunset Dusk
            )
            val pal = palettes[presetBackdropIndex.coerceAtLeast(0) % palettes.size]
            val bgPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    pal, null, Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        }

        val safeTracks = tracks.ifEmpty { REAL_POPULAR_SEEDS }
        val poses = getPosesForLayout(layout)

        // 2. First pass: Draw blurred background depth mini-cards (if present in layout like Photo 1)
        val cardBaseW = width * 0.42f
        val cardBaseH = cardBaseW * 1.24f

        for ((idx, pose) in poses.withIndex()) {
            if (!pose.isBlurredMini) continue
            val track = safeTracks[idx % safeTracks.size]
            val cardBmp = renderSingleFloatingCard(
                cardW = (cardBaseW * 0.65f).toInt().coerceAtLeast(120),
                cardH = (cardBaseH * 0.65f).toInt().coerceAtLeast(150),
                track = track,
                coverBmp = coverBitmaps[track.coverArtUrl],
                theme = theme,
                isBlurredMini = true
            )
            drawBitmapWith3DPose(canvas, cardBmp, width, height, pose)
            cardBmp.recycle()
        }

        // 3. Second pass: Draw main high-detail 3D perspective floating song cards
        var mainIdx = 0
        for (pose in poses) {
            if (pose.isBlurredMini) continue
            val track = safeTracks[mainIdx % safeTracks.size]
            mainIdx++
            val cardBmp = renderSingleFloatingCard(
                cardW = cardBaseW.toInt().coerceAtLeast(200),
                cardH = cardBaseH.toInt().coerceAtLeast(250),
                track = track,
                coverBmp = coverBitmaps[track.coverArtUrl],
                theme = theme,
                isBlurredMini = false
            )
            drawBitmapWith3DPose(canvas, cardBmp, width, height, pose)
            cardBmp.recycle()
        }

        return out
    }

    private fun getPosesForLayout(layout: FloatingPosterLayout): List<Card3DPose> {
        return when (layout) {
            FloatingPosterLayout.HALO_ORBIT_5 -> listOf(
                // 5 main glowing floating cards matching Photo 1
                Card3DPose(0.27f, 0.28f, 0.88f, 4f, 14f, -4f),
                Card3DPose(0.75f, 0.26f, 0.80f, 4f, -15f, 5f),
                Card3DPose(0.23f, 0.58f, 0.84f, -3f, 16f, -3f),
                Card3DPose(0.78f, 0.59f, 0.82f, -3f, -16f, 6f),
                Card3DPose(0.43f, 0.84f, 0.85f, 6f, 4f, -2f),
                // 4 small depth-of-field blurred background mini cards matching Photo 1
                Card3DPose(0.20f, 0.08f, 0.52f, 8f, 12f, -14f, isBlurredMini = true),
                Card3DPose(0.48f, 0.11f, 0.44f, 6f, -10f, 12f, isBlurredMini = true),
                Card3DPose(0.92f, 0.42f, 0.46f, 0f, -18f, 15f, isBlurredMini = true),
                Card3DPose(0.84f, 0.88f, 0.50f, 8f, -14f, 10f, isBlurredMini = true)
            )
            FloatingPosterLayout.TILT_QUAD_3D -> listOf(
                // 4 large dramatic 3D perspective tilted cards matching Photo 2
                Card3DPose(0.28f, 0.30f, 0.96f, 6f, 24f, -8f),
                Card3DPose(0.73f, 0.34f, 0.94f, 6f, -24f, 10f),
                Card3DPose(0.27f, 0.68f, 0.95f, -5f, 22f, 6f),
                Card3DPose(0.74f, 0.74f, 0.92f, -5f, -22f, -7f)
            )
            FloatingPosterLayout.SKY_GROUND_3D -> listOf(
                // 2 upper perspective cards + 2 ground-lying 3D cards matching Photo 3
                Card3DPose(0.23f, 0.35f, 0.94f, 10f, 18f, -4f),
                Card3DPose(0.80f, 0.35f, 0.94f, 10f, -20f, 8f),
                Card3DPose(0.26f, 0.79f, 0.90f, 34f, 14f, -24f),
                Card3DPose(0.68f, 0.72f, 0.92f, 36f, -16f, 28f)
            )
            FloatingPosterLayout.PRISM_ARC_5 -> listOf(
                Card3DPose(0.24f, 0.18f, 0.82f, 5f, 18f, -10f),
                Card3DPose(0.76f, 0.30f, 0.86f, 4f, -18f, 8f),
                Card3DPose(0.24f, 0.48f, 0.90f, 0f, 20f, -6f),
                Card3DPose(0.76f, 0.64f, 0.86f, -4f, -18f, 8f),
                Card3DPose(0.32f, 0.84f, 0.88f, 8f, 12f, -5f)
            )
            FloatingPosterLayout.IOS_STACK_4 -> listOf(
                Card3DPose(0.26f, 0.24f, 0.92f, 4f, 16f, -6f),
                Card3DPose(0.74f, 0.32f, 0.92f, 4f, -16f, 7f),
                Card3DPose(0.26f, 0.66f, 0.92f, -4f, 16f, -5f),
                Card3DPose(0.74f, 0.76f, 0.92f, -4f, -16f, 6f)
            )
            FloatingPosterLayout.DUO_SPOTLIGHT -> listOf(
                Card3DPose(0.29f, 0.27f, 1.05f, 6f, 20f, -7f),
                Card3DPose(0.72f, 0.73f, 1.05f, -6f, -20f, 7f)
            )
        }
    }

    private fun renderSingleFloatingCard(
        cardW: Int,
        cardH: Int,
        track: RezonaTrack,
        coverBmp: Bitmap?,
        theme: FloatingCardTheme,
        isBlurredMini: Boolean
    ): Bitmap {
        val pad = (cardW * 0.08f).coerceAtLeast(12f)
        val bmp = Bitmap.createBitmap(cardW, cardH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rect = RectF(pad * 0.5f, pad * 0.5f, cardW - pad * 0.5f, cardH - pad * 0.5f)
        val corner = cardW * 0.085f

        // 1. Card glass fill
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = when (theme) {
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

        // 2. Outer glowing glass border (matches Photo 1 golden-white rim / Photo 2 dark glass rim)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (cardW * 0.011f).coerceAtLeast(2f)
            color = when (theme) {
                FloatingCardTheme.GOLDEN_GLASS -> 0xCCEED9A4.toInt()
                FloatingCardTheme.IOS_DARK -> 0x55FFFFFF
                FloatingCardTheme.REZONA_CRYSTAL -> 0xAAFFFFFF.toInt()
            }
        }
        c.drawRoundRect(rect, corner, corner, borderPaint)

        // 3. Album Cover Art Square inside Card
        val innerPad = cardW * 0.095f
        val artTop = rect.top + innerPad * 0.85f
        val artSize = rect.width() - innerPad * 1.7f
        val artLeft = rect.left + (rect.width() - artSize) / 2f
        val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
        val artCorner = cardW * 0.045f

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

        // 4. Song Title & Artist + Green Check / Waveform
        val titleY = artRect.bottom + cardH * 0.068f
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = cardW * 0.064f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB8FFFFFF.toInt()
            textSize = cardW * 0.046f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        val maxTextW = artRect.width() * 0.82f
        val titleStr = TextUtils.ellipsize(track.title, titlePaint, maxTextW, TextUtils.TruncateAt.END).toString()
        val artistStr = TextUtils.ellipsize(track.artist, artistPaint, maxTextW, TextUtils.TruncateAt.END).toString()
        c.drawText(titleStr, artRect.left, titleY, titlePaint)
        c.drawText(artistStr, artRect.left, titleY + cardH * 0.046f, artistPaint)

        // Green check badge (like Photo 1 & 2) or white badge
        val badgeCx = artRect.right - cardW * 0.035f
        val badgeCy = titleY + cardH * 0.012f
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (theme == FloatingCardTheme.REZONA_CRYSTAL) 0xFFFFFFFF.toInt() else 0xFF1ED760.toInt()
        }
        c.drawCircle(badgeCx, badgeCy, cardW * 0.032f, badgePaint)

        // 5. Progress Bar + Scrubber Dot
        val barY = titleY + cardH * 0.088f
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44FFFFFF
            strokeWidth = (cardH * 0.010f).coerceAtLeast(2.5f)
            strokeCap = Paint.Cap.ROUND
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            strokeWidth = (cardH * 0.010f).coerceAtLeast(2.5f)
            strokeCap = Paint.Cap.ROUND
        }
        c.drawLine(artRect.left, barY, artRect.right, barY, trackPaint)
        val progX = artRect.left + artRect.width() * 0.42f
        c.drawLine(artRect.left, barY, progX, barY, fillPaint)
        c.drawCircle(progX, barY, cardW * 0.018f, fillPaint)

        // Timestamps under progress bar
        val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()
            textSize = cardW * 0.032f
        }
        c.drawText("1:24", artRect.left, barY + cardH * 0.034f, timePaint)
        val durText = track.duration.ifBlank { "3:42" }
        val durW = timePaint.measureText(durText)
        c.drawText(durText, artRect.right - durW, barY + cardH * 0.034f, timePaint)

        // 6. Transport Controls Row (Previous · Play/Pause Circle · Next)
        val ctrlY = barY + cardH * 0.095f
        val centerX = rect.centerX()
        val playRadius = cardW * 0.072f

        if (theme == FloatingCardTheme.GOLDEN_GLASS || theme == FloatingCardTheme.REZONA_CRYSTAL) {
            // White circle with dark pause bars (like Photo 1)
            c.drawCircle(centerX, ctrlY, playRadius, fillPaint)
            val pausePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF14161C.toInt()
                strokeWidth = (cardW * 0.018f).coerceAtLeast(3f)
                strokeCap = Paint.Cap.ROUND
            }
            val barOffset = playRadius * 0.28f
            val barHalfH = playRadius * 0.36f
            c.drawLine(centerX - barOffset, ctrlY - barHalfH, centerX - barOffset, ctrlY + barHalfH, pausePaint)
            c.drawLine(centerX + barOffset, ctrlY - barHalfH, centerX + barOffset, ctrlY + barHalfH, pausePaint)
        } else {
            // iOS Control Center bold white pause bars (like Photo 2 & 3)
            val pausePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                strokeWidth = (cardW * 0.024f).coerceAtLeast(4f)
                strokeCap = Paint.Cap.ROUND
            }
            val barOffset = playRadius * 0.30f
            val barHalfH = playRadius * 0.44f
            c.drawLine(centerX - barOffset, ctrlY - barHalfH, centerX - barOffset, ctrlY + barHalfH, pausePaint)
            c.drawLine(centerX + barOffset, ctrlY - barHalfH, centerX + barOffset, ctrlY + barHalfH, pausePaint)
        }

        // Previous & Next triangles
        drawSkipTriangle(c, centerX - cardW * 0.19f, ctrlY, cardW * 0.036f, isNext = false)
        drawSkipTriangle(c, centerX + cardW * 0.19f, ctrlY, cardW * 0.036f, isNext = true)

        return bmp
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

    private fun drawCenterCropBitmap(canvas: Canvas, src: Bitmap, targetW: Int, targetH: Int) {
        val scale = maxOf(targetW.toFloat() / src.width.toFloat(), targetH.toFloat() / src.height.toFloat())
        val scaledW = src.width * scale
        val scaledH = src.height * scale
        val left = (targetW - scaledW) / 2f
        val top = (targetH - scaledH) / 2f
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
