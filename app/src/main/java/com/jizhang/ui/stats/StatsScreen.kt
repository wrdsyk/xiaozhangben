package com.jizhang.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.LedgerState
import com.jizhang.domain.BudgetLevel
import com.jizhang.domain.Category
import com.jizhang.domain.CategoryShare
import com.jizhang.domain.Granularity
import com.jizhang.domain.Movement
import com.jizhang.domain.Period
import com.jizhang.domain.Record
import com.jizhang.domain.Trend
import com.jizhang.domain.TrendBucket
import com.jizhang.domain.TrendChange
import com.jizhang.domain.formatCents
import com.jizhang.domain.formatCentsShort
import com.jizhang.ui.budget.levelColor
import com.jizhang.ui.chipDate
import com.jizhang.ui.dayLabel
import com.jizhang.ui.percentLabel
import com.jizhang.ui.components.AutoScroll
import com.jizhang.ui.components.CategoryTile
import com.jizhang.ui.components.Glyph
import com.jizhang.ui.components.LocalFormSheet
import com.jizhang.ui.components.Odometer
import com.jizhang.ui.components.Segmented
import com.jizhang.ui.components.chip
import com.jizhang.ui.components.glass
import com.jizhang.ui.components.rise
import com.jizhang.ui.components.tap
import com.jizhang.ui.theme.LedgerIcons
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.Motion
import com.jizhang.ui.theme.numberStyle
import com.jizhang.ui.theme.rememberHaptics
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import ir.ehsannarmani.compose_charts.PieChart
import ir.ehsannarmani.compose_charts.models.LabelHelperProperties
import ir.ehsannarmani.compose_charts.models.Pie
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The switch as last left, kept across tab switches for the life of the process. */
private var lastGranularity = Granularity.Month

/** First day of the period last picked; null follows the current one as the days go by. */
private var lastPick: Long? = null

/** Axis labels travel with the chart model, so they always match the series on screen. */
private val AxisLabelsKey = ExtraStore.Key<List<String>>()

/** Where the summary title and the scrolled content sit; read while the menu lays out, so not state. */
private class MenuAnchors {
    var content: LayoutCoordinates? = null
    var title: LayoutCoordinates? = null
}

