package com.jizhang.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/*
 * 晶透's glass: the lens pipeline of QWEA0/Liquid-Glass-Android (GlassLensRenderer, MIT License,
 * Copyright (c) 2025-2026 pandadog), moved from Views to Compose. The backdrop (the ambient
 * background) is recorded into a GraphicsLayer; each pane draws that layer through a blur and the
 * reference's single AGSL pass: a rounded-box SDF with a bevel band, refraction along the normal
 * that folds the backdrop just inside the edge into a compressed mirror ring, per-channel
 * dispersion, the vibrancy saturation curve, and an edge light of two equal lobes. Android 13+.
 */

/** Where the panes take their backdrop from: the ambient background, recorded as it draws. */
@Stable
class LiquidBackdrop {
    internal var layer: GraphicsLayer? = null
    internal var origin by mutableStateOf(Offset.Zero)

    /** The screens' content, for panes that float over it (the nav bar); see [liquidContent]. */
    internal var content: GraphicsLayer? = null
    internal var contentOrigin by mutableStateOf(Offset.Zero)
}

val LocalLiquidBackdrop = staticCompositionLocalOf<LiquidBackdrop?> { null }

/**
 * True for panes that float over the screens' content and should bend it too. Only the nav bar:
 * it exists only while the tab content is on screen, so it never samples a stale recording, and
 * the cards inside the content never sample their own layer.
 */
val LocalLiquidOverContent = staticCompositionLocalOf { false }

/** Records the tab content for [LocalLiquidOverContent] panes, and draws it as usual. */
@Composable
fun Modifier.liquidContent(backdrop: LiquidBackdrop): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val layer = rememberGraphicsLayer()
    backdrop.content = layer
    return this
        .onGloballyPositioned { backdrop.contentOrigin = it.positionInWindow() }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
}

/** Records what this node draws as the backdrop of every liquid pane, and draws it as usual. */
@Composable
fun Modifier.liquidSource(backdrop: LiquidBackdrop): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val layer = rememberGraphicsLayer()
    backdrop.layer = layer
    return this
        .onGloballyPositioned { backdrop.origin = it.positionInWindow() }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
}

