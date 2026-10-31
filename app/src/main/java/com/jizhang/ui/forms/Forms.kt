package com.jizhang.ui.forms

// Note form photos.
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.jizhang.data.ImageStore
import com.jizhang.ui.components.PhotoStrip
import com.jizhang.ui.theme.Motion
import kotlinx.coroutines.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.LedgerViewModel
import com.jizhang.domain.Budget
import com.jizhang.domain.BudgetPeriod
import com.jizhang.domain.Category
import com.jizhang.domain.EntryKind
import com.jizhang.domain.Hue
import com.jizhang.domain.NoteChip
import com.jizhang.domain.formatCentsShort
import com.jizhang.ui.components.ButtonKind
import com.jizhang.ui.components.ChoiceChips
import com.jizhang.ui.components.DateWheel
import com.jizhang.ui.components.FormActions
import com.jizhang.ui.components.FormButton
import com.jizhang.ui.components.FormField
import com.jizhang.ui.components.FormLabel
import com.jizhang.ui.components.FormTitle
import com.jizhang.ui.components.Glyph
import com.jizhang.ui.components.NoteChips
import com.jizhang.ui.components.ChipInput
import com.jizhang.ui.components.NoteChipsState
import com.jizhang.ui.components.Segmented
import com.jizhang.ui.components.endsNoteEditing
import com.jizhang.ui.components.rememberNoteChipsState
import com.jizhang.ui.components.tap
import com.jizhang.ui.theme.LedgerIcons
import com.jizhang.ui.theme.LedgerTheme
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

/** "45.5" → 4550 cents; null when empty, malformed, zero or absurdly large. */
fun parseYuan(text: String): Long? = text.toBigDecimalOrNull()
    ?.takeIf { it.scale() <= 2 && it > BigDecimal.ZERO }
    ?.movePointRight(2)?.toLong()
    ?.takeIf { it <= 9_999_999_999L }

/** Keeps digits and one dot with at most two decimals while typing. */
fun cleanAmount(raw: String): String {
    val s = raw.filter { it.isDigit() || it == '.' }
    val dot = s.indexOf('.')
    if (dot < 0) return s.take(9)
    val whole = s.substring(0, dot).take(9)
    val frac = s.substring(dot + 1).filter { it.isDigit() }.take(2)
    return "$whole.$frac"
}

/**
 * Note sheet. [chips] are this category's quick-fill notes (ones used often + ones added with "+").
 * Long-press a chip for its × badge; the trailing "+" adds a custom one.
 */
