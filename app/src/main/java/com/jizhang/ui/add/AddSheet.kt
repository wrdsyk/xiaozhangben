package com.jizhang.ui.add

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
// Category grid: edit mode, drag to reorder.
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import com.jizhang.ui.components.CategoryTile
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.domain.AmountInput
import com.jizhang.LedgerViewModel
import com.jizhang.domain.Category
import com.jizhang.domain.CategoryCatalog
import com.jizhang.domain.EntryKind
import com.jizhang.domain.Key
import com.jizhang.domain.Record
import com.jizhang.domain.formatCents
import com.jizhang.ui.chipDate
import com.jizhang.ui.components.Glyph
import com.jizhang.ui.components.LocalFormSheet
import com.jizhang.ui.forms.CategoryForm
import com.jizhang.ui.forms.DateForm
import com.jizhang.ui.forms.NoteForm
import com.jizhang.ui.components.TypedAmount
import com.jizhang.ui.components.PhotoThumb
import androidx.compose.ui.text.style.TextOverflow
import com.jizhang.ui.components.chip
import com.jizhang.ui.components.glass
import com.jizhang.ui.components.tap
import com.jizhang.ui.theme.LedgerIcons
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.Motion
import com.jizhang.ui.theme.numberStyle
import com.jizhang.ui.theme.rememberHaptics
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** What the add sheet is opened for: a new entry, or editing [editing]. [origin] is where it grows from. */
data class SheetRequest(val id: Long, val editing: Record?, val origin: Offset)

class SaveRequest(
    val id: Long?,
    val category: Category,
    val cents: Long,
    val note: String,
    val at: LocalDateTime,
    val photos: List<String> = emptyList(),
)

/**
 * Full-screen add / edit sheet that grows out of the + button as a circle. Keypad taps,
 * category switches and the expense / income toggle all tick.
 */
@Composable
fun AddSheet(
    request: SheetRequest?,
    today: LocalDate,
    catalog: CategoryCatalog,
    vm: LedgerViewModel,
    onClose: () -> Unit,
    onSave: (SaveRequest, again: Boolean) -> Unit,
) {
    val reveal = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(request) }
    LaunchedEffect(request) {
        if (request != null) {
            shown = request
            reveal.animateTo(1f, Motion.layout())
        } else {
            reveal.animateTo(0f, Motion.layout())
            shown = null
        }
    }
    val current = shown ?: return
    // A form opened from inside the sheet (note, date, category) handles back itself.
    val forms = LocalFormSheet.current
    BackHandler(enabled = request != null && !forms.isOpen, onBack = onClose)

    val c = LedgerTheme.colors
    val haze = com.jizhang.ui.components.LocalHazeState.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val o = current.origin.takeIf { it != Offset.Zero } ?: Offset(w - 60f, h - 120f)
        val maxRadius = max(max(hypot(o.x, o.y), hypot(w - o.x, o.y)), max(hypot(o.x, h - o.y), hypot(w - o.x, h - o.y)))
        val circle = remember(o, maxRadius) {
            { p: Float ->
                GenericShape { _, _ -> addOval(Rect(o, maxRadius * p.coerceAtLeast(0f) + 1f)) }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    shape = circle(reveal.value)
                }
                .then(if (haze != null) Modifier.hazeBlur(HazeInput.Backdrop(haze), HazeBlurStyle { blurRadius(26.dp) }) else Modifier)
                .background(c.sheet),
        ) {
            key(current.id) {
                SheetContent(current.editing, today, catalog, vm, onClose, onSave)
            }
        }
    }
}

