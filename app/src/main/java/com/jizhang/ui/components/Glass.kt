package com.jizhang.ui.components

import android.content.Context
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.jizhang.BuildConfig
import com.jizhang.data.GlassFinish
import com.jizhang.ui.theme.LedgerColors
import com.jizhang.ui.theme.LedgerTheme
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.min

/** The ambient background registers itself here; every glass surface blurs it. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** The owner's pick in 我的; [glass], [chip] and [liftedPill] read it. */
val LocalGlassFinish = staticCompositionLocalOf { GlassFinish.Crystal }

private const val GlintSweepMs = 2_400
private const val GlintRestMs = 5_600L

/**
 * How much of the backdrop Haze renders. A blur this wide leaves little detail to lose, so half the
 * width and height (a quarter of the pixels) costs a quarter of the GPU work. The lens surfaces keep
 * a little more, for the edge where the refraction shows. Raise these if a blur looks blocky.
 */
private val BlurQuality = HazePerformanceMode.Performance
private val GlassQuality = HazePerformanceMode.Fixed(0.25f)

/** DEBUG ONLY. key=value lines in files/glass_tune.txt override the look, to tune it without a rebuild. */
internal object Tune {
    private var values: Map<String, Float>? = null

    fun of(context: Context): Map<String, Float> {
        values?.let { return it }
        val read = if (!BuildConfig.DEBUG) emptyMap() else runCatching {
            File(context.filesDir, "glass_tune.txt").readLines().mapNotNull { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size != 2) return@mapNotNull null
                parts[1].trim().toFloatOrNull()?.let { parts[0].trim() to it }
            }.toMap()
        }.getOrDefault(emptyMap())
        values = read
        return read
    }
}

