package com.jizhang.ui.add

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.LedgerViewModel
import com.jizhang.domain.Category
import com.jizhang.domain.Record
import com.jizhang.ui.components.ButtonKind
import com.jizhang.ui.components.FormActions
import com.jizhang.ui.components.FormButton
import com.jizhang.ui.components.FormTitle
import com.jizhang.ui.components.Glyph
import com.jizhang.ui.components.LocalFormSheet
import com.jizhang.ui.components.glass
import com.jizhang.ui.components.tap
import com.jizhang.ui.forms.ConfirmForm
import com.jizhang.ui.forms.RecordForm
import com.jizhang.ui.forms.RecordLine
import com.jizhang.ui.forms.TargetGrid
import com.jizhang.ui.monthLabel
import com.jizhang.ui.theme.LedgerIcons
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.Motion
import com.jizhang.ui.theme.rememberHaptics
import java.time.LocalDate
import java.time.YearMonth

/**
 * Every bill of one category, opened from its delete step ("查看全部"). Tap a bill to edit it
 * (a new category moves it off this page); long-press to start picking several, then move them
 * all to one category or delete them. Back ends picking first, then returns to the delete step.
 */
@Composable
internal fun CategoryBillsPage(
    /** The category shown; null hides the page. */
    category: Category?,
    vm: LedgerViewModel,
    today: LocalDate,
    onBack: () -> Unit,
) {
    var last by remember { mutableStateOf(category) }
    if (category != null) last = category
    AnimatedVisibility(
        visible = category != null,
        enter = slideInHorizontally(Motion.offset) { it } + fadeIn(Motion.fade()),
        exit = slideOutHorizontally(Motion.offset) { it } + fadeOut(Motion.fade()),
    ) {
        val shown = last ?: return@AnimatedVisibility
        BillsPage(shown, vm, today, active = category != null, onBack = onBack)
    }
}

@Composable
private fun BillsPage(category: Category, vm: LedgerViewModel, today: LocalDate, active: Boolean, onBack: () -> Unit) {
    val c = LedgerTheme.colors
    val haptics = rememberHaptics()
    val form = LocalFormSheet.current
    val state by vm.state.collectAsState()
    val records = remember(state.allRecords, category.key) { state.allRecords.filter { it.category.key == category.key } }
    val targets = state.catalog.forKind(category.kind).filter { it.key != category.key }
    // Plain remember: a bill moved or deleted elsewhere just drops out of the picked set.
    var picked by remember { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(records) {
        val ids = records.mapTo(HashSet()) { it.id }
        picked = picked.filterTo(HashSet()) { it in ids }
    }
    val picking = picked.isNotEmpty()
    BackHandler(enabled = active && !form.isOpen) { if (picking) picked = emptySet() else onBack() }

    fun toggle(r: Record) {
        haptics.tick()
        picked = if (r.id in picked) picked - r.id else picked + r.id
    }

    fun edit(r: Record) = form.open { s ->
        RecordForm(
            record = r,
            categories = listOf(category) + targets,
            today = today,
            onSave = { cat, cents, note, date -> vm.save(r.id, cat, cents, note, date.atTime(r.occurredAt.toLocalTime()), r.images) },
            onDelete = { vm.delete(r) },
            close = s::close,
        )
    }

    fun move() {
        val ids = picked
        form.open { s ->
            var pick by remember { mutableStateOf<Category?>(null) }
            FormTitle("移到哪一类？", "已选 ${ids.size} 笔，会一起移过去")
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                TargetGrid(targets, pick?.key ?: "") { pick = it }
            }
            FormActions(
                primary = pick?.let { "移到「${it.label}」" } ?: "移过去",
                primaryEnabled = pick != null,
                onPrimary = {
                    pick?.let { vm.moveRecords(ids, it) }
                    picked = emptySet()
                    s.close()
                },
                onSecondary = s::close,
            )
        }
    }

    fun delete() {
        val ids = picked
        form.open { s ->
            ConfirmForm(
                "删除 ${ids.size} 笔账单？", "删除后，顶部的提示里还可以撤销", "删除", danger = true,
                onConfirm = {
                    vm.deleteRecords(ids)
                    picked = emptySet()
                },
                close = s::close,
            )
        }
    }

    val nav = WindowInsets.navigationBars.asPaddingValues()
    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg0)
            .background(c.sheet)
            // Taps on empty space must not reach the add sheet underneath.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(category, records.size, picked.size, allPicked = picking && picked.size == records.size, onBack = {
                if (picking) picked = emptySet() else onBack()
            }) {
                haptics.tick()
                picked = if (picked.size == records.size) emptySet() else records.mapTo(HashSet()) { it.id }
            }
            if (records.isEmpty()) {
                Empty(Modifier.weight(1f), onBack)
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = nav.calculateBottomPadding() + 96.dp),
                ) {
                    item(key = "hint") {
                        Text(
                            "点一笔可以修改或换分类，长按可以多选",
                            fontSize = 12.sp, color = c.sub,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp).animateItem(fadeInSpec = Motion.fade(), placementSpec = Motion.offset, fadeOutSpec = Motion.fade()),
                        )
                    }
                    var month: YearMonth? = null
                    records.forEach { r ->
                        val m = YearMonth.from(r.date)
                        if (m != month) {
                            month = m
                            item(key = "m$m") {
                                Text(
                                    monthLabel(m), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.sub,
                                    modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp).animateItem(
                                        fadeInSpec = Motion.fade(), placementSpec = Motion.offset, fadeOutSpec = Motion.fade(),
                                    ),
                                )
                            }
                        }
                        item(key = r.id) {
                            BillRow(
                                r, today, picking, r.id in picked,
                                onClick = { if (picking) toggle(r) else edit(r) },
                                onLongClick = { toggle(r) },
                                modifier = Modifier.animateItem(fadeInSpec = Motion.fade(), placementSpec = Motion.offset, fadeOutSpec = Motion.fade()),
                            )
                        }
                    }
                }
            }
        }
        // Batch actions for the picked bills.
        AnimatedVisibility(
            visible = picking,
            enter = slideInVertically(Motion.offset) { it } + fadeIn(Motion.fade()),
            exit = slideOutVertically(Motion.offset) { it } + fadeOut(Motion.fade()),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                    .fillMaxWidth()
                    .glass(30.dp)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormButton("删除", ButtonKind.Danger, Modifier.weight(1f), onClick = ::delete)
                FormButton("移到别的分类", ButtonKind.Primary, Modifier.weight(1.6f), enabled = targets.isNotEmpty(), onClick = ::move)
            }
        }
    }
}

