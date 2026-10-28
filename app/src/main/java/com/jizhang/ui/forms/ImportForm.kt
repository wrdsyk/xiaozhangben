package com.jizhang.ui.forms

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.data.SharkNewCategory
import com.jizhang.data.SharkPlan
import com.jizhang.domain.EntryKind
import com.jizhang.domain.Hue
import com.jizhang.domain.formatCents
import com.jizhang.ui.components.FormActions
import com.jizhang.ui.components.FormTitle
import com.jizhang.ui.components.Glyph
import com.jizhang.ui.components.Segmented
import com.jizhang.ui.theme.LedgerIcons
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.Motion
import com.jizhang.ui.theme.numberStyle
import java.time.LocalDate

/** Error lines listed before "还有 N 行". */
private const val ERRORS_SHOWN = 5

private fun ymd(d: LocalDate) = "${d.year}年${d.monthValue}月${d.dayOfMonth}日"

/**
 * Preview of a 鲨鱼记账 CSV before anything is written: totals per kind, the date range, rows that
 * are already in the ledger, lines that couldn't be read and the categories the file would add.
 * [onImport] gets whether to create those categories (otherwise their rows go to 其他).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.SharkImportForm(
    plan: SharkPlan,
    onImport: (createCategories: Boolean) -> Unit,
    close: () -> Unit,
) {
    val c = LedgerTheme.colors
    // 0 = 新建这些分类, 1 = 归入「其他」
    var choice by remember { mutableIntStateOf(0) }
    // The sheet stays up while it slides away: a second tap on 导入 must not import twice.
    var started by remember { mutableStateOf(false) }
    val create = choice == 0
    val resolved = remember(plan, create) { plan.resolve(create) }
    val count = resolved.entries.size
    val chipAlpha by animateFloatAsState(if (create) 1f else .4f, Motion.fade(), label = "newCats")
    val first = plan.firstDay
    val last = plan.lastDay

    FormTitle(
        "从鲨鱼记账导入",
        if (first != null && last != null) "读到 ${plan.entries.size} 条记录 · ${ymd(first)} 至 ${ymd(last)}" else "这个文件里没有读到能导入的记录",
    )
    Column(
        Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (plan.entries.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Total("支出", plan.expenseCount, plan.expenseCents, c.ink, Modifier.weight(1f))
                Total("收入", plan.incomeCount, plan.incomeCents, c.income, Modifier.weight(1f))
            }
        }
        if (plan.newCategories.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("账本里还没有这 ${plan.newCategories.size} 个分类", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.sub)
                FlowRow(
                    Modifier.fillMaxWidth().graphicsLayer { alpha = chipAlpha },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    plan.newCategories.forEach { NewCategoryChip(it) }
                }
                Segmented(listOf("新建这些分类", "归入「其他」"), choice, { choice = it }, height = 48.dp)
            }
        }
        if (plan.entries.isEmpty() && plan.errors.isEmpty()) {
            Notice("info", Hue.Gift, "表头下面没有记录")
        }
        if (resolved.skipped > 0) {
            Notice("check", Hue.Daily, "${resolved.skipped} 条和账本里已有的一样，会跳过")
        }
        if (plan.errors.isNotEmpty()) {
            Notice("info", Hue.Gift, "${plan.errors.size} 行读不了，会忽略") {
                plan.errors.take(ERRORS_SHOWN).forEach { Text("第 ${it.line} 行 · ${it.reason}", fontSize = 12.sp, color = c.sub, lineHeight = 17.sp) }
                if (plan.errors.size > ERRORS_SHOWN) {
                    Text("还有 ${plan.errors.size - ERRORS_SHOWN} 行……", fontSize = 12.sp, color = c.sub)
                }
            }
        }
    }
    FormActions(
        primary = "导入 $count 条",
        primaryEnabled = count > 0 && !started,
        onPrimary = {
            started = true
            onImport(create)
            close()
        },
        onSecondary = close,
    )
}

/** One kind's row count and sum, in tabular figures. */
@Composable
private fun Total(title: String, count: Int, cents: Long, tone: Color, modifier: Modifier) {
    val c = LedgerTheme.colors
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(c.opKey).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("$title · $count 条", fontSize = 12.sp, color = c.sub)
        Text(
            "¥${formatCents(cents)}",
            style = numberStyle(19.sp, FontWeight.Normal, -.01f), color = tone,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NewCategoryChip(category: SharkNewCategory) {
    val c = LedgerTheme.colors
    Row(
        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(c.tint(category.hue)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Glyph(LedgerIcons.get(category.icon), 16.dp, c.hue(category.hue))
        Text(
            if (category.kind == EntryKind.Income) "${category.name} · 收入" else category.name,
            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.label,
        )
    }
}

/** A tinted note with an icon, [title] and optional detail lines under it. */
@Composable
private fun Notice(icon: String, hue: Hue, title: String, detail: (@Composable ColumnScope.() -> Unit)? = null) {
    val c = LedgerTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.tint(hue)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Glyph(LedgerIcons.get(icon), 18.dp, c.hue(hue), Modifier.padding(top = 1.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.label, lineHeight = 18.sp)
            detail?.invoke(this)
        }
    }
}
