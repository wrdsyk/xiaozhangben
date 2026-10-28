package com.jizhang.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.jizhang.ui.theme.Motion

/**
 * Odometer-style number: each character rolls on its own, so only digits that change move.
 * Slots are keyed from the right, so the units digit keeps its slot when the number grows a
 * digit (or a thousands comma appears) and only the new leading digit enters.
 */
@Composable
fun Odometer(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        val n = text.length
        text.forEachIndexed { i, ch ->
            key(n - i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically(Motion.offset) { h -> if (up) h else -h } + fadeIn(Motion.fade()))
                            .togetherWith(slideOutVertically(Motion.offset) { h -> if (up) -h else h } + fadeOut(Motion.fade()))
                            .using(SizeTransform(clip = true) { _, _ -> Motion.size })
                    },
                    label = "odometer",
                ) { c -> Text(c.toString(), style = style, color = color) }
            }
        }
    }
}

/**
 * Typed keypad input: keyed from the left, each newly typed character pops in (rises and
 * scales up from 60%) while existing characters stay still.
 */
@Composable
fun TypedAmount(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        text.forEachIndexed { i, ch ->
            key(i, ch) {
                val p = remember { Animatable(0f) }
                LaunchedEffect(Unit) { p.animateTo(1f, Motion.layout()) }
                Text(
                    text = if (ch == '-') "−" else ch.toString(),
                    style = style,
                    color = color,
                    modifier = Modifier.graphicsLayer {
                        val v = p.value
                        alpha = v.coerceIn(0f, 1f)
                        val s = .6f + .4f * v
                        scaleX = s
                        scaleY = s
                        translationY = (1f - v) * 16.dp.toPx()
                    },
                )
            }
        }
    }
}
