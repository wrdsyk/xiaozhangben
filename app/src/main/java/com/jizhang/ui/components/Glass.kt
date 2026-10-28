package com.jizhang.ui.components

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.jizhang.ui.theme.LedgerColors
import com.jizhang.ui.theme.LedgerTheme
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.ChromaticAberrationMode
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.SurfaceProfile
import dev.chrisbanes.haze.glass.hazeGlass

/** The ambient background registers itself here; every glass surface blurs it. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * 液态玻璃: draw every surface with Haze's optics shader (specular rim, chromatic edges, ambient
 * response) instead of a plain blur. One flag, one code path; it costs more GPU, so 我的 can turn
 * it off and everything falls back to the cheaper blur.
 */
val LocalLiquidGlass = staticCompositionLocalOf { true }

/** CSS saturate(180%) brightness(1.06), the prototype's backdrop filter. */
private val backdropFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply {
        setToSaturation(1.8f)
        timesAssign(ColorMatrix().apply { setToScale(1.06f, 1.06f, 1.06f, 1f) })
    },
)

private fun LedgerColors.rim() = Brush.linearGradient(
    0f to rimStrong,
    .3f to rimStrong.copy(alpha = rimStrong.alpha * .2f),
    .52f to Color.Transparent,
    .78f to rimMid,
    1f to rimEnd,
)

/**
 * Liquid-glass surface: blurred backdrop, a faint gradient fill, an inner highlight and a
 * gradient rim. [refract] uses Haze's glass shader (Android 13+) for the hero card, nav bar
 * and add button; other surfaces use a plain blur, which is cheaper when many are on screen.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.glass(
    corner: Dp,
    refract: Boolean = false,
    shadow: Boolean = true,
    fill: Brush? = null,
    glint: Boolean = false,
    blur: Dp = 14.dp,
    /** false for surfaces that already sit on a blurred sheet, e.g. keypad keys. */
    backdrop: Boolean = true,
): Modifier {
    val c = LedgerTheme.colors
    val haze = LocalHazeState.current
    val shape = RoundedCornerShape(corner)
    var m = this
    if (shadow) {
        m = m
            .dropShadow(shape, Shadow(radius = 36.dp, color = c.shadow, spread = (-18).dp, offset = DpOffset(0.dp, 14.dp)))
            .dropShadow(shape, Shadow(radius = 8.dp, color = c.shadowSoft, spread = (-4).dp, offset = DpOffset(0.dp, 3.dp)))
    }
    m = m.clip(shape)
    val liquid = LocalLiquidGlass.current
    val canShade = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    if (haze != null && backdrop) {
        val input = HazeInput.Backdrop(haze)
        m = when {
            liquid && canShade -> m.hazeGlass(input, liquidStyle(shape))
            refract && canShade -> m.hazeGlass(
                input,
                GlassStyle {
                    shape(shape)
                    tint(Color.Transparent)
                },
            )
            else -> m.hazeBlur(
                input,
                HazeBlurStyle {
                    blurRadius(blur)
                    noiseFactor(0.03f)
                    colorEffects(listOf(HazeColorEffect.colorFilter(backdropFilter)))
                },
            )
        }
    }
    val highlight = .95f - .55f * c.darkness
    m = m
        .background(fill ?: Brush.linearGradient(listOf(c.glassTop, c.glassBottom), start = Offset.Zero, end = Offset(600f, 900f)))
        // A lit top edge and a dark bottom one: that pair is what makes the pane read as a solid
        // slab rather than a flat tint. Both are thin, so they stay a rim and not a smudge.
        .innerShadow(shape, Shadow(radius = 1.5.dp, color = Color.White.copy(alpha = highlight), offset = DpOffset(0.dp, 1.5.dp)))
        .innerShadow(shape, Shadow(radius = 2.dp, color = c.shadow.copy(alpha = .22f), offset = DpOffset(0.dp, (-1.5).dp)))
        // A soft bloom inside the glass, kept faint: any stronger and it flattens the contrast
        // that the two rims just created.
        .innerShadow(shape, Shadow(radius = 20.dp, color = Color.White.copy(alpha = .14f - .1f * c.darkness)))
    if (glint) m = m.glint(c.glint)
    return m.border(1.5.dp, c.rim(), shape)
}

/**
 * The 液态 profile: Haze's optics shader turned up — a bright specular rim, a little chromatic
 * split at the edges, ambient light picked up from the backdrop, and a soft shaded edge so the
 * pane reads as a solid piece rather than a tinted blur.
 */
private fun liquidStyle(shape: RoundedCornerShape): GlassStyle = GlassStyle {
    shape(shape)
    tint(Color.Transparent)
    specularIntensity(1.35f)
    specularExponent(42f)
    fresnelExponent(3f)
    chromaticAberrationStrength(.45f)
    chromaticAberrationMode(ChromaticAberrationMode.Simple)
    ambientResponse(.9f)
    edgeSoftness(1.dp)
    edgeShadow(Color.Black.copy(alpha = .14f))
    surfaceProfile(SurfaceProfile.Squircle)
    lightPosition(Alignment.TopStart)
}

/** A soft diagonal highlight that sweeps across every eight seconds. */
@Composable
private fun Modifier.glint(color: Color): Modifier {
    val sweep = rememberInfiniteTransition("glint").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "glint",
    )
    return drawWithContent {
        drawContent()
        // Idle for the first 55% of the cycle, then cross from right to left (prototype keyframes).
        val p = ((sweep.value - .55f) / .3f).coerceIn(0f, 1f)
        if (p <= 0f || p >= 1f) return@drawWithContent
        val w = size.width
        val x = w * (1.4f - 1.8f * p)
        drawRect(
            Brush.linearGradient(
                0f to Color.Transparent, .5f to color, 1f to Color.Transparent,
                start = Offset(x - w * .35f, 0f), end = Offset(x + w * .35f, size.height * .4f),
            ),
        )
    }
}

/** Frosted pill / chip without blur: used for small chips that sit on top of glass. */
@Composable
fun Modifier.chip(corner: Dp, strong: Boolean = false): Modifier {
    val c = LedgerTheme.colors
    val shape = RoundedCornerShape(corner)
    return this
        .clip(shape)
        .background(if (strong) c.chipStrong else c.chip)
        .innerShadow(shape, Shadow(radius = 0.dp, color = c.chipEdge, offset = DpOffset(0.dp, 1.dp)))
        .border(0.5.dp, c.chipEdge, shape)
}
