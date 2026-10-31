package com.jizhang.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.LedgerState
import com.jizhang.LedgerViewModel
import com.jizhang.ToastMessage
import com.jizhang.data.GlassFinish
import com.jizhang.data.ThemeId
import com.jizhang.ui.add.AddSheet
import com.jizhang.ui.add.SheetRequest
import com.jizhang.ui.budget.BudgetScreen
import com.jizhang.ui.components.AmbientBackground
import com.jizhang.ui.components.FormSheetController
import com.jizhang.ui.components.FormSheetHost
import com.jizhang.ui.components.RecordButton
import com.jizhang.ui.components.LocalFormSheet
import com.jizhang.ui.components.Glyph
import com.jizhang.ui.components.LocalEntrancePlayed
import com.jizhang.ui.components.LocalGlassFinish
import com.jizhang.ui.components.LiquidBackdrop
import com.jizhang.ui.components.LocalHazeState
import com.jizhang.ui.components.LocalLiquidBackdrop
import com.jizhang.ui.components.LocalLiquidOverContent
import com.jizhang.ui.components.liquidContent
import com.jizhang.ui.components.liquidSource
import com.jizhang.ui.components.glass
import com.jizhang.ui.components.liftedPill
import com.jizhang.ui.components.tap
import com.jizhang.ui.detail.DetailScreen
import com.jizhang.ui.home.HomeScreen
import com.jizhang.ui.me.MeScreen
import com.jizhang.ui.recurring.RecurringScreen
import com.jizhang.ui.stats.StatsScreen
import com.jizhang.ui.update.UpdateEvents
import com.jizhang.ui.theme.LedgerIcons
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.Motion
import com.jizhang.ui.theme.rememberHaptics
import com.jizhang.ui.theme.rememberThemeReveal
import com.jizhang.ui.theme.themeReveal
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.math.roundToInt