@Composable
private fun SheetContent(
    editing: Record?,
    today: LocalDate,
    catalog: CategoryCatalog,
    vm: LedgerViewModel,
    onClose: () -> Unit,
    onSave: (SaveRequest, again: Boolean) -> Unit,
) {
    val c = LedgerTheme.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(editing?.kind ?: EntryKind.Expense) }
    var category by remember { mutableStateOf(editing?.category ?: Category.Food) }
    var input by remember { mutableStateOf(editing?.let { AmountInput.fromCents(it.amountCents) } ?: AmountInput()) }
    var date by remember { mutableStateOf(editing?.date ?: today) }
    val time = remember { editing?.occurredAt?.toLocalTime() }
    var note by remember { mutableStateOf(editing?.note ?: "") }
    var photos by remember { mutableStateOf(editing?.images ?: emptyList()) }
    val form = LocalFormSheet.current
    val shake = remember { Animatable(0f) }
    val options = catalog.forKind(kind)
    // An edited custom category comes back as a new instance; a deleted one falls back to the first.
    LaunchedEffect(options) {
        category = options.firstOrNull { it.key == category.key } ?: options.first()
    }

    /** Long-press on the grid: tiles wiggle, can be dragged, and a tap edits them. */
    var arranging by remember { mutableStateOf(false) }
    // Declared after AddSheet's own handler, so back ends edit mode before it closes the sheet.
    BackHandler(enabled = arranging && !form.isOpen) { arranging = false }

    fun editCategory(existing: Category?) {
        val k = existing?.kind ?: kind
        // Built-ins can't be deleted; once edited they can go back to how they shipped.
        val original = existing?.takeIf { !it.custom }?.let { Category.of(it.key) }
        form.open { s ->
            CategoryForm(
                existing = existing,
                kind = k,
                taken = catalog.forKind(k).filter { it.key != existing?.key }.map { it.label }.toSet(),
                onSave = { name, icon, hue, color -> vm.saveCategory(existing, name, k, icon, hue, color) },
                onDelete = existing?.takeIf { it.custom }?.let { cat -> { vm.deleteCategory(cat) } },
                onReset = existing?.takeIf { original != null && it != original }?.let { cat -> { vm.resetCategory(cat) } },
                close = s::close,
            )
        }
    }

    fun reject() {
        haptics.reject()
        scope.launch {
            shake.snapTo(0f)
            shake.animateTo(0f, Motion.layout(), initialVelocity = 420f)
        }
    }

    fun submit(again: Boolean) {
        val cents = input.totalCents()
        if (cents <= 0) return reject()
        val at = date.atTime(time ?: LocalTime.now().withSecond(0).withNano(0))
        onSave(SaveRequest(editing?.id, category, cents, note, at, photos), again)
        if (again) {
            input = AmountInput()
            note = ""
            photos = emptyList()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 14.dp),
    ) {
        // Header + grid take what the amount card and keypad leave; the grid scrolls inside it.
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).glass(24.dp).tap(label = "关闭", onClick = onClose), contentAlignment = Alignment.Center) {
                    Glyph(LedgerIcons.get("close"), 20.dp, c.ink)
                }
                Spacer(Modifier.weight(1f))
                KindToggle(kind) {
                    if (it != kind) {
                        haptics.tick()
                        arranging = false
                        kind = it
                        category = catalog.forKind(it).first()
                    }
                }
                Spacer(Modifier.weight(1f))
                GridDone(visible = arranging) { arranging = false }
            }

            key(kind) {
                CategoryGrid(
                    categories = options,
                    selected = category,
                    editing = arranging,
                    onStartEditing = { arranging = true },
                    onPick = {
                        if (it.key != category.key) haptics.tick()
                        category = it
                    },
                    onEdit = { editCategory(it) },
                    onReorder = vm::reorderCategories,
                    onAdd = { editCategory(null) },
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }

        Column(
            Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                .fillMaxWidth()
                .glass(28.dp)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 62.dp).offset { IntOffset(shake.value.dp.roundToPx(), 0) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryChip(category)
                Spacer(Modifier.width(10.dp).weight(1f))
                AmountReadout(input)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.height(44.dp).chip(16.dp).tap(label = "选择日期") { form.open { s -> DateForm(date, today, { date = it }, s::close) } }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Glyph(LedgerIcons.get("cal"), 16.dp, c.ink)
                    Text(chipDate(date, today), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.ink)
                }
                Row(
                    Modifier.weight(1f).height(44.dp).chip(16.dp).tap(label = "备注") {
                        val cat = category
                        form.open { s ->
                            NoteForm(
                                initial = note,
                                chips = remember(cat.key) { vm.noteChips(cat) },
                                onAddChip = { vm.addNoteChip(cat, it) },
                                onRemoveChip = { vm.removeNoteChip(cat, it) },
                                photos = photos,
                                newCaptureUri = vm::newCaptureUri,
                                importPhoto = vm::importPhoto,
                                onSave = { text, shots ->
                                    note = text
                                    photos = shots
                                },
                                close = s::close,
                            )
                        }
                    }.padding(start = 14.dp, end = if (photos.isEmpty()) 14.dp else 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Glyph(LedgerIcons.get("note"), 16.dp, if (note.isBlank()) c.sub else c.ink)
                    Text(
                        note.ifBlank { "添加备注" }, fontSize = 13.sp, color = if (note.isBlank()) c.sub else c.ink, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    // Attached photos: the first one as a tiny thumbnail, plus the count when more.
                    if (photos.isNotEmpty()) {
                        if (photos.size > 1) Text("${photos.size}张", style = numberStyle(12.sp, FontWeight.Medium, 0f), color = c.sub)
                        PhotoThumb(photos.first(), Modifier.size(28.dp), corner = 8.dp)
                    }
                }
            }
        }

        Keypad(
            onKey = { k ->
                haptics.tick()
                val next = input.press(k)
                if (next == null) reject() else input = next
            },
            onAgain = { haptics.tick(); if (editing != null) onClose() else submit(again = true) },
            onDone = { haptics.tick(); submit(again = false) },
            editing = editing != null,
        )
    }

}

