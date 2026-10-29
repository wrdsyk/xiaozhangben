package com.jizhang.ui.recurring

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.LedgerViewModel
import com.jizhang.domain.Category
import com.jizhang.domain.CategoryCatalog
import com.jizhang.domain.EntryKind
import com.jizhang.domain.Hue
import com.jizhang.domain.RecurringRule
import com.jizhang.domain.Repeat
import com.jizhang.ui.components.ButtonKind
import com.jizhang.ui.components.ChoiceChips
import com.jizhang.ui.components.DateWheel
import com.jizhang.ui.components.FormActions
import com.jizhang.ui.components.FormButton
import com.jizhang.ui.components.FormField
import com.jizhang.ui.components.FormLabel
import com.jizhang.ui.components.FormTitle
import com.jizhang.ui.components.Glyph
import com.jizhang.ui.components.LocalFormSheet
import com.jizhang.ui.components.Segmented
import com.jizhang.ui.components.tap
import com.jizhang.ui.dayLabel
import com.jizhang.ui.forms.cleanAmount
import com.jizhang.ui.forms.parseYuan
import com.jizhang.ui.relativeDay
import com.jizhang.ui.theme.LedgerIcons
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.Motion
import com.jizhang.ui.theme.rememberHaptics
import java.time.LocalDate
import java.time.Month

/**
 * Create or edit a 定时记账 rule: kind, category, amount, note, how it repeats, start and an
 * optional end. The options scroll between the fixed title and buttons; a live line below them
 * says what the rule does, when it next runs and how many missed days saving will back-fill.
 */