@Composable
fun StatsScreen(
    state: LedgerState,
    contentPadding: PaddingValues,
    onOpenRecord: (Record) -> Unit,
) {
    val today = state.today
    var granularity by rememberSaveable { mutableStateOf(lastGranularity) }
    var pick by rememberSaveable { mutableStateOf(lastPick) }
    var menuOpen by remember { mutableStateOf(false) }
    val options = remember(state.allRecords, granularity, today) { Period.options(state.allRecords, granularity, today) }
    val current = remember(granularity, today) { Period.containing(granularity, today) }
    // A pick that lost its last record (deleted meanwhile) falls back to the current period.
    val period = pick?.let { Period.containing(granularity, LocalDate.ofEpochDay(it)) }?.takeIf { it in options } ?: current
    val trend = remember(state.allRecords, period, today) { Trend.of(state.allRecords, period, today) }
    // The period before needs its own records, which `trend` (this period only) has dropped.
    val change = remember(state.allRecords, period, today) { TrendChange.of(trend, state.allRecords, today) }
    val anchors = remember { MenuAnchors() }
    // The donut slice picked, as an index into trend.shares (-1: none). It lives up here because the
    // ranking below follows it; a new period (or granularity, or any change of the ledger that
    // reshapes the shares) starts over with no slice picked.
    var slice by remember(trend) { mutableIntStateOf(-1) }
    // A form opened above wins the back press; the menu only reacts while none is open.
    BackHandler(enabled = menuOpen && !LocalFormSheet.current.isOpen) { menuOpen = false }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp),
    ) {
        Box(Modifier.fillMaxWidth().onGloballyPositioned { anchors.content = it }) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Same 48dp row and 15sp labels as the 明细 header, so the two tabs start alike.
                Box(Modifier.fillMaxWidth().height(48.dp).rise(0).glass(24.dp)) {
                    Segmented(
                        options = Granularity.entries.map { it.label },
                        selected = granularity.ordinal,
                        onSelect = {
                            granularity = Granularity.entries[it]
                            pick = null
                            menuOpen = false
                            lastGranularity = granularity
                            lastPick = null
                        },
                        height = 48.dp,
                        fontSize = 15.sp,
                    )
                }
                SummaryCard(trend, today, menuOpen, { menuOpen = !menuOpen }, anchors, Modifier.rise(1))
                TrendCard(trend, change, today, Modifier.rise(2))
                ShareCard(trend.shares, trend.totalCents, slice, { slice = it }, Modifier.rise(3))
                RankingCard(trend, trend.shares.getOrNull(slice), { slice = -1 }, today, onOpenRecord, Modifier.rise(4))
            }
            // Catches the tap that lands outside the menu; the content under it stays put.
            if (menuOpen) Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { menuOpen = false } })
            PeriodMenu(
                visible = menuOpen,
                options = options,
                selected = period,
                today = today,
                anchors = anchors,
                onPick = {
                    pick = if (it == current) null else it.start.toEpochDay()
                    lastPick = pick
                    menuOpen = false
                },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/**
 * Hero total for the period, with its average and income. The title is the period picker: it
 * names the period ("本月支出") and opens [PeriodMenu] under itself.
 */
@Composable
private fun SummaryCard(
    trend: Trend,
    today: LocalDate,
    open: Boolean,
    onToggle: () -> Unit,
    anchors: MenuAnchors,
    modifier: Modifier,
) {
    val c = LedgerTheme.colors
    val period = trend.period
    val turn by animateFloatAsState(if (open) 180f else 0f, Motion.layout(), label = "chevron")
    Row(
        modifier.fillMaxWidth().glass(28.dp, refract = true).padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            // The whole title block is the 48dp touch target; the week's date range rides along.
            Column(
                Modifier
                    .heightIn(min = 48.dp)
                    .tap(label = "选择周期", pressedScale = .96f, onClick = onToggle)
                    .onGloballyPositioned { anchors.title = it },
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // "2025年第52周支出" is the longest; the chevron must stay in view next to it.
                    Text(
                        period.title(today),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Glyph(LedgerIcons.get("chev"), 16.dp, c.ink, Modifier.graphicsLayer { rotationZ = turn })
                }
                if (period.granularity == Granularity.Week) {
                    Text(period.rangeLabel, fontSize = 12.sp, color = c.sub, maxLines = 1)
                }
            }
            // Holds the width the column gives it: a total of ¥100,000+ glides sideways once (and
            // can be dragged) instead of running under the average and income on the right.
            AutoScroll(key = trend.totalCents to period, verticalAlignment = Alignment.Bottom) {
                Text("¥", style = numberStyle(17.sp, FontWeight.Normal, 0f), color = c.ink, modifier = Modifier.padding(end = 3.dp, bottom = 4.dp))
                Odometer(formatCents(trend.totalCents), numberStyle(32.sp, FontWeight.Light, -.03f), c.ink)
            }
        }
        Spacer(Modifier.width(10.dp))
        // Capped, so big averages and incomes leave the total its room; each scrolls on its own.
        Column(Modifier.widthIn(max = 132.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LabeledValue(trend.averageLabel, "¥${formatCents(trend.averageCents)}", c.ink, period)
            LabeledValue("收入", "¥${formatCents(trend.incomeCents)}", c.income, period)
        }
    }
}

private val MenuWidth = 292.dp
private val MenuRowHeight = 52.dp

/** Five and a half rows: the cut-off row tells the list scrolls. */
private val MenuMaxHeight = 298.dp

/**
 * The period dropdown: a glass panel in the page's own layout (not a popup window, so it stays
 * under the app's back handling and the form sheets), hung under the summary title. It springs
 * out from the title, lists [options] newest first and scrolls to the picked one.
 */
@Composable
private fun PeriodMenu(
    visible: Boolean,
    options: List<Period>,
    selected: Period,
    today: LocalDate,
    anchors: MenuAnchors,
    onPick: (Period) -> Unit,
    modifier: Modifier,
) {
    val c = LedgerTheme.colors
    val haptics = rememberHaptics()
    val origin = TransformOrigin(.12f, 0f)
    Box(
        modifier.layout { measurable, constraints ->
            val w = min(MenuWidth.roundToPx(), constraints.maxWidth)
            val p = measurable.measure(Constraints(maxWidth = w))
            val content = anchors.content?.takeIf { it.isAttached }
            val title = anchors.title?.takeIf { it.isAttached }
            val anchor = if (content != null && title != null) {
                content.localPositionOf(title, Offset(0f, title.size.height.toFloat()))
            } else {
                Offset.Zero
            }
            val x = (anchor.x - 8.dp.toPx()).coerceIn(0f, (constraints.maxWidth - p.width).toFloat().coerceAtLeast(0f))
            val y = anchor.y - 6.dp.toPx()
            layout(constraints.maxWidth, constraints.maxHeight) { p.place(x.roundToInt(), y.roundToInt()) }
        },
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.fade()) + scaleIn(Motion.layout(), initialScale = .86f, transformOrigin = origin),
            exit = fadeOut(Motion.fade()) + scaleOut(Motion.layout(), targetScale = .92f, transformOrigin = origin),
        ) {
            // Opaque, like the scrub popup: the cards it covers must not show through.
            val base = if (c.isDark) lerp(c.bg1, Color.Black, .25f) else lerp(c.bg0, Color.White, .5f)
            val scroll = rememberScrollState()
            val rowPx = with(LocalDensity.current) { MenuRowHeight.toPx() }
            LaunchedEffect(Unit) {
                val i = options.indexOf(selected)
                if (i > 2) scroll.scrollTo(((i - 2) * rowPx).roundToInt())
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass(22.dp, fill = SolidColor(base), backdrop = false)
                    .heightIn(max = MenuMaxHeight)
                    .verticalScroll(scroll)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                options.forEach { p ->
                    MenuRow(p.label(today), p.detail(today), p == selected) {
                        haptics.tick()
                        onPick(p)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, detail: String?, on: Boolean, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(MenuRowHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) c.opKey else Color.Transparent)
            .tap(label = label, pressedScale = .98f, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = c.ink, maxLines = 1)
            if (detail != null) Text(detail, fontSize = 12.sp, color = c.sub, maxLines = 1)
        }
        if (on) Glyph(LedgerIcons.get("check", 2.4f), 18.dp, c.ink)
    }
}

@Composable
private fun LabeledValue(label: String, value: String, color: Color, period: Period) {
    val c = LedgerTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ", fontSize = 12.sp, color = c.sub, maxLines = 1)
        // The label keeps its width; a value too long for the rest glides to its end once.
        AutoScroll(key = value to period, verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = numberStyle(12.sp, FontWeight.Medium, 0f), color = color, maxLines = 1, softWrap = false)
        }
    }
}