/** 支出 / 收入 toggle; the white droplet stretches as it slides, like the prototype's dropR/dropL. */
@Composable
private fun KindToggle(kind: EntryKind, onChange: (EntryKind) -> Unit) {
    val c = LedgerTheme.colors
    val x = remember { Animatable(if (kind == EntryKind.Expense) 0f else 1f) }
    LaunchedEffect(kind) { x.animateTo(if (kind == EntryKind.Expense) 0f else 1f, Motion.layout()) }
    Box(Modifier.width(168.dp).height(44.dp).glass(22.dp).padding(4.dp)) {
        Box(
            Modifier
                .offset { IntOffset((x.value * 80.dp.toPx()).toInt(), 0) }
                .graphicsLayer {
                    val stretch = (abs(x.velocity) / 36f).coerceAtMost(.32f)
                    scaleX = 1f + stretch
                    scaleY = 1f - stretch * .3f
                }
                .width(80.dp)
                .height(36.dp)
                .dropShadow(RoundedCornerShape(18.dp), Shadow(radius = 16.dp, color = c.shadow, spread = (-6).dp, offset = DpOffset(0.dp, 6.dp)))
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = .92f)),
        )
        Row(Modifier.fillMaxSize()) {
            listOf(EntryKind.Expense to "支出", EntryKind.Income to "收入").forEach { (k, label) ->
                Box(
                    Modifier.weight(1f).fillMaxSize().tap(label = label, pressedScale = 1f) { onChange(k) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (k == kind) c.segOn else c.segOff)
                }
            }
        }
    }
}

/** Five columns; a cell is the tile plus its label, rows [GridGap] apart. */
private const val GRID_COLUMNS = 5
private val CellHeight = 80.dp
private val GridGap = 16.dp
private val GridPadTop = 8.dp

/** In edit mode a short hold picks a tile up, while a quick swipe still scrolls the grid. */
private const val EDIT_HOLD_MS = 150L

/**
 * Drag-to-reorder state of [CategoryGrid]. [order] is what the grid shows: it follows the
 * catalog, and leads it while a drop is being saved. Positions are in the grid's own pixels.
 */
@Stable
private class GridDrag(keys: List<String>) {
    var order by mutableStateOf(keys)

    /** The tile under the finger. */
    var key by mutableStateOf<String?>(null)

    /** Just dropped: still drawn at [pos] until its own spring takes over from there. */
    var landing by mutableStateOf<String?>(null)
    var pos by mutableStateOf(Offset.Zero)
    var pressed by mutableStateOf<String?>(null)

    /** Where the finger holds the tile, for auto-scroll. */
    var grab = Offset.Zero

    /**
     * Where the dragged tile was last placed. Its pointer handler moves with it, so a finger delta
     * read in tile space is always ~zero; the drag works out the finger in grid space from this.
     */
    var laid = Offset.Zero
    var cellW = 1f
    var cellH = 1f
    var pitch = 1f

    fun slot(i: Int) = Offset(i % GRID_COLUMNS * cellW, i / GRID_COLUMNS * pitch)

