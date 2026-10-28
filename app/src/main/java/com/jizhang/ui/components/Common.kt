package com.jizhang.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.domain.BudgetLevel
import com.jizhang.domain.Category
import com.jizhang.ui.budget.levelColor
import com.jizhang.ui.theme.LedgerIcons
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.Motion
import com.jizhang.ui.theme.numberStyle
import kotlinx.coroutines.delay

/** Tap target with the prototype's press feedback (scale to 93%) instead of a ripple. */
@Composable
fun Modifier.tap(
    label: String? = null,
    pressedScale: Float = .93f,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, Motion.layout(), label = "press")
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interaction, indication = null, enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
}

/** Whether the launch entrance has already played; screens that come back don't replay it. */
val LocalEntrancePlayed = compositionLocalOf { false }

/** Cards rise into place on launch, staggered by [index] (prototype: rise 760ms). */
@Composable
fun Modifier.rise(index: Int): Modifier {
    if (LocalEntrancePlayed.current) return this
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(80L * index)
        p.animateTo(1f, Motion.layout())
    }
    return graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 18.dp.toPx()
        val s = .985f + .015f * v
        scaleX = s
        scaleY = s
    }
}

@Composable
fun Glyph(icon: ImageVector, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** Rounded category tile: tinted background, coloured glyph, the prototype's tile highlight. */
@Composable
fun CategoryTile(
    category: Category,
    size: Dp,
    corner: Dp,
    modifier: Modifier = Modifier,
    background: Color? = null,
    glyph: Color? = null,
    iconSize: Dp = size / 2,
) {
    val c = LedgerTheme.colors
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(background ?: c.tint(category))
            .innerShadow(shape, Shadow(radius = 1.dp, color = Color.White.copy(alpha = .85f - .6f * c.darkness), offset = DpOffset(0.dp, 1.dp))),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(LedgerIcons.of(category), iconSize, glyph ?: c.hue(category))
    }
}

/**
 * Colour band of the budget ring for [percent] used (the whole number the ring shows, so the
 * colour and the digits never disagree): green up to 50%, yellow up to 80%, red from 80% on and
 * beyond 100%, the same bands as [BudgetLevel] and the budget bars.
 */
fun ringLevel(percent: Int): BudgetLevel = when {
    percent <= 50 -> BudgetLevel.Green
    percent < 80 -> BudgetLevel.Yellow
    else -> BudgetLevel.Red
}

/**
 * Budget ring (not a chart): a gradient arc of the share of budget used, in the traffic-light
 * colour of [ringLevel] (the same greens, yellows and reds as the budget bars).
 */
@Composable
fun BudgetRing(fraction: Float, modifier: Modifier = Modifier, label: String = "预算已用") {
    val c = LedgerTheme.colors
    val sweep by animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.layout(), label = "ring")
    val percent = (fraction * 100).toInt()
    val tone by animateColorAsState(c.levelColor(ringLevel(percent)), Motion.fade(), label = "ringLevel")
    Box(modifier.size(92.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(92.dp)) {
            val stroke = 9.dp.toPx()
            val inset = 8.dp.toPx() // prototype: r = 38 inside a 92 box
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(c.track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (sweep > 0f) {
                // Two tones of one colour (soft at the start, full at the far side), like the bars.
                val soft = tone.copy(alpha = .6f)
                rotate(-90f) {
                    drawArc(
                        Brush.sweepGradient(listOf(soft, tone, soft)),
                        0f, 360f * sweep.coerceAtLeast(.001f), false, topLeft, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$percent%", style = numberStyle(19.sp, FontWeight.Normal, 0f), color = c.ink)
            Text(label, fontSize = 11.sp, color = c.sub)
        }
    }
}