/** Where the crosshair sits: the bucket under it and its x on the chart canvas, in px. */
private data class Scrub(val index: Int, val x: Float)

/** Where the trend card and its chart sit; read while the popup lays out, so not state. */
private class Anchors {
    var card: LayoutCoordinates? = null
    var chart: LayoutCoordinates? = null
}

/**
 * Spending line (Vico) for the chosen period: grows in, gradient area, press and drag for a
 * crosshair. While pressed, a popup card above the crosshair shows that point's top three expenses.
 * Under the title, [change] says how the period's spending moved against the one before.
 */
@Composable
private fun TrendCard(trend: Trend, change: TrendChange, today: LocalDate, modifier: Modifier) {
    val c = LedgerTheme.colors
    val g = trend.granularity
    val buckets = trend.buckets
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(trend) {
        if (buckets.isNotEmpty()) {
            producer.runTransaction {
                lineSeries { series(buckets.map { it.expenseCents / 100.0 }) }
                extras { it.set(AxisLabelsKey, trend.axisLabels) }
            }
        }
    }
    val scrub = remember(buckets) { mutableStateOf<Scrub?>(null) }
    val anchors = remember { Anchors() }
    // The popup sits beside the glass card, not in it: the card clips, and the popup may rise over
    // the card's header and beyond.
    Box(modifier.fillMaxWidth().onGloballyPositioned { anchors.card = it }) {
        Column(
            Modifier.fillMaxWidth().glass(28.dp).padding(top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("支出趋势", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                if (buckets.isNotEmpty()) CompareLine(change, today)
            }
            if (buckets.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(164.dp), contentAlignment = Alignment.Center) {
                    Text("还没开始", fontSize = 13.sp, color = c.sub)
                }
            } else {
                TrendChart(producer, g, buckets, scrub) { anchors.chart = it }
            }
        }
        if (buckets.isNotEmpty()) ScrubPopup(scrub.value, buckets, g, anchors, Modifier.matchParentSize())
    }
}

/**
 * "相比上周 +¥200.00（+15.85%）": the figures read red when the period spent more than the one before
 * and green when it spent less (an expense page, so more is bad); equal reads "持平", and with
 * nothing spent on either side "均无支出". When the period before spent nothing there is no base
 * for a percent, so only the amount shows. Digits roll like the page's other totals, and the
 * colour fades when the verdict flips.
 */