    /** Moves the dragged key to the slot under the tile's centre; true when that slot changed. */
    fun retarget(): Boolean {
        val k = key ?: return false
        val from = order.indexOf(k)
        if (from < 0) return false
        val center = pos + Offset(cellW / 2, cellH / 2)
        val col = (center.x / cellW).toInt().coerceIn(0, GRID_COLUMNS - 1)
        val row = (center.y / pitch).toInt().coerceAtLeast(0)
        val to = (row * GRID_COLUMNS + col).coerceIn(0, order.size - 1)
        if (to == from) return false
        order = order.toMutableList().apply {
            removeAt(from)
            add(to, k)
        }
        return true
    }
}

/**
 * Five tiles per row, then a dashed "添加" tile that always stays last. Long-pressing any tile
 * enters edit mode: tiles wiggle and wear a pencil badge, a tap edits instead of picking, and
 * the press that entered carries straight on into a drag (later, a short hold is enough).
 * Other tiles spring aside as the dragged one crosses slots, and the drop saves the order.
 * Scrolls once taller than [modifier] allows (capped near three and a half rows); a tile
 * dragged near either edge scrolls it.
 */
@Composable
private fun CategoryGrid(
    categories: List<Category>,
    selected: Category,
    editing: Boolean,
    onStartEditing: () -> Unit,
    onPick: (Category) -> Unit,
    onEdit: (Category) -> Unit,
    /** Every key of this kind, in the new order. */
    onReorder: (List<String>) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LedgerTheme.colors
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val keys = categories.map { it.key }
    val grid = remember { GridDrag(keys) }
    // Follows the catalog (a new, edited or deleted category, or a saved drop) unless mid-drag.
    LaunchedEffect(keys) { if (grid.key == null) grid.order = keys }
    // Until that lands, a different set of keys means the catalog is ahead: show it as it is.
    val order = if (grid.order.size == keys.size && grid.order.toSet() == keys.toSet()) grid.order else keys
    var viewport by remember { mutableIntStateOf(0) }

    // The pointer handlers below outlive recompositions; they read these.
    val isEditing by rememberUpdatedState(editing)
    val latest by rememberUpdatedState(categories.associateBy { it.key })
    val pick by rememberUpdatedState(onPick)
    val edit by rememberUpdatedState(onEdit)
    val startEditing by rememberUpdatedState(onStartEditing)
    val reorder by rememberUpdatedState(onReorder)

    fun tap(key: String) {
        val cat = latest[key] ?: return
        if (isEditing) edit(cat) else pick(cat)
    }

    fun lift(key: String, at: Offset) {
        haptics.tick()
        if (!isEditing) startEditing()
        grid.pos = grid.slot(grid.order.indexOf(key).coerceAtLeast(0))
        grid.laid = grid.pos
        grid.grab = at
        grid.key = key
    }

    fun move(delta: Offset) {
        grid.pos += delta
        if (grid.retarget()) haptics.tick()
    }

    fun drop() {
        val k = grid.key ?: return
        grid.landing = k
        grid.key = null
        if (grid.order != latest.keys.toList()) reorder(grid.order)
    }

    // Auto-scroll while the finger is near the top or bottom edge; the tile stays under it.
    val dragging = grid.key != null
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        val edge = with(density) { 44.dp.toPx() }
        val top = with(density) { GridPadTop.toPx() }
        val fastest = with(density) { 9.dp.toPx() }
        while (grid.key != null) {
            withFrameNanos { }
            val y = top + grid.pos.y + grid.grab.y - scroll.value
            val step = when {
                y < edge -> -fastest * ((edge - y) / edge).coerceAtMost(1f)
                y > viewport - edge -> fastest * ((y - viewport + edge) / edge).coerceAtMost(1f)
                else -> 0f
            }
            if (step != 0f) {
                val used = scroll.scrollBy(step)
                if (used != 0f) move(Offset(0f, used))
            }
        }
    }

    val amp = animateFloatAsState(if (editing) 1f else 0f, Motion.layout(), label = "wiggleAmp")
    // Endless, so a tween (springs can't repeat); only running while edit mode is on or easing off.
    val wiggle = if (editing || amp.value > .01f) {
        rememberInfiniteTransition("wiggle").animateFloat(
            initialValue = -1f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(240, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "wiggle",
        )
    } else {
        null
    }
    val badge = animateFloatAsState(if (editing) 1f else 0f, Motion.layout(), label = "badge")
    val hint by animateFloatAsState(if (editing) 1f else 0f, Motion.fade(), label = "hint")

    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.BottomCenter) {
            Text(
                "按住拖动排序，点按修改",
                fontSize = 11.sp, color = c.sub, maxLines = 1,
                modifier = Modifier.graphicsLayer { alpha = hint },
            )
        }
        Box(
            Modifier
                .heightIn(max = 330.dp)
                .onSizeChanged { viewport = it.height }
                .verticalScroll(scroll)
                .padding(start = 12.dp, end = 12.dp, top = GridPadTop, bottom = 8.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cellW = constraints.maxWidth / GRID_COLUMNS.toFloat()
                with(density) {
                    grid.cellW = cellW
                    grid.cellH = CellHeight.toPx()
                    grid.pitch = (CellHeight + GridGap).toPx()
                }
                val width = with(density) { cellW.toDp() }
                val rows = (order.size + GRID_COLUMNS) / GRID_COLUMNS
                Box(Modifier.fillMaxWidth().height(CellHeight * rows + GridGap * (rows - 1))) {
                    categories.forEachIndexed { first, cat ->
                        key(cat.key) {
                            GridTile(
                                cat = cat,
                                slot = order.indexOf(cat.key).coerceAtLeast(0),
                                first = first,
                                selected = cat.key == selected.key,
                                grid = grid,
                                width = width,
                                wiggle = wiggle,
                                amp = amp,
                                badge = badge,
                                gesture = Modifier
                                    .pointerInput(cat.key) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown()
                                            if (grid.key != null) return@awaitEachGesture
                                            grid.pressed = cat.key
                                            var tapped = false
                                            val editingAtDown = isEditing
                                            val hold = if (editingAtDown) EDIT_HOLD_MS else viewConfiguration.longPressTimeoutMillis
                                            // Lifted (a tap) or taken by the scroll before the hold: no drag.
                                            val ended = withTimeoutOrNull(hold) {
                                                tapped = waitForUpOrCancellation() != null
                                            }
                                            // In edit mode the short hold only claims the finger from the scroll:
                                            // a move past touch slop (or a full long press) picks the tile up, and
                                            // letting go before that is still a tap, so a slow tap edits.
                                            var travel = Offset.Zero
                                            if (ended == null && editingAtDown) {
                                                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis - EDIT_HOLD_MS) {
                                                    while (travel.getDistance() <= viewConfiguration.touchSlop) {
                                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                                        if (!change.pressed) {
                                                            tapped = true
                                                            break
                                                        }
                                                        travel += change.positionChange()
                                                        change.consume()
                                                    }
                                                }
                                            }
                                            grid.pressed = null
                                            if (ended != null || tapped) {
                                                if (tapped) tap(cat.key)
                                                return@awaitEachGesture
                                            }
                                            lift(cat.key, down.position)
                                            if (travel != Offset.Zero) move(travel)
                                            try {
                                                drag(down.id) {
                                                    it.consume()
                                                    move(grid.laid + it.position - grid.grab - grid.pos)
                                                }
                                            } finally {
                                                drop()
                                            }
                                        }
                                    }
                                    .semantics(mergeDescendants = true) {
                                        role = Role.Button
                                        onClick(label = if (editing) "修改分类" else cat.label) {
                                            tap(cat.key)
                                            true
                                        }
                                    },
                            )
                        }
                    }
                    val addTarget = grid.slot(order.size)
                    val addPos = remember { Animatable(addTarget, Offset.VectorConverter) }
                    LaunchedEffect(addTarget) { addPos.animateTo(addTarget, Motion.layout()) }
                    AddTile(
                        Modifier
                            .offset { addPos.value.round() }
                            .size(width, CellHeight)
                            .popIn(rememberPop(categories.size))
                            .tap(label = "添加分类", onClick = onAdd),
                    )
                }
            }
        }
    }
}