private fun Color.mul(f: Float) = copy(alpha = (alpha * f).coerceIn(0f, 1f))

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
    if (LocalGlassFinish.current == GlassFinish.Frost) return frost(corner, shadow, fill, blur, backdrop)
    val c = LedgerTheme.colors
    val haze = LocalHazeState.current
    val t = Tune.of(LocalContext.current)
    fun p(key: String, default: Float) = t[key] ?: default
    val d = c.darkness
    val shape = RoundedCornerShape(corner)
    val own = !refract || p("gOwn", 1f) > .5f
    var m = this
    if (shadow) {
        val a = p("shA", .7f)
        m = m
            .dropShadow(shape, Shadow(radius = p("shR", 36f).dp, color = c.shadow.mul(a), spread = p("shS", -18f).dp, offset = DpOffset(0.dp, p("shY", 14f).dp)))
            .dropShadow(shape, Shadow(radius = p("sh2R", 8f).dp, color = c.shadowSoft.mul(a * p("sh2A", 1f)), spread = (-4).dp, offset = DpOffset(0.dp, p("sh2Y", 3f).dp)))
    }
    m = m.clip(shape)
    if (haze != null && backdrop) {
        val input = HazeInput.Backdrop(haze)
        m = when {
            refract && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val style = remember(shape, t) {
                    GlassStyle {
                        shape(shape)
                        tint(Color.Transparent)
                        t["gWhite"]?.let { whitePoint(it) }
                        t["gSpec"]?.let { specularIntensity(it) }
                        t["gEdge"]?.let { edgeShadow(Color.Black.copy(alpha = it)) }
                        t["gChroma"]?.let { chromaMultiplier(it) }
                        t["gAmb"]?.let { ambientResponse(it) }
                        t["gContrast"]?.let { contrast(it) }
                        t["gSoft"]?.let { edgeSoftness(it.dp) }
                        if (t["gLight"] == 1f) lightPosition(Alignment.TopStart)
                    }
                }
                m.hazeGlass(input, style, performanceMode = GlassQuality)
            }
            else -> {
                val filter = remember(t, d) {
                    ColorFilter.colorMatrix(
                        ColorMatrix().apply {
                            setToSaturation(t["sat"] ?: (1.5f + .3f * d))
                            val b = t["bri"] ?: 1.06f
                            timesAssign(ColorMatrix().apply { setToScale(b, b, b, 1f) })
                        },
                    )
                }
                m.hazeBlur(
                    input,
                    HazeBlurStyle {
                        blurRadius(blur * p("blurMul", 1f))
                        noiseFactor(p("noise", .03f))
                        colorEffects(listOf(HazeColorEffect.colorFilter(filter)))
                    },
                    performanceMode = BlurQuality,
                )
            }
        }
    }
    m = m.background(
        fill ?: Brush.linearGradient(
            listOf(c.glassTop.mul(p("fillT", 1f - .4f * (1f - d))), c.glassBottom.mul(p("fillB", 1f - .3f * (1f - d)))),
            start = Offset.Zero, end = Offset(600f, 900f),
        ),
    )
    // Volume: a little light at the top of the pane and a little shade at the bottom.
    val lightTop = p("lightTop", .10f * (1f - .5f * d))
    val shadeBot = p("shadeBot", .05f * (1f - .3f * d))
    if (lightTop > 0f || shadeBot > 0f) {
        m = m.background(
            Brush.verticalGradient(
                0f to Color.White.mul(lightTop),
                .5f to Color.Transparent,
                1f to c.shadow.copy(alpha = shadeBot),
            ),
        )
    }
    val sheenA = p("sheenA", .22f)
    if (sheenA > 0f) {
        m = m.sheen(Color.White.mul(sheenA * (1f - .6f * d)), p("sheenH", 48f).dp, diagonal = p("sheenMode", 0f) > .5f, reach = p("sheenReach", .55f))
    }
    if (own) {
        val hiA = p("hiA", 0f)
        val dkA = p("dkA", .14f)
        val bloomA = p("bloomA", 0f)
        if (hiA > 0f) {
            m = m.innerShadow(shape, Shadow(radius = p("hiR", 1.5f).dp, color = Color.White.copy(alpha = hiA), offset = DpOffset(p("hiX", 0f).dp, p("hiY", 1.5f).dp)))
        }
        if (dkA > 0f) {
            m = m.innerShadow(shape, Shadow(radius = p("dkR", 2f).dp, color = c.shadow.copy(alpha = dkA), offset = DpOffset(p("dkX", 0f).dp, p("dkY", -1.5f).dp)))
        }
        if (bloomA > 0f) {
            m = m.innerShadow(shape, Shadow(radius = p("bloomR", 20f).dp, color = Color.White.copy(alpha = bloomA)))
        }
    }
    if (glint) m = m.glint(c.glint)
    if (!own) return m
    return if (p("rimMode", 1f) < .5f) {
        m.border(p("rimW", 1.5f).dp, c.rim(), shape)
    } else {
        m.rim(
            corner, p("rimW", .8f).dp,
            lit = Color.White.copy(alpha = p("rimLit", .95f - .35f * d)),
            dark = c.shadow.mul(p("rimDark", .3f)),
            bright = Color.White.copy(alpha = p("rimBr", .6f - .25f * d)),
            fadeLit = p("rimFade", .34f),
            hair = (if (d > .5f) Color.Black else c.ink).mul(p("hairA", .09f)),
            hairW = p("hairW", .5f).dp,
        )
    }
}

/**
 * Frosted glass, after the owner's reference: a pane clear enough to show the colours behind it,
 * a wide blur, an even 1dp light edge with a soft glow running in from it, and a little light in
 * the upper left corner. Every surface takes a plain blur (no lens shader) and nothing moves.
 */
