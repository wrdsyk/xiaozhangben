package com.jizhang.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

/** How the stats page slices spending; declared in the switch's left-to-right order. */
enum class Granularity(val label: String) {
    Year("年"),
    Month("月"),
    Week("周");

    /** "月均" for a year, "日均" for a month or a week. */
    val averageLabel: String get() = if (this == Year) "月均" else "日均"

    /** What one chart point stands for: a month in a year, a day in a month or a week. */
    val bucketUnit: String get() = if (this == Year) "月" else "日"
}

private val weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * One selectable stretch of the ledger: a calendar year, a calendar month or an ISO-8601 week.
 * Weeks start on Monday and are numbered the ISO way: week 1 is the one holding the year's first
 * Thursday, so 1 January can fall in week 52/53 of the previous year and 29–31 December in week 1
 * of the next. Build one with [containing]; [start] is always the first day of the stretch.
 */
data class Period(val granularity: Granularity, val start: LocalDate) {
    val endInclusive: LocalDate = when (granularity) {
        Granularity.Year -> start.with(TemporalAdjusters.lastDayOfYear())
        Granularity.Month -> start.with(TemporalAdjusters.lastDayOfMonth())
        Granularity.Week -> start.plusDays(6)
    }

    operator fun contains(date: LocalDate): Boolean = date.within(start, endInclusive)

    /** The ISO week-based year of a [Granularity.Week] period (not always [start]'s calendar year). */
    val weekYear: Int get() = start.get(WeekFields.ISO.weekBasedYear())

    /** The ISO week number (1..53) of a [Granularity.Week] period. */
    val weekNumber: Int get() = start.get(WeekFields.ISO.weekOfWeekBasedYear())

    /** "10月26日–11月1日", with years when the span crosses one. */
    val rangeLabel: String get() = spanLabel(start, endInclusive)

    /**
     * Short name of the period as seen from [today]: "今年", "去年", "2024年" · "本月", "上月", "8月",
     * "2025年8月" · "本周", "上周", "第44周", "2025年第52周" (the year only when the ISO week
     * belongs to another one than today's).
     */
    fun label(today: LocalDate): String = when (granularity) {
        Granularity.Year -> when (start.year) {
            today.year -> "今年"
            today.year - 1 -> "去年"
            else -> "${start.year}年"
        }
        Granularity.Month -> {
            val now = YearMonth.from(today)
            val m = YearMonth.from(start)
            when {
                m == now -> "本月"
                m == now.minusMonths(1) -> "上月"
                m.year == now.year -> "${m.monthValue}月"
                else -> "${m.year}年${m.monthValue}月"
            }
        }
        Granularity.Week -> {
            val now = containing(Granularity.Week, today)
            when {
                this == now -> "本周"
                start == now.start.minusWeeks(1) -> "上周"
                weekYear == now.weekYear -> "第${weekNumber}周"
                else -> "${weekYear}年第${weekNumber}周"
            }
        }
    }

    /** Summary title: "今年支出", "第44周支出". */
    fun title(today: LocalDate): String = label(today) + "支出"

    /**
     * What a relative [label] stands for ("今年" is 2026年, "本周" is 10月26日–11月1日); null when
     * the label already says it.
     */
    fun detail(today: LocalDate): String? = when (granularity) {
        Granularity.Year -> if (start.year >= today.year - 1) "${start.year}年" else null
        Granularity.Month -> {
            val m = YearMonth.from(start)
            val now = YearMonth.from(today)
            if (m == now || m == now.minusMonths(1)) "${m.year}年${m.monthValue}月" else null
        }
        Granularity.Week -> rangeLabel
    }

    companion object {
        /** The [granularity] period that holds [date]. */
        fun containing(granularity: Granularity, date: LocalDate): Period = Period(
            granularity,
            when (granularity) {
                Granularity.Year -> date.withDayOfYear(1)
                Granularity.Month -> date.withDayOfMonth(1)
                Granularity.Week -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            },
        )

        /**
         * The periods worth picking: every one with a record dated up to [today] (income or
         * expense) plus the current one, newest first. Later-dated records pick nothing.
         */
        fun options(records: List<Record>, granularity: Granularity, today: LocalDate): List<Period> =
            (records.asSequence().map { it.date }.filter { !it.isAfter(today) }.map { containing(granularity, it) } +
                containing(granularity, today))
                .distinct()
                .sortedByDescending { it.start }
                .toList()
    }
}

/**
 * One point of the stats trend: a month of a year, a day of a month or of a week. [top] holds the
 * bucket's three largest expenses, largest first.
 */
data class TrendBucket(
    /** "10月" (year view), "10月28日" (month view), "周三" (week view). */
    val label: String,
    val start: LocalDate,
    val endInclusive: LocalDate,
    val expenseCents: Long,
    val top: List<Record>,
)