/** The reference's LENS_AGSL, unchanged. */
private const val LensAgsl = """
uniform shader content;
uniform float  margin;
uniform float2 viewSize;
uniform float4 shape1;
uniform float4 radii1;
uniform float4 shape1L;
uniform float  rimSoft;
uniform float4 shape2;
uniform float  radius2;
uniform float  blendK;
uniform float  bevel;
uniform float  refractPx;
uniform float  falloff;
uniform float  refractDir;
uniform float2 sampleLo;
uniform float2 sampleHi;
uniform float  dispersion;
uniform float2 lightDir;
uniform float  specStrength;
uniform float  rimBandMax;
uniform float4 tintColor;
uniform float  adaptiveTint;
uniform float4 glassTint;
uniform float  dimAmount;
uniform float  satFactor;
uniform float  press;
uniform float2 touchPos;
uniform float  touchAmp;

float sdRoundedBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

float sdRoundedBox4(float2 p, float2 b, float4 r) {
    float rx = (p.x > 0.0) ? ((p.y > 0.0) ? r.z : r.y) : ((p.y > 0.0) ? r.w : r.x);
    float2 q = abs(p) - b + rx;
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - rx;
}

float sminPoly(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float lensSDF(float2 p) {
    float d = sdRoundedBox4(p - shape1L.xy, shape1L.zw, radii1);
    if (shape2.z > 0.5) {
        float d2 = sdRoundedBox(p - shape2.xy, shape2.zw, radius2);
        if (blendK > 0.5) {
            d = sminPoly(d, d2, blendK);
        } else {
            d = min(d, d2);
        }
    }
    return d;
}

half4 main(float2 coord) {
    float2 p = coord - float2(margin, margin);
    if (p.x < 0.0 || p.y < 0.0 || p.x > viewSize.x || p.y > viewSize.y) {
        return half4(0.0);
    }
    float d = lensSDF(p);
    float cov = clamp(0.5 - d / 1.5, 0.0, 1.0);
    if (cov <= 0.004) {
        return half4(0.0);
    }
    float2 n = float2(
        lensSDF(p + float2(1.0, 0.0)) - lensSDF(p - float2(1.0, 0.0)),
        lensSDF(p + float2(0.0, 1.0)) - lensSDF(p - float2(0.0, 1.0))
    );
    float nLen = length(n);
    if (nLen > 0.0001) {
        n = n / nLen;
    } else {
        n = float2(0.0, -1.0);
    }
    float t = clamp(-d / max(bevel, 1.0), 0.0, 1.0);
    float edge = 1.0 - t;
    float slope;
    if (falloff > 0.001) {
        float gB = pow(5.0, -falloff);
        slope = (pow(1.0 + 4.0 * t, -falloff) - gB) / (1.0 - gB);
    } else {
        slope = edge * edge;
    }
    float refr = refractPx * (1.0 + 0.6 * press);
    float2 offset = n * (refractDir * slope * refr);
    if (touchAmp > 0.001) {
        float2 tp = p - touchPos;
        float tr = length(tp);
        float sigma = max(max(shape1.z, shape1.w), 1.0);
        float bump = touchAmp * exp(-(tr * tr) / (sigma * sigma * 0.30));
        if (tr > 1.0) {
            offset -= (tp / tr) * (bump * refr * 0.5);
        }
    }
    float2 cR = coord + offset * (1.0 - dispersion * slope);
    float2 cG = coord + offset;
    float2 cB = coord + offset * (1.0 + dispersion * slope);
    float2 lo = sampleLo;
    float2 hi = sampleHi;
    cR = clamp(cR, lo, hi);
    cG = clamp(cG, lo, hi);
    cB = clamp(cB, lo, hi);
    float3 col;
    if (rimSoft > 0.01 && slope > 0.001) {
        float2 sm = n * (rimSoft * slope);
        float2 cR1 = clamp(cR - sm, lo, hi);
        float2 cR2 = clamp(cR + sm, lo, hi);
        float2 cG1 = clamp(cG - sm, lo, hi);
        float2 cG2 = clamp(cG + sm, lo, hi);
        float2 cB1 = clamp(cB - sm, lo, hi);
        float2 cB2 = clamp(cB + sm, lo, hi);
        col = float3(
            (content.eval(cR).r + content.eval(cR1).r + content.eval(cR2).r) / 3.0,
            (content.eval(cG).g + content.eval(cG1).g + content.eval(cG2).g) / 3.0,
            (content.eval(cB).b + content.eval(cB1).b + content.eval(cB2).b) / 3.0
        );
    } else {
        col = float3(
            content.eval(cR).r,
            content.eval(cG).g,
            content.eval(cB).b
        );
    }
    float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
    if (satFactor <= 1.0) {
        col = mix(float3(lum), col, satFactor);
    } else {
        float satNow = max(col.r, max(col.g, col.b)) - min(col.r, min(col.g, col.b));
        float room = 1.0 - smoothstep(0.2, 0.85, satNow);
        float hl = 1.0 - smoothstep(0.75, 0.98, lum);
        float amount = 1.0 + (satFactor - 1.0) * mix(0.3, 1.0, room * hl);
        col = clamp(mix(float3(lum), col, amount), float3(0.0), float3(1.0));
    }
    if (adaptiveTint > 0.5) {
        float lumT = dot(col, float3(0.2126, 0.7152, 0.0722));
        float e = smoothstep(0.35, 0.75, lumT);
        col = mix(col, float3(1.0 - e), 0.14 + 0.08 * e);
    } else {
        col = mix(col, tintColor.rgb, tintColor.a);
    }
    col = col * (1.0 - dimAmount);
    if (glassTint.a > 0.002) {
        float lumTint = dot(col, float3(0.2126, 0.7152, 0.0722));
        float3 absorbed = col * mix(float3(1.0), glassTint.rgb, 0.85);
        float3 scattered = glassTint.rgb * (0.38 * (1.0 - lumTint));
        col = mix(col, clamp(absorbed + scattered, float3(0.0), float3(1.0)), glassTint.a);
    }
    float facing = dot(n, -lightDir);
    float lobeF = pow(max(facing, 0.0), 4.5);
    float lobeB = pow(max(-facing, 0.0), 4.5);
    float bandW = clamp(bevel * 0.3, 2.0, rimBandMax);
    float glowIn = clamp((-d - 1.0) / 2.0, 0.0, 1.0);
    float glow = glowIn * pow(clamp(1.0 - (-d - 3.0) / bandW, 0.0, 1.0), 1.5) * cov;
    float hair = clamp(1.0 - abs(d + 1.0) / 2.0, 0.0, 1.0) * cov;
    float spec = (hair * 0.70 * (lobeF + lobeB) + glow * 0.10 * lobeF)
                 * specStrength * (1.0 - 0.35 * press);
    col += float3(spec);
    col = clamp(col, float3(0.0), float3(1.0));
    return half4(half3(col * cov), half(cov));
}
"""

// The reference's defaults (LiquidGlassView, Regular material), in its pixels on a ~2.75x screen.
private const val RefDensity = 2.75f
private const val BevelPx = 48f
private const val RefractPx = 160f
private const val Falloff = 2f
private const val Dispersion = .10f
private const val Saturation = 1.4f
private const val RimBandMaxPx = 6f
/** Light from the upper left, about 30° above the horizontal (shader takes light → surface). */
private const val LightX = .866f
private const val LightY = .5f

/**
 * Draws the backdrop through the lens, under this node's content, or returns null where it can't:
 * below Android 13, with no backdrop provided, or on a driver that rejects the shader. The lens
 * output carries its own coverage, so it needs no clip; [tint] is the glass's white veil.
 */
