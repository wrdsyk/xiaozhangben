package com.jizhang.ui.components

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import com.jizhang.ui.theme.LedgerTheme

/**
 * Three slowly drifting colour orbs and silk ribbons over a gradient, laid out on the
 * prototype's 390×844 canvas and scaled to the screen. This is the blur source for all glass.
 * The loops use infiniteRepeatable(tween): springs can't repeat forever.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val c = LedgerTheme.colors
    val loop = rememberInfiniteTransition("ambient")
    fun drift(ms: Int) = infiniteRepeatable<Float>(tween(ms, easing = EaseInOutSine), RepeatMode.Reverse)
    val a = loop.animateFloat(0f, 1f, drift(16_000), label = "orbA")
    val b = loop.animateFloat(0f, 1f, drift(19_000), label = "orbB")
    val d = loop.animateFloat(0f, 1f, drift(22_000), label = "orbC")
    val silk = loop.animateFloat(0f, 1f, drift(14_000), label = "silk")

    Box(
        modifier.drawBehind {
            val sx = size.width / 390f
            val sy = size.height / 844f
            drawRect(
                Brush.linearGradient(
                    0f to c.bg0, .38f to c.bg1, .68f to c.bg2, 1f to c.bg3,
                    start = Offset(size.width * .3f, 0f), end = Offset(size.width * .7f, size.height),
                ),
            )
            orb(c.orb1, 70f, 90f, 190f, 70f, 70f * a.value, 50f * a.value, 1f + .12f * a.value, sx, sy)
            orb(c.orb2, 360f, 330f, 160f, 72f, -60f * b.value, 70f * b.value, 1f - .08f * b.value, sx, sy)
            orb(c.orb3, 150f, 690f, 210f, 80f, 50f * d.value, -60f * d.value, 1f + .1f * d.value, sx, sy)
            translate(-18f * sx * silk.value, 14f * sy * silk.value) {
                ribbon(c.silkStart, c.silkMid, c.silkEnd, sx, sy)
            }
        },
    )
}

/** A blurred circle: CSS blur(Npx) approximated by a radial falloff of the same reach. */
private fun DrawScope.orb(
    color: Color, cx: Float, cy: Float, r: Float, blur: Float,
    dx: Float, dy: Float, scale: Float, sx: Float, sy: Float,
) {
    val center = Offset((cx + dx) * sx, (cy + dy) * sy)
    val radius = (r * scale + blur) * sx
    val inner = (r * scale - blur) / (r * scale + blur)
    drawCircle(
        Brush.radialGradient(
            0f to color, inner.coerceIn(0f, 1f) to color.copy(alpha = color.alpha * .8f), 1f to Color.Transparent,
            center = center, radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.ribbon(start: Color, mid: Color, end: Color, sx: Float, sy: Float) {
    val brush = Brush.horizontalGradient(listOf(start, mid, end), startX = -40f * sx, endX = 430f * sx)
    fun curve(y0: Float, c1x: Float, c1y: Float, c2x: Float, c2y: Float, y1: Float) = Path().apply {
        moveTo(-40f * sx, y0 * sy)
        cubicTo(c1x * sx, c1y * sy, c2x * sx, c2y * sy, 430f * sx, y1 * sy)
    }
    drawPath(curve(250f, 80f, 170f, 230f, 340f, 210f), brush, style = Stroke(28f * sx, cap = StrokeCap.Round))
    drawPath(curve(320f, 120f, 250f, 250f, 420f, 290f), brush, alpha = .7f, style = Stroke(9f * sx, cap = StrokeCap.Round))
    drawPath(curve(640f, 110f, 560f, 260f, 720f, 600f), brush, alpha = .6f, style = Stroke(18f * sx, cap = StrokeCap.Round))
}
