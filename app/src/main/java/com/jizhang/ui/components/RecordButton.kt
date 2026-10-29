package com.jizhang.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.jizhang.ui.theme.LedgerIcons
import com.jizhang.ui.theme.LedgerTheme

/*
 * The + button, from the design canvas's "记一笔 · 圆钮" (按钮与开关 board):
 *
 *   .lit     radial white highlight at (34%, 14%), a tinted glow at the bottom right, and a 160°
 *            gradient from a lifted accent to a deepened one; inset top line and bevel, pink on
 *            the left and blue on the right (dispersion), shade along the bottom
 *   .bezel   a 1.2px ring 3.5px outside the disc, conic from 200°: bright, dark, warm, dark, bright
 *   .plus    2.2 stroke, pressed in: a dark line above a white plus, a light line under a dark one
 *
 * A dark accent makes an obsidian disc (pearl and the other light themes), a light accent a
 * champagne one (dusk and the other dark themes). Everything is static: it only redraws on press.
 */

private val Pink = Color(0xFFFF5A96)
private val Blue = Color(0xFF46AAFF)

private fun Color.a(alpha: Float) = copy(alpha = alpha.coerceIn(0f, 1f))

private fun inset(x: Float, y: Float, blur: Float, spread: Float, color: Color) =
    Shadow(radius = blur.dp, color = color, spread = spread.dp, offset = DpOffset(x.dp, y.dp))

@Composable
fun RecordButton(diameter: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LedgerTheme.colors
    val accent = c.accent
    val light = accent.luminance() > .5f
    // A light disc shades into a deeper tone of its own hue (champagne into bronze, mint into
    // teal), so the warm canvas tones don't muddy other accents; obsidian shades into black.
    val shade = if (light) lerp(accent, Color.Black, .55f) else Color.Black
    Box(
        modifier
            .size(diameter)
            .drawBehind { bezel(accent, light) }
            .dropShadow(CircleShape, Shadow(28.dp, c.accentShadow, spread = (-12).dp, offset = DpOffset(0.dp, 14.dp)))
            .clip(CircleShape)
            .drawWithCache {
                val body = Brush.linearGradient(
                    0f to lerp(accent, Color.White, if (light) .45f else .2f),
                    .52f to accent,
                    1f to lerp(accent, shade, if (light) .2f else .45f),
                    // 160°: from the top left, leaning a little right on the way down.
                    start = Offset(size.width * .32f, 0f), end = Offset(size.width * .68f, size.height),
                )
                onDrawBehind {
                    drawRect(body)
                    ellipse(Offset(size.width * .7f, size.height), size.width * .7f, size.height * .6f, (if (light) lerp(accent, Color.Black, .3f) else Color(0xFF788CBE)).a(if (light) .35f else .22f), .7f)
                    ellipse(Offset(size.width * .34f, size.height * .14f), size.width * .6f, size.height * .46f, Color.White.a(if (light) .75f else .42f), .7f)
                }
            }
            .innerShadow(CircleShape, inset(0f, 1f, .5f, 0f, Color.White.a(if (light) .95f else .55f)))
            .innerShadow(CircleShape, inset(1.5f, 2f, 2f, -1f, Color.White.a(if (light) .9f else .45f)))
            .innerShadow(CircleShape, inset(-1.5f, -2f, 3f, -1f, if (light) shade.a(.25f) else Color.White.a(.12f)))
            .innerShadow(CircleShape, inset(7f, 0f, 8f, -7f, Pink.a(if (light) .55f else .75f)))
            .innerShadow(CircleShape, inset(-7f, 0f, 8f, -7f, Blue.a(if (light) .55f else .8f)))
            .innerShadow(CircleShape, inset(0f, -6f, 10f, -6f, shade.a(if (light) .4f else .6f)))
            .tap(label = "记一笔", pressedScale = .92f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val plus = LedgerIcons.get("plus", 2.2f)
        // The engraving: a dark edge above a white plus, a light edge under a dark one.
        Glyph(
            plus, 26.dp,
            if (light) Color.White.a(.55f) else Color.Black.a(.45f),
            Modifier.offset(y = if (light) 1.dp else (-.5).dp),
        )
        Glyph(plus, 26.dp, c.onAccent, Modifier.align(Alignment.Center))
    }
}

/**
 * The bezel ring just outside the disc, tinted by a light [accent]: CSS `conic-gradient(from 200deg, …)` as a sweep. CSS starts
 * a conic at twelve o'clock and Compose's sweep at three, so the stops turn by 200° − 90° = 110°.
 */
private fun DrawScope.bezel(accent: Color, light: Boolean) {
    val width = 1.2.dp.toPx()
    val radius = size.minDimension / 2 + 3.5.dp.toPx() - width / 2
    val css = if (light) {
        val glow = lerp(accent, Color.White, .8f).a(.95f)
        listOf(0f to glow, .22f to Color.White.a(.14f), .46f to lerp(accent, Color.White, .15f).a(.75f), .72f to Color.White.a(.1f), 1f to glow)
    } else {
        listOf(0f to Color.White, .22f to Color(0xFF3C3A46).a(.28f), .46f to Color(0xFFB09268).a(.75f), .72f to Color(0xFF3C3A46).a(.22f), 1f to Color.White)
    }
    val shift = 110f / 360f
    // The colour at 1 − shift, where the turned sweep wraps.
    val at = 1f - shift
    val i = css.indexOfLast { it.first <= at }.coerceIn(0, css.size - 2)
    val (p0, c0) = css[i]
    val (p1, c1) = css[i + 1]
    val wrap = lerp(c0, c1, ((at - p0) / (p1 - p0)).coerceIn(0f, 1f))
    val stops = buildList {
        add(0f to wrap)
        css.dropLast(1).forEach { (p, col) -> add((p + shift) % 1f to col) }
        add(1f to wrap)
    }.sortedBy { it.first }.toTypedArray()
    val center = Offset(size.width / 2, size.height / 2)
    drawCircle(Brush.sweepGradient(*stops, center = center), radius = radius, center = center, style = Stroke(width))
}

/** An elliptical radial gradient, as CSS draws `radial-gradient(rx ry at center, color, transparent stop)`. */
private fun DrawScope.ellipse(center: Offset, rx: Float, ry: Float, color: Color, stop: Float) {
    if (rx <= 0f || ry <= 0f) return
    val k = ry / rx
    val brush = Brush.radialGradient(0f to color, stop to Color.Transparent, center = center, radius = rx)
    scale(1f, k, pivot = center) {
        val top = center.y - center.y / k
        drawRect(brush, topLeft = Offset(0f, top), size = Size(size.width, size.height / k))
    }
}