/**
 * Expense trend of one [period]: a year in months (1–12月), a month in days (1…31日) or a week in
 * days (周一…周日). Nothing after today counts, so the current period's [buckets] stop at the
 * bucket that holds today; [axisLabels] still names every bucket of the period, which lets the
 * chart keep the whole axis while the line is only drawn up to now.
 *
 * The average divides the period's spending by the units elapsed so far: the months of a year up
 * to the current one (月均), the days of a month or a week up to today (日均). A finished period
 * divides by its full length: 12 months, the month's days, 7 days.
 */
data class Trend(
    val period: Period,
    val buckets: List<TrendBucket>,
    val axisLabels: List<String>,
    /** Every expense in the period, largest first. */
    val expenses: List<Record>,
    val incomeCents: Long,
) {
    val granularity: Granularity get() = period.granularity
    val totalCents: Long = expenses.sumOf { it.amountCents }
    val averageCents: Long = if (buckets.isEmpty()) 0 else totalCents / buckets.size
    val averageLabel: String get() = granularity.averageLabel

    /** Top five categories in the period, the rest merged into one "其他" slice. */
    val shares: List<CategoryShare> = CategoryShare.topOf(expenses, totalCents)

    /** Only for a period that has not begun; the picker never offers one. */
    val isEmpty: Boolean get() = buckets.isEmpty()

    /** The [n] largest single expenses in the period. */
    fun topExpenses(n: Int): List<Record> = expenses.take(n)

    /**
     * The period's single expenses behind one donut slice, largest first: the slice's category, or
     * for the merged "其他" slice (no category) everything outside the categories [shares] names.
     * Categories are matched by key, since a real "其他" may sit among the top five too.
     */
    fun expensesOf(share: CategoryShare): List<Record> {
        val key = share.category?.key
        if (key != null) return expenses.filter { it.category.key == key }
        val shown = shares.mapNotNull { it.category?.key }.toSet()
        return expenses.filter { it.category.key !in shown }
    }

    /** The [n] largest single expenses of one slice ([expensesOf]), or of the whole period for null. */
    fun topExpenses(n: Int, share: CategoryShare?): List<Record> =
        if (share == null) topExpenses(n) else expensesOf(share).take(n)

    companion object {
        private val largestFirst =
            compareByDescending<Record> { it.amountCents }.thenByDescending { it.occurredAt }.thenByDescending { it.id }

        fun of(records: List<Record>, period: Period, today: LocalDate): Trend {
            val inScope = records.filter { r -> r.date in period && !r.date.isAfter(today) }
            val expenses = inScope.filter { it.kind == EntryKind.Expense }.sortedWith(largestFirst)
            val slots = slotsOf(period)
            val buckets = slots.filter { !it.start.isAfter(today) }.map { s ->
                val mine = expenses.filter { it.date.within(s.start, s.end) }
                TrendBucket(s.label, s.start, s.end, mine.sumOf { it.amountCents }, mine.take(3))
            }
            return Trend(
                period = period,
                buckets = buckets,
                axisLabels = slots.map { it.axis },
                expenses = expenses,
                incomeCents = inScope.filter { it.kind == EntryKind.Income }.sumOf { it.amountCents },
            )
        }

        private class Slot(val label: String, val axis: String, val start: LocalDate, val end: LocalDate)

        /** Every bucket of the period, future ones included. */
        private fun slotsOf(p: Period): List<Slot> = when (p.granularity) {
            Granularity.Year -> (1..12).map { m ->
                val ym = YearMonth.of(p.start.year, m)
                Slot("${m}月", "${m}月", ym.atDay(1), ym.atEndOfMonth())
            }
            Granularity.Month -> {
                val ym = YearMonth.from(p.start)
                (1..ym.lengthOfMonth()).map { d ->
                    val date = ym.atDay(d)
                    Slot("${ym.monthValue}月${d}日", "${d}日", date, date)
                }
            }
            Granularity.Week -> weekdayNames.mapIndexed { i, name ->
                val date = p.start.plusDays(i.toLong())
                Slot(name, name, date, date)
            }
        }

    }
}

/** "10月6日", "10月6日–12日", "12月29日–1月4日" or, when the years differ, with both years. */
private fun spanLabel(start: LocalDate, end: LocalDate): String = when {
    start == end -> "${start.monthValue}月${start.dayOfMonth}日"
    start.year != end.year ->
        "${start.year}年${start.monthValue}月${start.dayOfMonth}日–${end.year}年${end.monthValue}月${end.dayOfMonth}日"
    start.monthValue != end.monthValue ->
        "${start.monthValue}月${start.dayOfMonth}日–${end.monthValue}月${end.dayOfMonth}日"
    else -> "${start.monthValue}月${start.dayOfMonth}日–${end.dayOfMonth}日"
}

private fun LocalDate.within(start: LocalDate, endInclusive: LocalDate): Boolean =
    !isBefore(start) && !isAfter(endInclusive)
