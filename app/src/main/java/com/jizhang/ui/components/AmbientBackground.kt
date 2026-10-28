package com.jizhang.ui.components

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
import com.jizhang.ui.theme.LedgerTheme

/**
 * Three colour orbs and silk ribbons over a gradient, laid out on the prototype's 390×844 canvas
 * and scaled to the screen. This is the blur source for all glass.
 *
 * Deliberately still. While the orbs drifted, this layer changed every frame, so every glass
 * surface re-blurred every frame and the GPU never idled — the phone ran hot for a screen that
 * looked static. A fixed backdrop gives the same depth and lets each blur be computed once.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val c = LedgerTheme.colors
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
            orb(c.orb1, 70f, 90f, 190f, 70f, sx, sy)
            orb(c.orb2, 360f, 330f, 160f, 72f, sx, sy)
            orb(c.orb3, 150f, 690f, 210f, 80f, sx, sy)
            ribbon(c.silkStart, c.silkMid, c.silkEnd, sx, sy)
        },
    )
}

/** A blurred circle: CSS blur(Npx) approximated by a radial falloff of the same reach. */
private fun DrawScope.orb(
    color: Color, cx: Float, cy: Float, r: Float, blur: Float, sx: Float, sy: Float,
) {
    val center = Offset(cx * sx, cy * sy)
    val radius = (r + blur) * sx
    val inner = (r - blur) / (r + blur)
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