@Composable
fun ColumnScope.RecurringForm(
    existing: RecurringRule?,
    catalog: CategoryCatalog,
    today: LocalDate,
    onSave: (
        category: Category, cents: Long, note: String, repeat: Repeat, days: Int, yearDays: List<Int>, start: LocalDate, end: LocalDate?,
    ) -> Unit,
    onDelete: (() -> Unit)?,
    close: () -> Unit,
) {
    val c = LedgerTheme.colors
    var kind by remember { mutableStateOf(existing?.category?.kind ?: EntryKind.Expense) }
    var categoryKey by remember { mutableStateOf(existing?.category?.key) }
    var amount by remember { mutableStateOf(existing?.let { LedgerViewModel.plainYuan(it.amountCents) } ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var pattern by remember { mutableStateOf(existing?.repeat ?: Repeat.Monthly) }
    var start by remember { mutableStateOf(existing?.start ?: today) }
    var end by remember { mutableStateOf(existing?.end) }
    // What the end wheel shows, kept while "不结束" is picked so switching back restores it.
    var endDraft by remember { mutableStateOf(existing?.end ?: today.plusYears(1)) }
    // Each repeat keeps its own choice, so trying another one and coming back loses nothing.
    fun initial(r: Repeat) =
        existing?.takeIf { it.repeat == r }?.days?.takeIf { it != 0 } ?: RecurringRule.defaultDays(r, existing?.start ?: today)
    var weekBits by remember { mutableIntStateOf(initial(Repeat.Weekly)) }
    var monthBits by remember { mutableIntStateOf(initial(Repeat.Monthly)) }
    var yearDates by remember {
        mutableStateOf(
            existing?.takeIf { it.repeat == Repeat.Yearly && it.yearDays.isNotEmpty() }?.yearDays
                ?: RecurringRule.defaultYearDays(existing?.start ?: today),
        )
    }
    // The month whose days the 每年 picker shows.
    var pickMonth by remember { mutableIntStateOf(yearDates.first() / 100) }
    // A repeat's days follow the start date until the user picks them for that repeat themselves.
    var touched by remember { mutableStateOf(if (existing != null) Repeat.entries.toSet() else emptySet()) }
    var startOpen by remember { mutableStateOf(false) }
    var endOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Latest day the wheels offer: far enough ahead for any plan, and never short of a saved date.
    val latest = remember { maxOf(today.plusYears(10), existing?.start ?: today, existing?.end ?: today) }

    val categories = catalog.forKind(kind, existing?.category)
    val category = categories.firstOrNull { it.key == categoryKey } ?: categories.first()
    val cents = parseYuan(amount)
    val days = when (pattern) {
        Repeat.Weekly -> weekBits
        Repeat.Monthly -> monthBits
        else -> 0
    }
    val dates = if (pattern == Repeat.Yearly) yearDates else emptyList()
    // The pickers keep one on, so this only guards against a rule loaded empty.
    val noDays = when (pattern) {
        Repeat.Weekly -> weekBits == 0
        Repeat.Monthly -> monthBits == 0
        Repeat.Yearly -> yearDates.isEmpty()
        else -> false
    }
    val endBeforeStart = end?.isBefore(start) == true
    val valid = cents != null && !noDays && !endBeforeStart

    if (confirmDelete && onDelete != null) {
        // Back steps out of the confirmation, not out of the whole sheet.
        val form = LocalFormSheet.current
        DisposableEffect(form) {
            form.backClaims++
            onDispose { form.backClaims-- }
        }
        BackHandler { confirmDelete = false }
        FormTitle("删除这条定时记账？", "「${existing?.note?.ifBlank { null } ?: category.label}」以后不再自动记账，已经记下的账会保留。")
        FormActions(
            primary = "删除",
            primaryKind = ButtonKind.Danger,
            onPrimary = { onDelete(); close() },
            secondary = "返回",
            onSecondary = { confirmDelete = false },
        )
        return
    }

    val preview = RecurringRule(
        id = existing?.id ?: 0, category = category, amountCents = cents ?: 0, note = note, repeat = pattern,
        days = days, start = start, end = end, last = existing?.last, yearDays = dates,
    )
    // Saving writes every due day up to today straight away (the edit keeps the rule's progress).
    val backfill = remember(preview, today) {
        if (existing?.paused == true) emptyList()
        else preview.dueDates(existing?.last ?: start.minusDays(1), today, limit = Int.MAX_VALUE)
    }
    val nextRun = remember(preview, today) { preview.copy(last = maxOf(existing?.last ?: today, today)).next(today) }

    FormTitle(if (existing == null) "新建定时记账" else "编辑定时记账", "到期自动记一笔，不用再手动记重复的账")
    Column(
        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Segmented(listOf("支出", "收入"), kind.ordinal, { kind = EntryKind.entries[it] })
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormLabel("分类")
            ChoiceChips(
                categories.map { it.label }, categories.indexOf(category), { categoryKey = categories[it].key },
                dots = categories.map { c.hue(it) },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormLabel("每次金额")
            FormField(
                amount, { amount = cleanAmount(it) }, placeholder = "0", prefix = "¥", large = true,
                keyboardType = KeyboardType.Decimal, autoFocus = false,
                isError = amount.isNotEmpty() && cents == null,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormLabel("备注（可不填）")
            FormField(note, { note = it.take(40) }, placeholder = category.label, autoFocus = false)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormLabel("重复")
            ChoiceChips(Repeat.entries.map { it.label }, pattern.ordinal, { pattern = Repeat.entries[it] })
            when (pattern) {
                Repeat.Weekly -> WeekdayPicker(weekBits) {
                    weekBits = it
                    touched = touched + Repeat.Weekly
                }
                Repeat.Monthly -> {
                    MonthDayPicker(monthBits) {
                        monthBits = it
                        touched = touched + Repeat.Monthly
                    }
                    val picked = RecurringRule.monthlyDays(monthBits)
                    val late = picked.filter { it >= 29 }
                    if (late.isNotEmpty()) {
                        // 30 and 31 both land on the 30th of April: still one record that day.
                        val clash = picked.count { it >= 28 } >= 2
                        Hint("${late.joinToString("、")} 日遇到较短的月份，记在当月最后一天" + if (clash) "，同一天只记一笔" else "")
                    }
                }
                Repeat.Yearly -> {
                    YearDatePicker(yearDates, pickMonth, { pickMonth = it }) {
                        yearDates = it
                        touched = touched + Repeat.Yearly
                    }
                    if (229 in yearDates) Hint("平年没有 2 月 29 日，记在 2 月 28 日")
                }
                Repeat.Workdays -> Hint("周一至周五，跳过法定节假日；调休上班的周末照记。" + workCalendarNote())
                Repeat.Restdays -> Hint("周末和法定节假日；调休上班的周末不记。" + workCalendarNote())
                Repeat.Daily -> {}
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DateRow("开始", dateText(start, today), startOpen, {
                startOpen = !startOpen
                endOpen = false
            }) {
                DateWheel(start, today, latest = latest) { d ->
                    start = d
                    if (Repeat.Weekly !in touched) weekBits = RecurringRule.defaultDays(Repeat.Weekly, d)
                    if (Repeat.Monthly !in touched) monthBits = RecurringRule.defaultDays(Repeat.Monthly, d)
                    if (Repeat.Yearly !in touched) {
                        yearDates = RecurringRule.defaultYearDays(d)
                        pickMonth = d.monthValue
                    }
                }
            }
            DateRow("结束", end?.let { dateText(it, today) } ?: "不结束", endOpen, {
                endOpen = !endOpen
                startOpen = false
            }) {
                Segmented(
                    listOf("不结束", "到某天为止"), if (end == null) 0 else 1,
                    { end = if (it == 0) null else maxOf(endDraft, start) },
                )
                AnimatedVisibility(
                    visible = end != null,
                    enter = expandVertically(Motion.size) + fadeIn(Motion.fade()),
                    exit = shrinkVertically(Motion.size) + fadeOut(Motion.fade()),
                ) {
                    DateWheel(end ?: endDraft, today, latest = latest, quickPicks = false) {
                        end = it
                        endDraft = it
                    }
                }
            }
        }
        Summary(
            text = when {
                noDays -> if (pattern == Repeat.Yearly) "每年至少选一个日期" else "${pattern.label}至少选一天"
                endBeforeStart -> "结束日期早于开始日期"
                nextRun == null -> preview.description + " · 不会再记了"
                existing?.paused == true -> preview.description + " · 已暂停"
                else -> preview.description + " · 下次 " + nextLabel(nextRun, today)
            },
            hint = when {
                cents == null || backfill.isEmpty() -> null
                backfill.size == 1 && backfill[0] == today -> "保存后先记入今天这一笔"
                existing == null -> "会补记从开始日期到今天的 ${backfill.size} 笔"
                else -> "保存后会补记 ${backfill.size} 笔"
            },
            error = endBeforeStart || noDays,
        )
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onDelete != null) {
            FormButton("删除", ButtonKind.Danger, Modifier.weight(1f)) { confirmDelete = true }
        } else {
            FormButton("取消", ButtonKind.Secondary, Modifier.weight(1f), onClick = close)
        }
        FormButton("保存", ButtonKind.Primary, Modifier.weight(1.6f), enabled = valid) {
            if (cents != null && valid) {
                onSave(category, cents, note, pattern, days, dates, start, end)
                close()
            }
        }
    }
}

/** "今天 10月27日 周二", "11月1日 周日", "2027年3月8日 周一". */
private fun dateText(d: LocalDate, today: LocalDate): String =
    (relativeDay(d, today)?.let { "$it " } ?: "") + (if (d.year != today.year) "${d.year}年" else "") + dayLabel(d)

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 12.sp, color = LedgerTheme.colors.sub, modifier = Modifier.padding(start = 4.dp))
}

/** 一 … 日 toggles, Monday first; at least one stays on. */
@Composable
private fun WeekdayPicker(bits: Int, onChange: (Int) -> Unit) {
    val haptics = rememberHaptics()
    Row(Modifier.fillMaxWidth()) {
        RecurringRule.WEEKDAY_NAMES.forEachIndexed { i, name ->
            DayCell("周$name", name, bits and (1 shl i) != 0, Modifier.weight(1f)) {
                val next = bits xor (1 shl i)
                if (next == 0) {
                    haptics.reject()
                } else {
                    haptics.tick()
                    onChange(next)
                }
            }
        }
    }
}

/** 1 … 31 toggles for the days of the month; at least one stays on. */
@Composable
private fun MonthDayPicker(bits: Int, onChange: (Int) -> Unit) {
    val haptics = rememberHaptics()
    DayGrid(count = 31, columns = 7, label = { "$it" }, isOn = { bits and RecurringRule.monthlyBit(it) != 0 }) { day ->
        val next = bits xor RecurringRule.monthlyBit(day)
        if (next == 0) {
            haptics.reject()
        } else {
            haptics.tick()
            onChange(next)
        }
    }
}

/**
 * The dates of a yearly rule as removable chips (e.g. "3月8日 ×") and an 添加日期 chip that springs a
 * month + day picker open below; tapping a day there adds the date, tapping it again takes it off.
 * At least one date stays.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YearDatePicker(dates: List<Int>, month: Int, onMonth: (Int) -> Unit, onChange: (List<Int>) -> Unit) {
    val haptics = rememberHaptics()
    var open by remember { mutableStateOf(false) }
    fun toggle(value: Int) {
        val next = if (value in dates) dates - value else dates + value
        if (next.isEmpty()) {
            haptics.reject()
        } else {
            haptics.tick()
            onChange(next.sorted())
        }
    }
    Column(Modifier.fillMaxWidth()) {
        FlowRow(Modifier.fillMaxWidth().animateContentSize(Motion.size), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            dates.forEach { value -> DateChip(RecurringRule.yearDayText(value), removable = dates.size > 1) { toggle(value) } }
            AddDateChip(open) { open = !open }
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(Motion.size) + fadeIn(Motion.fade()),
            exit = shrinkVertically(Motion.size) + fadeOut(Motion.fade()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DayGrid(count = 12, columns = 6, label = { "${it}月" }, isOn = { it == month }) {
                    if (it != month) {
                        haptics.tick()
                        onMonth(it)
                    }
                }
                DayGrid(count = Month.of(month).maxLength(), columns = 7, label = { "$it" }, isOn = { month * 100 + it in dates }) {
                    toggle(month * 100 + it)
                }
                Hint("点一下添加这天，再点一下取消")
            }
        }
    }
}

/** A picked date with a × ; 48dp tall to tap, drawn as a slimmer pill. */
@Composable
private fun DateChip(text: String, removable: Boolean, onRemove: () -> Unit) {
    val c = LedgerTheme.colors
    Box(Modifier.height(48.dp).tap(label = "移除 $text", onClick = onRemove), contentAlignment = Alignment.Center) {
        Row(
            Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(c.accent).padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.onAccent, maxLines = 1)
            Glyph(LedgerIcons.get("close", 2.2f), 14.dp, c.onAccent.copy(alpha = if (removable) 1f else .45f))
        }
    }
}

