package com.jizhang.ui.recurring

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.LedgerState
import com.jizhang.LedgerViewModel
import com.jizhang.domain.EntryKind
import com.jizhang.domain.Hue
import com.jizhang.domain.RecurringRule
import com.jizhang.domain.WorkCalendar
import com.jizhang.domain.formatSigned
import com.jizhang.ui.components.ButtonKind
import com.jizhang.ui.components.CategoryTile
import com.jizhang.ui.components.FormButton
import com.jizhang.ui.components.Glyph
import com.jizhang.ui.components.LocalFormSheet
import com.jizhang.ui.components.LocalHazeState
import com.jizhang.ui.components.glass
import com.jizhang.ui.components.tap
import com.jizhang.ui.dayLabel
import com.jizhang.ui.theme.LedgerIcons
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.Motion
import com.jizhang.ui.theme.numberStyle
import com.jizhang.ui.theme.rememberHaptics
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 定时记账: rules that write a record by themselves on every due day. A full-screen overlay
 * stacked in LedgerApp like the add sheet; it slides in from the right over the blurred
 * ambient background. Rows open [RecurringForm]; the pill switch pauses / resumes a rule.
 */
@Composable
fun RecurringScreen(
    visible: Boolean,
    rules: List<RecurringRule>,
    state: LedgerState,
    vm: LedgerViewModel,
    onClose: () -> Unit,
) {
    // A form opened from this page handles back itself.
    val form = LocalFormSheet.current
    BackHandler(enabled = visible && !form.isOpen, onBack = onClose)
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(Motion.offset) { it } + fadeIn(Motion.fade()),
        exit = slideOutHorizontally(Motion.offset) { it } + fadeOut(Motion.fade()),
    ) {
        RecurringPage(rules, state, vm, onClose)
    }
}

@Composable
private fun RecurringPage(rules: List<RecurringRule>, state: LedgerState, vm: LedgerViewModel, onClose: () -> Unit) {
    val c = LedgerTheme.colors
    val haze = LocalHazeState.current
    val form = LocalFormSheet.current
    val today = state.today
    val nav = WindowInsets.navigationBars.asPaddingValues()

    fun edit(rule: RecurringRule?) = form.open { s ->
        RecurringForm(
            existing = rule,
            catalog = state.catalog,
            today = today,
            onSave = { category, cents, note, repeat, days, yearDays, start, end ->
                vm.saveRecurring(rule?.id, category, cents, note, repeat, days, yearDays, start, end)
            },
            onDelete = rule?.let { r -> { vm.deleteRecurring(r) } },
            close = s::close,
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .then(if (haze != null) Modifier.hazeBlur(HazeInput.Backdrop(haze), HazeBlurStyle { blurRadius(26.dp) }) else Modifier)
            .background(c.sheet)
            // Content on a layer of its own, so its animations don't re-run the full-screen blur.
            .graphicsLayer()
            // Taps on empty space must not reach the tab underneath.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.align(Alignment.CenterStart).size(48.dp).glass(24.dp).tap(label = "返回", onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Glyph(LedgerIcons.get("chevl"), 20.dp, c.ink) }
                Text("定时记账", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                Box(
                    Modifier.align(Alignment.CenterEnd).size(48.dp).glass(24.dp).tap(label = "添加定时记账") { edit(null) },
                    contentAlignment = Alignment.Center,
                ) { Glyph(LedgerIcons.get("plus", 2.2f), 20.dp, c.ink) }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = nav.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (rules.isEmpty()) {
                    item(key = "empty") {
                        EmptyRecurring(Modifier.animateItem(fadeInSpec = Motion.fade(), placementSpec = Motion.offset, fadeOutSpec = Motion.fade())) {
                            edit(null)
                        }
                    }
                } else {
                    item(key = "count") {
                        val active = rules.count { !it.paused && it.next(today) != null }
                        Text(
                            if (active == rules.size) "${rules.size} 条，都在进行中" else "${rules.size} 条 · $active 条进行中",
                            fontSize = 12.sp,
                            color = c.sub,
                            modifier = Modifier.padding(start = 4.dp).animateItem(
                                fadeInSpec = Motion.fade(), placementSpec = Motion.offset, fadeOutSpec = Motion.fade(),
                            ),
                        )
                    }
                }
                items(rules, key = { it.id }) { rule ->
                    RuleRow(
                        rule = rule,
                        today = today,
                        onOpen = { edit(rule) },
                        onToggle = { vm.setRecurringPaused(rule, !rule.paused) },
                        modifier = Modifier.animateItem(fadeInSpec = Motion.fade(), placementSpec = Motion.offset, fadeOutSpec = Motion.fade()),
                    )
                }
                item(key = "footnote") {
                    Footnote(Modifier.animateItem(fadeInSpec = Motion.fade(), placementSpec = Motion.offset, fadeOutSpec = Motion.fade()))
                }
            }
        }
    }
}

