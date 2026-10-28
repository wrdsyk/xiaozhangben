package com.jizhang.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.MonthDay
import java.time.temporal.ChronoUnit

/** How often a recurring record repeats. [key] is stored in Room and backups; never rename one. */
enum class Repeat(val key: String, val label: String) {
    Daily("daily", "每天"),
    /** 法定工作日：周一至周五，去掉法定节假日，加上调休上班的周末（见 [WorkCalendar]）。 */
    Workdays("workdays", "工作日"),
    /** 法定休息日：周末和法定节假日，去掉调休上班的周末。 */
    Restdays("restdays", "休息日"),
    Weekly("weekly", "每周"),
    Monthly("monthly", "每月"),
    Yearly("yearly", "每年");

    companion object {
        fun of(key: String): Repeat = entries.firstOrNull { it.key == key } ?: Daily
    }
}

/**
 * A record the app writes by itself on every due day ("定时记账").
 *
 * [days] depends on [repeat]: a Monday-first bit set for [Repeat.Weekly] (bit 0 = Monday) and a
 * day-of-month bit set for [Repeat.Monthly] (bit d − 1 = day d, 1–31); it is unused (0) for the
 * rest. A yearly rule lists its dates in [yearDays] instead. Days past the end of a short month
 * fall on its last day, so "31 日" still runs in February and 2 月 29 日 runs on the 28th in
 * other years; several picks that land on the same date still write one record that day.
 *
 * Records are written for due days in (`last`, today]; [last] is the last day already handled.
 */