/** The chip that opens / closes the month + day picker. */
@Composable
private fun AddDateChip(open: Boolean, onToggle: () -> Unit) {
    val c = LedgerTheme.colors
    Box(Modifier.height(48.dp).tap(label = if (open) "收起日期选择" else "添加日期", onClick = onToggle), contentAlignment = Alignment.Center) {
        Row(
            Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(c.opKey).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Glyph(LedgerIcons.get(if (open) "chevl" else "plus", 2.2f), 14.dp, c.ink, Modifier.graphicsLayer { rotationZ = if (open) 90f else 0f })
            Text(if (open) "收起" else "添加日期", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.ink, maxLines = 1)
        }
    }
}

/** Numbers 1..[count] in rows of [columns]; [isOn] ones are lit, a tap goes to [onTap]. Every cell is a 48dp target. */
@Composable
private fun DayGrid(count: Int, columns: Int, label: (Int) -> String, isOn: (Int) -> Boolean, onTap: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        (1..count).chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { n -> DayCell(label(n), label(n), isOn(n), Modifier.weight(1f)) { onTap(n) } }
                for (i in row.size until columns) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DayCell(description: String, text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    val fill by animateColorAsState(if (on) c.accent else c.opKey, Motion.fade(), label = "dayCell")
    val ink by animateColorAsState(if (on) c.onAccent else c.ink, Motion.fade(), label = "dayCellText")
    Box(modifier.height(48.dp).tap(label = description, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier.padding(horizontal = 2.dp).fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp)).background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, color = ink, maxLines = 1)
        }
    }
}