enum class Tab(val label: String, val icon: String) {
    Home("明细", "list"),
    Stats("统计", "chart"),
    Budget("预算", "target"),
    Me("我的", "user"),
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LedgerApp(vm: LedgerViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val justAdded by vm.justAdded.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val recurringRules by vm.recurring.collectAsStateWithLifecycle()

    LedgerTheme(state.settings.theme) {
        val c = LedgerTheme.colors
        val reveal = rememberThemeReveal()
        SystemBars(
            statusDark = { reveal.barIsDark(top = true, targetDark = c.isDark) },
            navDark = { reveal.barIsDark(top = false, targetDark = c.isDark) },
        )
        val haze = rememberHazeState()
        val liquid = remember { LiquidBackdrop() }
        val scope = rememberCoroutineScope()
        var tab by rememberSaveable { mutableStateOf(Tab.Home) }
        var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
        // The 定时记账 page, opened from 我的; an overlay above the tabs like the add sheet.
        var recurringOpen by rememberSaveable { mutableStateOf(false) }
        val form = remember { FormSheetController() }
        var sheet by remember { mutableStateOf<SheetRequest?>(null) }
        var sheetSeq by remember { mutableStateOf(0L) }
        var fabCenter by remember { mutableStateOf(Offset.Zero) }
        val homeList = rememberLazyListState()
        val haptics = rememberHaptics()
        val density = LocalDensity.current
        // The month header and the hero card are the list's first two items. Once the first visible
        // item is a day card the hero has scrolled off, the list is being browsed, and the way back
        // up is a long swipe: that is when the ↑ button is worth showing.
        val homeScrolled by remember { derivedStateOf { homeList.firstVisibleItemIndex >= HomeLeadingItems } }
        // The glide to the top is under way: the button stays up until the list has landed.
        var toTopRunning by remember { mutableStateOf(false) }
        var revealJob by remember { mutableStateOf<Job?>(null) }
        var entrancePlayed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(1_500)
            entrancePlayed = true
        }
        // The theme the settings flow has caught up to; a switch waits for it before revealing.
        val appliedTheme = rememberUpdatedState(state.settings.theme)
        var pendingTheme by remember { mutableStateOf<ThemeId?>(null) }

        // A record opened in the detail page can disappear (deleted elsewhere).
        val detail = detailId?.let { id -> state.allRecords.firstOrNull { it.id == id } }
        LaunchedEffect(detail == null, state.loaded) {
            if (state.loaded && detailId != null && detail == null) detailId = null
        }
        BackHandler(enabled = sheet == null && !form.isOpen && (detailId != null || tab != Tab.Home)) {
            if (detailId != null) detailId = null else tab = Tab.Home
        }

        val status = WindowInsets.statusBars.asPaddingValues()
        val nav = WindowInsets.navigationBars.asPaddingValues()
        val listPadding = PaddingValues(
            top = status.calculateTopPadding() + 8.dp,
            bottom = nav.calculateBottomPadding() + 62.dp + 40.dp,
        )

        CompositionLocalProvider(
            LocalHazeState provides haze,
            LocalGlassFinish provides state.settings.glass,
            LocalLiquidBackdrop provides liquid,
            LocalEntrancePlayed provides entrancePlayed,
            LocalFormSheet provides form,
        ) {
            Box(Modifier.fillMaxSize().themeReveal(reveal).background(c.bg0)) {
                AmbientBackground(Modifier.fillMaxSize().hazeSource(haze).liquidSource(liquid))

                SharedTransitionLayout(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = detailId,
                        transitionSpec = { fadeIn(Motion.fade()) togetherWith fadeOut(Motion.fade()) },
                        label = "detail",
                    ) { id ->
                        val animatedScope = this
                        if (id == null) {
                            Box(Modifier.fillMaxSize()) {
                                AnimatedContent(
                                    // Frosted glass shows the list passing under the nav bar and the
                                    // sheets. Cards inside the screens still blur only the background:
                                    // a backdrop takes the sources behind its nearest source (zIndex < 1).
                                    // 晶透 records the content for the nav bar's lens instead.
                                    modifier = if (state.settings.glass == GlassFinish.Frost) Modifier.hazeSource(haze, zIndex = 1f) else Modifier.liquidContent(liquid),
                                    targetState = tab,
                                    transitionSpec = {
                                        (fadeIn(Motion.fade()) + slideInVertically(Motion.offset) { it / 30 })
                                            .togetherWith(fadeOut(Motion.fade()))
                                    },
                                    label = "tab",
                                ) { t ->
                                    when (t) {
                                        Tab.Home -> HomeScreen(
                                            state = state,
                                            listState = homeList,
                                            justAdded = justAdded,
                                            onOpenBudgets = { tab = Tab.Budget },
                                            animatedScope = animatedScope,
                                            contentPadding = listPadding,
                                            onMonth = vm::selectMonth,
                                            onOpen = { detailId = it.id },
                                            onEdit = { r, origin -> sheet = SheetRequest(++sheetSeq, r, origin) },
                                            onDelete = vm::delete,
                                            onJustAddedShown = vm::clearJustAdded,
                                        )
                                        Tab.Stats -> StatsScreen(state, listPadding, onOpenRecord = { detailId = it.id })
                                        Tab.Budget -> BudgetScreen(state, budgets, vm, listPadding)
                                        Tab.Me -> MeScreen(state, vm, listPadding, onOpenRecurring = { recurringOpen = true }) { theme, origin ->
                                            if (theme != (pendingTheme ?: state.settings.theme)) {
                                                pendingTheme = theme
                                                reveal.start(scope, origin, c.isDark) {
                                                    vm.setTheme(theme)
                                                    // Reveal only once the settings flow has caught up, or the circle
                                                    // would open onto the old colours.
                                                    withTimeoutOrNull(600) { snapshotFlow { appliedTheme.value }.first { it == theme } }
                                                    if (pendingTheme == theme) pendingTheme = null
                                                }
                                            }
                                        }
                                    }
                                }
                                // Lists scroll under the status bar (edge-to-edge); a soft wash keeps
                                // the clock and icons readable over passing cards.
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(status.calculateTopPadding() + 16.dp)
                                        .background(
                                            // Neutral, not bg0: the dusk base colour is a saturated blue
                                            // that doesn't match its ambient gradient.
                                            androidx.compose.ui.graphics.lerp(Color.White, Color.Black, c.darkness).let { w ->
                                                Brush.verticalGradient(listOf(w.copy(alpha = .62f), w.copy(alpha = .38f), w.copy(alpha = 0f)))
                                            },
                                        ),
                                )
                                CompositionLocalProvider(LocalLiquidOverContent provides true) {
                                    BottomBar(
                                        tab = tab,
                                        onTab = { tab = it },
                                        onAdd = { sheet = SheetRequest(++sheetSeq, null, fabCenter) },
                                        onFabPositioned = { fabCenter = it },
                                        modifier = Modifier.align(Alignment.BottomCenter),
                                    )
                                    ScrollTopButton(
                                        visible = tab == Tab.Home && (homeScrolled || toTopRunning),
                                        onClick = {
                                            if (!toTopRunning) {
                                                haptics.tick()
                                                toTopRunning = true
                                                scope.launch {
                                                    // A glide, not a jump.
                                                    try {
                                                        homeList.animateScrollToItem(0)
                                                    } finally {
                                                        toTopRunning = false
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.align(Alignment.BottomEnd),
                                    )
                                }
                            }
                        } else {
                            val record = state.allRecords.firstOrNull { it.id == id }
                            if (record != null) {
                                val recordMonth = java.time.YearMonth.from(record.date)
                                // The record's own month, for its summary and for the category share on the page.
                                val monthRecords = remember(state.allRecords, recordMonth) {
                                    state.allRecords.filter { java.time.YearMonth.from(it.date) == recordMonth }
                                }
                                DetailScreen(
                                    record = record,
                                    summary = if (recordMonth == state.month) state.summary else com.jizhang.domain.MonthSummary.of(
                                        recordMonth,
                                        monthRecords,
                                        state.settings.budgetCents,
                                        state.today,
                                    ),
                                    monthRecords = monthRecords,
                                    animatedScope = animatedScope,
                                    onBack = { detailId = null },
                                    onEdit = { origin -> sheet = SheetRequest(++sheetSeq, record, origin) },
                                    onDelete = {
                                        detailId = null
                                        vm.delete(record)
                                    },
                                )
                            }
                        }
                    }
                }

                // Above the tabs, so its back handler (registered later) wins over the tab one.
                RecurringScreen(
                    visible = recurringOpen,
                    rules = recurringRules,
                    state = state,
                    vm = vm,
                    onClose = { recurringOpen = false },
                )

                AddSheet(
                    request = sheet,
                    today = state.today,
                    catalog = state.catalog,
                    vm = vm,
                    onClose = { sheet = null },
                    onSave = { req, again ->
                        val opened = sheet?.editing
                        val saved = vm.save(req.id, req.category, req.cents, req.note, req.at, req.photos)
                        if (!again) {
                            // An edit opened from the detail page or the stats stays where it is.
                            val fromList = detailId == null && tab == Tab.Home
                            sheet = null
                            if (req.id == null) tab = Tab.Home
                            // A new bill, or an edit that moved to another day, is followed to where it now sits.
                            if (req.id == null || (fromList && opened?.date != req.at.toLocalDate())) {
                                revealJob?.cancel()
                                revealJob = scope.launch {
                                    revealBill({ state }, vm::selectMonth, homeList, density, saved, req.at)
                                }
                            }
                        }
                    },
                )

                FormSheetHost(form)
                // Opens the update sheet when a check finds a newer release (waits for the add sheet).
                UpdateEvents(vm, canOpen = sheet == null)
                // Record photos, full screen; above forms since NoteForm opens it too.
                com.jizhang.ui.components.PhotoViewerHost()
                ToastHost(toast, onUndo = vm::undo, onTimeout = vm::dismissToast)
            }
        }
    }
}

/**
 * Status and navigation bar icons: dark icons on light surfaces. Each answers "is the surface behind
 * this bar dark?", and during a theme reveal that flips when the circle reaches the bar. They are
 * read inside snapshotFlow, so the animation never recomposes the app.
 */
@Composable
private fun SystemBars(statusDark: () -> Boolean, navDark: () -> Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val status by rememberUpdatedState(statusDark)
    val nav by rememberUpdatedState(navDark)
    LaunchedEffect(view) {
        val window = (view.context as Activity).window
        val bars = WindowCompat.getInsetsController(window, view)
        snapshotFlow { status() to nav() }.collect { (statusOnDark, navOnDark) ->
            bars.isAppearanceLightStatusBars = !statusOnDark
            bars.isAppearanceLightNavigationBars = !navOnDark
        }
    }
}

// The bar's measurements, shared with ScrollTopButton, which floats over the + button.
private val BarSide = 20.dp
private val BarBottom = 16.dp
private val FabSize = 62.dp

/** Floating glass nav with a sliding lens, plus the + button. Sits above the gesture bar. */
@Composable
private fun BottomBar(
    tab: Tab,
    onTab: (Tab) -> Unit,
    onAdd: () -> Unit,
    onFabPositioned: (Offset) -> Unit,
    modifier: Modifier,
) {
    val c = LedgerTheme.colors
    val lens by animateFloatAsState(tab.ordinal.toFloat(), Motion.layout(), label = "lens")
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = BarSide, end = BarSide, bottom = BarBottom),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BoxWithConstraints(Modifier.weight(1f).height(FabSize).glass(31.dp, refract = true).padding(5.dp)) {
            val slot = maxWidth / Tab.entries.size
            Box(
                Modifier
                    .offset { IntOffset((slot.toPx() * lens).toInt(), 0) }
                    .width(slot)
                    .fillMaxHeight()
                    .liftedPill(26.dp, c.lens),
            )
            Row(Modifier.fillMaxSize()) {
                Tab.entries.forEach { t ->
                    val on = t == tab
                    Column(
                        Modifier.weight(1f).fillMaxHeight().tap(label = t.label, pressedScale = .95f) { onTab(t) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Glyph(LedgerIcons.get(t.icon), 22.dp, if (on) c.ink else c.sub)
                        Text(t.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (on) c.ink else c.sub)
                    }
                }
            }
        }
        RecordButton(FabSize, onAdd, Modifier.onGloballyPositioned { onFabPositioned(it.boundsInRoot().center) })
    }
}

private val TopButtonSize = 48.dp

/**
 * Round glass ↑ above the + button, centred on it: glides the 明细 list back to the top. It is a
 * sibling of the bar rather than part of it, so the bar keeps its size and its taps, and a child
 * placed outside the bar's bounds would not receive touches anyway. Same insets as the bar.
 */
@Composable
private fun ScrollTopButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = LedgerTheme.colors
    Box(
        modifier
            .navigationBarsPadding()
            .padding(end = BarSide + (FabSize - TopButtonSize) / 2, bottom = BarBottom + FabSize + 14.dp),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.fade()) + scaleIn(Motion.layout(), initialScale = .6f) + slideInVertically(Motion.offset) { it / 2 },
            exit = fadeOut(Motion.fade()) + scaleOut(Motion.layout(), targetScale = .6f) + slideOutVertically(Motion.offset) { it / 2 },
        ) {
            Box(
                Modifier
                    .size(TopButtonSize)
                    .glass(TopButtonSize / 2)
                    .semantics { contentDescription = "回到顶部" }
                    .tap(label = "回到顶部", pressedScale = .92f, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(LedgerIcons.get("arrow-up", 2.2f), 20.dp, c.ink)
            }
        }
    }
}

