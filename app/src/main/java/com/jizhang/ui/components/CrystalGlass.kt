package com.jizhang.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.jizhang.ui.theme.LedgerTheme
import kotlin.math.ceil
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/*
 * 晶透: set A of the design canvas (小账本 · 质感组件), translated layer by layer from its CSS.
 *
 *   .A .g        backdrop-filter: blur(9px) saturate(2.1) brightness(1.08) contrast(1.05)
 *                background: linear-gradient(160deg, white .24, white .05 55%, white .12)
 *                inset bevel lit at the upper left and, fainter, the lower right
 *                inset shade along the bottom (the pane's thickness)
 *                inset pink on the left and blue on the right (dispersion)
 *   ::before     two elliptical specular highlights, upper left and lower right
 *   ::after      a 1px rim, a conic gradient bright at both lit corners
 *
 * Whites fade on dark themes (they were drawn for pearl). Everything is static: no frame is drawn
 * unless the pane itself changes.
 */

private val Pink = Color(0xFFFF508C)
private val Blue = Color(0xFF3CA0FF)

/** CSS px on the 420px-wide canvas are taken as dp. */
private fun inset(x: Float, y: Float, blur: Float, spread: Float, color: Color) =
    Shadow(radius = blur.dp, color = color, spread = spread.dp, offset = DpOffset(x.dp, y.dp))

private fun Color.a(alpha: Float) = copy(alpha = alpha.coerceIn(0f, 1f))

/** The pane of 晶透; [glass] hands it every surface when that finish is picked. */
@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun Modifier.crystalGlass(corner: Dp, shadow: Boolean, fill: Brush?, glint: Boolean, backdrop: Boolean): Modifier {
    val c = LedgerTheme.colors
    val d = c.darkness
    val lift = 1f - .55f * d
    val fringe = 1f - .4f * d
    val shape = RoundedCornerShape(corner)
    var m = this
    if (shadow) {
        // 0 22px 44px -22px rgba(25,30,50,.38), 0 2px 6px -2px rgba(25,30,50,.1)
        m = m
            .dropShadow(shape, Shadow(radius = 44.dp, color = c.shadow.a(.38f + .3f * d), spread = (-22).dp, offset = DpOffset(0.dp, 22.dp)))
            .dropShadow(shape, Shadow(radius = 6.dp, color = c.shadow.a(.1f + .15f * d), spread = (-2).dp, offset = DpOffset(0.dp, 2.dp)))
    }
    m = m.clip(shape)
    if (backdrop) {
        // CSS blur() is a standard deviation; Android's radius is about sigma / 0.577. The canvas
        // saturated 2.1× over plain pastel orbs; the app's orbs are already boosted, so 1.4× lands
        // on the same colour.
        m = m.then(
            Modifier.backdropBlur(base = c.bg2, blur = 15.dp, saturation = 1.4f, brightness = 1.08f, contrast = 1.05f)
                ?: LocalHazeState.current?.let { haze ->
                    Modifier.hazeBlur(
                        HazeInput.Backdrop(haze),
                        HazeBlurStyle {
                            blurRadius(15.dp)
                            colorEffects(listOf(HazeColorEffect.colorFilter(ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.4f) }))))
                        },
                        performanceMode = HazePerformanceMode.Performance,
                    )
                }
                ?: Modifier,
        )
    }
    m = if (fill != null) m.background(fill) else m.drawWithCache {
        // linear-gradient(160deg, …): top left to bottom, leaning a little right.
        val pane = Brush.linearGradient(
            0f to Color.White.a(.24f * lift),
            .55f to Color.White.a(.05f * lift),
            1f to Color.White.a(.12f * lift),
            start = Offset.Zero, end = Offset(size.height * .36f, size.height),
        )
        onDrawBehind { drawRect(pane) }
    }
    m = m.drawBehind {
        // ::before: radial-gradient(90% 70% at 18% -10%, white .42, transparent 55%),
        //           radial-gradient(60% 50% at 105% 110%, white .22, transparent 60%)
        ellipse(Offset(size.width * .18f, size.height * -.1f), size.width * .9f, size.height * .7f, Color.White.a(.42f * lift), .55f)
        ellipse(Offset(size.width * 1.05f, size.height * 1.1f), size.width * .6f, size.height * .5f, Color.White.a(.22f * lift), .6f)
    }
    m = m
        .innerShadow(shape, inset(3f, 4f, 3f, -3f, Color.White.a(lift)))
        .innerShadow(shape, inset(-3f, -4f, 4f, -3f, Color.White.a(.65f * lift)))
        .innerShadow(shape, inset(0f, -14f, 22f, -16f, c.shadow.a(.22f + .2f * d)))
        .innerShadow(shape, inset(7f, 0f, 10f, -9f, Pink.a(.45f * fringe)))
        .innerShadow(shape, inset(-7f, 0f, 10f, -9f, Blue.a(.5f * fringe)))
    if (glint) m = m.graphicsLayer().glint(c.glint)
    return m.conicRim(corner, 1.dp, lift)
}