data class RecurringRule(
    val id: Long,
    val category: Category,
    val amountCents: Long,
    val note: String,
    val repeat: Repeat,
    val days: Int,
    val start: LocalDate,
    val end: LocalDate? = null,
    val last: LocalDate? = null,
    val minuteOfDay: Int = DEFAULT_MINUTE,
    val paused: Boolean = false,
    /** [Repeat.Yearly] dates, each month × 100 + day (308 = 3 月 8 日), sorted and distinct. */
    val yearDays: List<Int> = emptyList(),
) {
    /** The time of day records are written at; an out-of-range [minuteOfDay] (hand-edited backup) is clamped, never thrown on. */
    val time: LocalTime get() = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1).let { LocalTime.of(it / 60, it % 60) }

    fun at(date: LocalDate): LocalDateTime = date.atTime(time)

    /** Whether [date] matches the repeat pattern (ignores start / end / paused). */
    fun matches(date: LocalDate): Boolean = when (repeat) {
        Repeat.Daily -> true
        Repeat.Workdays -> WorkCalendar.isWorkday(date)
        Repeat.Restdays -> WorkCalendar.isRestday(date)
        Repeat.Weekly -> days and (1 shl (date.dayOfWeek.value - 1)) != 0
        Repeat.Monthly -> {
            val d = date.dayOfMonth
            val picked = days and MONTH_MASK
            // The last day of a month also takes every pick past it (31 → 28 in February).
            picked and (1 shl (d - 1)) != 0 || (d == date.lengthOfMonth() && picked ushr d != 0)
        }
        Repeat.Yearly -> yearDays.any { v ->
            date.monthValue == v / 100 && date.dayOfMonth == (v % 100).coerceIn(1, 31).coerceAtMost(date.lengthOfMonth())
        }
    }

    /** Due days in (`after`, `until`], oldest first, at most [limit] of them. */
    fun dueDates(after: LocalDate, until: LocalDate, limit: Int = MAX_CATCH_UP): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var d = maxOf(after.plusDays(1), start)
        val stop = if (end != null && end.isBefore(until)) end else until
        while (!d.isAfter(stop) && out.size < limit) {
            if (matches(d)) out += d
            d = d.plusDays(1)
        }
        return out
    }

    /** Due days not written yet, up to and including [today]. */
    fun pending(today: LocalDate): List<LocalDate> =
        if (paused) emptyList() else dueDates(last ?: start.minusDays(1), today)

    /** The next day this rule will write a record, from [from] on (inclusive); null when it has ended. */
    fun next(from: LocalDate): LocalDate? {
        var d = maxOf(from, start, last?.plusDays(1) ?: start)
        // A yearly rule matches within 366 days; 4 years covers 2 月 29 日 in any year.
        val limit = d.plus(4, ChronoUnit.YEARS)
        while (!d.isAfter(limit)) {
            if (end != null && d.isAfter(end)) return null
            if (matches(d)) return d
            d = d.plusDays(1)
        }
        return null
    }

    /** "每周一、三、五", "每月 1、15、31 日", "每年 3月8日、12月25日", "工作日（按法定节假日和调休）". */
    val description: String
        get() = when (repeat) {
            Repeat.Daily -> "每天"
            Repeat.Workdays -> "工作日（按法定节假日和调休）"
            Repeat.Restdays -> "休息日（周末和法定节假日）"
            Repeat.Weekly -> {
                val picked = DayOfWeek.entries.filter { days and (1 shl (it.value - 1)) != 0 }
                when (picked.size) {
                    0 -> "每周"
                    7 -> "每天"
                    else -> "每周" + picked.joinToString("、") { WEEKDAY_NAMES[it.value - 1] }
                }
            }
            Repeat.Monthly -> {
                val picked = monthlyDays(days)
                when (picked.size) {
                    0 -> "每月"
                    31 -> "每天"
                    else -> "每月 ${picked.joinToString("、")} 日"
                }
            }
            Repeat.Yearly -> {
                val picked = normalizeYearDays(yearDays)
                if (picked.isEmpty()) "每年" else "每年 " + picked.joinToString("、") { yearDayText(it) }
            }
        }

    companion object {
        const val DEFAULT_MINUTE = 9 * 60

        const val MINUTES_PER_DAY = 24 * 60

        /** A rule started long ago writes at most this many records in one go. */
        const val MAX_CATCH_UP = 1_000

        val WEEKDAY_NAMES = listOf("一", "二", "三", "四", "五", "六", "日")

        fun weekdayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

        /** Day-of-month bits of a monthly [days] set (bit d − 1 = day d); 1–31, so it fits a positive Int. */
        private const val MONTH_MASK = 0x7FFFFFFF

        /** The bit for [day] of the month in a monthly [days] set. */
        fun monthlyBit(day: Int): Int = 1 shl (day.coerceIn(1, 31) - 1)

        /** The days of the month in a monthly [days] set, ascending. */
        fun monthlyDays(bits: Int): List<Int> = (1..31).filter { bits and (1 shl (it - 1)) != 0 }

        /** One [yearDays] entry: month × 100 + day. */
        fun yearlyDays(md: MonthDay): Int = md.monthValue * 100 + md.dayOfMonth

        /** "3月8日" for 308. */
        fun yearDayText(value: Int): String = "${value / 100}月${value % 100}日"

        /** Whether [value] is a real calendar date as month × 100 + day (2 月 29 日 counts). */
        fun isYearDay(value: Int): Boolean = value / 100 in 1..12 && value % 100 in 1..Month.of(value / 100).maxLength()

        /** Valid dates only, sorted and without repeats. */
        fun normalizeYearDays(values: Collection<Int>): List<Int> = values.filter(::isYearDay).distinct().sorted()

        /** The stored form of [yearDays]: "308,1225". */
        fun formatYearDays(values: Collection<Int>): String = normalizeYearDays(values).joinToString(",")

        /** Reads what [formatYearDays] wrote; anything that is not a date is dropped. */
        fun parseYearDays(text: String): List<Int> = normalizeYearDays(text.split(',').mapNotNull { it.trim().toIntOrNull() })

        /** Sensible [days] for a new rule of [repeat] starting on [start] (yearly keeps its dates in [yearDays]). */
        fun defaultDays(repeat: Repeat, start: LocalDate): Int = when (repeat) {
            Repeat.Weekly -> weekdayBit(start.dayOfWeek)
            Repeat.Monthly -> monthlyBit(start.dayOfMonth)
            else -> 0
        }

        /** The one date a new yearly rule starting on [start] repeats on. */
        fun defaultYearDays(start: LocalDate): List<Int> = listOf(yearlyDays(MonthDay.from(start)))

        /**
         * Rules saved before DB / backup v5 kept one number in [days]: a day of month for monthly, month × 100 + day
         * for yearly. Returns the ([days], [yearDays]) pair that means the same thing now; the v5 migration does the
         * same in SQL.
         */
        fun upgradeLegacy(repeat: Repeat, days: Int): Pair<Int, List<Int>> = when (repeat) {
            Repeat.Monthly -> (if (days in 1..31) monthlyBit(days) else days) to emptyList()
            Repeat.Yearly -> 0 to normalizeYearDays(listOf(days))
            else -> days to emptyList()
        }
    }
}