// HomeScreen's list: item 0 is the "month" header, item 1 the "hero" card, then one DayCard per day
// of state.days. revealBill counts on that order, so keep it in step with HomeScreen.kt.
private const val HomeLeadingItems = 2

// A DayCard's geometry (HomeScreen.kt): 6dp padding and a 38dp date row above 60dp bill rows, 8dp below.
private val CardHead = 44.dp
private val CardRow = 60.dp
private val CardFoot = 8.dp

/** Where a bill sits in the 明细 list: its day card's item index, its row in that card, and the card's row count. */
private class BillSpot(val item: Int, val row: Int, val rows: Int)

/**
 * Scrolls the 明细 list to the bill [saved] wrote, dated [at], switching to its month once the bill has
 * landed there. The write lands asynchronously, so this waits (briefly) for the bill to reach [state]
 * and for the list to be laid out with it before it aims at an item index; if that never happens it
 * gives up quietly.
 */
private suspend fun revealBill(
    state: () -> LedgerState,
    selectMonth: (YearMonth) -> Unit,
    list: LazyListState,
    density: Density,
    saved: Deferred<Long>,
    at: LocalDateTime,
) {
    val date = at.toLocalDate()
    val month = YearMonth.from(date)
    val spot = withTimeoutOrNull(RevealWaitMs) {
        val id = saved.await()
        // Switch months only when the bill is there to show, so the new month never flashes its
        // empty state (an add has already switched: the ViewModel does it after the insert).
        snapshotFlow { state().allRecords.any { it.id == id && it.date == date } }.first { it }
        if (state().month != month) selectMonth(month)
        val found = snapshotFlow<BillSpot?> {
            val s = state()
            val item = s.days.indexOfFirst { it.date == date }
            if (s.month != month || item < 0) return@snapshotFlow null
            val records = s.days[item].records
            val row = records.indexOfFirst { it.id == id }
            // The list has caught up with the state once it counts the same items.
            if (row < 0 || list.layoutInfo.totalItemsCount != HomeLeadingItems + s.days.size) {
                null
            } else {
                BillSpot(HomeLeadingItems + item, row, records.size)
            }
        }.first { it != null }
        // A list that was off screen (another tab) still holds its old layout, which can match the
        // new item count by coincidence. The recomposition and measure for the new state land in
        // the next frame, so let two frames pass before the layout is read.
        repeat(2) { withFrameNanos { } }
        found
    } ?: return

    val info = list.layoutInfo
    // While a search is typed the list shows its results (keys "search-…") in place of the month.
    val firstKey = info.visibleItemsInfo.firstOrNull { it.index > 0 }?.key
    if (firstKey is String && firstKey.startsWith("search")) return

    val headPx = with(density) { CardHead.toPx() }
    val rowPx = with(density) { CardRow.toPx() }
    val billTop = headPx + rowPx * spot.row
    // Item offsets start at the content area, below the status bar wash; its end is above the bar.
    val contentEnd = info.viewportEndOffset - info.afterContentPadding
    val card = info.visibleItemsInfo.firstOrNull { it.index == spot.item }
    // The bill is already on screen: nothing to chase.
    if (card != null && card.offset + billTop >= 0 && card.offset + billTop + rowPx <= contentEnd) return
    // The whole card when it fits; otherwise the bill itself, with the row above it peeking in.
    val cardPx = headPx + rowPx * spot.rows + with(density) { CardFoot.toPx() }
    val scrollOffset = if (cardPx <= contentEnd) 0 else (billTop - headPx).roundToInt().coerceAtLeast(0)
    list.animateScrollToItem(spot.item, scrollOffset)
}

