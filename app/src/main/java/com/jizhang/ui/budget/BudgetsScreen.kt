package com.jizhang.ui.budget

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.LedgerState
import com.jizhang.LedgerViewModel
import com.jizhang.domain.Budget
import com.jizhang.domain.BudgetLevel
import com.jizhang.domain.BudgetPeriod
import com.jizhang.domain.BudgetProgress
import com.jizhang.domain.BudgetSort
import com.jizhang.domain.EntryKind
import com.jizhang.domain.TotalBudget
import com.jizhang.domain.formatCentsShort
import com.jizhang.ui.components.BudgetRing
import com.jizhang.ui.components.ButtonKind
import com.jizhang.ui.components.CategoryTile
import com.jizhang.ui.components.FormButton
import com.jizhang.ui.components.Glyph
import com.jizhang.ui.components.LocalFormSheet
import com.jizhang.ui.components.Odometer
import com.jizhang.ui.components.Segmented
import com.jizhang.ui.components.glass
import com.jizhang.ui.components.rise
import com.jizhang.ui.components.tap
import com.jizhang.ui.forms.BudgetDetailForm
import com.jizhang.ui.forms.BudgetForm
import com.jizhang.ui.forms.TotalBudgetForm
import com.jizhang.ui.theme.LedgerColors
import com.jizhang.ui.theme.LedgerIcons
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.Motion
import com.jizhang.ui.theme.numberStyle
import com.jizhang.ui.theme.rememberHaptics
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Progress colour for a [BudgetLevel]: green up to 50%, yellow to 80%, red from 80%. The category
 * hues are too muted to read as traffic lights (the "yellow" one is ochre brown), so these are
 * their own, lerped with the theme's darkness (0 light, 1 dark).
 */
fun LedgerColors.levelColor(level: BudgetLevel): Color {
    val (light, dark) = when (level) {
        BudgetLevel.Green -> Color(0xFF35A26A) to Color(0xFF8EE0BE)
        BudgetLevel.Yellow -> Color(0xFFE2A31F) to Color(0xFFF7D58C)
        BudgetLevel.Red -> Color(0xFFD9473C) to Color(0xFFFF9E95)
    }
    return lerp(light, dark, darkness.coerceIn(0f, 1f))
}

/** Thin progress bar coloured by how much of the budget is used (green → yellow → red). */
@Composable
fun BudgetBar(progress: BudgetProgress, modifier: Modifier = Modifier) {
    LevelBar(progress.fraction, progress.level, modifier)
}

@Composable
fun LevelBar(fraction: Float, level: BudgetLevel, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val c = LedgerTheme.colors
    val w by animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.layout(), label = "budgetBar")
    val fill by animateColorAsState(c.levelColor(level), Motion.fade(), label = "budgetLevel")
    val shape = RoundedCornerShape(height / 2)
    Box(modifier.fillMaxWidth().height(height).clip(shape).background(c.track)) {
        if (w > .001f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(w.coerceIn(0f, 1f))
                    .clip(shape)
                    .background(Brush.horizontalGradient(listOf(fill.copy(alpha = .7f), fill))),
            )
        }
    }
}

private fun BudgetProgress.leftText(): String =
    if (over) "超出 ¥${formatCentsShort(-leftCents)}" else "剩 ¥${formatCentsShort(leftCents)}"

/** Home-screen card listing custom budgets; tapping it opens the budgets page. */
@Composable
fun BudgetCard(budgets: List<BudgetProgress>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = LedgerTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .glass(26.dp)
            .tap(label = "管理预算", pressedScale = .98f, onClick = onOpen)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("预算", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
            Text("管理", fontSize = 12.sp, color = c.sub)
            Glyph(LedgerIcons.get("chevr"), 14.dp, c.sub)
        }
        budgets.take(4).forEach { p ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(p.budget.displayName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        p.leftText(),
                        style = numberStyle(12.sp, FontWeight.Medium, 0f),
                        color = if (p.over) c.levelColor(BudgetLevel.Red) else c.sub,
                    )
                }
                BudgetBar(p)
            }
        }
        if (budgets.size > 4) Text("还有 ${budgets.size - 4} 个", fontSize = 12.sp, color = c.sub)
    }
}