/** Stat chips, keys and other small panes on a card: .A .chip. */
@Composable
internal fun Modifier.crystalChip(corner: Dp, strong: Boolean): Modifier {
    val c = LedgerTheme.colors
    val d = c.darkness
    val lift = 1f - .55f * d
    val fringe = 1f - .4f * d
    val shape = RoundedCornerShape(corner)
    return this
        .outerShadow(corner, blur = 8.dp, spread = (-4).dp, y = 3.dp, color = c.shadow.a(.22f + .2f * d))
        .clip(shape)
        .background(Color.White.a((if (strong) .28f else .22f) * lift))
        .innerShadow(shape, inset(1.5f, 2f, 1.5f, -1f, Color.White.a(lift)))
        .innerShadow(shape, inset(-1.5f, -2f, 2f, -1f, Color.White.a(.55f * lift)))
        .innerShadow(shape, inset(4f, 0f, 6f, -5f, Pink.a(.45f * fringe)))
        .innerShadow(shape, inset(-4f, 0f, 6f, -5f, Blue.a(.5f * fringe)))
}

/** A selected segment, the nav bar's lens, a switch thumb: .A .raised, a little glass drop. */
@Composable
internal fun Modifier.crystalRaised(corner: Dp, base: Color): Modifier {
    val c = LedgerTheme.colors
    val d = c.darkness
    val lift = 1f - .55f * d
    val fringe = 1f - .4f * d
    val shape = RoundedCornerShape(corner)
    // The canvas drop is white .34. On dark themes the label on it is dark, so the drop keeps its base.
    val fill = base.a(base.alpha * (.4f + .6f * d))
    return this
        .outerShadow(corner, blur = 10.dp, spread = (-4).dp, y = 4.dp, color = c.shadow.a(.28f + .2f * d))
        .clip(shape)
        .background(fill)
        .innerShadow(shape, inset(2f, 3f, 2f, -2f, Color.White.a(lift)))
        .innerShadow(shape, inset(-2f, -3f, 3f, -2f, Color.White.a(.75f * lift)))
        .innerShadow(shape, inset(5f, 0f, 6f, -5f, Pink.a(.5f * fringe)))
        .innerShadow(shape, inset(-5f, 0f, 6f, -5f, Blue.a(.55f * fringe)))
        .border(.5.dp, Color.White.a(.6f * lift), shape)
}

/** The finish on a solid accent button (the + button, primary buttons): .A .fill. */
@Composable
internal fun Modifier.crystalSolid(corner: Dp): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .drawBehind {
            // radial-gradient(100% 70% at 30% 0%, white .32, transparent 60%)
            ellipse(Offset(size.width * .3f, 0f), size.width, size.height * .7f, Color.White.a(.32f), .6f)
        }
        .innerShadow(shape, inset(2f, 3f, 2f, -2f, Color.White.a(.6f)))
        .innerShadow(shape, inset(-2f, -3f, 2f, -2f, Color.White.a(.25f)))
        .innerShadow(shape, inset(5f, 0f, 6f, -5f, Pink.a(.6f)))
        .innerShadow(shape, inset(-5f, 0f, 6f, -5f, Blue.a(.6f)))
}