@Composable
private fun CompareLine(change: TrendChange, today: LocalDate) {
    val c = LedgerTheme.colors
    // levelColor is tuned for bars. As 12sp text on a light glass its green reads at about 3:1 and
    // its red at 4:1, so on the light themes both go a shade darker (the dark themes need no help).
    fun ink(level: BudgetLevel) = lerp(c.levelColor(level), Color.Black, .22f * (1f - c.darkness.coerceIn(0f, 1f)))
    val target = when (change.movement) {
        Movement.Up -> ink(BudgetLevel.Red)
        Movement.Down -> ink(BudgetLevel.Green)
        Movement.Flat -> c.sub
    }
    val tone by animateColorAsState(target, Motion.fade(), label = "compareTone")
    val figures = numberStyle(12.sp, FontWeight.Medium, 0f)
    // A long amount glides sideways once instead of being cut off, like the summary's figures.
    AutoScroll(key = change, verticalAlignment = Alignment.CenterVertically) {
        Text("${change.label(today)} ", fontSize = 12.sp, color = c.sub, maxLines = 1, softWrap = false)
        if (change.movement == Movement.Flat) {
            Text(if (change.isEmpty) "均无支出" else "持平", fontSize = 12.sp, color = tone, maxLines = 1, softWrap = false)
        } else {
            Odometer(change.amountText, figures, tone)
            change.percentText?.let { percent ->
                Text("（", style = figures, color = tone, maxLines = 1, softWrap = false)
                Odometer(percent, figures, tone)
                Text("）", style = figures, color = tone, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** The popup card carries the numbers; Vico's own marker label stays empty and takes no room. */
private val NoLabel = object : DefaultCartesianMarker.ValueFormatter {
    override fun format(context: CartesianDrawingContext, targets: List<CartesianMarker.Target>): CharSequence = ""
}

/**
 * Headroom over the highest point, so the crosshair dot never sits under the popup's pointer; and
 * an x range as long as the whole period (one label per axis entry), so the current month's line
 * stops at today with the rest of the axis still there.
 */
private val ChartRange = object : CartesianLayerRangeProvider {
    override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore): Double =
        max(maxX, (extraStore.getOrNull(AxisLabelsKey)?.size ?: 0) - 1.0)

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
        if (maxY > 0) maxY * 1.14 else super.getMaxY(minY, maxY, extraStore)
}

@Composable
private fun TrendChart(
    producer: CartesianChartModelProducer,
    g: Granularity,
    buckets: List<TrendBucket>,
    scrub: MutableState<Scrub?>,
    onPlaced: (LayoutCoordinates) -> Unit,
) {
    val c = LedgerTheme.colors
    val haptics = rememberHaptics()
    val listener = remember(haptics, scrub) {
        object : CartesianMarkerVisibilityListener {
            override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) = point(targets)

            override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) = point(targets)

            override fun onHidden(marker: CartesianMarker) {
                scrub.value = null
            }

            private fun point(targets: List<CartesianMarker.Target>) {
                val t = targets.firstOrNull() ?: return
                val x = t.x.roundToInt()
                if (x != scrub.value?.index) haptics.tick()
                scrub.value = Scrub(x, t.canvasX)
            }
        }
    }
    // Every month and weekday gets its label; a month's days would crowd, so every seventh one.
    val spacing = if (g == Granularity.Month) 7 else 1
    val itemPlacer = remember(spacing) { HorizontalAxis.ItemPlacer.aligned(spacing = { spacing }) }
    val axisLabel = TextComponent(TextStyle(color = c.sub, fontSize = 10.sp))
    val hiddenLabel = TextComponent(TextStyle(color = Color.Transparent, fontSize = 1.sp))
    val line = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(Brush.horizontalGradient(listOf(c.ring1, c.ring2)))),
        stroke = LineCartesianLayer.LineStroke.Continuous(2.4.dp),
        areaFill = LineCartesianLayer.AreaFill.single(
            Fill(Brush.verticalGradient(listOf(c.ring2.copy(alpha = .34f), c.ring2.copy(alpha = 0f)))),
        ),
        // A single bucket (e.g. 周 on a Monday) has no segment to draw: show a dot.
        pointProvider = if (buckets.size == 1) {
            LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    ShapeComponent(Fill(c.accent), CircleShape, strokeFill = Fill(c.chipStrong.compositeOverOpaque(c)), strokeThickness = 3.dp),
                    12.dp,
                ),
            )
        } else {
            null
        },
        interpolator = LineCartesianLayer.Interpolator.cubic(),
    )
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(LineCartesianLayer.LineProvider.series(line), rangeProvider = ChartRange),
            bottomAxis = HorizontalAxis.rememberBottom(
                line = null,
                label = axisLabel,
                tick = null,
                guideline = null,
                valueFormatter = CartesianValueFormatter { context, x, _ ->
                    context.model.extraStore.getOrNull(AxisLabelsKey)?.getOrNull(x.roundToInt()) ?: ""
                },
                itemPlacer = itemPlacer,
            ),
            // Guideline and dot only; AroundPoint reserves no margin for the (empty) label.
            marker = rememberDefaultCartesianMarker(
                label = hiddenLabel,
                valueFormatter = NoLabel,
                labelPosition = DefaultCartesianMarker.LabelPosition.AroundPoint,
                indicator = { _ ->
                    ShapeComponent(Fill(c.accent), CircleShape, strokeFill = Fill(c.chipStrong.compositeOverOpaque(c)), strokeThickness = 3.dp)
                },
                indicatorSize = 12.dp,
                guideline = LineComponent(Fill(c.sub.copy(alpha = .35f)), 1.dp),
            ),
            markerVisibilityListener = listener,
            // Scrubbing owns the drag: without this the page's vertical scroll also moves while the
            // finger slides along the line, so the chart jumps under the finger mid-scrub.
            markerController = CartesianMarkerController.rememberShowOnPress(consumeMoveEvents = true),
        ),
        modelProducer = producer,
        modifier = Modifier.fillMaxWidth().height(164.dp).padding(horizontal = 10.dp).onGloballyPositioned(onPlaced),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        animationSpec = Motion.layout(),
    )
}

/** The chart's dot ring needs an opaque fill to stay legible over the line. */
private fun Color.compositeOverOpaque(c: com.jizhang.ui.theme.LedgerColors): Color =
    androidx.compose.ui.graphics.lerp(c.bg0, Color(red, green, blue), alpha.coerceAtLeast(.6f))