private val TotalCardHeight = 150.dp

private val BudgetPeriod.totalName: String
    get() = when (this) {
        BudgetPeriod.Year -> "年预算"
        BudgetPeriod.Month -> "月预算"
        BudgetPeriod.Day -> "日预算"
    }

private val BudgetPeriod.short: String
    get() = when (this) {
        BudgetPeriod.Year -> "年"
        BudgetPeriod.Month -> "月"
        BudgetPeriod.Day -> "日"
    }

/** Survives tab switches (the tab's content leaves composition), like the stats granularity. */
private var lastSort = BudgetSort.Available

/**
 * The 预算 tab (between 统计 and 我的): the total budgets in a 年/月/日 pager, then the custom
 * budgets grouped by period and sorted by 可用 (most money left) or 已用 (highest progress).
 */
@Composable
fun BudgetScreen(
    state: LedgerState,
    budgets: List<BudgetProgress>,
    vm: LedgerViewModel,
    contentPadding: PaddingValues,
) {
    val form = LocalFormSheet.current
    val expenseCategories = state.catalog.forKind(EntryKind.Expense)
    var sort by rememberSaveable { mutableStateOf(lastSort) }
    val sorted = remember(budgets, sort) { sort.apply(budgets) }
    val sections = remember(sorted) {
        listOf(BudgetPeriod.Day, BudgetPeriod.Month, BudgetPeriod.Year)
            .map { p -> p to sorted.filter { it.budget.period == p } }
            .filter { it.second.isNotEmpty() }
    }

    fun editBudget(existing: Budget?) = form.open { s ->
        BudgetForm(
            existing = existing,
            categories = expenseCategories,
            chips = { vm.valueChips(LedgerViewModel.budgetChipScope(it)) },
            onAddChip = { p, text -> vm.addValueChip(LedgerViewModel.budgetChipScope(p), text) },
            onRemoveChip = { p, chip -> vm.removeValueChip(LedgerViewModel.budgetChipScope(p), chip) },
            onSave = { name, period, category, cents -> vm.saveBudget(existing?.id, name, period, category, cents) },
            onDelete = if (existing != null) ({ vm.deleteBudget(existing.id) }) else null,
            close = s::close,
        )
    }

    /** A budget opens on what it is made of (spent, bar, ranking); 编辑预算 there swaps in [editBudget]. */
    fun openDetail(p: BudgetProgress) = form.open { s ->
        BudgetDetailForm(
            progress = p,
            records = state.allRecords,
            today = state.today,
            onEdit = { editBudget(p.budget) },
            close = s::close,
        )
    }

    fun editTotal(t: TotalBudget) = form.open { s ->
        val chipScope = LedgerViewModel.budgetChipScope(t.period)
        TotalBudgetForm(
            t.period, t.amountCents,
            chips = remember { vm.valueChips(chipScope) },
            onAddChip = { vm.addValueChip(chipScope, it) },
            onRemoveChip = { vm.removeValueChip(chipScope, it) },
            onSave = { vm.setTotalBudget(t.period, it) },
            close = s::close,
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "totals") {
            TotalsPager(state.totals, onEdit = ::editTotal, modifier = Modifier.rise(0))
        }
        item(key = "sort") {
            SortBar(
                count = budgets.size,
                sort = sort,
                onSort = {
                    sort = it
                    lastSort = it
                },
                modifier = Modifier.padding(horizontal = 20.dp).rise(1).animateItem(
                    fadeInSpec = Motion.fade(), placementSpec = Motion.offset, fadeOutSpec = Motion.fade(),
                ),
            )
        }
        if (budgets.isEmpty()) {
            item(key = "empty") {
                EmptyBudgets(Modifier.padding(horizontal = 20.dp).rise(2).animateItem(
                    fadeInSpec = Motion.fade(), placementSpec = Motion.offset, fadeOutSpec = Motion.fade(),
                ))
            }
        }
        var base = 0
        sections.forEach { (period, list) ->
            val start = base
            base += list.size
            item(key = "section-${period.key}") {
                SectionHeader(
                    period.label, list.size,
                    Modifier.padding(horizontal = 24.dp).rise(2 + start.coerceAtMost(4)).animateItem(
                        fadeInSpec = Motion.fade(), placementSpec = Motion.offset, fadeOutSpec = Motion.fade(),
                    ),
                )
            }
            itemsIndexed(list, key = { _, p -> p.budget.id }) { i, p ->
                BudgetRow(
                    p,
                    Modifier.padding(horizontal = 20.dp).rise(2 + (start + i).coerceAtMost(4)).animateItem(
                        fadeInSpec = Motion.fade(), placementSpec = Motion.offset, fadeOutSpec = Motion.fade(),
                    ),
                ) { openDetail(p) }
            }
        }
        item(key = "new") {
            FormButton(
                "新建预算", ButtonKind.Primary,
                Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp).fillMaxWidth().rise(3 + budgets.size.coerceAtMost(4)).animateItem(
                    fadeInSpec = Motion.fade(), placementSpec = Motion.offset, fadeOutSpec = Motion.fade(),
                ),
            ) { editBudget(null) }
        }
    }
}

