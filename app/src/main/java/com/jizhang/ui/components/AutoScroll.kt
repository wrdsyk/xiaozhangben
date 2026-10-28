package com.jizhang.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jizhang.ui.theme.Motion
import kotlinx.coroutines.delay

/**
 * A row that keeps its width when its content (a big amount) is wider. When it overflows it
 * glides once to the end and back to the start, so the cut-off digits are seen and the leading
 * (largest) ones stay in view; after that the user can drag it sideways. The glide replays when
 * [key] changes (the value, or the screen being shown again). A drag stops it at once.
 */
@Composable
fun AutoScroll(
    key: Any?,
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.Bottom,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val overflows = scroll.maxValue > 0
    LaunchedEffect(key, overflows) {
        if (!overflows) {
            scroll.scrollTo(0)
            return@LaunchedEffect
        }
        delay(PEEK_DELAY_MS) // let the odometer / entrance settle first
        scroll.animateScrollTo(scroll.maxValue, Motion.layout())
        delay(PEEK_HOLD_MS)
        scroll.animateScrollTo(0, Motion.layout())
    }
    Row(
        modifier.horizontalScroll(scroll),
        verticalAlignment = verticalAlignment,
        horizontalArrangement = horizontalArrangement,
        content = content,
    )
}

private const val PEEK_DELAY_MS = 650L
private const val PEEK_HOLD_MS = 900L
