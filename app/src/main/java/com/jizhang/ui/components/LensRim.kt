package com.jizhang.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The edge light of 晶透, after QWEA0/Liquid-Glass-Android's lens shader (itself measured off iOS 26).
 * The outward normal comes from the rounded rectangle's distance field, and two equal lobes of
 * |dot(normal, light)|^4.5 light it: one facing the light in the upper left, one on the far inner
 * wall at the lower right. Half strength 30° off the axis and nothing at 45°, so the top edge and
 * the right edge stay almost dark and no line runs round the pane. The lobes drive a thin line on
 * the very edge; the lit side adds a short glow running inwards. A little dispersion shifts the
 * line's red inwards and blue outwards, a faint spectral fringe that still reads as white.
 */
private const val RimAgsl = """
uniform float2 size;
uniform float radius;
uniform float2 light;
uniform float line;
uniform float glow;
uniform float glowAlpha;
uniform float back;
uniform float disp;
uniform float4 tint;

float band(float inside, float w) {
    return smoothstep(-0.6, 0.6, inside) * (1.0 - smoothstep(w - 0.6, w + 0.6, inside));
}

half4 main(float2 xy) {
    float2 b = size * 0.5;
    float2 p = xy - b;
    float2 q = abs(p) - b + radius;
    float inside = radius - length(max(q, 0.0)) - min(max(q.x, q.y), 0.0);
    if (inside > line * (1.0 + disp) + glow + 1.0) return half4(0.0);
    float2 s = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
    float2 n;
    if (q.x > 0.0 && q.y > 0.0) {
        n = normalize(q) * s;
    } else if (q.x > q.y) {
        n = float2(s.x, 0.0);
    } else {
        n = float2(0.0, s.y);
    }
    float k = dot(n, light);
    float lit = pow(max(k, 0.0), 4.5);
    float lobe = lit + back * pow(max(-k, 0.0), 4.5);
    // Red a hair further in, blue a hair further out: a fringe on both sides that averages to white.
    float s = disp * line;
    float3 rgb = float3(band(inside - s, line), band(inside, line), band(inside + s, line)) * lobe;
    float g = 0.0;
    if (inside > line) g = pow(max(1.0 - (inside - line) / glow, 0.0), 1.5) * lit * glowAlpha;
    float3 col = clamp(rgb + g, 0.0, 1.0);
    float a = max(max(col.r, col.g), col.b);
    return half4(half3(col * tint.rgb * tint.a), half(a * tint.a));
}
"""

/** Light from the upper left, 30° above the horizontal, as a unit vector in screen space. */
private val LightX = -cos(Math.toRadians(30.0)).toFloat()
private val LightY = -sin(Math.toRadians(30.0)).toFloat()

/**
 * Draws the lens edge light over the content, or returns null where it can't (below Android 13,
 * or a driver that rejects the shader); the caller then keeps its own rim.
 */
@Composable
fun Modifier.lensRim(
    corner: Dp,
    color: Color,
    /** Strength of the lower right lobe relative to the lit one; the reference keeps them equal. */
    back: Float = 1f,
    glowAlpha: Float = .55f,
    line: Dp = .75.dp,
    glow: Dp = 2.5.dp,
): Modifier? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val shader = remember { runCatching { RuntimeShader(RimAgsl) }.getOrNull() } ?: return null
    return drawWithCache {
        val short = min(size.width, size.height)
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("radius", min(corner.toPx(), short / 2))
        shader.setFloatUniform("light", LightX, LightY)
        shader.setFloatUniform("line", line.toPx())
        // Small controls keep their glow inside a sliver of their short side, or they'd be all edge.
        shader.setFloatUniform("glow", min(glow.toPx(), short * .08f).coerceAtLeast(1f))
        shader.setFloatUniform("glowAlpha", glowAlpha)
        shader.setFloatUniform("back", back)
        shader.setFloatUniform("disp", .2f)
        shader.setFloatUniform("tint", color.red, color.green, color.blue, color.alpha)
        val brush = ShaderBrush(shader)
        onDrawWithContent {
            drawContent()
            drawRect(brush)
        }
    }
}