@Composable
private fun Modifier.frost(corner: Dp, shadow: Boolean, fill: Brush?, blur: Dp, backdrop: Boolean): Modifier {
    val c = LedgerTheme.colors
    val haze = LocalHazeState.current
    val d = c.darkness
    val shape = RoundedCornerShape(corner)
    var m = this
    if (shadow) {
        m = m
            .dropShadow(shape, Shadow(radius = 40.dp, color = c.shadow.mul(.75f), spread = (-22).dp, offset = DpOffset(0.dp, 18.dp)))
            .dropShadow(shape, Shadow(radius = 3.dp, color = c.shadowSoft.mul(.35f), offset = DpOffset(0.dp, 1.dp)))
    }
    m = m.clip(shape)
    if (haze != null && backdrop) {
        val filter = remember(d) {
            ColorFilter.colorMatrix(
                ColorMatrix().apply {
                    setToSaturation(1.4f)
                    val b = 1.04f + .06f * d
                    timesAssign(ColorMatrix().apply { setToScale(b, b, b, 1f) })
                },
            )
        }
        m = m.hazeBlur(
            HazeInput.Backdrop(haze),
            HazeBlurStyle {
                blurRadius(if (blur > 24.dp) blur else 24.dp)
                noiseFactor(0f)
                colorEffects(listOf(HazeColorEffect.colorFilter(filter)))
            },
            performanceMode = BlurQuality,
        )
    }
    m = if (fill != null) m.background(fill) else m.drawWithCache {
        // Clearer towards the bottom, falling slightly off the vertical like the light.
        val pane = Brush.linearGradient(
            0f to c.glassTop.copy(alpha = .34f - .18f * d),
            .5f to c.glassBottom.copy(alpha = .14f - .08f * d),
            1f to c.glassBottom.copy(alpha = .1f - .06f * d),
            start = Offset.Zero, end = Offset(size.width * .35f, size.height),
        )
        val light = Brush.linearGradient(
            0f to Color.White.copy(alpha = .22f - .1f * d), 1f to Color.Transparent,
            start = Offset.Zero, end = Offset(size.width * .42f, size.height * .42f),
        )
        onDrawBehind {
            drawRect(pane)
            drawRect(light)
        }
    }
    // A faint glow running in from the edge; at full strength it read as a white frame on pale themes.
    m = m.innerShadow(shape, Shadow(radius = 18.dp, color = Color.White.copy(alpha = .16f - .1f * d)))
    return m.drawWithCache {
        // A hairline lit only at the upper left and, more faintly, the lower right; between them it
        // all but vanishes, so the pane has an edge without a line drawn round it.
        val w = .6.dp.toPx()
        val edge = Brush.linearGradient(
            0f to Color.White.copy(alpha = .75f - .3f * d),
            .3f to Color.White.copy(alpha = .12f),
            .7f to Color.White.copy(alpha = .06f),
            1f to Color.White.copy(alpha = .4f - .2f * d),
            start = Offset.Zero, end = Offset(size.width, size.height),
        )
        val radius = CornerRadius((corner.toPx() - w / 2).coerceAtLeast(0f))
        onDrawWithContent {
            drawContent()
            drawRoundRect(edge, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), cornerRadius = radius, style = Stroke(w))
        }
    }
}

/**
 * A rim lit from the top left: bright where the light grazes the upper left edge, a shaded edge
 * along the lower right, and a small catch-light in the far corner. Nothing runs round the whole
 * pane, so it reads as the edge of a slab and not as an outline drawn on it.
 */
private fun Modifier.rim(
    corner: Dp, width: Dp, lit: Color, dark: Color, bright: Color, fadeLit: Float, hair: Color, hairW: Dp,
): Modifier =
    drawWithCache {
        val hw = if (hair.alpha > 0f) hairW.toPx() else 0f
        val w = width.toPx()
        val brush = Brush.linearGradient(
            0f to lit,
            fadeLit * .5f to lit.mul(.4f),
            fadeLit to Color.Transparent,
            .6f to Color.Transparent,
            .8f to dark,
            .93f to bright.mul(.5f),
            1f to bright,
            start = Offset.Zero, end = Offset(size.width, size.height),
        )
        val cr = corner.toPx()
        onDrawWithContent {
            drawContent()
            // A neutral hairline on the very edge gives a pale pane something to stand against; the
            // lit rim sits just inside it.
            if (hw > 0f) {
                drawRoundRect(
                    hair,
                    topLeft = Offset(hw / 2, hw / 2),
                    size = Size(size.width - hw, size.height - hw),
                    cornerRadius = CornerRadius((cr - hw / 2).coerceAtLeast(0f)),
                    style = Stroke(hw),
                )
            }
            val o = hw + w / 2
            drawRoundRect(
                brush,
                topLeft = Offset(o, o),
                size = Size(size.width - 2 * o, size.height - 2 * o),
                cornerRadius = CornerRadius((cr - o).coerceAtLeast(0f)),
                style = Stroke(w),
            )
        }
    }

/** A soft light reflected on the upper part of the pane, [height] tall at most. */
private fun Modifier.sheen(color: Color, height: Dp, diagonal: Boolean, reach: Float): Modifier = drawWithCache {
    if (diagonal) {
        // Light spilling in from the upper left corner and dying out along the diagonal.
        val brush = Brush.linearGradient(
            0f to color, 1f to Color.Transparent,
            start = Offset.Zero, end = Offset(size.width * reach, size.height * reach),
        )
        onDrawBehind { drawRect(brush) }
    } else {
        val h = min(height.toPx(), size.height * .5f)
        val brush = Brush.verticalGradient(0f to color, 1f to Color.Transparent, startY = 0f, endY = h)
        onDrawBehind { drawRect(brush, size = Size(size.width, h)) }
    }
}