/** A tappable well showing a date; tapping springs its picker open below. */
@Composable
private fun DateRow(label: String, value: String, open: Boolean, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LedgerTheme.colors
    val turn by animateFloatAsState(if (open) 180f else 0f, Motion.layout(), label = "dateChev")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.opKey)
                .tap(label = "${label}日期", pressedScale = .98f, onClick = onToggle)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Glyph(LedgerIcons.get("cal"), 18.dp, c.sub)
            Text(label, fontSize = 15.sp, color = c.ink, modifier = Modifier.weight(1f))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink, maxLines = 1)
            Glyph(LedgerIcons.get("chev"), 16.dp, c.sub, Modifier.graphicsLayer { rotationZ = turn })
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(Motion.size) + fadeIn(Motion.fade()),
            exit = shrinkVertically(Motion.size) + fadeOut(Motion.fade()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

/** What the rule will do, in one line, plus the back-fill note. */
@Composable
private fun Summary(text: String, hint: String?, error: Boolean) {
    val c = LedgerTheme.colors
    val tone = if (error) c.hue(Hue.Gift) else c.ink
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.opKey).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Glyph(LedgerIcons.get("repeat"), 18.dp, if (error) tone else c.sub)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = tone)
            if (hint != null) Text(hint, fontSize = 12.sp, color = c.sub)
        }
    }
}