@Composable
fun Modifier.liquidGlass(
    corner: Dp,
    /** Laid under the recording: past the screen's edge there is no backdrop, only transparent black. */
    base: Color,
    blur: Dp = 10.dp,
    tint: Float = .14f,
    spec: Float = 1f,
): Modifier? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val backdrop = LocalLiquidBackdrop.current ?: return null
    val overContent = LocalLiquidOverContent.current
    val shader = remember { runCatching { RuntimeShader(LensAgsl) }.getOrNull() } ?: return null
    val layer = rememberGraphicsLayer()
    // The reference's "layerNode": an effect's input is only guaranteed inside its output clip, which
    // on screen is cut to the window, so near the screen's edge the blur read transparent black.
    // Drawn inside a compositing layer the size of the whole recording, the clip is that layer.
    val frame = rememberGraphicsLayer().apply { compositingStrategy = CompositingStrategy.Offscreen }
    val pos = remember { mutableStateOf(Offset.Unspecified) }
    return this
        .onGloballyPositioned { pos.value = it.positionInWindow() }
        .drawWithCache {
            val w = size.width
            val h = size.height
            val k = density / RefDensity
            val blurPx = blur.toPx()
            // Room around the pane for the blur to read real backdrop instead of clamped edge.
            val margin = (max(blurPx * 3f, 32f).toInt() + 15) / 16 * 16
            // Adaptive lens scale: the defaults are for big panels, so a small control would be
            // all edge. Bevel, refraction and the glow band are capped by the short side.
            val minDim = min(w, h)
            val refDim = 110.dp.toPx()
            val bevel = min(BevelPx * k, minDim * .3f).coerceAtLeast(2f)
            val refract = min(RefractPx * k, .7f * minDim * min(1f, minDim / refDim))
            val rimBand = min(RimBandMaxPx * k, minDim * .05f).coerceAtLeast(2f)
            val r = min(corner.toPx(), minDim / 2)
            val effect = runCatching {
                shader.setFloatUniform("margin", margin.toFloat())
                shader.setFloatUniform("viewSize", w, h)
                shader.setFloatUniform("shape1", w / 2, h / 2, w / 2, h / 2)
                shader.setFloatUniform("radii1", r, r, r, r)
                shader.setFloatUniform("shape1L", w / 2, h / 2, w / 2, h / 2)
                shader.setFloatUniform("rimSoft", 0f)
                shader.setFloatUniform("shape2", 0f, 0f, 0f, 0f)
                shader.setFloatUniform("radius2", 0f)
                shader.setFloatUniform("blendK", 0f)
                shader.setFloatUniform("bevel", bevel)
                shader.setFloatUniform("refractPx", refract)
                shader.setFloatUniform("falloff", Falloff)
                shader.setFloatUniform("refractDir", -1f)
                shader.setFloatUniform("sampleLo", margin + 1f, margin + 1f)
                shader.setFloatUniform("sampleHi", margin + w - 1f, margin + h - 1f)
                shader.setFloatUniform("dispersion", Dispersion)
                shader.setFloatUniform("lightDir", LightX, LightY)
                shader.setFloatUniform("specStrength", spec)
                shader.setFloatUniform("rimBandMax", rimBand)
                shader.setFloatUniform("tintColor", 1f, 1f, 1f, tint)
                shader.setFloatUniform("adaptiveTint", 0f)
                shader.setFloatUniform("glassTint", 0f, 0f, 0f, 0f)
                shader.setFloatUniform("dimAmount", 0f)
                shader.setFloatUniform("satFactor", Saturation)
                shader.setFloatUniform("press", 0f)
                shader.setFloatUniform("touchPos", 0f, 0f)
                shader.setFloatUniform("touchAmp", 0f)
                val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
                if (blurPx > .01f) {
                    RenderEffect.createChainEffect(lens, RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP))
                } else {
                    lens
                }
            }.getOrNull()
            layer.renderEffect = effect?.asComposeRenderEffect()
            val recW = ceil(w).toInt() + 2 * margin
            val recH = ceil(h).toInt() + 2 * margin
            onDrawBehind {
                val src = backdrop.layer
                val at = pos.value
                if (effect == null || src == null || at == Offset.Unspecified) return@onDrawBehind
                val content = if (overContent) backdrop.content else null
                val bg = backdrop.origin
                val co = backdrop.contentOrigin
                layer.record(IntSize(recW, recH)) {
                    drawRect(base)
                    translate(margin - (at.x - bg.x), margin - (at.y - bg.y)) { drawLayer(src) }
                    if (content != null) translate(margin - (at.x - co.x), margin - (at.y - co.y)) { drawLayer(content) }
                }
                frame.record(IntSize(recW, recH)) { drawLayer(layer) }
                frame.topLeft = IntOffset(-margin, -margin)
                drawLayer(frame)
            }
        }
}