/**
 * A soft diagonal highlight that sweeps across every eight seconds: 2.4 s of sweep, 5.6 s of rest
 * (the prototype's keyframes). Frames are asked for only while it crosses the card. An endless
 * transition would redraw, and re-blur, this big glass card at every frame of the rest as well,
 * which is most of the time and the one thing on the home screen that never stood still.
 */
@Composable
private fun Modifier.glint(color: Color): Modifier {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(GlintRestMs)
            sweep.animateTo(1f, tween(GlintSweepMs, easing = LinearEasing))
            sweep.snapTo(0f)
        }
    }
    return drawWithContent {
        drawContent()
        // Right to left across the card; at rest (0) or done (1) there is nothing to draw.
        val p = sweep.value
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

/**
 * The finish for a solid, dark button: the glass's lit rim, so a button and a card share one light
 * and the button gets a lit upper edge instead of a white line drawn all round it.
 */
fun Modifier.solidEdge(corner: Dp): Modifier =
    rim(
        corner, .8.dp,
        lit = Color.White.copy(alpha = .5f), dark = Color.Transparent, bright = Color.White.copy(alpha = .2f),
        fadeLit = .34f, hair = Color.Transparent, hairW = 0.dp,
    )

/**
 * A white, lifted pill: the selected segment of a switch, or the nav bar's sliding lens. Lighter at
 * the top than at the bottom, lit along its upper edge and shaded below.
 */
@Composable
fun Modifier.liftedPill(corner: Dp, base: Color): Modifier {
    val c = LedgerTheme.colors
    val shape = RoundedCornerShape(corner)
    if (LocalGlassFinish.current == GlassFinish.Frost) {
        val d = c.darkness
        return this
            .clip(shape)
            .background(base)
            .innerShadow(shape, Shadow(radius = 10.dp, color = Color.White.copy(alpha = .2f - .14f * d)))
            .border(.5.dp, Color.White.copy(alpha = .55f - .35f * d), shape)
    }
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(base.mul(1.08f), base.mul(.9f))))
        .innerShadow(shape, Shadow(radius = 1.dp, color = Color.White.copy(alpha = .9f - .6f * c.darkness), offset = DpOffset(0.dp, 1.dp)))
        .innerShadow(shape, Shadow(radius = 1.5.dp, color = c.shadow.mul(.12f), offset = DpOffset(0.dp, (-1).dp)))
}

/** Frosted pill / chip without blur: used for small chips that sit on top of glass. */
@Composable
fun Modifier.chip(corner: Dp, strong: Boolean = false): Modifier {
    val c = LedgerTheme.colors
    val shape = RoundedCornerShape(corner)
    val t = Tune.of(LocalContext.current)
    val d = c.darkness
    val base = if (strong) c.chipStrong else c.chip
    if (LocalGlassFinish.current == GlassFinish.Frost) {
        // A small pane of the same frosted glass: clear, an even light edge, a glow running in.
        return this
            .clip(shape)
            .background(Color.White.copy(alpha = (if (strong) .38f else .3f) - .22f * d))
            .innerShadow(shape, Shadow(radius = 8.dp, color = Color.White.copy(alpha = .16f - .12f * d)))
            .border(.5.dp, Color.White.copy(alpha = .35f - .22f * d), shape)
    }
    return if ((t["chipMode"] ?: 1f) < .5f) {
        this
            .clip(shape)
            .background(base)
            .innerShadow(shape, Shadow(radius = 0.dp, color = c.chipEdge, offset = DpOffset(0.dp, 1.dp)))
            .border(0.5.dp, c.chipEdge, shape)
    } else {
        // A raised key: lighter at the top than at the bottom, lit along its upper edge, shaded
        // below, and lifted off the pane by a small shadow. Nothing runs round the whole edge.
        this
            .dropShadow(shape, Shadow(radius = 6.dp, color = c.shadowSoft.mul(.7f), spread = (-1).dp, offset = DpOffset(0.dp, 2.dp)))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(base.mul(1.4f), base.mul(.7f))))
            .innerShadow(shape, Shadow(radius = 1.dp, color = Color.White.copy(alpha = .9f - .6f * d), offset = DpOffset(0.dp, 1.dp)))
            .innerShadow(shape, Shadow(radius = 1.5.dp, color = c.shadow.mul(.15f), offset = DpOffset(0.dp, (-1).dp)))
    }
}
