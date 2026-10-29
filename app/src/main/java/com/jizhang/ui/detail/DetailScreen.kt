package com.jizhang.ui.detail

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.domain.EntryKind
import com.jizhang.domain.MonthSummary
import com.jizhang.domain.Record
import com.jizhang.domain.formatSigned
import com.jizhang.ui.components.CategoryTile
import com.jizhang.ui.components.Glyph
import com.jizhang.ui.components.LocalHazeState
import com.jizhang.ui.components.PhotoStrip
import com.jizhang.ui.components.glass
import com.jizhang.ui.components.tap
import com.jizhang.ui.fullDateTime
import com.jizhang.ui.home.SharedKeys
import com.jizhang.ui.theme.LedgerIcons
import com.jizhang.ui.theme.LedgerTheme
import com.jizhang.ui.theme.Motion
import com.jizhang.ui.theme.numberStyle
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

/**
 * Bill detail. The list row's bounds grow into this page (sharedBounds); the category icon
 * and amount fly across as their own shared elements; everything else fades up after.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.DetailScreen(
    record: Record,
    summary: MonthSummary,
    animatedScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onEdit: (origin: Offset) -> Unit,
    onDelete: () -> Unit,
    /**
     * The record's month's records, for the category share of any category and of income. Optional:
     * without it only a top-five spending category's share can be shown.
     */
    monthRecords: List<Record>? = null,
) {
    val c = LedgerTheme.colors
    val haze = LocalHazeState.current
    val later = with(animatedScope) {
        Modifier.animateEnterExit(
            enter = fadeIn(Motion.fade()) + slideInVertically(Motion.offset) { it / 3 },
            exit = fadeOut(Motion.fade()),
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .sharedBounds(
                rememberSharedContentState(SharedKeys.card(record.id)),
                animatedScope,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                boundsTransform = { _, _ -> Motion.bounds },
                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(16.dp)),
            )
            .then(if (haze != null) Modifier.hazeBlur(HazeInput.Backdrop(haze), HazeBlurStyle { blurRadius(26.dp) }) else Modifier)
            .background(c.sheet)
            // Content on a layer of its own, so its animations don't re-run the full-screen blur.
            .graphicsLayer(),
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                Box(
                    later.align(Alignment.CenterStart).size(48.dp).glass(24.dp).tap(label = "返回", onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Glyph(LedgerIcons.get("chevl"), 20.dp, c.ink) }
                Text("账单详情", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = later)
            }
            Spacer(Modifier.height(20.dp))
            CategoryTile(
                record.category, 72.dp, 24.dp, iconSize = 34.dp,
                modifier = Modifier.sharedElement(
                    rememberSharedContentState(SharedKeys.icon(record.id)), animatedScope,
                    boundsTransform = { _, _ -> Motion.bounds },
                ),
            )
            Spacer(Modifier.height(14.dp))
            Text(record.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink, modifier = later)
            Text(
                formatSigned(record.amountCents, record.kind),
                style = numberStyle(44.sp, FontWeight.Light, -.03f),
                color = if (record.kind == EntryKind.Income) c.income else c.ink,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .sharedBounds(
                        rememberSharedContentState(SharedKeys.amount(record.id)), animatedScope,
                        boundsTransform = { _, _ -> Motion.bounds },
                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                    ),
            )
            val kindLabel = if (record.kind == EntryKind.Expense) "支出" else "收入"
            Text("$kindLabel · ${record.category.label}", fontSize = 12.sp, color = c.sub, modifier = later.padding(top = 4.dp))

            Column(
                later.padding(top = 28.dp).fillMaxWidth().glass(26.dp).padding(horizontal = 18.dp, vertical = 4.dp),
            ) {
                InfoRow("时间", fullDateTime(record.occurredAt))
                HorizontalDivider(thickness = 0.5.dp, color = c.hair)
                InfoRow("分类", record.category.label)
                HorizontalDivider(thickness = 0.5.dp, color = c.hair)
                InfoRow("类型", kindLabel)
                HorizontalDivider(thickness = 0.5.dp, color = c.hair)
                InfoRow("备注", record.note.ifBlank { "无" }, dim = record.note.isBlank())
                // Photos sit under the note they belong to; a tap opens the viewer there.
                if (record.images.isNotEmpty()) {
                    PhotoStrip(record.images, Modifier.padding(top = 2.dp, bottom = 14.dp))
                }
            }

            // Share of the record's own month (the caller hands in that month's summary), of
            // spending for an expense and of income for an income record.
            val monthTotal = if (record.kind == EntryKind.Expense) summary.expenseCents else summary.incomeCents
            if (monthTotal > 0 && YearMonth.from(record.date) == summary.month) {
                val categoryCents = remember(record, summary, monthRecords) { categoryCentsOf(record, summary, monthRecords) }
                val own = "这笔占本月$kindLabel ${formatShare(record.amountCents, monthTotal)}"
                ShareCard(
                    text = if (categoryCents == null) own
                    else "$own · ${record.category.label}共占 ${formatShare(categoryCents, monthTotal)}",
                    // The bar shows the category's share; when that is unknown, the record's own.
                    fraction = (categoryCents ?: record.amountCents).toFloat() / monthTotal,
                    modifier = later.padding(top = 14.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            var editOrigin by remember { mutableStateOf(Offset.Zero) }
            Row(later.padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .onGloballyPositioned { editOrigin = it.boundsInRoot().center }
                        .glass(26.dp)
                        .tap(label = "编辑") { onEdit(editOrigin) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(LedgerIcons.get("note"), 18.dp, c.ink)
                    Text("编辑", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.padding(start = 6.dp))
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(c.tint(com.jizhang.domain.Hue.Gift))
                        .tap(label = "删除", onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("删除", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.hue(com.jizhang.domain.Hue.Gift))
                }
            }
        }
    }
}

/**
 * [part] of [whole] as a percentage with two decimals, rounded half up ("0.42%", "23.70%").
 * Exact integer maths (no float drift). A share too small to show as 0.01% reads "<0.01%" so a
 * real record never looks like it counts for nothing; a zero [whole] reads "0.00%".
 */
internal fun formatShare(part: Long, whole: Long): String {
    if (whole <= 0 || part <= 0) return "0.00%"
    val pct = BigDecimal(part.coerceAtMost(whole)).multiply(BigDecimal(100))
        .divide(BigDecimal(whole), 2, RoundingMode.HALF_UP)
    return if (pct.signum() == 0) "<0.01%" else "${pct.toPlainString()}%"
}

/**
 * What [record]'s category adds up to in its month, among the same kind (spending or income), or
 * null when that can't be told. [monthRecords] (that month's records) always can; without it
 * only spending categories in the summary's top five can — the rest sit in its merged 其他 slice.
 */
internal fun categoryCentsOf(record: Record, summary: MonthSummary, monthRecords: List<Record>?): Long? {
    if (monthRecords != null) {
        return monthRecords
            .filter { it.kind == record.kind && it.category.key == record.category.key && YearMonth.from(it.date) == summary.month }
            .sumOf { it.amountCents }
    }
    if (record.kind != EntryKind.Expense) return null
    return summary.shares.firstOrNull { it.category?.key == record.category.key }?.cents
}

@Composable
private fun InfoRow(label: String, value: String, dim: Boolean = false) {
    val c = LedgerTheme.colors
    Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = c.sub, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = if (dim) c.sub else c.ink, style = numberStyle(14.sp, FontWeight.Normal, 0f))
    }
}

@Composable
private fun ShareCard(text: String, fraction: Float, modifier: Modifier) {
    val c = LedgerTheme.colors
    val w by animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.layout(), label = "share")
    Column(
        modifier.fillMaxWidth().glass(22.dp).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, style = numberStyle(13.sp, FontWeight.Normal, 0f), color = c.ink)
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.track)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(w.coerceAtLeast(.001f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(c.ring2),
            )
        }
    }
}