private const val RevealWaitMs = 2_000L

/** Glass pill that drops in from the top; delete toasts carry an undo button. */
@Composable
private fun ToastHost(message: ToastMessage?, onUndo: (ToastMessage) -> Unit, onTimeout: (Long) -> Unit) {
    val c = LedgerTheme.colors
    var last by remember { mutableStateOf(message) }
    if (message != null) last = message
    LaunchedEffect(message?.id) {
        val m = message ?: return@LaunchedEffect
        delay(if (m.undo.isNotEmpty()) 5_000 else 2_100)
        onTimeout(m.id)
    }
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = slideInVertically(Motion.offset) { -it * 2 } + scaleIn(Motion.layout(), initialScale = .8f) + fadeIn(Motion.fade()),
            exit = slideOutVertically(Motion.offset) { -it } + scaleOut(Motion.layout(), targetScale = .96f) + fadeOut(Motion.fade()),
        ) {
            val m = last ?: return@AnimatedVisibility
            Row(
                Modifier
                    .height(44.dp)
                    .glass(22.dp)
                    .padding(start = 9.dp, end = if (m.undo.isNotEmpty()) 6.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    Glyph(LedgerIcons.get(if (m.undo.isNotEmpty()) "trash" else "check", 2.6f), 14.dp, c.onAccent)
                }
                Text(m.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.ink, maxLines = 1)
                if (m.undo.isNotEmpty()) {
                    Box(
                        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(c.chipStrong)
                            .tap(label = "撤销删除") { onUndo(m) }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("撤销", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.accent) }
                }
            }
        }
    }
}