/** One category cell: springs to its [slot], or follows the finger while it is the dragged one. */
@Composable
private fun GridTile(
    cat: Category,
    slot: Int,
    /** Position when the grid appeared: entrance stagger and wiggle direction. */
    first: Int,
    selected: Boolean,
    grid: GridDrag,
    width: Dp,
    wiggle: State<Float>?,
    amp: State<Float>,
    badge: State<Float>,
    gesture: Modifier,
) {
    val c = LedgerTheme.colors
    val k = cat.key
    val target = grid.slot(slot)
    val pos = remember { Animatable(target, Offset.VectorConverter) }
    val dragged = grid.key == k
    val held = dragged || grid.landing == k
    LaunchedEffect(target, dragged) {
        if (dragged) return@LaunchedEffect
        if (grid.landing == k) {
            pos.snapTo(grid.pos)
            grid.landing = null
        }
        pos.animateTo(target, Motion.layout())
    }
    val lift by animateFloatAsState(if (dragged) 1f else 0f, Motion.layout(), label = "lift")
    val press by animateFloatAsState(if (grid.pressed == k) 1f else 0f, Motion.layout(), label = "press")
    val grow by animateFloatAsState(if (selected) 1.08f else 1f, Motion.layout(), label = "tile")
    // Fixed per tile: [first] shifts after a saved drop, and a flipped phase would jolt the wiggle.
    val phase = remember { if (first % 2 == 0) 1f else -1f }
    Box(
        Modifier
            .offset {
                if (held) grid.pos.round().also { grid.laid = Offset(it.x.toFloat(), it.y.toFloat()) } else pos.value.round()
            }
            .zIndex(if (held) 1f else 0f)
            .size(width, CellHeight)
            // Before the transforms below, so drag deltas stay in screen pixels.
            .then(gesture)
            .popIn(rememberPop(first))
            .graphicsLayer {
                val s = (1f - .07f * press) * (1f + .14f * lift)
                scaleX = s
                scaleY = s
                rotationZ = (wiggle?.value ?: 0f) * amp.value * (1f - lift) * phase * 1.6f
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                CategoryTile(
                    cat, 54.dp, 19.dp, iconSize = 25.dp,
                    background = if (selected) c.hue(cat) else c.tint(cat),
                    glyph = if (selected) c.onTile else c.hue(cat),
                    modifier = Modifier.graphicsLayer {
                        scaleX = grow
                        scaleY = grow
                    },
                )
                EditBadge(badge, Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp))
            }
            Text(cat.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.label, maxLines = 1)
        }
    }
}

