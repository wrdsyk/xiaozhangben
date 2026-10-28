package com.jizhang.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.coerceAtLeast
import com.jizhang.domain.MonthSummary
import com.jizhang.domain.formatCentsShort
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.jizhang.LedgerState
import com.jizhang.domain.BudgetProgress
import com.jizhang.ui.budget.BudgetCard
import com.jizhang.domain.DayGroup
import com.jizhang.domain.EntryKind
import com.jizhang.domain.Record
import com.jizhang.domain.formatCents
import com.jizhang.domain.formatSigned
import com.jizhang.ui.components.AutoScroll
import com.jizhang.ui.components.BudgetRing
import com.jizhang.ui.components.CategoryTile
import com.jizhang.ui.components.Glyph
import com.jizhang.ui.components.MonthPill
import com.jizhang.ui.components.Odometer
import com.jizhang.ui.components.chip
import com.jizhang.ui.components.glass
import com.jizhang.ui.components.rise
import com.jizhang.ui.components.tap
import com.jizhang.ui.dayLabel
import com.jizhang.ui.hhmm
import com.jizhang.ui.relativeDay
import com.jizhang.ui.theme.LedgerIcons
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.Motion
import com.jizhang.ui.theme.numberStyle
import com.jizhang.ui.theme.rememberHaptics
import java.time.YearMonth

