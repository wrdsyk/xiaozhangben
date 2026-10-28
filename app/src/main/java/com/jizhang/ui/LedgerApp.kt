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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.LedgerViewModel
import com.jizhang.ToastMessage
import com.jizhang.data.ThemeId
import com.jizhang.ui.add.AddSheet
import com.jizhang.ui.add.SheetRequest
import com.jizhang.ui.budget.BudgetScreen
import com.jizhang.ui.components.AmbientBackground
import com.jizhang.ui.components.FormSheetController
import com.jizhang.ui.components.FormSheetHost
import com.jizhang.ui.components.LocalFormSheet
import com.jizhang.ui.components.Glyph
import com.jizhang.ui.components.LocalEntrancePlayed
import com.jizhang.ui.components.LocalHazeState
import com.jizhang.ui.components.glass
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
import com.jizhang.ui.theme.rememberThemeReveal
import com.jizhang.ui.theme.themeReveal
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
            LocalEntrancePlayed provides entrancePlayed,
            LocalFormSheet provides form,
        ) {
            Box(Modifier.fillMaxSize().themeReveal(reveal).background(c.bg0)) {
                AmbientBackground(Modifier.fillMaxSize().hazeSource(haze))

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
                                            budgets = budgets,
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
                                BottomBar(
                                    tab = tab,
                                    onTab = { tab = it },
                                    onAdd = { sheet = SheetRequest(++sheetSeq, null, fabCenter) },
                                    onFabPositioned = { fabCenter = it },
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                )
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
                        vm.save(req.id, req.category, req.cents, req.note, req.at, req.photos)
                        if (!again) {
                            sheet = null
                            if (req.id == null) {
                                tab = Tab.Home
                                scope.launch { homeList.animateScrollToItem(0) }
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
            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BoxWithConstraints(Modifier.weight(1f).height(62.dp).glass(31.dp, refract = true).padding(5.dp)) {
            val slot = maxWidth / Tab.entries.size
            Box(
                Modifier
                    .offset { IntOffset((slot.toPx() * lens).toInt(), 0) }
                    .width(slot)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(26.dp))
                    .background(c.lens)
                    .innerShadow(RoundedCornerShape(26.dp), Shadow(0.dp, Color.White.copy(alpha = 1f - .7f * c.darkness), offset = DpOffset(0.dp, 1.dp))),
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
        val fabShape = CircleShape
        Box(
            Modifier
                .size(62.dp)
                .onGloballyPositioned { onFabPositioned(it.boundsInRoot().center) }
                .dropShadow(fabShape, Shadow(28.dp, c.accentShadow, spread = (-12).dp, offset = DpOffset(0.dp, 14.dp)))
                .clip(fabShape)
                .background(Brush.verticalGradient(0f to lerp(c.accent, Color.White, c.sheen.alpha), .55f to c.accent))
                .innerShadow(fabShape, Shadow(1.dp, Color.White.copy(alpha = .35f + .25f * c.darkness), offset = DpOffset(0.dp, 1.dp)))
                .tap(label = "记一笔", onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) { Glyph(LedgerIcons.get("plus", 2.2f), 26.dp, c.onAccent) }
    }
}

/** Glass pill that drops in from the top; delete toasts carry an undo button. */
@Composable
private fun ToastHost(message: ToastMessage?, onUndo: (ToastMessage) -> Unit, onTimeout: (Long) -> Unit) {
    val c = LedgerTheme.colors
    var last by remember { mutableStateOf(message) }
    if (message != null) last = message
    LaunchedEffect(message?.id) {
        val m = message ?: return@LaunchedEffect
        delay(if (m.undo != null) 5_000 else 2_100)
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
                    .padding(start = 9.dp, end = if (m.undo != null) 6.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    Glyph(LedgerIcons.get(if (m.undo != null) "trash" else "check", 2.6f), 14.dp, c.onAccent)
                }
                Text(m.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.ink, maxLines = 1)
                if (m.undo != null) {
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
