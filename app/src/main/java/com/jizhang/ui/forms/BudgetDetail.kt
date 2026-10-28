package com.jizhang.ui.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.domain.BudgetProgress
import com.jizhang.domain.Record
import com.jizhang.domain.formatCents
import com.jizhang.domain.formatCentsShort
import com.jizhang.ui.chipDate
import com.jizhang.ui.percentLabel
import com.jizhang.ui.budget.LevelBar
import com.jizhang.ui.budget.levelColor
import com.jizhang.ui.components.FormActions
import com.jizhang.ui.components.FormLabel
import com.jizhang.ui.components.FormTitle
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.numberStyle
import java.time.LocalDate

/** Tallest the ranking grows before it scrolls inside itself. */
private val RankingMaxHeight = 280.dp

/**
 * One custom budget in detail: 已用 on top, the progress bar under it, then the expenses behind
 * the number, largest first. 编辑预算 swaps this sheet for the edit form.
 */
@Composable
fun BudgetDetailForm(
    progress: BudgetProgress,
    records: List<Record>,
    today: LocalDate,
    onEdit: () -> Unit,
    close: () -> Unit,
) {
    val c = LedgerTheme.colors
    val b = progress.budget
    val all = remember(progress, records) {
        b.expensesOf(records, today).sortedWith(compareByDescending<Record> { it.amountCents }.thenByDescending { it.occurredAt })
    }
    val scroll = rememberScrollState()

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FormTitle(b.displayName, "${b.period.label} ¥${formatCentsShort(b.amountCents)} · ${b.period.scope}已用")

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("¥", style = numberStyle(17.sp, FontWeight.Normal, 0f), color = c.ink, modifier = Modifier.padding(end = 3.dp, bottom = 3.dp))
                Text(formatCents(progress.spentCents), style = numberStyle(30.sp, FontWeight.Light, -.03f), color = c.ink)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("预算 ¥${formatCentsShort(b.amountCents)}", fontSize = 12.sp, color = c.sub)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (progress.over) "超出 ¥${formatCentsShort(-progress.leftCents)}" else "还剩 ¥${formatCentsShort(progress.leftCents)}",
                    style = numberStyle(12.sp, FontWeight.Medium, 0f),
                    color = if (progress.over) c.levelColor(progress.level) else c.sub,
                )
                Spacer(Modifier.weight(1f))
                Text(percentLabel(progress.fraction), style = numberStyle(12.sp, FontWeight.Medium, 0f), color = c.levelColor(progress.level))
            }
            LevelBar(progress.fraction, progress.level, Modifier.padding(top = 2.dp), height = 10.dp)
        }

        HorizontalDivider(thickness = 0.5.dp, color = c.hair)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FormLabel("支出排名")
                Spacer(Modifier.weight(1f))
                Text(
                    if (all.isEmpty()) "" else "共 ${all.size} 笔",
                    fontSize = 12.sp, color = c.sub, textAlign = TextAlign.End,
                )
            }
            if (all.isEmpty()) {
                Box { Text("这段时间还没有支出", fontSize = 13.sp, color = c.sub) }
            } else {
                // The whole list, in its own scroller: a long month must not push the buttons off
                // screen, and the sheet itself stays short enough to reach the scrim.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = RankingMaxHeight)
                        .verticalScroll(scroll),
                ) {
                    all.forEachIndexed { i, r -> DetailRow(i + 1, r, today) }
                }
            }
        }

        FormActions("编辑预算", onEdit, secondary = "关闭", onSecondary = close)
    }
}

@Composable
private fun DetailRow(rank: Int, record: Record, today: LocalDate) {
    val c = LedgerTheme.colors
    Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$rank", style = numberStyle(12.sp, FontWeight.Medium, 0f), color = c.sub,
            textAlign = TextAlign.Center, modifier = Modifier.width(22.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                record.note.ifBlank { record.category.label },
                fontSize = 14.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${chipDate(record.date, today)} · ${record.category.label}",
                fontSize = 11.sp, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text("¥${formatCents(record.amountCents)}", style = numberStyle(14.sp, FontWeight.Medium, 0f), color = c.ink)
    }
}
