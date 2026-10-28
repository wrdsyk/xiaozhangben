package com.jizhang.ui.components

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.jizhang.JizhangApp
import com.jizhang.ui.theme.LedgerIcons
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.Motion
import com.jizhang.ui.theme.numberStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

// Loading ----------------------------------------------------------------------------------

private sealed interface PhotoLoad {
    data object Loading : PhotoLoad
    data object Missing : PhotoLoad
    class Ready(val image: ImageBitmap) : PhotoLoad
}

private val maxMemory = Runtime.getRuntime().maxMemory()

/** Decoded thumbnails, keyed by "name@px"; small enough to keep a screenful or two around. */
private val thumbCache = object : LruCache<String, Bitmap>((maxMemory / 32).toInt()) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

/** Screen-sized decodes for the viewer: the current photo and its neighbours. */
private val fullCache = object : LruCache<String, Bitmap>((maxMemory / 8).toInt()) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

private fun photoFile(context: Context, name: String): File =
    (context.applicationContext as JizhangApp).container.images.file(name)

/**
 * Decodes [file] at the largest power-of-two downsample that still covers [target] px — on the
 * short side for cropped thumbnails ([crop]), the long side for the fitted viewer. Stored photos
 * are already upright (ImageStore re-encodes them), so no EXIF handling here.
 */
private fun decode(file: File, target: Int, crop: Boolean): Bitmap? = runCatching {
    if (!file.isFile) return@runCatching null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    val side = if (crop) min(bounds.outWidth, bounds.outHeight) else max(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (side / (sample * 2) >= target) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

/** Loads [name] off the main thread at about [px]; a cached decode shows on the first frame. */
@Composable
private fun rememberPhoto(name: String, px: Int, crop: Boolean): State<PhotoLoad> {
    val context = LocalContext.current
    val cache = if (crop) thumbCache else fullCache
    val key = "$name@$px"
    return produceState(cache.get(key)?.let { PhotoLoad.Ready(it.asImageBitmap()) } ?: PhotoLoad.Loading, key) {
        val hit = cache.get(key)
        value = if (hit != null) {
            PhotoLoad.Ready(hit.asImageBitmap())
        } else {
            withContext(Dispatchers.IO) { decode(photoFile(context, name), px, crop)?.also { cache.put(key, it) } }
                ?.let { PhotoLoad.Ready(it.asImageBitmap()) } ?: PhotoLoad.Missing
        }
    }
}

// Thumbnails -------------------------------------------------------------------------------

/**
 * A record photo, centre-cropped into [modifier]'s size (give it one). Decoded at the displayed
 * size; a missing file shows a quiet image glyph instead.
 */
@Composable
fun PhotoThumb(name: String, modifier: Modifier = Modifier, corner: Dp = 14.dp) {
    val c = LedgerTheme.colors
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(corner)).background(c.opKey),
        contentAlignment = Alignment.Center,
    ) {
        val px = with(LocalDensity.current) {
            max(if (constraints.hasBoundedWidth) maxWidth.roundToPx() else 0, if (constraints.hasBoundedHeight) maxHeight.roundToPx() else 0)
        }.coerceAtLeast(48)
        val load by rememberPhoto(name, px, crop = true)
        val shown by animateFloatAsState(if (load is PhotoLoad.Ready) 1f else 0f, Motion.fade(), label = "thumb")
        when (val l = load) {
            is PhotoLoad.Ready -> Image(
                l.image, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = shown },
            )
            PhotoLoad.Missing -> Glyph(LedgerIcons.get("image"), (min(maxWidth.value, maxHeight.value) * .36f).coerceIn(10f, 40f).dp, c.sub.copy(alpha = .7f))
            PhotoLoad.Loading -> Unit
        }
    }
}

/**
 * One scrolling row of photos; a tap opens the viewer at that photo. With [onRemove] each tile
 * gets a × badge, and [loading] adds that many spinner tiles for imports still in flight.
 */