/** 年 / 月 / 日 total budgets; opens on 月. Swipe or tap a label below to switch. */
@Composable
private fun TotalsPager(totals: List<TotalBudget>, onEdit: (TotalBudget) -> Unit, modifier: Modifier = Modifier) {
    val c = LedgerTheme.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = 1.coerceAtMost(totals.lastIndex.coerceAtLeast(0))) { totals.size }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { haptics.tick() }
    }
    Column(modifier.fillMaxWidth()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth(), beyondViewportPageCount = 1) { page ->
            val t = totals[page]
            // Bottom padding leaves room for the glass shadow inside the pager's clip.
            Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp)) {
                if (t.isSet) TotalCard(t) { onEdit(t) } else EmptyTotalCard(t) { onEdit(t) }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            totals.forEachIndexed { i, t ->
                if (i > 0) Text("·", fontSize = 12.sp, color = c.sub)
                val on = pager.currentPage == i
                val tone by animateColorAsState(if (on) c.ink else c.sub, Motion.fade(), label = "pagerTone")
                val dot by animateColorAsState(if (on) c.ink else c.track, Motion.fade(), label = "pagerDot")
                val w by animateDpAsState(if (on) 16.dp else 6.dp, Motion.layout(), label = "pagerDotWidth")
                Column(
                    Modifier.size(48.dp).tap(label = "查看${t.period.totalName}") {
                        if (pager.currentPage != i) scope.launch { pager.animateScrollToPage(i, animationSpec = Motion.layout()) }
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                ) {
                    Box(Modifier.size(w, 5.dp).clip(RoundedCornerShape(3.dp)).background(dot))
                    Text(t.period.short, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = tone)
                }
            }
        }
    }
}

@Composable
private fun TotalCard(t: TotalBudget, onEdit: () -> Unit) {
    val c = LedgerTheme.colors
    val left = if (t.over) "已超出 ¥${formatCentsShort(-t.leftCents)}" else "还剩 ¥${formatCentsShort(t.leftCents)}"
    Row(
        Modifier
            .fillMaxWidth()
            .height(TotalCardHeight)
            .glass(30.dp, refract = true)
            .tap(label = "修改${t.period.totalName}", pressedScale = .98f, onClick = onEdit)
            .padding(start = 20.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t.period.totalName, fontSize = 13.sp, color = c.sub)
                Glyph(LedgerIcons.get("chevr"), 13.dp, c.sub)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("¥", style = numberStyle(20.sp, FontWeight.Normal, 0f), color = c.ink, modifier = Modifier.padding(end = 4.dp, bottom = 5.dp))
                Odometer(formatCentsShort(t.amountCents), numberStyle(38.sp, FontWeight.Light, -.035f), c.ink)
            }
            // Two lines: "今年已用 ¥X · 还剩 ¥Y" on one line clips beside the ring on a 390dp phone.
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    "${t.period.scope}已用 ¥${formatCentsShort(t.spentCents)}",
                    style = numberStyle(12.sp, FontWeight.Normal, 0f),
                    color = c.sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    left,
                    style = numberStyle(12.sp, FontWeight.Medium, 0f),
                    color = if (t.over) c.levelColor(BudgetLevel.Red) else c.sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        BudgetRing(t.fraction, label = "已用")
    }
}

