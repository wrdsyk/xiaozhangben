package com.jizhang.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.Motion
import com.jizhang.ui.theme.numberStyle
import com.jizhang.ui.theme.rememberHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

private val ItemHeight = 48.dp
private const val VISIBLE = 5
private const val YEARS_BACK = 10

/**
 * Whatever a wheel cannot scroll stops here. With its first row centred and a finger still pulling
 * down, the sheet the wheel sits in must not follow (drag-to-close takes the leftovers of lists).
 */
private val KeepOverscroll = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
}

private val weekday = mapOf(
    DayOfWeek.MONDAY to "周一", DayOfWeek.TUESDAY to "周二", DayOfWeek.WEDNESDAY to "周三",
    DayOfWeek.THURSDAY to "周四", DayOfWeek.FRIDAY to "周五", DayOfWeek.SATURDAY to "周六",
    DayOfWeek.SUNDAY to "周日",
)

/**
 * Date picker as three wheels — 年 | 月 | 日 — over one shared lens. Each wheel snaps, ticks
 * on every step, enlarges its centred row and fades the rest. Dates after [latest] (and days past
 * the month's end) are dimmed; landing on one springs back to the latest valid value. [latest] is
 * [today] for records; schedules (定时记账) pass a later day. [quickPicks] shows 今天 / 昨天 / … chips.
 */