@Composable
fun PhotoStrip(
    photos: List<String>,
    modifier: Modifier = Modifier,
    loading: Int = 0,
    tile: Dp = 64.dp,
    onRemove: ((String) -> Unit)? = null,
) {
    val c = LedgerTheme.colors
    // Removable tiles sit in a slot with room above and to the right, so the badge's 48dp
    // target is centred near the tile corner and only overlaps a corner of the photo.
    val bleed = if (onRemove != null) 20.dp else 0.dp
    LazyRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (onRemove != null) 0.dp else 8.dp),
    ) {
        itemsIndexed(photos, key = { _, name -> name }) { i, name ->
            Box(
                Modifier
                    .animateItem(fadeInSpec = Motion.fade(), placementSpec = Motion.offset, fadeOutSpec = Motion.fade())
                    .size(tile + bleed),
            ) {
                PhotoThumb(
                    name,
                    Modifier.align(Alignment.BottomStart).size(tile).tap(label = "查看图片") { PhotoViewer.open(photos, i) },
                )
                if (onRemove != null) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(48.dp).tap(label = "移除图片") { onRemove(name) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = .55f))
                                .border(1.dp, Color.White.copy(alpha = .8f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Glyph(LedgerIcons.get("close"), 12.dp, Color.White) }
                    }
                }
            }
        }
        items(loading) { i ->
            Box(Modifier.size(tile + bleed)) {
                Box(
                    Modifier.align(Alignment.BottomStart).size(tile).clip(RoundedCornerShape(14.dp)).background(c.opKey),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp, trackColor = c.track) }
            }
        }
    }
}

// Viewer -----------------------------------------------------------------------------------

internal class ViewerRequest(val id: Long, val photos: List<String>, val start: Int)

/**
 * The full-screen photo viewer. There is one per app, drawn by [PhotoViewerHost] above
 * everything else (forms included), so any screen or form can open it.
 */
@Stable
object PhotoViewer {
    internal var request by mutableStateOf<ViewerRequest?>(null)
        private set
    private var seq = 0L

    val isOpen: Boolean get() = request != null

    fun open(photos: List<String>, index: Int = 0) {
        if (photos.isEmpty()) return
        request = ViewerRequest(++seq, photos, index.coerceIn(photos.indices))
    }

    fun close() {
        request = null
    }
}

private const val MAX_ZOOM = 4f
private const val TAP_ZOOM = 2.5f

/** Goes last in the app root, after FormSheetHost, so the viewer covers forms too. */
@Composable
fun PhotoViewerHost() {
    val req = PhotoViewer.request
    var last by remember { mutableStateOf(req) }
    if (req != null) last = req
    if (req != null) {
        // Above an open form, its BackHandler stands aside (same mechanism as note-chip editing);
        // this one is registered when the viewer opens, so it also beats the screens' handlers.
        val form = LocalFormSheet.current
        DisposableEffect(form) {
            form.backClaims++
            onDispose { form.backClaims-- }
        }
        BackHandler { PhotoViewer.close() }
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) { keyboard?.hide() }
        LightStatusIcons()
    }
    AnimatedVisibility(visible = req != null, enter = fadeIn(Motion.fade()), exit = fadeOut(Motion.fade())) {
        val r = last ?: return@AnimatedVisibility
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .94f))
                // Nothing underneath reacts while the viewer is up.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            key(r.id) {
                ViewerContent(
                    r,
                    Modifier.animateEnterExit(
                        enter = scaleIn(Motion.layout(), initialScale = .92f),
                        exit = scaleOut(Motion.layout(), targetScale = .92f),
                    ),
                )
            }
        }
    }
}

/** The scrim is black in every theme, so the system bar icons turn light while it's up. */
@Composable
private fun LightStatusIcons() {
    val view = LocalView.current
    val dark = LedgerTheme.colors.isDark
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        onDispose {
            bars?.isAppearanceLightStatusBars = !dark
            bars?.isAppearanceLightNavigationBars = !dark
        }
    }
}