/** The pencil on every tile in edit mode; the whole tile is its touch target. */
@Composable
private fun EditBadge(shown: State<Float>, modifier: Modifier) {
    val c = LedgerTheme.colors
    Box(
        modifier
            .size(20.dp)
            .graphicsLayer {
                val s = shown.value.coerceAtLeast(0f)
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .background(c.accent)
            .border(1.5.dp, c.bg0, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Glyph(LedgerIcons.get("edit"), 11.dp, c.onAccent) }
}

/** Ends the category grid's edit mode; sits in the header opposite the close button. */
@Composable
private fun GridDone(visible: Boolean, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible,
            enter = scaleIn(Motion.layout(), initialScale = .6f) + fadeIn(Motion.fade()),
            exit = scaleOut(Motion.layout(), targetScale = .6f) + fadeOut(Motion.fade()),
        ) {
            Box(Modifier.size(48.dp).glass(24.dp).tap(label = "完成编辑", onClick = onClick), contentAlignment = Alignment.Center) {
                Text("完成", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
            }
        }
    }
}

/** Grid entrance: each cell pops in after a stagger by its [index]. */
@Composable
private fun rememberPop(index: Int): Animatable<Float, AnimationVector1D> {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(60L + 30L * index)
        pop.animateTo(1f, Motion.layout())
    }
    return pop
}

private fun Modifier.popIn(pop: Animatable<Float, AnimationVector1D>) = graphicsLayer {
    alpha = pop.value.coerceIn(0f, 1f)
    val s = .6f + .4f * pop.value
    scaleX = s
    scaleY = s
    translationY = (1f - pop.value) * 14.dp.toPx()
}

/** Dashed outline tile that opens the new-category form. */
@Composable
private fun AddTile(modifier: Modifier) {
    val c = LedgerTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(54.dp)
                .drawBehind {
                    val w = 1.4.dp.toPx()
                    drawRoundRect(
                        color = c.sub.copy(alpha = .55f),
                        topLeft = Offset(w / 2, w / 2),
                        size = Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(19.dp.toPx() - w / 2),
                        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                    )
                },
            contentAlignment = Alignment.Center,
        ) { Glyph(LedgerIcons.get("plus"), 22.dp, c.sub) }
        Text("添加", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.sub)
    }
}