@Composable
fun NoteForm(
    initial: String,
    chips: Flow<List<NoteChip>>,
    onAddChip: (String) -> Unit,
    onRemoveChip: (NoteChip) -> Unit,
    /** Photo file names already attached ([ImageStore]). */
    photos: List<String>,
    newCaptureUri: () -> Uri,
    importPhoto: suspend (Uri) -> String?,
    onSave: (note: String, photos: List<String>) -> Unit,
    close: () -> Unit,
) {
    val c = LedgerTheme.colors
    var draft by remember { mutableStateOf(initial) }
    // Bumped on every chip tap so the cursor lands after the text even if it didn't change.
    var picked by remember { mutableIntStateOf(0) }
    val list by chips.collectAsState(initial = emptyList())
    val chipState = rememberNoteChipsState()

    var shots by remember { mutableStateOf(photos) }
    /** Photos being copied in; each shows a spinner tile and holds back "好". */
    var importing by remember { mutableIntStateOf(0) }
    var choosing by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    val room = ImageStore.MAX_PER_RECORD - shots.size - importing
    val scope = rememberCoroutineScope()

    fun attach(uris: List<Uri>) {
        val take = uris.take(ImageStore.MAX_PER_RECORD - shots.size - importing)
        if (take.isEmpty()) return
        importing += take.size
        choosing = false
        scope.launch {
            var failed = 0
            take.forEach { uri ->
                var name: String? = null
                try {
                    name = importPhoto(uri)
                } finally {
                    // Thumbnail in before the spinner goes, so the row and "好" don't flicker.
                    val got = name
                    if (got != null) shots = shots + got else failed++
                    importing--
                }
            }
            if (failed > 0) hint = if (take.size == 1) "这张图片读不出来" else "有 $failed 张图片读不出来"
        }
    }

    // The camera writes into a file we hand it; keep its Uri across the launch (and a recreate).
    var capture by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(remember { ActivityResultContracts.TakePicture() }) { ok ->
        val uri = capture?.let(Uri::parse)
        capture = null
        if (ok && uri != null) attach(listOf(uri))
    }
    // The system photo picker needs no permission. Its multi-select wants maxItems >= 2.
    val pickOne = rememberLauncherForActivityResult(remember { ActivityResultContracts.PickVisualMedia() }) { uri ->
        if (uri != null) attach(listOf(uri))
    }
    val pickMany = rememberLauncherForActivityResult(
        remember(room) { ActivityResultContracts.PickMultipleVisualMedia(room.coerceAtLeast(2)) },
    ) { attach(it) }

    fun takePhoto() {
        // A new capture target clears the old one, which an import may still be reading.
        if (importing > 0) {
            hint = "上一张还在处理，稍等一下"
            return
        }
        hint = null
        try {
            val uri = newCaptureUri()
            capture = uri.toString()
            camera.launch(uri)
        } catch (_: ActivityNotFoundException) {
            capture = null
            hint = "没有找到相机应用"
        } catch (_: SecurityException) {
            capture = null
            hint = "相机打不开"
        }
    }

    fun pickPhotos() {
        hint = null
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        try {
            if (room >= 2) pickMany.launch(request) else pickOne.launch(request)
        } catch (_: ActivityNotFoundException) {
            hint = "没有找到相册应用"
        }
    }

    fun save() {
        onSave(draft, shots)
        close()
    }

    Column(Modifier.fillMaxWidth().endsNoteEditing(chipState), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FormTitle("备注", "写一句，之后在明细里就能认出这笔；小票、照片也能附上")
        // The chip row's 48dp hit area already pads it, so it sits closer to the field.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FormField(
                draft, { draft = it.take(40) }, placeholder = "写点什么",
                onDone = { if (importing == 0) save() },
                cursorToEnd = picked,
                // Hidden once the record holds as many photos as it can.
                trailing = if (room > 0) ({ AddPhotoButton(open = choosing) { hint = null; choosing = !choosing } }) else null,
            )
            AnimatedVisibility(
                visible = choosing && room > 0,
                enter = expandVertically(Motion.size) + fadeIn(Motion.fade()),
                exit = shrinkVertically(Motion.size) + fadeOut(Motion.fade()),
            ) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PhotoSource("camera", "拍照", Modifier.weight(1f), ::takePhoto)
                    PhotoSource("image", "从相册选", Modifier.weight(1f), ::pickPhotos)
                }
            }
            hint?.let { Text(it, fontSize = 12.sp, color = c.hue(Hue.Gift), modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }
            if (shots.isNotEmpty() || importing > 0) {
                PhotoStrip(shots, loading = importing, onRemove = { shots = shots - it })
            }
            NoteChips(
                chips = list,
                onPick = {
                    draft = it.take(40)
                    picked++
                },
                onAdd = onAddChip,
                onRemove = onRemoveChip,
                state = chipState,
            )
        }
        FormActions(
            primary = "好",
            primaryEnabled = importing == 0,
            onPrimary = ::save,
            secondary = if (initial.isNotEmpty()) "清空" else "取消",
            // 清空 clears the words only; the photos stay as they are in the form.
            onSecondary = { if (initial.isNotEmpty()) onSave("", shots); close() },
        )
    }
}

/** The image button at the right end of the note field; [open] while the source choices show. */
@Composable
private fun AddPhotoButton(open: Boolean, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    val on by animateFloatAsState(if (open) 1f else 0f, Motion.fade(), label = "photoBtn")
    Box(Modifier.size(48.dp).tap(label = "添加图片", onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(lerp(Color.Transparent, c.accent.copy(alpha = .14f), on)),
            contentAlignment = Alignment.Center,
        ) { Glyph(LedgerIcons.get("image"), 20.dp, lerp(c.sub, c.accent, on)) }
    }
}