private val PopupWidth = 264.dp
private val PopupCorner = 16.dp
private val NotchWidth = 16.dp
private val NotchHeight = 8.dp

/**
 * The crosshair's popup: a lifted card above the chart, centred on the guideline (kept inside the
 * trend card) with a pointer down to it. Its fill is opaque, since the chart under it is not the
 * blurred backdrop. Springs in from the pointer, glides from point to point and crossfades its
 * content when the bucket changes.
 */
@Composable
private fun ScrubPopup(scrub: Scrub?, buckets: List<TrendBucket>, g: Granularity, anchors: Anchors, modifier: Modifier) {
    val c = LedgerTheme.colors
    val statusTop = WindowInsets.statusBars.getTop(LocalDensity.current)
    var last by remember { mutableStateOf(scrub) }
    if (scrub != null) last = scrub
    val visible = scrub != null
    val appear = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    val x = remember { Animatable(0f) }
    // The pointer's tip (on the guideline; also the scale origin) and the middle of its base (kept
    // clear of the rounded corners), as fractions of the card's width. They part only near the ends
    // of the line, where the pointer leans out to still meet the guideline.
    var pointer by remember { mutableFloatStateOf(.5f) }
    var base by remember { mutableFloatStateOf(.5f) }
    LaunchedEffect(visible) {
        launch { fade.animateTo(if (visible) 1f else 0f, Motion.fade()) }
        appear.animateTo(if (visible) 1f else 0f, Motion.layout())
    }
    val targetX = last?.x
    LaunchedEffect(targetX) {
        if (targetX == null) return@LaunchedEffect
        // A fresh press starts under the finger; after that the card glides between points.
        if (appear.value < .05f) x.snapTo(targetX) else x.animateTo(targetX, Motion.layout())
    }
    val shown = last ?: return
    val top = c.glassTop.compositeOver(c.bg1)
    val bottom = c.glassBottom.compositeOver(c.bg1)
    val edge = c.rimEnd
    Column(
        modifier
            .layout { measurable, constraints ->
                val margin = 6.dp.roundToPx()
                val w = min(PopupWidth.roundToPx(), constraints.maxWidth - 2 * margin).coerceAtLeast(1)
                val p = measurable.measure(Constraints(minWidth = w, maxWidth = w))
                val card = anchors.card?.takeIf { it.isAttached }
                val chart = anchors.chart?.takeIf { it.isAttached }
                val origin = if (card != null && chart != null) card.localPositionOf(chart, Offset.Zero) else Offset.Zero
                val tipX = origin.x + x.value
                val left = (tipX - w / 2f).coerceIn(margin.toFloat(), (constraints.maxWidth - margin - w).toFloat().coerceAtLeast(margin.toFloat()))
                val inset = PopupCorner.toPx() + NotchWidth.toPx() / 2
                pointer = (tipX - left).coerceIn(0f, w.toFloat()) / w
                base = (tipX - left).coerceIn(inset, (w - inset).coerceAtLeast(inset)) / w
                // The pointer rests on the chart's top edge, but the card never slides up under
                // the status bar when the page is scrolled.
                val floor = statusTop + 8.dp.toPx() - (card?.positionInRoot()?.y ?: 0f)
                val y = max(origin.y - p.height, floor)
                layout(constraints.maxWidth, constraints.maxHeight) { p.place(left.roundToInt(), y.roundToInt()) }
            }
            .graphicsLayer {
                val s = .86f + .14f * appear.value
                scaleX = s
                scaleY = s
                alpha = fade.value.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(pointer, 1f)
            }
            .drawWithContent {
                drawContent()
                // The pointer: filled like the card's bottom, tucked over its rim, edged like it.
                val nw = NotchWidth.toPx()
                val edgeY = size.height - NotchHeight.toPx()
                val cx = size.width * base
                val tip = size.width * pointer
                val fill = Path().apply {
                    moveTo(cx - nw / 2, edgeY - 2.dp.toPx())
                    lineTo(cx - nw / 2, edgeY)
                    lineTo(tip, size.height)
                    lineTo(cx + nw / 2, edgeY)
                    lineTo(cx + nw / 2, edgeY - 2.dp.toPx())
                    close()
                }
                drawPath(fill, bottom)
                val rim = Path().apply {
                    moveTo(cx - nw / 2, edgeY)
                    lineTo(tip, size.height)
                    lineTo(cx + nw / 2, edgeY)
                }
                drawPath(rim, edge, style = Stroke(1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            },
    ) {
        Box(Modifier.fillMaxWidth().glass(PopupCorner, fill = Brush.verticalGradient(listOf(top, bottom)), backdrop = false)) {
            AnimatedContent(
                targetState = shown.index,
                transitionSpec = {
                    (fadeIn(Motion.fade()) togetherWith fadeOut(Motion.fade()))
                        .using(SizeTransform(clip = false) { _, _ -> Motion.size })
                },
                label = "scrub",
            ) { i ->
                val b = buckets.getOrNull(i)
                if (b != null) PopupBody(b, g)
            }
        }
        Spacer(Modifier.height(NotchHeight))
    }
}

/** "最大3笔交易", the bucket's three largest expenses, and its total. */
@Composable
private fun PopupBody(b: TrendBucket, g: Granularity) {
    val c = LedgerTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "最大3笔交易",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.ink,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(c.ring1.copy(alpha = .5f), c.ring2.copy(alpha = .32f))))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                popupTitle(b, g),
                fontSize = 11.sp,
                color = c.sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(6.dp))
        if (b.top.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.CenterStart) {
                Text("没有支出", fontSize = 12.sp, color = c.sub)
            }
        } else {
            b.top.forEach { r ->
                Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryTile(r.category, 22.dp, 11.dp, iconSize = 12.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(shortDate(r.date), style = numberStyle(11.sp, FontWeight.Normal, 0f), color = c.sub)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        r.title,
                        fontSize = 12.sp,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(formatCents(r.amountCents), style = numberStyle(12.sp, FontWeight.Medium, 0f), color = c.ink)
                }
            }
        }
        Box(Modifier.padding(top = 6.dp, bottom = 7.dp).fillMaxWidth().height(1.dp).background(c.hair))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("当${g.bucketUnit}总支出：", fontSize = 11.sp, color = c.sub)
            Text(formatCents(b.expenseCents), style = numberStyle(12.sp, FontWeight.SemiBold, 0f), color = c.ink)
        }
    }
}