/** Keys shared between a list row and the detail page it expands into. */
object SharedKeys {
    fun card(id: Long) = "card-$id"
    fun icon(id: Long) = "icon-$id"
    fun amount(id: Long) = "amount-$id"
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.HomeScreen(
    state: LedgerState,
    listState: LazyListState,
    justAdded: Long?,
    budgets: List<BudgetProgress>,
    onOpenBudgets: () -> Unit,
    animatedScope: AnimatedVisibilityScope,
    contentPadding: PaddingValues,
    onMonth: (YearMonth) -> Unit,
    onOpen: (Record) -> Unit,
    onEdit: (Record, Offset) -> Unit,
    onDelete: (Record) -> Unit,
    onJustAddedShown: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val collapse: () -> Unit = {
        HomeSearch.query = ""
        HomeSearch.open = false
        HomeSearch.focusPending = false
        focusManager.clearFocus()
        keyboard?.hide()
    }
    // An open form owns the back press (see FormSheetController.isOpen).
    val forms = com.jizhang.ui.components.LocalFormSheet.current
    BackHandler(enabled = HomeSearch.open && !forms.isOpen) { collapse() }

    val query = HomeSearch.query.trim()
    val searching = HomeSearch.open && query.isNotEmpty()
    val results = remember(state.allRecords, query) {
        if (query.isEmpty()) emptyList() else state.allRecords.filter { it.matches(query) }
    }
    val resultDays = remember(results) { MonthSummary.groupByDay(results) }

    // Dragging the results means reading them: get the keyboard out of the way. Only a finger
    // drag counts — the field's own bring-into-view scroll must not drop the focus it just got.
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragging) {
        if (dragging && HomeSearch.open) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    // A record saved while its row is filtered out would otherwise glow much later. Wait until
    // the record has actually arrived from the database before deciding it isn't a result.
    LaunchedEffect(justAdded, searching, results) {
        if (searching && justAdded != null && state.allRecords.any { it.id == justAdded } && results.none { it.id == justAdded }) {
            onJustAddedShown()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "month") {
            HeaderRow(state, onMonth, onClose = collapse, modifier = Modifier.padding(horizontal = 20.dp).rise(0))
        }
        if (searching) {
            if (results.isEmpty()) {
                item(key = "search-empty") {
                    NoResults(Modifier.padding(horizontal = 20.dp).animateItem(fadeInSpec = Motion.fade(), placementSpec = Motion.offset, fadeOutSpec = Motion.fade()))
                }
            } else {
                item(key = "search-summary") {
                    val expense = results.filter { it.kind == EntryKind.Expense }.sumOf { it.amountCents }
                    val income = results.filter { it.kind == EntryKind.Income }.sumOf { it.amountCents }
                    Text(
                        "找到 ${results.size} 笔 · 支出 ¥${formatCents(expense)} · 收入 ¥${formatCents(income)}",
                        style = numberStyle(12.sp, FontWeight.Normal, 0f),
                        color = LedgerTheme.colors.sub,
                        modifier = Modifier.padding(horizontal = 28.dp).animateItem(
                            fadeInSpec = Motion.fade(), placementSpec = Motion.offset, fadeOutSpec = Motion.fade(),
                        ),
                    )
                }
                resultDays.forEach { day ->
                    item(key = "search-${day.date.toEpochDay()}") {
                        DayCard(
                            day = day,
                            state = state,
                            justAdded = justAdded,
                            animatedScope = animatedScope,
                            onOpen = onOpen,
                            onEdit = onEdit,
                            onDelete = onDelete,
                            onJustAddedShown = onJustAddedShown,
                            showYear = true,
                            modifier = Modifier.padding(horizontal = 20.dp).animateItem(
                                fadeInSpec = Motion.fade(), placementSpec = Motion.offset, fadeOutSpec = Motion.fade(),
                            ),
                        )
                    }
                }
            }
            return@LazyColumn
        }
        item(key = "hero") {
            HeroCard(
                state,
                onOpenBudgets,
                Modifier.padding(horizontal = 20.dp).rise(1).animateItem(
                    fadeInSpec = Motion.fade(), placementSpec = Motion.offset, fadeOutSpec = Motion.fade(),
                ),
            )
        }
        if (budgets.isNotEmpty() && state.month == YearMonth.from(state.today)) {
            item(key = "budgets") { BudgetCard(budgets, onOpenBudgets, Modifier.padding(horizontal = 20.dp).rise(2).animateItem()) }
        }
        if (state.loaded && state.days.isEmpty()) {
            item(key = "empty") { EmptyMonth(Modifier.padding(horizontal = 20.dp).rise(2)) }
        }
        state.days.forEachIndexed { i, day ->
            item(key = day.date.toEpochDay()) {
                DayCard(
                    day = day,
                    state = state,
                    justAdded = justAdded,
                    animatedScope = animatedScope,
                    onOpen = onOpen,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onJustAddedShown = onJustAddedShown,
                    modifier = Modifier.padding(horizontal = 20.dp).rise(2 + i.coerceAtMost(4)).animateItem(
                        fadeInSpec = Motion.fade(), placementSpec = Motion.offset, fadeOutSpec = Motion.fade(),
                    ),
                )
            }
        }
    }
}

/**
 * Search state lives outside the composable: opening a result swaps HomeScreen out for the
 * detail page, and coming back should find the same query still there.
 */
private object HomeSearch {
    var open by mutableStateOf(false)
    var query by mutableStateOf("")
    /** Set by the magnifier tap, so only that (not a return from detail) raises the keyboard. */
    var focusPending by mutableStateOf(false)
}

private val amountQuery = Regex("""\d+(\.\d{0,2})?""")

/** Every whitespace-separated term must hit the note, the category name or the amount. */
private fun Record.matches(query: String): Boolean =
    query.split(Regex("\\s+")).filter { it.isNotEmpty() }.all { t ->
        note.contains(t, ignoreCase = true) || category.label.contains(t, ignoreCase = true) || amountMatches(amountCents, t)
    }

/** "32", "32.5", "¥32" match 32.00 / 32.50 / 320.00 … as a prefix of the plain amount. */
private fun amountMatches(cents: Long, term: String): Boolean {
    val q = term.trimStart('¥', '￥', '+', '-').replace(",", "")
    if (!amountQuery.matches(q)) return false
    return formatCents(cents, grouping = false).startsWith(q) || formatCentsShort(cents).replace(",", "").startsWith(q)
}

/**
 * Month pill on the left, a round glass magnifier on the right. The magnifier grows into a
 * full-width search field while the pill fades and slides away.
 */
@Composable
private fun HeaderRow(state: LedgerState, onMonth: (YearMonth) -> Unit, onClose: () -> Unit, modifier: Modifier) {
    val c = LedgerTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current
    val open = HomeSearch.open
    val expand by animateFloatAsState(if (open) 1f else 0f, Motion.layout(), label = "search")
    val fade by animateFloatAsState(if (open) 1f else 0f, Motion.fade(), label = "searchFade")
    val focus = remember { FocusRequester() }
    BoxWithConstraints(modifier.fillMaxWidth().height(48.dp)) {
        val full = maxWidth
        if (fade < .99f) {
            MonthPill(
                state.month, state.months, onMonth,
                modifier = Modifier.align(Alignment.CenterStart).graphicsLayer {
                    alpha = 1f - fade
                    translationX = -expand * 24.dp.toPx()
                },
            )
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                // The bouncy spring may overshoot; width() clamps to the row anyway.
                .width((48.dp + (full - 48.dp) * expand).coerceAtLeast(40.dp))
                .height(48.dp)
                .glass(24.dp)
                // One handler for both states: a disabled inner clickable would swallow the tap.
                .tap(label = if (open) "输入搜索内容" else "搜索") {
                    if (!open) {
                        HomeSearch.focusPending = true
                        HomeSearch.open = true
                    } else {
                        focus.requestFocus()
                        keyboard?.show()
                    }
                },
        ) {
            // Laid out at full width and clipped by the glass, so the magnifier slides left as it grows.
            Row(
                Modifier.wrapContentWidth(Alignment.Start, unbounded = true).width(full).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Glyph(LedgerIcons.get("search"), 20.dp, lerp(c.ink, c.sub, fade.coerceIn(0f, 1f)))
                }
                if (open || fade > .01f) {
                    Box(Modifier.weight(1f).graphicsLayer { alpha = fade }, contentAlignment = Alignment.CenterStart) {
                        if (HomeSearch.query.isEmpty()) {
                            Text("搜索备注、分类或金额", fontSize = 15.sp, color = c.placeholder, maxLines = 1)
                        }
                        BasicTextField(
                            value = HomeSearch.query,
                            onValueChange = { HomeSearch.query = it },
                            singleLine = true,
                            enabled = open,
                            textStyle = TextStyle(fontSize = 15.sp, color = c.ink),
                            cursorBrush = SolidColor(c.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    Box(
                        Modifier.size(48.dp).graphicsLayer { alpha = fade }.tap(label = "关闭搜索", enabled = open, onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Glyph(LedgerIcons.get("close", 2.2f), 18.dp, c.sub)
                    }
                    LaunchedEffect(HomeSearch.focusPending) {
                        if (HomeSearch.focusPending) {
                            HomeSearch.focusPending = false
                            focus.requestFocus()
                            keyboard?.show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoResults(modifier: Modifier) {
    val c = LedgerTheme.colors
    Column(
        modifier.fillMaxWidth().glass(26.dp).padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Glyph(LedgerIcons.get("search"), 28.dp, c.sub)
        Text("没有找到相关账单", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink)
        Text("换个关键词或金额试试", fontSize = 12.sp, color = c.sub)
    }
}

@Composable
private fun HeroCard(state: LedgerState, onOpenBudgets: () -> Unit, modifier: Modifier) {
    val c = LedgerTheme.colors
    val s = state.summary
    Column(
        modifier.fillMaxWidth().glass(30.dp, refract = true, glint = true).padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("本月支出", fontSize = 13.sp, color = c.sub)
                // Six-figure totals outgrow the space beside the ring: the amount keeps its width,
                // glides to the decimals and back once when it overflows, and can be dragged.
                val amount = formatCents(s.expenseCents)
                AutoScroll(amount, Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text("¥", style = numberStyle(20.sp, FontWeight.Normal, 0f), color = c.ink, modifier = Modifier.padding(end = 4.dp, bottom = 5.dp), maxLines = 1, softWrap = false)
                    Odometer(amount, numberStyle(40.sp, FontWeight.Light, -.035f), c.ink)
                }
                val hint = when {
                    s.perDayLeftCents != null -> "月底前每天约可花 ¥${formatCents(s.perDayLeftCents)}"
                    s.month == YearMonth.from(state.today) && s.budgetLeftCents < 0 -> "已超出预算 ¥${formatCents(-s.budgetLeftCents)}"
                    else -> "日均支出 ¥${formatCents(s.avgPerDayCents)}"
                }
                Text(hint, fontSize = 12.sp, color = c.sub, style = numberStyle(12.sp, FontWeight.Normal, 0f))
            }
            // Both budget figures open the 预算 tab.
            BudgetRing(s.budgetUsed, Modifier.clip(CircleShape).tap(label = "查看预算", pressedScale = .95f, onClick = onOpenBudgets))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("收入", formatCents(s.incomeCents), Modifier.weight(1f))
            StatChip("结余", formatCents(s.balanceCents), Modifier.weight(1f))
            StatChip("预算还剩", formatCents(s.budgetLeftCents), Modifier.weight(1f), onClick = onOpenBudgets)
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val c = LedgerTheme.colors
    Column(
        modifier.chip(18.dp).then(if (onClick != null) Modifier.tap(label = label, pressedScale = .96f, onClick = onClick) else Modifier).padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, fontSize = 12.sp, color = c.sub)
        // A long figure keeps the chip's width: it glides to its end and back once, and can be dragged.
        AutoScroll(value, Modifier.fillMaxWidth()) {
            Text(value, style = numberStyle(13.sp, FontWeight.Medium, -.02f), color = c.ink, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun EmptyMonth(modifier: Modifier) {
    val c = LedgerTheme.colors
    Column(
        modifier.fillMaxWidth().glass(26.dp).padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Glyph(LedgerIcons.get("list"), 28.dp, c.sub)
        Text("这个月还没有记录", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink)
        Text("点右下角的 + 记一笔", fontSize = 12.sp, color = c.sub)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.DayCard(
    day: DayGroup,
    state: LedgerState,
    justAdded: Long?,
    animatedScope: AnimatedVisibilityScope,
    onOpen: (Record) -> Unit,
    onEdit: (Record, Offset) -> Unit,
    onDelete: (Record) -> Unit,
    onJustAddedShown: () -> Unit,
    modifier: Modifier,
    /** Search results span years: prefix the year for days outside the current one. */
    showYear: Boolean = false,
) {
    val c = LedgerTheme.colors
    Column(modifier.fillMaxWidth().glass(26.dp).padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp).animateContentSize(Motion.size)) {
        Row(Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    relativeDay(day.date, state.today)?.let {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(it) }
                        append(" ")
                    }
                    withStyle(SpanStyle(color = c.sub)) {
                        if (showYear && day.date.year != state.today.year) append("${day.date.year}年")
                        append(dayLabel(day.date))
                    }
                },
                fontSize = 13.sp,
                color = c.ink,
                modifier = Modifier.weight(1f),
            )
            val parts = buildList {
                if (day.expenseCents > 0) add("支出 ${formatCents(day.expenseCents)}")
                if (day.incomeCents > 0) add("收入 ${formatCents(day.incomeCents)}")
            }
            Text(parts.joinToString("  "), style = numberStyle(13.sp, FontWeight.Normal, 0f), color = c.sub)
        }
        day.records.forEach { r ->
            androidx.compose.runtime.key(r.id) {
                SwipeRow(r, highlight = r.id == justAdded, animatedScope, onOpen, onEdit, onDelete, onJustAddedShown)
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.SwipeRow(
    r: Record,
    highlight: Boolean,
    animatedScope: AnimatedVisibilityScope,
    onOpen: (Record) -> Unit,
    onEdit: (Record, Offset) -> Unit,
    onDelete: (Record) -> Unit,
    onJustAddedShown: () -> Unit,
) {
    val c = LedgerTheme.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    // Plain remember, not rememberSaveable: a row restored by undo must come back settled,
    // not in the swiped-away position the list would otherwise restore.
    var drag by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    var armed by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val threshold = width * .4f

    // A freshly saved row grows in from zero height with a warm glow (prototype "rowIn").
    val grow = remember { Animatable(if (highlight) 0f else 1f) }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(highlight) {
        if (!highlight) return@LaunchedEffect
        glow.snapTo(1f)
        grow.animateTo(1f, Motion.layout())
        glow.animateTo(0f, Motion.theme())
        onJustAddedShown()
    }

    val dragState = rememberDraggableState { delta ->
        if (deleting) return@rememberDraggableState
        drag = (drag + delta).coerceIn(-width.toFloat(), 0f)
        val now = -drag > threshold
        if (now != armed) {
            armed = now
            haptics.tick()
        }
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .onSizeChanged { width = it.width }
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints)
                val h = (p.height * grow.value.coerceIn(0f, 1f)).toInt()
                layout(p.width, h) { p.place(0, 0) }
            }
            .draggable(
                dragState,
                Orientation.Horizontal,
                enabled = !deleting,
                onDragStopped = { velocity ->
                    if (armed || velocity < -2400f) {
                        deleting = true
                        animate(drag, -width.toFloat(), velocity, Motion.layout()) { v, _ -> drag = v }
                        onDelete(r)
                    } else {
                        armed = false
                        animate(drag, 0f, velocity, Motion.layout()) { v, _ -> drag = v }
                    }
                },
            ),
        contentAlignment = Alignment.CenterEnd,
    ) {
        // Rows are translucent glass, so only paint the strip the row has uncovered.
        val revealed = with(LocalDensity.current) { (-drag).toDp() }
        if (revealed > 0.dp) {
            val gift = c.hue(com.jizhang.domain.Hue.Gift)
            Box(
                Modifier
                    .matchParentSize()
                    .wrapContentWidth(Alignment.End)
                    .width(revealed)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (armed) gift.copy(alpha = .9f) else c.tint(com.jizhang.domain.Hue.Gift)),
                contentAlignment = Alignment.Center,
            ) {
                if (revealed > 40.dp) Glyph(LedgerIcons.get("trash"), 22.dp, if (armed) c.onTile else gift)
            }
        }
        RecordRow(
            r = r,
            animatedScope = animatedScope,
            modifier = Modifier
                .offset { IntOffset(drag.roundToInt(), 0) }
                .background(c.rowGlow.copy(alpha = c.rowGlow.alpha * glow.value))
                .graphicsLayer { alpha = grow.value.coerceIn(0f, 1f) },
            onOpen = {
                if (drag != 0f) scope.launch { animate(drag, 0f, 0f, Motion.layout()) { v, _ -> drag = v } } else onOpen(r)
            },
            onEdit = { origin -> onEdit(r, origin) },
        )
    }
}

/**
 * One record. The middle opens the detail page; the icon and the amount jump straight into
 * the edit sheet, which grows out of whichever was tapped.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.RecordRow(
    r: Record,
    animatedScope: AnimatedVisibilityScope,
    modifier: Modifier,
    onOpen: () -> Unit,
    onEdit: (Offset) -> Unit,
) {
    val c = LedgerTheme.colors
    var iconCenter by remember { mutableStateOf(Offset.Zero) }
    var amountCenter by remember { mutableStateOf(Offset.Zero) }
    Row(
        modifier
            .sharedBounds(
                rememberSharedContentState(SharedKeys.card(r.id)),
                animatedScope,
                boundsTransform = { _, _ -> Motion.bounds },
                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(16.dp)),
            )
            .fillMaxWidth()
            .height(60.dp)
            .tap(label = "查看 ${r.title}", pressedScale = .98f, onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 48dp+ touch targets around the 42dp tile and the amount.
        Box(
            Modifier
                .size(width = 62.dp, height = 60.dp)
                .onGloballyPositioned { iconCenter = it.boundsInRoot().center }
                .tap(label = "编辑 ${r.title}") { onEdit(iconCenter) },
            contentAlignment = Alignment.Center,
        ) {
            CategoryTile(
                r.category, 42.dp, 15.dp, iconSize = 21.dp,
                modifier = Modifier.sharedElement(
                    rememberSharedContentState(SharedKeys.icon(r.id)), animatedScope,
                    boundsTransform = { _, _ -> Motion.bounds },
                ),
            )
        }
        Column(Modifier.weight(1f).padding(start = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    r.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                // Has photos: a quiet hint, the photos themselves live in the detail page.
                if (r.images.isNotEmpty()) Glyph(LedgerIcons.get("image"), 13.dp, c.sub)
            }
            Text("${hhmm(r.occurredAt)} · ${r.category.label}", fontSize = 12.sp, color = c.sub)
        }
        Box(
            Modifier
                .heightIn(min = 60.dp)
                .widthIn(min = 48.dp)
                .onGloballyPositioned { amountCenter = it.boundsInRoot().center }
                .tap(label = "编辑金额") { onEdit(amountCenter) }
                .padding(start = 12.dp, end = 8.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                formatSigned(r.amountCents, r.kind),
                style = numberStyle(16.sp, FontWeight.Medium, 0f),
                color = if (r.kind == EntryKind.Income) c.income else c.ink,
                modifier = Modifier.sharedBounds(
                    rememberSharedContentState(SharedKeys.amount(r.id)), animatedScope,
                    boundsTransform = { _, _ -> Motion.bounds },
                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                ),
            )
        }
    }
}

/** Space the floating nav bar needs at the bottom of scrolling content. */
val NavBarSpace: Dp = 62.dp + 24.dp + 16.dp