@Composable
fun DateWheel(
    date: LocalDate,
    today: LocalDate,
    latest: LocalDate = today,
    quickPicks: Boolean = true,
    onChange: (LocalDate) -> Unit,
) {
    val c = LedgerTheme.colors
    // Reaches back far enough for an old record too, or opening it would silently move its year.
    // Remembered: the wheel's row indices must not shift when the picked year changes.
    val firstYear = remember { minOf(today.year - YEARS_BACK, date.year) }
    val yearCount = latest.year - firstYear + 1

    fun clamp(y: Int, m: Int, d: Int): LocalDate {
        val year = y.coerceIn(firstYear, latest.year)
        val month = m.coerceIn(1, if (year == latest.year) latest.monthValue else 12)
        val day = d.coerceIn(1, YearMonth.of(year, month).lengthOfMonth())
        val picked = LocalDate.of(year, month, day)
        return if (picked.isAfter(latest)) latest else picked
    }
    fun clampDate(d: LocalDate) = clamp(d.year, d.monthValue, d.dayOfMonth)

    // The wheels follow this, not [date], so a report never scrolls the wheel that sent it.
    var sel by remember { mutableStateOf(clampDate(date)) }
    fun commit(d: LocalDate): LocalDate {
        if (d != sel) {
            sel = d
            onChange(d)
        }
        return d
    }
    LaunchedEffect(date) {
        val d = clampDate(date)
        if (d != sel) sel = d
        if (d != date) onChange(d)
    }

    val maxMonth = if (sel.year == latest.year) latest.monthValue else 12
    val maxDay = if (sel.year == latest.year && sel.monthValue == latest.monthValue) latest.dayOfMonth else sel.lengthOfMonth()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (quickPicks) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("今天" to 0L, "昨天" to 1L, "前天" to 2L, "一周前" to 7L).forEach { (label, back) ->
                val d = today.minusDays(back)
                val on = d == sel
                val fill by animateColorAsState(if (on) c.accent else c.opKey, Motion.fade(), label = "dayChip")
                val text by animateColorAsState(if (on) c.onAccent else c.ink, Motion.fade(), label = "dayChipText")
                // 48dp tall touch target around the 34dp pill.
                Box(Modifier.height(48.dp).tap(label = label) { commit(clampDate(d)) }, contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(fill)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = text)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(ItemHeight * VISIBLE), contentAlignment = Alignment.Center) {
            // One lens behind the middle row of all three wheels.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ItemHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.opKey),
            )
            Row(Modifier.fillMaxWidth()) {
                Wheel(
                    count = yearCount,
                    target = sel.year - firstYear,
                    enabled = { true },
                    describe = { "${firstYear + it}年" },
                    onSettle = { commit(clamp(firstYear + it, sel.monthValue, sel.dayOfMonth)).year - firstYear },
                    modifier = Modifier.weight(1.2f),
                ) { i, ink, _ ->
                    WheelLabel("${firstYear + i}年", ink)
                }
                Wheel(
                    count = 12,
                    target = sel.monthValue - 1,
                    enabled = { it + 1 <= maxMonth },
                    describe = { "${it + 1}月" },
                    onSettle = { commit(clamp(sel.year, it + 1, sel.dayOfMonth)).monthValue - 1 },
                    modifier = Modifier.weight(1f),
                ) { i, ink, _ ->
                    WheelLabel("${i + 1}月", ink)
                }
                Wheel(
                    count = 31,
                    target = sel.dayOfMonth - 1,
                    enabled = { it + 1 <= maxDay },
                    describe = { "${it + 1}日" },
                    onSettle = { commit(clamp(sel.year, sel.monthValue, it + 1)).dayOfMonth - 1 },
                    modifier = Modifier.weight(1f),
                ) { i, ink, distance ->
                    val day = i + 1
                    WheelLabel("${day}日", ink)
                    Text(
                        if (day <= sel.lengthOfMonth()) weekday.getValue(sel.withDayOfMonth(day).dayOfWeek) else "",
                        fontSize = 11.sp,
                        color = c.sub,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(start = 3.dp)
                            .width(24.dp)
                            // Only the centred day shows its weekday.
                            .graphicsLayer { alpha = (1f - abs(distance()) * 2f).coerceIn(0f, 1f) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WheelLabel(text: String, ink: Color) {
    Text(text, style = numberStyle(18.sp, FontWeight.Medium, 0f), color = ink, maxLines = 1)
}

/** Signed distance of item [i] from the viewport's middle, in rows (3 when it is off screen). */
private fun LazyListState.distanceOf(i: Int): Float {
    val info = layoutInfo
    val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    val item = info.visibleItemsInfo.firstOrNull { it.index == i } ?: return 3f
    return (item.offset + item.size / 2f - mid) / item.size
}

/**
 * One snapping wheel. It scrolls itself to [target] whenever that changes; when the user lets go
 * it hands the centred index to [onSettle], which returns the index actually kept (a disabled row
 * springs back to it).
 */
@Composable
private fun Wheel(
    count: Int,
    target: Int,
    enabled: (Int) -> Boolean,
    describe: (Int) -> String,
    onSettle: (Int) -> Int,
    modifier: Modifier = Modifier,
    content: @Composable (index: Int, ink: Color, distance: () -> Float) -> Unit,
) {
    val c = LedgerTheme.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val rowPx = with(LocalDensity.current) { ItemHeight.roundToPx() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = target.coerceIn(0, count - 1))
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Center)
    val settle by rememberUpdatedState(onSettle)
    var auto by remember { mutableStateOf<Job?>(null) }

    // With two rows of padding above, the first visible item sits in the middle slot.
    val centered by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: state.firstVisibleItemIndex
        }
    }

    fun go(i: Int) {
        auto = scope.launch {
            val delta = (i - state.firstVisibleItemIndex) * rowPx - state.firstVisibleItemScrollOffset
            if (delta != 0) state.animateScrollBy(delta.toFloat(), Motion.layout())
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { centered }.distinctUntilChanged().drop(1).collect { haptics.tick() }
    }
    // A user scroll that came to rest: keep it, or spring back to what the parent allows.
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.distinctUntilChanged().drop(1).filter { !it }.collect {
            if (auto?.isActive == true) return@collect
            // The drag and the snap fling may run as separate scrolls; wait a frame so the gap
            // between them isn't taken for a stop.
            withFrameNanos { }
            if (state.isScrollInProgress || auto?.isActive == true) return@collect
            val kept = settle(centered)
            if (kept != centered) go(kept)
        }
    }
    LaunchedEffect(target) {
        if (target != centered) go(target)
    }

    LazyColumn(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(vertical = ItemHeight * (VISIBLE / 2)),
        modifier = modifier.nestedScroll(KeepOverscroll).height(ItemHeight * VISIBLE),
    ) {
        items(count) { i ->
            val on = enabled(i)
            val ink by animateColorAsState(if (on) c.ink else c.placeholder, Motion.fade(), label = "wheelInk")
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ItemHeight)
                    .graphicsLayer {
                        val signed = state.distanceOf(i)
                        val t = (1f - abs(signed) / 2.5f).coerceIn(0f, 1f)
                        val s = .82f + .26f * t * t
                        scaleX = s
                        scaleY = s
                        alpha = .25f + .75f * t * t
                        rotationX = signed.coerceIn(-2.5f, 2.5f) * -14f
                    }
                    .tap(label = describe(i), pressedScale = 1f, enabled = on) {
                        auto?.cancel()
                        go(settle(i))
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                content(i, ink) { state.distanceOf(i) }
            }
        }
    }
}