/** Which bucket the popup describes: "10月" in a year, "10月28日 周三" in a month or a week. */
private fun popupTitle(b: TrendBucket, g: Granularity): String =
    if (g == Granularity.Year) b.label else dayLabel(b.start)

/** "26/10/17" */
private fun shortDate(d: LocalDate) = "%02d/%02d/%02d".format(d.year % 100, d.monthValue, d.dayOfMonth)

private val DonutSize = 132.dp
private val RingWidth = 14.dp

/** How far past the ring, into the hole and out beyond it, a finger still counts as on the ring. */
private val RingReach = 14.dp

/** A press that stays put this long starts scrubbing; lifting sooner is a tap. */
private const val ScrubHoldMs = 170L

/** How a touch on the donut turned out within [ScrubHoldMs]. */
private enum class Press { Tap, Abandon, Hold }

/**
 * Category share donut (ComposeCharts). Tap a slice or a legend row to pick it, or press on the ring
 * and slide round it (like the line chart's crosshair); [selected] is the picked slice, -1 for none.
 * The ring dims the others and the centre shows the pick's amount and share; tapping it again, the
 * centre, or the clear chip in the ranking lets it go.
 */
@Composable
private fun ShareCard(
    shares: List<CategoryShare>,
    total: Long,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
) {
    val c = LedgerTheme.colors
    val haptics = rememberHaptics()
    val cents = remember(shares) { shares.map { it.cents } }
    fun colorOf(s: CategoryShare) = s.category?.let(c::hue) ?: c.mergedSlice
    Column(
        modifier.fillMaxWidth().glass(28.dp).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("分类占比", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
        if (shares.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(132.dp), contentAlignment = Alignment.Center) {
                Text("没有支出", fontSize = 13.sp, color = c.sub)
            }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(DonutSize).donutPicker(cents, selected, onSelect), contentAlignment = Alignment.Center) {
                // The chart only draws: taps and slides are read by donutPicker around it, so its own
                // tap callback stays unset.
                PieChart(
                    modifier = Modifier.size(DonutSize),
                    data = shares.mapIndexed { i, s ->
                        Pie(
                            label = s.label,
                            data = s.cents.toDouble(),
                            color = colorOf(s).copy(alpha = if (selected < 0 || selected == i) 1f else .35f),
                            selectedColor = colorOf(s),
                            selected = i == selected,
                        )
                    },
                    selectedScale = 1.08f,
                    spaceDegree = 2f,
                    style = Pie.Style.Stroke(width = RingWidth),
                    scaleAnimEnterSpec = Motion.layout(),
                    scaleAnimExitSpec = Motion.layout(),
                    colorAnimEnterSpec = Motion.fade(),
                    colorAnimExitSpec = Motion.fade(),
                    // The legend lives in the column on the right; the chart's own one would squeeze the ring.
                    labelHelperProperties = LabelHelperProperties(enabled = false),
                )
                val pick = shares.getOrNull(selected)
                val amount = formatCentsShort(pick?.cents ?: total)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 84.dp)) {
                    Text(pick?.label ?: "合计", fontSize = 11.sp, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // Past eight characters (¥1,234,567) 15sp would touch the ring.
                    Text(
                        amount,
                        style = numberStyle(if (amount.length > 8) 12.sp else 15.sp, FontWeight.Medium, -.02f),
                        color = c.ink,
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (pick != null) Text(percentLabel(pick.fraction), fontSize = 11.sp, color = c.sub)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                shares.forEachIndexed { i, s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .tap(label = s.label, pressedScale = .97f) {
                                haptics.tick()
                                onSelect(if (selected == i) -1 else i)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val dim = selected >= 0 && selected != i
                        Box(Modifier.size(8.dp).clip(CircleShape).background(colorOf(s).copy(alpha = if (dim) .35f else 1f)))
                        Spacer(Modifier.size(7.dp))
                        Text(s.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (dim) c.sub else c.ink, modifier = Modifier.weight(1f))
                        Text(percentLabel(s.fraction), style = numberStyle(12.sp, FontWeight.Normal, 0f), color = c.sub)
                    }
                }
            }
        }
    }
}