/** "下次 10月28日 周三", "下次 明天", "已暂停", "已结束". */
private fun statusText(rule: RecurringRule, next: LocalDate?, today: LocalDate): String = when {
    next == null -> "已结束"
    rule.paused -> "已暂停"
    else -> "下次 " + nextLabel(next, today)
}

/** "今天", "明天", "10月28日 周三", or with the year when it isn't this year. */
internal fun nextLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "今天"
    today.plusDays(1) -> "明天"
    else -> (if (d.year != today.year) "${d.year}年" else "") + dayLabel(d)
}

@Composable
private fun RuleRow(
    rule: RecurringRule,
    today: LocalDate,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LedgerTheme.colors
    val next = remember(rule, today) { rule.next(today) }
    val active = !rule.paused && next != null
    // Paused and ended rules fade back so the running ones stand out.
    val dim by animateFloatAsState(if (active) 1f else .5f, Motion.fade(), label = "ruleDim")
    val title = rule.note.ifBlank { rule.category.label }
    Row(
        modifier
            .fillMaxWidth()
            .glass(22.dp)
            .tap(label = "编辑 $title", pressedScale = .98f, onClick = onOpen)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryTile(rule.category, 42.dp, 15.dp, iconSize = 21.dp, modifier = Modifier.graphicsLayer { alpha = dim })
        Column(Modifier.weight(1f).graphicsLayer { alpha = dim }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(rule.description, fontSize = 12.sp, color = c.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Glyph(LedgerIcons.get("clock"), 12.dp, c.sub)
                Text(statusText(rule, next, today), fontSize = 12.sp, color = c.sub, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatSigned(rule.amountCents, rule.category.kind),
                style = numberStyle(16.sp, FontWeight.Medium, 0f),
                color = if (rule.category.kind == EntryKind.Income) c.income else c.ink,
                modifier = Modifier.padding(end = 10.dp, top = 6.dp).graphicsLayer { alpha = dim },
            )
            // An ended rule has nothing to pause; editing its end date brings it back.
            if (next != null) {
                PauseSwitch(on = !rule.paused, label = if (rule.paused) "恢复 $title" else "暂停 $title", onToggle = onToggle)
            }
        }
    }
}

/** Small pill switch: the thumb springs across, the track crossfades. 48dp tall touch target. */
@Composable
private fun PauseSwitch(on: Boolean, label: String, onToggle: () -> Unit) {
    val c = LedgerTheme.colors
    val haptics = rememberHaptics()
    val x by animateFloatAsState(if (on) 1f else 0f, Motion.layout(), label = "switch")
    val track by animateColorAsState(if (on) c.accent else c.track, Motion.fade(), label = "switchTrack")
    val thumb by animateColorAsState(if (on) c.onAccent else Color.White, Motion.fade(), label = "switchThumb")
    Box(
        Modifier.size(width = 56.dp, height = 48.dp).tap(label = label) {
            haptics.tick()
            onToggle()
        },
        contentAlignment = Alignment.Center,
    ) {
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

@Composable
private fun EmptyRecurring(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    val c = LedgerTheme.colors
    Column(
        modifier.fillMaxWidth().glass(26.dp).padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(c.tint(Hue.Transit)),
            contentAlignment = Alignment.Center,
        ) { Glyph(LedgerIcons.get("repeat"), 26.dp, c.hue(Hue.Transit)) }
        Text("还没有定时记账", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
        Text(
            "房租、工资、话费、会员费这类固定的账，设好一次，到期自动记上，不用每次手动记。",
            fontSize = 13.sp, color = c.sub, textAlign = TextAlign.Center, lineHeight = 19.sp,
        )
        Text(
            "比如：工作日的早餐、每月 1 日的房租、每年 3 月的保险",
            fontSize = 12.sp, color = c.sub, textAlign = TextAlign.Center,
        )
        FormButton("添加定时记账", ButtonKind.Primary, Modifier.padding(top = 6.dp).fillMaxWidth(), height = 48.dp, onClick = onAdd)
    }
}

/** Which years 工作日 / 休息日 know the holiday arrangement of, and what every other year does. */
internal fun workCalendarNote(): String {
    val years = WorkCalendar.coveredYears
    return if (years.isEmpty()) {
        "没有收录节假日安排，一律按周一至周五上班、周末休息"
    } else {
        "已收录 ${years.joinToString("、")} 年的节假日安排，其余年份按周一至周五上班、周末休息"
    }
}

@Composable
private fun Footnote(modifier: Modifier = Modifier) {
    val c = LedgerTheme.colors
    Column(modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            "工作日按国家法定节假日和调休安排：" + workCalendarNote(),
            "到期当天打开应用时自动记入，错过的日子会补记",
            "暂停期间的日子不补记；删除规则不影响已记下的账",
        ).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("·", fontSize = 12.sp, color = c.sub)
                Text(line, fontSize = 12.sp, color = c.sub, lineHeight = 17.sp)
            }
        }
    }
}