@Composable
private fun EmptyTotalCard(t: TotalBudget, onSet: () -> Unit) {
    val c = LedgerTheme.colors
    val hint = when (t.period) {
        BudgetPeriod.Year -> "设好后，这里会显示今年还能花多少"
        BudgetPeriod.Month -> "首页的预算环按它来算"
        BudgetPeriod.Day -> "给每天的所有支出定个上限"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .height(TotalCardHeight)
            .glass(30.dp, refract = true)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TargetTile()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("还没有设置${t.period.totalName}", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink)
                Text(hint, fontSize = 12.sp, color = c.sub, maxLines = 1)
            }
        }
        FormButton("设置${t.period.totalName}", ButtonKind.Primary, Modifier.fillMaxWidth(), height = 48.dp, onClick = onSet)
    }
}

@Composable
private fun SortBar(count: Int, sort: BudgetSort, onSort: (BudgetSort) -> Unit, modifier: Modifier = Modifier) {
    val c = LedgerTheme.colors
    Row(modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("自定义预算", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(
                if (count == 0) "按分类或全部支出设上限" else "$count 个 · ${sort.hint}",
                fontSize = 12.sp,
                color = c.sub,
            )
        }
        if (count > 0) {
            Segmented(
                BudgetSort.entries.map { it.label },
                sort.ordinal,
                { onSort(BudgetSort.entries[it]) },
                Modifier.width(140.dp).glass(22.dp, shadow = false),
            )
        }
    }
}

@Composable
private fun SectionHeader(label: String, count: Int, modifier: Modifier = Modifier) {
    val c = LedgerTheme.colors
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
        Spacer(Modifier.width(6.dp))
        Text("$count 个", style = numberStyle(12.sp, FontWeight.Normal, 0f), color = c.sub)
    }
}

@Composable
private fun EmptyBudgets(modifier: Modifier = Modifier) {
    val c = LedgerTheme.colors
    Column(
        modifier.fillMaxWidth().glass(26.dp).padding(horizontal = 24.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Glyph(LedgerIcons.get("target"), 28.dp, c.sub)
        Text("还没有自定义预算", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink)
        Text("可以给某个分类或全部支出，按天、按月或按年设一个上限", fontSize = 12.sp, color = c.sub, lineHeight = 18.sp)
    }
}

/** The gradient target tile that stands for 全部支出. */
@Composable
private fun TargetTile() {
    val c = LedgerTheme.colors
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(c.ring1, c.ring2))),
        contentAlignment = Alignment.Center,
    ) { Glyph(LedgerIcons.get("target"), 20.dp, c.onTile) }
}

@Composable
private fun BudgetRow(p: BudgetProgress, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    val b = p.budget
    Column(
        modifier.fillMaxWidth().glass(24.dp).tap(label = "编辑 ${b.displayName}", pressedScale = .98f, onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (b.category != null) CategoryTile(b.category, 40.dp, 14.dp, iconSize = 20.dp) else TargetTile()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(b.displayName, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${b.period.label} ¥${formatCentsShort(b.amountCents)} · ${b.period.scope}已用 ¥${formatCentsShort(p.spentCents)}",
                    style = numberStyle(12.sp, FontWeight.Normal, 0f),
                    color = c.sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                p.leftText(),
                style = numberStyle(14.sp, FontWeight.Medium, 0f),
                color = if (p.over) c.levelColor(BudgetLevel.Red) else c.ink,
            )
        }
        BudgetBar(p)
    }
}