/**
 * A drop shadow drawn only outside the shape, as CSS draws box-shadow. Compose's dropShadow fills
 * under the shape as well, which shows through a translucent chip as a grey smudge.
 *
 * The blur runs once per size into a bitmap: a mask filter on the path itself is worked out on
 * the CPU and uploaded again at every draw, which made every frame of a sliding pill slow.
 */
private fun Modifier.outerShadow(corner: Dp, blur: Dp, spread: Dp, y: Dp, color: Color): Modifier = drawWithCache {
    val r = corner.toPx()
    val own = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
    val s = spread.toPx()
    val dy = y.toPx()
    val radius = blur.toPx() / 2f
    // The blur reaches about 1.7 radii past the shape.
    val pad = ceil(radius * 2f).toInt() + 2
    val castW = size.width + 2 * s
    val castH = size.height + 2 * s
    val w = ceil(castW).toInt() + 2 * pad
    val h = ceil(castH).toInt() + 2 * pad
    if (castW <= 0f || castH <= 0f || radius <= 0f) return@drawWithCache onDrawBehind { }
    val shadow = ImageBitmap(w, h)
    Canvas(shadow).drawRoundRect(
        pad.toFloat(), pad.toFloat(), pad + castW, pad + castH,
        (r + s).coerceAtLeast(0f), (r + s).coerceAtLeast(0f),
        Paint().apply {
            this.color = color
            asFrameworkPaint().maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        },
    )
    val at = Offset(-s - pad, -s + dy - pad)
    onDrawBehind {
        clipPath(own, ClipOp.Difference) { drawImage(shadow, topLeft = at) }
    }
}

/** An elliptical radial gradient, as CSS draws `radial-gradient(rx ry at center, color, transparent stop)`. */
private fun DrawScope.ellipse(center: Offset, rx: Float, ry: Float, color: Color, stop: Float) {
    if (rx <= 0f || ry <= 0f) return
    val k = ry / rx
    val brush = Brush.radialGradient(0f to color, stop to Color.Transparent, center = center, radius = rx)
    scale(1f, k, pivot = center) {
        // Cover the whole pane in the scaled space; the clip trims the rest.
        val top = center.y - center.y / k
        drawRect(brush, topLeft = Offset(0f, top), size = Size(size.width, size.height / k))
    }
}

/**
 * ::after of .A .g: `conic-gradient(from 315deg, #fff, .25 18%, .04 32%, .65 50%, .08 68%, .25 85%, #fff)`
 * as a stroke on the edge. CSS starts a conic at twelve o'clock, Compose's sweep at three, so the
 * stops are turned by 315° − 90° = 225° and the wrap point is filled in.
 */
private fun Modifier.conicRim(corner: Dp, width: Dp, lift: Float): Modifier = drawWithCache {
    val css = listOf(0f to 1f, .18f to .25f, .32f to .04f, .5f to .65f, .68f to .08f, .85f to .25f, 1f to 1f)
    val shift = 225f / 360f
    fun alphaAt(p: Float): Float {
        val i = css.indexOfLast { it.first <= p }.coerceIn(0, css.size - 2)
        val (p0, a0) = css[i]
        val (p1, a1) = css[i + 1]
        return a0 + (a1 - a0) * ((p - p0) / (p1 - p0)).coerceIn(0f, 1f)
    }
    val wrap = alphaAt(1f - shift)
    val stops = buildList {
        add(0f to wrap)
        css.dropLast(1).forEach { (p, a) -> add((p + shift) % 1f to a) }
        add(1f to wrap)
    }.sortedBy { it.first }.map { (p, a) -> p to Color.White.a(a * lift) }.toTypedArray()
    val brush = Brush.sweepGradient(*stops, center = Offset(size.width / 2, size.height / 2))
    val w = width.toPx()
    val r = CornerRadius((corner.toPx() - w / 2).coerceAtLeast(0f))
    onDrawWithContent {
        drawContent()
        drawRoundRect(brush, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), cornerRadius = r, style = Stroke(w))
    }
}