@Composable
private fun ViewerContent(r: ViewerRequest, modifier: Modifier) {
    val pager = rememberPagerState(initialPage = r.start) { r.photos.size }
    var zoomed by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        HorizontalPager(
            pager,
            Modifier.fillMaxSize(),
            pageSpacing = 16.dp,
            beyondViewportPageCount = 1,
            // A zoomed photo pans instead; zoom out to page on.
            userScrollEnabled = !zoomed,
            key = { r.photos[it] },
        ) { page ->
            ZoomablePhoto(
                r.photos[page],
                active = page == pager.currentPage,
                onZoomed = { if (page == pager.currentPage) zoomed = it },
            )
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(48.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (r.photos.size > 1) {
                    Text(
                        "${pager.currentPage + 1} / ${r.photos.size}",
                        style = numberStyle(14.sp, FontWeight.Medium, 0f),
                        color = Color.White.copy(alpha = .85f),
                    )
                }
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f)).tap(label = "关闭", onClick = PhotoViewer::close),
                contentAlignment = Alignment.Center,
            ) { Glyph(LedgerIcons.get("close"), 20.dp, Color.White) }
        }
    }
}

/** One page: pinch to zoom (1–4×) around the fingers, drag to pan when zoomed, double-tap to toggle. */
@Composable
private fun ZoomablePhoto(name: String, active: Boolean, onZoomed: (Boolean) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val load by rememberPhoto(name, max(constraints.maxWidth, constraints.maxHeight), crop = false)
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val scope = rememberCoroutineScope()
        val anim = remember { arrayOfNulls<Job>(1) }

        // Panning stops where the fitted photo's edge meets the screen edge.
        val image = (load as? PhotoLoad.Ready)?.image
        val fit = if (image != null) min(boxW / image.width, boxH / image.height) else 1f
        val fitW = image?.let { it.width * fit } ?: boxW
        val fitH = image?.let { it.height * fit } ?: boxH
        fun clamp(o: Offset, s: Float): Offset {
            val mx = max(0f, (fitW * s - boxW) / 2)
            val my = max(0f, (fitH * s - boxH) / 2)
            return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
        }
        fun settle(s: Float, o: Offset) {
            anim[0]?.cancel()
            val s0 = scale
            val o0 = offset
            anim[0] = scope.launch {
                animate(0f, 1f, animationSpec = Motion.layout()) { t, _ ->
                    scale = s0 + (s - s0) * t
                    offset = o0 + (o - o0) * t
                }
            }
        }

        val isZoomed = scale > 1.01f
        LaunchedEffect(isZoomed, active) { if (active) onZoomed(isZoomed) }
        // A page swiped away comes back at its normal size.
        LaunchedEffect(active) {
            if (!active) {
                anim[0]?.cancel()
                scale = 1f
                offset = Offset.Zero
            }
        }

        when (val l = load) {
            is PhotoLoad.Ready -> Image(
                l.image,
                contentDescription = "图片",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(fitW, fitH) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            anim[0]?.cancel()
                            do {
                                val event = awaitPointerEvent()
                                val fingers = event.changes.count { it.pressed }
                                // One finger at 1× belongs to the pager; two fingers, or any drag
                                // while zoomed, belongs to the photo.
                                if (fingers > 1 || scale > 1.01f) {
                                    val s = (scale * event.calculateZoom()).coerceIn(.8f, MAX_ZOOM)
                                    val z = s / scale
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    val center = Offset(boxW / 2, boxH / 2)
                                    val pan = event.calculatePan()
                                    val o = if (centroid.isSpecified) (centroid - center) * (1 - z) + offset * z + pan else offset + pan
                                    scale = s
                                    offset = clamp(o, s)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                            if (scale < 1f) settle(1f, Offset.Zero)
                        }
                    }
                    .pointerInput(fitW, fitH) {
                        detectTapGestures(onDoubleTap = { p ->
                            if (scale > 1.01f) {
                                settle(1f, Offset.Zero)
                            } else {
                                val center = Offset(boxW / 2, boxH / 2)
                                settle(TAP_ZOOM, clamp((p - center) * (1 - TAP_ZOOM), TAP_ZOOM))
                            }
                        })
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            PhotoLoad.Missing -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Glyph(LedgerIcons.get("image"), 40.dp, Color.White.copy(alpha = .5f))
                Text("图片找不到了", fontSize = 14.sp, color = Color.White.copy(alpha = .6f))
            }
            PhotoLoad.Loading -> CircularProgressIndicator(Modifier.size(28.dp), color = Color.White.copy(alpha = .8f), strokeWidth = 2.dp)
        }
    }
}
