package com.jizhang.ui.forms

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.LedgerViewModel
import com.jizhang.domain.Category
import com.jizhang.domain.EntryKind
import com.jizhang.domain.Record
import com.jizhang.domain.formatSigned
import com.jizhang.ui.chipDate
import com.jizhang.ui.components.ButtonKind
import com.jizhang.ui.components.FormButton
import com.jizhang.ui.components.FormField
import com.jizhang.ui.components.FormLabel
import com.jizhang.ui.components.FormTitle
import com.jizhang.ui.components.DateWheel
import com.jizhang.ui.components.Glyph
import com.jizhang.ui.components.LocalFormSheet
import com.jizhang.ui.components.tap
import com.jizhang.ui.dayLabel
import com.jizhang.ui.hhmm
import com.jizhang.ui.theme.LedgerIcons
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.Motion
import com.jizhang.ui.theme.numberStyle
import com.jizhang.ui.theme.rememberHaptics
import java.time.LocalDate

/**
 * Quick edit of one bill picked from a category's bill list (deleting a category, or its 全部
 * page): amount, category, date and note. Photos and the time of day stay as they are. A new
 * category moves the bill there, which takes it off that list. Deleting it leaves an undo toast.
 */
@Composable
fun ColumnScope.RecordForm(
    record: Record,
    /** Categories of the bill's kind to pick from, its own included. */
    categories: List<Category>,
    today: LocalDate,
    onSave: (category: Category, cents: Long, note: String, date: LocalDate) -> Unit,
    onDelete: () -> Unit,
    close: () -> Unit,
) {
    val c = LedgerTheme.colors
    val haptics = rememberHaptics()
    val focus = LocalFocusManager.current
    var amount by remember { mutableStateOf(LedgerViewModel.plainYuan(record.amountCents)) }
    var pick by remember { mutableStateOf(record.category.key) }
    var note by remember { mutableStateOf(record.note) }
    var date by remember { mutableStateOf(record.date) }
    var picking by remember { mutableStateOf(false) }
    val cents = parseYuan(amount)
    val category = categories.firstOrNull { it.key == pick } ?: record.category

    FormTitle(
        "编辑账单",
        if (category.key == record.category.key) "${dayLabel(record.date)} ${hhmm(record.occurredAt)} 记的" else "保存后移到「${category.label}」",
    )
    FormField(
        amount, { amount = cleanAmount(it) }, placeholder = "0", prefix = "¥", large = true,
        keyboardType = KeyboardType.Decimal, autoFocus = false,
        isError = amount.isNotEmpty() && cents == null,
        onDone = { focus.clearFocus() },
    )
    // Scrolls when the categories, the open date wheel and the note outgrow the sheet.
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FormLabel("分类")
        TargetGrid(categories, pick) {
            focus.clearFocus()
            pick = it.key
        }
        FormLabel("日期")
        Row(
            Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.opKey)
                .tap(label = "选择日期", pressedScale = .97f) {
                    focus.clearFocus()
                    haptics.tick()
                    picking = !picking
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Glyph(LedgerIcons.get("cal"), 16.dp, c.ink)
            Text(chipDate(date, today), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.ink)
            val turn by animateFloatAsState(if (picking) 1f else 0f, Motion.layout(), label = "dateChevron")
            Glyph(LedgerIcons.get("chevr"), 14.dp, c.sub, Modifier.graphicsLayer { rotationZ = 90f * turn })
        }
        AnimatedVisibility(
            visible = picking,
            enter = expandVertically(Motion.size) + fadeIn(Motion.fade()),
            exit = shrinkVertically(Motion.size) + fadeOut(Motion.fade()),
        ) {
            DateWheel(date, today, latest = maxOf(today, record.date)) { date = it }
        }
        FormLabel("备注")
        FormField(note, { note = it.take(40) }, placeholder = "添加备注", autoFocus = false, onDone = { focus.clearFocus() })
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FormButton("删除", ButtonKind.Danger, Modifier.weight(1f)) {
            onDelete()
            close()
        }
        FormButton("取消", ButtonKind.Secondary, Modifier.weight(1f), onClick = close)
        FormButton("保存", ButtonKind.Primary, Modifier.weight(1.4f), enabled = cents != null) {
            cents?.let {
                onSave(category, it, note, date)
                close()
            }
        }
    }
}

/** While a form shows a step of its own, back steps out of it instead of closing the sheet. */
@Composable
internal fun ClaimBack(onBack: () -> Unit) {
    val form = LocalFormSheet.current
    DisposableEffect(form) {
        form.backClaims++
        onDispose { form.backClaims-- }
    }
    BackHandler(onBack = onBack)
}

/**
 * One bill in a category's list: note (or category name), date and time, signed amount. In
 * [selecting] mode a check circle leads the row. The caller gives the row its gestures.
 */
@Composable
fun RecordLine(
    record: Record,
    today: LocalDate,
    modifier: Modifier = Modifier,
    selecting: Boolean = false,
    selected: Boolean = false,
) {
    val c = LedgerTheme.colors
    Row(
        modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedVisibility(
            visible = selecting,
            enter = expandHorizontallyIn() + fadeIn(Motion.fade()),
            exit = shrinkHorizontallyOut() + fadeOut(Motion.fade()),
        ) { CheckCircle(selected) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    record.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (record.images.isNotEmpty()) Glyph(LedgerIcons.get("image"), 13.dp, c.sub)
            }
            Text(
                (if (record.date.year != today.year) "${record.date.year}年" else "") + "${dayLabel(record.date)} ${hhmm(record.occurredAt)}",
                fontSize = 12.sp, color = c.sub, maxLines = 1,
            )
        }
        Text(
            formatSigned(record.amountCents, record.kind),
            style = numberStyle(15.sp, FontWeight.Medium, 0f),
            color = if (record.kind == EntryKind.Income) c.income else c.ink,
        )
    }
}

private fun expandHorizontallyIn() = androidx.compose.animation.expandHorizontally(Motion.size, expandFrom = Alignment.Start)

private fun shrinkHorizontallyOut() = androidx.compose.animation.shrinkHorizontally(Motion.size, shrinkTowards = Alignment.Start)

/** Selection mark of [RecordLine]: an empty ring, or an accent disc with a tick. */
@Composable
private fun CheckCircle(on: Boolean) {
    val c = LedgerTheme.colors
    val fill by animateFloatAsState(if (on) 1f else 0f, Motion.fade(), label = "check")
    val pop by animateFloatAsState(if (on) 1f else .6f, Motion.layout(), label = "checkPop")
    Box(
        Modifier.size(24.dp).clip(CircleShape).border(1.5.dp, c.sub.copy(alpha = .5f * (1f - fill)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .graphicsLayer {
                    alpha = fill
                    scaleX = pop
                    scaleY = pop
                }
                .clip(CircleShape)
                .background(c.accent),
            contentAlignment = Alignment.Center,
        ) { Glyph(LedgerIcons.get("check", 2.6f), 14.dp, c.onAccent) }
    }
}