@Composable
private fun PhotoSource(icon: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    Row(
        modifier.height(48.dp).clip(RoundedCornerShape(16.dp)).background(c.opKey).tap(label = label, pressedScale = .96f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Glyph(LedgerIcons.get(icon), 18.dp, c.accent)
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.ink, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun DateForm(initial: LocalDate, today: LocalDate, onSave: (LocalDate) -> Unit, close: () -> Unit) {
    // The wheel reports the centred date while it still turns, so 确定 during a fling saves that date.
    var draft by remember { mutableStateOf(initial) }
    FormTitle("日期")
    DateWheel(draft, today) { draft = it }
    FormActions(primary = "确定", onPrimary = { onSave(draft); close() }, onSecondary = close)
}

/** The "+" input of budget amount chips: decimal keyboard, kept as plain yuan ("3000", "45.5"). */
private val BudgetChipInput = ChipInput(
    keyboardType = KeyboardType.Decimal,
    placeholder = "金额",
    addLabel = "添加自定义金额",
    filter = ::cleanAmount,
    commit = { raw -> if (raw.isBlank()) "" else parseYuan(raw)?.let(LedgerViewModel::plainYuan) },
)

/** Budget amounts under an amount field, like the note chips: tap fills, long-press ×, "+" adds. */
@Composable
private fun BudgetChips(
    chips: List<NoteChip>,
    state: NoteChipsState,
    onPick: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (NoteChip) -> Unit,
) {
    NoteChips(
        chips = chips,
        onPick = { onPick(cleanAmount(it)) },
        onAdd = onAdd,
        onRemove = onRemove,
        state = state,
        input = BudgetChipInput,
        label = { "¥$it" },
    )
}

/**
 * Sets the year, month or day total budget. For year / day an empty field clears it.
 * [chips] are this period's amounts (every one saved before + ones added with "+").
 */
@Composable
fun TotalBudgetForm(
    period: BudgetPeriod,
    initialCents: Long,
    chips: Flow<List<NoteChip>>,
    onAddChip: (String) -> Unit,
    onRemoveChip: (NoteChip) -> Unit,
    onSave: (Long) -> Unit,
    close: () -> Unit,
) {
    var draft by remember { mutableStateOf(if (initialCents > 0) formatCentsShort(initialCents).replace(",", "") else "") }
    // Bumped on every chip tap so the cursor lands after the amount even if it didn't change.
    var picked by remember { mutableIntStateOf(0) }
    val list by chips.collectAsState(initial = emptyList())
    val chipState = rememberNoteChipsState()
    val cents = parseYuan(draft)
    val (title, hint) = when (period) {
        BudgetPeriod.Year -> "年预算" to "今年所有支出加起来的上限"
        BudgetPeriod.Month -> "月预算" to "首页的预算环和「每天约可花」都按它来算"
        BudgetPeriod.Day -> "日预算" to "每天所有支出加起来的上限"
    }
    val canClear = period != BudgetPeriod.Month && initialCents > 0
    Column(Modifier.fillMaxWidth().endsNoteEditing(chipState), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FormTitle(title, hint)
        // The chip row's 48dp hit area already pads it, so it sits closer to the field.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FormField(
                draft, { draft = cleanAmount(it) }, placeholder = "0", prefix = "¥", large = true,
                keyboardType = KeyboardType.Decimal, isError = draft.isNotEmpty() && cents == null,
                onDone = { cents?.let { onSave(it); close() } },
                cursorToEnd = picked,
            )
            BudgetChips(
                list, chipState,
                onPick = {
                    draft = it
                    picked++
                },
                onAdd = onAddChip,
                onRemove = onRemoveChip,
            )
        }
        FormActions(
            primary = "保存",
            primaryEnabled = cents != null,
            onPrimary = { cents?.let { onSave(it); close() } },
            secondary = if (canClear) "不设置" else "取消",
            onSecondary = { if (canClear) onSave(0); close() },
        )
    }
}

/**
 * Create or edit a custom budget: period, what it covers, amount and an optional name.
 * The amount chips follow the selected period ([chips] / [onAddChip] / [onRemoveChip] take it).
 */
@Composable
fun BudgetForm(
    existing: Budget?,
    /** Expense categories a budget can be limited to (built-in and custom). */
    categories: List<Category>,
    chips: (BudgetPeriod) -> Flow<List<NoteChip>>,
    onAddChip: (BudgetPeriod, String) -> Unit,
    onRemoveChip: (BudgetPeriod, NoteChip) -> Unit,
    onSave: (name: String, period: BudgetPeriod, category: Category?, cents: Long) -> Unit,
    onDelete: (() -> Unit)?,
    close: () -> Unit,
) {
    val c = LedgerTheme.colors
    val periods = BudgetPeriod.entries
    val scopes: List<Category?> = listOf<Category?>(null) + categories
    var period by remember { mutableIntStateOf(periods.indexOf(existing?.period ?: BudgetPeriod.Day)) }
    var scope by remember { mutableIntStateOf(scopes.indexOfFirst { it?.key == existing?.category?.key }.coerceAtLeast(0)) }
    var amount by remember { mutableStateOf(existing?.let { formatCentsShort(it.amountCents).replace(",", "") } ?: "") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    // Bumped on every chip tap so the cursor lands after the amount even if it didn't change.
    var picked by remember { mutableIntStateOf(0) }
    val chipFlow = remember(period) { chips(periods[period]) }
    val list by chipFlow.collectAsState(initial = emptyList())
    val chipState = rememberNoteChipsState()
    val cents = parseYuan(amount)
    val preview = Budget(0, "", periods[period], scopes[scope], 0).displayName

    Column(Modifier.fillMaxWidth().endsNoteEditing(chipState), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FormTitle(if (existing == null) "新建预算" else "编辑预算", "每个周期结束后自动从零开始算")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormLabel("周期")
            Segmented(periods.map { it.label }, period, { period = it })
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormLabel("范围")
            ChoiceChips(
                scopes.map { it?.label ?: "全部支出" }, scope, { scope = it },
                dots = scopes.map { it?.let { cat -> c.hue(cat) } },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormLabel("${periods[period].scope}最多花")
            // The chip row's 48dp hit area already pads it, so it sits closer to the field.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FormField(
                    amount, { amount = cleanAmount(it) }, placeholder = "0", prefix = "¥", large = true,
                    keyboardType = KeyboardType.Decimal, autoFocus = existing == null,
                    isError = amount.isNotEmpty() && cents == null,
                    cursorToEnd = picked,
                )
                BudgetChips(
                    list, chipState,
                    onPick = {
                        amount = it
                        picked++
                    },
                    onAdd = { onAddChip(periods[period], it) },
                    onRemove = { onRemoveChip(periods[period], it) },
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormLabel("名称（可不填）")
            FormField(name, { name = it.take(16) }, placeholder = preview, autoFocus = false)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onDelete != null) {
                FormButton("删除", ButtonKind.Danger, Modifier.weight(1f)) { onDelete(); close() }
            } else {
                FormButton("取消", ButtonKind.Secondary, Modifier.weight(1f), onClick = close)
            }
            FormButton("保存", ButtonKind.Primary, Modifier.weight(1.6f), enabled = cents != null) {
                cents?.let { onSave(name, periods[period], scopes[scope], it); close() }
            }
        }
    }
}

@Composable
fun BackupForm(onExport: () -> Unit, onImport: () -> Unit, onImportShark: () -> Unit, close: () -> Unit) {
    FormTitle("备份与恢复", "备份是一个 JSON 文件，可以存到云盘、电脑或发给自己")
    OptionRow("backup", Hue.Med, "导出备份", "把全部记录和预算存成一个文件") { close(); onExport() }
    OptionRow("undo", Hue.Transit, "从备份导入", "用备份替换手机上现有的数据") { close(); onImport() }
    OptionRow("list", Hue.Fun, "从鲨鱼记账导入", "选择导出的 CSV 明细文件") { close(); onImportShark() }
    FormButton("取消", ButtonKind.Secondary, Modifier.fillMaxWidth(), onClick = close)
}

@Composable
private fun OptionRow(icon: String, hue: Hue, title: String, desc: String, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.opKey)
            .tap(label = title, pressedScale = .98f, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(c.tint(hue)), contentAlignment = Alignment.Center) {
            Glyph(LedgerIcons.get(icon), 21.dp, c.hue(hue))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(desc, fontSize = 12.sp, color = c.sub)
        }
        Glyph(LedgerIcons.get("chevr"), 16.dp, c.sub)
    }
}

/** A yes/no question; [danger] paints the confirm button red. */
@Composable
fun ConfirmForm(title: String, message: String, confirm: String, danger: Boolean, onConfirm: () -> Unit, close: () -> Unit) {
    FormTitle(title, message)
    FormActions(
        primary = confirm,
        primaryKind = if (danger) ButtonKind.Danger else ButtonKind.Primary,
        onPrimary = { onConfirm(); close() },
        onSecondary = close,
    )
}

@Composable
fun InfoForm(title: String, message: String, close: () -> Unit) {
    FormTitle(title, message)
    FormButton("好", ButtonKind.Primary, Modifier.fillMaxWidth().height(52.dp), onClick = close)
}