/** Back (or, while picking, cancel), the title, and 全选 while picking. */
@Composable
private fun Header(category: Category, total: Int, picked: Int, allPicked: Boolean, onBack: () -> Unit, onPickAll: () -> Unit) {
    val c = LedgerTheme.colors
    val picking = picked > 0
    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.align(Alignment.CenterStart).size(48.dp).glass(24.dp).tap(label = if (picking) "取消多选" else "返回", onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(picking, transitionSpec = { fadeIn(Motion.fade()) togetherWith fadeOut(Motion.fade()) }, label = "headerBack") { p ->
                Glyph(LedgerIcons.get(if (p) "close" else "chevl"), 20.dp, c.ink)
            }
        }
        AnimatedContent(picking, transitionSpec = { fadeIn(Motion.fade()) togetherWith fadeOut(Motion.fade()) }, label = "headerTitle") { p ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (p) "已选 $picked 笔" else "「${category.label}」的账单", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                if (!p) Text("共 $total 笔", fontSize = 11.sp, color = c.sub)
            }
        }
        val shown by animateFloatAsState(if (picking) 1f else 0f, Motion.fade(), label = "pickAll")
        if (shown > 0f) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .height(48.dp)
                    .widthIn(min = 64.dp)
                    .graphicsLayer { alpha = shown }
                    .glass(24.dp)
                    .tap(label = if (allPicked) "全不选" else "全选", enabled = picking, onClick = onPickAll)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (allPicked) "全不选" else "全选", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
            }
        }
    }
}

/** A bill on a light card; long-press starts picking, a press shrinks it a little. */
@Composable
private fun BillRow(
    r: Record,
    today: LocalDate,
    picking: Boolean,
    picked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
) {
    val c = LedgerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .98f else 1f, Motion.layout(), label = "billPress")
    val tint by animateFloatAsState(if (picked) 1f else 0f, Motion.fade(), label = "billPicked")
    RecordLine(
        r, today,
        modifier
            .padding(vertical = 2.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(c.opKey)
            .background(c.accent.copy(alpha = .10f * tint))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = if (picking) "选择" else "编辑",
                onLongClickLabel = "多选",
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp),
        selecting = picking,
        selected = picked,
    )
}

/** Every bill has moved or gone: nothing left here but to carry on deleting. */
@Composable
private fun Empty(modifier: Modifier, onBack: () -> Unit) {
    val c = LedgerTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("这个分类下已经没有账单了", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink)
        FormButton("回去删除分类", ButtonKind.Secondary, Modifier.fillMaxWidth(), onClick = onBack)
    }
}
