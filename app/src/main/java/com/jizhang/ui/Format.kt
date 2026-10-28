package com.jizhang.ui

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

private val weekdays = mapOf(
    DayOfWeek.MONDAY to "周一", DayOfWeek.TUESDAY to "周二", DayOfWeek.WEDNESDAY to "周三",
    DayOfWeek.THURSDAY to "周四", DayOfWeek.FRIDAY to "周五", DayOfWeek.SATURDAY to "周六",
    DayOfWeek.SUNDAY to "周日",
)

fun monthLabel(m: YearMonth) = "${m.year}年${m.monthValue}月"

/** "10月25日 周日" */
fun dayLabel(d: LocalDate) = "${d.monthValue}月${d.dayOfMonth}日 ${weekdays.getValue(d.dayOfWeek)}"

/** "今天" / "昨天" / null for other days. */
fun relativeDay(d: LocalDate, today: LocalDate): String? = when (d) {
    today -> "今天"
    today.minusDays(1) -> "昨天"
    else -> null
}

/** Short date for the keypad's date chip: "今天", "昨天", "10月24日" or "2025年3月2日". */
fun chipDate(d: LocalDate, today: LocalDate): String = relativeDay(d, today) ?: if (d.year == today.year) {
    "${d.monthValue}月${d.dayOfMonth}日"
} else {
    "${d.year}年${d.monthValue}月${d.dayOfMonth}日"
}

fun hhmm(t: LocalDateTime) = "%02d:%02d".format(t.hour, t.minute)

/** "2026年10月25日 19:10" */
fun fullDateTime(t: LocalDateTime) = "${t.year}年${t.monthValue}月${t.dayOfMonth}日 ${hhmm(t)}"

/** 0.3333 → "33.3%": one decimal, rounded half up, so a donut's labels read alike. */
fun percentLabel(fraction: Float): String = String.format(java.util.Locale.ROOT, "%.1f%%", fraction * 100)
