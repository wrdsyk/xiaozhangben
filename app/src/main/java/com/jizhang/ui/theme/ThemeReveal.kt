package com.jizhang.ui.theme

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Telegram-style theme switch. Fading every colour frame by frame recomposes the whole app (screens,
 * Haze blurs, charts) for as long as the fade lasts. Instead the screen is frozen into a bitmap,
 * the new theme is applied in one step underneath it, and the bitmap is drawn on top with a round
 * hole that grows from the tapped swatch until the new theme is fully uncovered. Only that overlay
 * redraws while the circle expands.
 *
 * The overlay is drawn inside the recorded layer, so freezing the screen again (a second tap while
 * the first reveal is still running) captures exactly what is on screen and carries on from there.
 */
@Stable
class ThemeReveal internal constructor(private val layer: GraphicsLayer) {
    /** The screen as it looked before the switch; null whenever no reveal is running. */
    private var shot by mutableStateOf<ImageBitmap?>(null)
    private var origin by mutableStateOf(Offset.Zero)
    private var radius by mutableFloatStateOf(0f)

    /** Distance from [origin] to the farthest screen corner: the radius that uncovers everything. */
    private var reach by mutableFloatStateOf(1f)

    /** Whether the frozen screen is dark behind the status bar / navigation bar. */
    private var shotTopDark by mutableStateOf(false)
    private var shotBottomDark by mutableStateOf(false)
    private var job: Job? = null

    /**
     * Freezes the screen, runs [apply] (which must switch the theme and return once the new colours
     * are in effect) and reveals the result from [origin]. [currentDark] is whether the theme being
     * left is dark. A second call cancels a reveal that is still running.
     */
    fun start(scope: CoroutineScope, origin: Offset, currentDark: Boolean, apply: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch { reveal(origin, currentDark, apply) }
    }

    /**
     * Whether the surface behind the status bar ([top]) or the navigation bar is dark right now.
     * The bar icons flip when the circle reaches the bar, not the moment the theme changes.
     * Read it inside snapshotFlow: it follows the animation without recomposing anything.
     */
    fun barIsDark(top: Boolean, targetDark: Boolean): Boolean {
        if (shot == null) return targetDark
        val size = layer.size
        val edge = Offset(size.width / 2f, if (top) 0f else size.height.toFloat())
        return when {
            radius >= (edge - origin).getDistance() -> targetDark
            top -> shotTopDark
            else -> shotBottomDark
        }
    }

    private suspend fun reveal(at: Offset, currentDark: Boolean, apply: suspend () -> Unit) {
        val size = layer.size
        val frozen = if (size.width > 0 && size.height > 0) {
            try {
                layer.toImageBitmap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        if (frozen == null) {
            // Nothing to freeze (not drawn yet): switch without the show.
            shot = null
            apply()
            return
        }
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        val far = maxOf(
            (Offset(0f, 0f) - at).getDistance(), (Offset(w, 0f) - at).getDistance(),
            (Offset(0f, h) - at).getDistance(), (Offset(w, h) - at).getDistance(),
        )
        // Written together with no suspension point between, so no frame can show a half-updated cover.
        shotTopDark = barIsDark(top = true, targetDark = currentDark)
        shotBottomDark = barIsDark(top = false, targetDark = currentDark)
        origin = at
        reach = far
        radius = 0f
        shot = frozen

        withFrameNanos { } // the frozen frame is on screen before anything moves underneath it
        apply()
        withFrameNanos { }
        withFrameNanos { } // the new theme has been composed and drawn behind the cover

        coroutineScope {
            // Aim past the corner so the circle is still moving when it gets there, then stop the
            // spring's long tail as soon as everything is uncovered.
            val grow = launch { animate(0f, far * 1.1f, animationSpec = Motion.theme()) { v, _ -> radius = v } }
            snapshotFlow { radius >= far }.first { it }
            grow.cancel()
        }
        shot = null
    }

    internal fun DrawScope.drawCover() {
        val frozen = shot ?: return
        val r = radius
        if (r <= .5f) {
            drawImage(frozen)
            return
        }
        val hole = Path().apply { addOval(Rect(origin, r)) }
        clipPath(hole, ClipOp.Difference) { drawImage(frozen) }
        val rim = .5f * (1f - r / reach).coerceIn(0f, 1f)
        if (rim > 0f) drawCircle(Color.White.copy(alpha = rim), r, origin, style = Stroke(1.5.dp.toPx()))
    }

    internal fun record(scope: ContentDrawScope) = with(scope) {
        layer.record {
            this@with.drawContent()
            drawCover()
        }
        drawLayer(layer)
    }
}

@Composable
fun rememberThemeReveal(): ThemeReveal {
    val layer = rememberGraphicsLayer()
    return remember(layer) { ThemeReveal(layer) }
}

/** Records everything below into the reveal's layer and draws the frozen cover over it. */
fun Modifier.themeReveal(reveal: ThemeReveal): Modifier = drawWithContent { reveal.record(this) }
