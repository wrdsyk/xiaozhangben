package com.jizhang.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.Motion
import com.jizhang.ui.theme.rememberHaptics
import kotlin.math.roundToInt

/**
 * Small pill switch: the thumb springs across, the track crossfades. With [onToggle] it is a 48dp
 * tall touch target of its own; without, it only shows [on] and the row around it does the tapping.
 */
@Composable
fun PillSwitch(on: Boolean, label: String, onToggle: (() -> Unit)? = null) {
    val c = LedgerTheme.colors
    val haptics = rememberHaptics()
    val x by animateFloatAsState(if (on) 1f else 0f, Motion.layout(), label = "switch")
    val track by animateColorAsState(if (on) c.accent else c.track, Motion.fade(), label = "switchTrack")
    val thumb by animateColorAsState(if (on) c.onAccent else Color.White, Motion.fade(), label = "switchThumb")
    val touch = if (onToggle != null) {
        Modifier.size(width = 56.dp, height = 48.dp).tap(label = label) {
            haptics.tick()
            onToggle()
        }
    } else {
        Modifier.size(width = 56.dp, height = 48.dp)
    }
    Box(touch, contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 40.dp, height = 24.dp).clip(RoundedCornerShape(12.dp)).background(track).padding(3.dp)) {
            Box(
                Modifier
                    .offset { IntOffset((x * 16.dp.toPx()).roundToInt(), 0) }
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(thumb),
            )
        }
    }
}
