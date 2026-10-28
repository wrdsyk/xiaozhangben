package com.jizhang.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.ui.monthLabel
import com.jizhang.ui.theme.LedgerIcons
import com.jizhang.ui.theme.LedgerTheme
import java.time.YearMonth

/** "2026年10月 ▾" glass pill; opens a list of months that have records. */
@Composable
fun MonthPill(
    month: YearMonth,
    months: List<YearMonth>,
    onSelect: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    fontSize: TextUnit = 15.sp,
) {
    val c = LedgerTheme.colors
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .height(height)
                .glass(height / 2)
                .tap(label = "选择月份") { open = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(monthLabel(month), fontSize = fontSize, fontWeight = FontWeight.Medium, color = c.ink)
            Glyph(LedgerIcons.get("chev"), 16.dp, c.ink)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = if (c.isDark) c.bg1 else c.bg0,
            modifier = Modifier.heightIn(max = 360.dp),
        ) {
            months.forEach { m ->
                DropdownMenuItem(
                    text = {
                        Text(
                            monthLabel(m),
                            color = c.ink,
                            fontWeight = if (m == month) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    trailingIcon = if (m == month) {
                        { Glyph(LedgerIcons.get("check", 2.4f), 18.dp, c.ink) }
                    } else null,
                    onClick = {
                        open = false
                        onSelect(m)
                    },
                )
            }
        }
    }
}