/**
 * Reads touches on the donut ([cents] are the slices' amounts, in the order PieChart draws them:
 * from 3 o'clock, clockwise, each as wide as its share) and reports the slice picked through
 * [onSelect], -1 for none.
 *  - A tap picks the slice under the finger, or lets it go when it is the picked one or the touch
 *    is on the centre.
 *  - A press that stays put for [ScrubHoldMs] starts scrubbing: the slice at the finger's angle is
 *    picked as it slides round (the radius no longer matters), ticking each time it changes, and
 *    letting go keeps it. Every move is consumed from then on, so the page does not scroll.
 *  - A touch that moves before that is a scroll and is left alone, and so is one that starts well
 *    off the ring: vertical flicks over the donut still scroll the page.
 */
@Composable
private fun Modifier.donutPicker(cents: List<Long>, selected: Int, onSelect: (Int) -> Unit): Modifier {
    val haptics = rememberHaptics()
    val shown by rememberUpdatedState(selected)
    val report by rememberUpdatedState(onSelect)
    return pointerInput(cents) {
        val whole = cents.sum().toDouble()
        val slop = viewConfiguration.touchSlop
        val ring = RingWidth.toPx()
        val reach = RingReach.toPx()
        awaitEachGesture {
            // PieChart's own tap handling already took this down event, hence not requireUnconsumed.
            val down = awaitFirstDown(requireUnconsumed = false)
            val half = min(size.width, size.height) / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f
            // Inside `hole` is the donut's centre; past `rim` the finger is off the donut.
            val hole = half - ring - reach
            val rim = half + reach
            fun radius(p: Offset) = hypot(p.x - cx, p.y - cy)
            fun sliceAt(p: Offset): Int {
                var deg = atan2((p.y - cy).toDouble(), (p.x - cx).toDouble()) * 180.0 / PI
                if (deg < 0) deg += 360.0
                var end = 0.0
                cents.forEachIndexed { i, v ->
                    end += v * 360.0 / whole
                    if (deg < end) return i
                }
                return cents.lastIndex
            }
            if (whole <= 0.0 || radius(down.position) > rim) return@awaitEachGesture
            val onHub = radius(down.position) < hole
            val first = if (onHub) -1 else sliceAt(down.position)
            // Tracked here: the state above only catches up on the next recomposition.
            var now = shown
            fun pick(i: Int) {
                if (i == now) return
                now = i
                haptics.tick()
                report(i)
            }
            val press = withTimeoutOrNull(ScrubHoldMs) {
                var out: Press? = null
                while (out == null) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                    out = when {
                        change == null -> Press.Abandon
                        !change.pressed -> Press.Tap
                        (change.position - down.position).getDistance() > slop -> Press.Abandon
                        else -> null
                    }
                }
                out
            } ?: Press.Hold
            when (press) {
                Press.Abandon -> return@awaitEachGesture
                Press.Tap -> {
                    pick(if (onHub || first == now) -1 else first)
                    return@awaitEachGesture
                }
                Press.Hold -> Unit
            }
            val before = now
            if (!onHub) pick(first)
            var moved = false
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                if (!change.pressed) break
                if ((change.position - down.position).getDistance() > slop) moved = true
                // Through the centre the angle means nothing, so the pick stays where it was.
                if (radius(change.position) >= hole) pick(sliceAt(change.position))
            }
            // Held and let go on the spot: a slow tap, so it lets go of a picked slice like a tap does.
            if (!moved && (onHub || first == before)) pick(-1)
        }
    }
}

/** What the ranking lists: [rows] in order, and whether they are one category's only. */
private data class RankView(val rows: List<Record>, val narrowed: Boolean)

/**
 * Ranking of the period picked above: its ten largest single expenses, largest first. With a donut
 * slice picked ([share]) it lists only that slice's expenses, headed "排行榜 · 餐饮" with a chip that
 * clears the pick. Rows open the record.
 */