@Composable
private fun CategoryChip(category: Category) {
    val c = LedgerTheme.colors
    Row(
        Modifier.height(34.dp).chip(17.dp, strong = true).padding(start = 5.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(9.dp)).background(c.hue(category)),
            contentAlignment = Alignment.Center,
        ) { Glyph(LedgerIcons.of(category, 2.2f), 14.dp, c.onTile) }
        Text(category.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.ink)
    }
}

@Composable
private fun AmountReadout(input: AmountInput) {
    val c = LedgerTheme.colors
    val len = input.text.length
    val size by animateFloatAsState(
        when {
            len <= 8 -> 48f
            len <= 12 -> 36f
            else -> 28f
        },
        Motion.layout(), label = "amountSize",
    )
    val caret by rememberInfiniteTransition("caret").animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(keyframes { durationMillis = 1100; 1f at 549 using LinearEasing; 0f at 550 }, RepeatMode.Restart),
        label = "caret",
    )
    Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("¥", style = numberStyle(22.sp, FontWeight.Normal, 0f), color = c.sub, modifier = Modifier.padding(end = 4.dp))
            if (input.isEmpty) {
                Text("0", style = numberStyle(48.sp, FontWeight.Light, -.02f), color = c.placeholder)
            } else {
                TypedAmount(input.text, numberStyle(size.sp, FontWeight.Light, -.02f), c.ink)
            }
            Box(
                Modifier
                    .padding(start = 4.dp)
                    .width(2.dp)
                    .height(36.dp)
                    .graphicsLayer { alpha = caret }
                    .clip(RoundedCornerShape(1.dp))
                    .background(c.accent),
            )
        }
        if (input.hasOperator) {
            Text(
                "= ${formatCents(input.totalCents())}",
                style = numberStyle(13.sp, FontWeight.Normal, 0f),
                color = c.sub,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Keypad(onKey: (Key) -> Unit, onAgain: () -> Unit, onDone: () -> Unit, editing: Boolean) {
    val c = LedgerTheme.colors
    val rows = listOf(
        listOf<Any>("1", "2", "3", Key.Back),
        listOf("4", "5", "6", Key.Plus),
        listOf("7", "8", "9", Key.Minus),
        listOf(".", "0", "again", "done"),
    )
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    val mod = Modifier.weight(1f).height(56.dp)
                    when (k) {
                        Key.Back -> KeyButton(mod, "删除", fill = c.opKey, onClick = { onKey(Key.Back) }) {
                            Glyph(LedgerIcons.get("backspace"), 24.dp, c.ink)
                        }
                        Key.Plus -> KeyButton(mod, "加", fill = c.opKey, onClick = { onKey(Key.Plus) }) { KeyText("+") }
                        Key.Minus -> KeyButton(mod, "减", fill = c.opKey, onClick = { onKey(Key.Minus) }) { KeyText("−") }
                        "again" -> KeyButton(mod, if (editing) "取消" else "再记一笔", fill = c.opKey, onClick = onAgain) {
                            Text(if (editing) "取消" else "再记", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
                        }
                        "done" -> KeyButton(
                            mod, "完成",
                            fill = c.accent,
                            gradient = Brush.verticalGradient(0f to lerp(c.accent, Color.White, c.sheen.alpha), .55f to c.accent),
                            onClick = onDone,
                        ) { Text(if (editing) "保存" else "完成", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.onAccent) }
                        "." -> KeyButton(mod, "小数点", onClick = { onKey(Key.Dot) }) { KeyText(".") }
                        else -> KeyButton(mod, k as String, onClick = { onKey(Key.Digit(k.toInt())) }) { KeyText(k) }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyText(s: String) {
    Text(s, style = numberStyle(23.sp, FontWeight.Normal, 0f), color = LedgerTheme.colors.ink, textAlign = TextAlign.Center)
}

@Composable
private fun KeyButton(
    modifier: Modifier,
    label: String,
    fill: Color? = null,
    gradient: Brush? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .glass(18.dp, shadow = gradient != null, fill = gradient ?: fill?.let { Brush.linearGradient(listOf(it, it)) }, backdrop = false)
            .tap(label = label, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