@Composable
private fun RankingCard(
    trend: Trend,
    share: CategoryShare?,
    onClear: () -> Unit,
    today: LocalDate,
    onOpenRecord: (Record) -> Unit,
    modifier: Modifier,
) {
    val c = LedgerTheme.colors
    val haptics = rememberHaptics()
    val view = remember(trend, share) { RankView(trend.topExpenses(10, share), share != null) }
    Column(
        modifier.fillMaxWidth().glass(28.dp).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The chip is a 48dp target, so the header grows a little while it shows; that growth springs.
        Row(Modifier.fillMaxWidth().animateContentSize(Motion.size), verticalAlignment = Alignment.CenterVertically) {
            Glyph(LedgerIcons.get("trophy"), 18.dp, c.ink)
            Spacer(Modifier.width(6.dp))
            Text(
                if (share == null) "排行榜" else "排行榜 · ${share.label}",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (share == null) Modifier else Modifier.weight(1f),
            )
            if (share == null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "${trend.period.label(today)} · 单笔前 10",
                    fontSize = 12.sp,
                    color = c.sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
            AnimatedVisibility(
                visible = share != null,
                enter = fadeIn(Motion.fade()) + scaleIn(Motion.layout(), initialScale = .8f),
                exit = fadeOut(Motion.fade()) + scaleOut(Motion.layout(), targetScale = .8f),
            ) {
                ClearChip {
                    haptics.tick()
                    onClear()
                }
            }
        }
        AnimatedContent(
            targetState = view,
            transitionSpec = {
                (fadeIn(Motion.fade()) togetherWith fadeOut(Motion.fade()))
                    .using(SizeTransform(clip = false) { _, _ -> Motion.size })
            },
            label = "ranking",
        ) { v ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (v.rows.isEmpty()) RankEmpty()
                val max = v.rows.firstOrNull()?.amountCents ?: 0L
                v.rows.forEachIndexed { i, r ->
                    RankRow(
                        rank = i + 1,
                        title = r.title,
                        // The category is the list's own when narrowed to one: no need to repeat it.
                        meta = if (v.narrowed) chipDate(r.date, today) else "${chipDate(r.date, today)} · ${r.category.label}",
                        cents = r.amountCents,
                        fraction = if (max > 0) r.amountCents.toFloat() / max else 0f,
                        tile = r.category,
                        modifier = Modifier.tap(label = "查看 ${r.title}", pressedScale = .98f) { onOpenRecord(r) },
                    )
                }
            }
        }
    }
}

/** "× 全部": back to the whole period's ranking. A 30dp pill inside a 48dp touch target. */
@Composable
private fun ClearChip(onClick: () -> Unit) {
    val c = LedgerTheme.colors
    Box(
        Modifier.heightIn(min = 48.dp).tap(label = "显示全部分类", pressedScale = .94f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.height(30.dp).chip(15.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Glyph(LedgerIcons.get("close", 2.4f), 12.dp, c.ink)
            Text("全部", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.ink, maxLines = 1)
        }
    }
}

@Composable
private fun RankEmpty() {
    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
        Text("没有支出", fontSize = 13.sp, color = LedgerTheme.colors.sub)
    }
}

@Composable
private fun RankRow(
    rank: Int,
    title: String,
    meta: String,
    cents: Long,
    fraction: Float,
    modifier: Modifier = Modifier,
    tile: Category? = null,
) {
    val c = LedgerTheme.colors
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        RankBadge(rank)
        Spacer(Modifier.width(10.dp))
        if (tile != null) {
            CategoryTile(tile, 34.dp, 12.dp, iconSize = 17.dp)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text("¥${formatCents(cents)}", style = numberStyle(14.sp, FontWeight.Medium, 0f), color = c.ink)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RankBar(fraction, Modifier.weight(1f))
                if (meta.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(meta, style = numberStyle(11.sp, FontWeight.Normal, 0f), color = c.sub, maxLines = 1)
                }
            }
        }
    }
}

/** 1–3 sit on a gold/rim gradient (ring1 → ring2), fading by place; the rest are plain. */
@Composable
private fun RankBadge(rank: Int) {
    val c = LedgerTheme.colors
    val strength = when (rank) {
        1 -> .75f
        2 -> .5f
        3 -> .32f
        else -> 0f
    }
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .then(
                if (strength > 0f) {
                    Modifier.background(Brush.linearGradient(listOf(c.ring1.copy(alpha = strength), c.ring2.copy(alpha = strength * .7f))))
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$rank",
            style = numberStyle(13.sp, if (strength > 0f) FontWeight.SemiBold else FontWeight.Normal, 0f),
            color = if (strength > 0f) c.ink else c.sub,
        )
    }
}

/** Thin proportional bar; grows in with a spring. */
@Composable
private fun RankBar(fraction: Float, modifier: Modifier) {
    val c = LedgerTheme.colors
    val p = remember { Animatable(0f) }
    LaunchedEffect(fraction) { p.animateTo(fraction.coerceIn(0f, 1f), Motion.layout()) }
    Box(modifier.height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.track)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(p.value.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.horizontalGradient(listOf(c.ring1, c.ring2))),
        )
    }
}
