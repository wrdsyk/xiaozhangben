package com.jizhang.domain

import java.time.LocalDate

/**
 * 中国大陆的法定节假日与调休安排：某一天到底上不上班。
 *
 * 数据逐条抄自国务院办公厅的放假通知（2026 年：国办发明电〔2025〕7 号）。通知一般在头一年 11 月
 * 发布，所以每年要维护一次：在 [SPECS] 里补一行，再补上当年的测试。没收录的年份退回“周一至
 * 周五”，不会凭空跳过或补记任何一天。
 *
 * 只做日历，不碰时区：传入的日期已经是本地日期。
 */
object WorkCalendar {

    /**
     * 每年一行：`放假区间; 放假区间 | 调休上班日, 调休上班日`，日期写作 `MM-DD`，区间用 `~`。
     * 照通知的原文顺序抄，核对起来快；周末本来就放假，也一并写进区间，好和通知逐条对照。
     */
    private val SPECS: Map<Int, String> = mapOf(
        // 元旦 1/1；春节 1/28–2/4；清明 4/4–4/6；劳动 5/1–5/5；端午 5/31–6/2；国庆中秋 10/1–10/8。
        2025 to "01-01; 01-28~02-04; 04-04~04-06; 05-01~05-05; 05-31~06-02; 10-01~10-08" +
            " | 01-26, 02-08, 04-27, 09-28, 10-11",
        // 元旦 1/1–1/3；春节 2/15–2/23；清明 4/4–4/6；劳动 5/1–5/5；端午 6/19–6/21；
        // 中秋 9/25–9/27；国庆 10/1–10/7。
        2026 to "01-01~01-03; 02-15~02-23; 04-04~04-06; 05-01~05-05; 06-19~06-21; 09-25~09-27; 10-01~10-07" +
            " | 01-04, 02-14, 02-28, 05-09, 09-20, 10-10",
    )

    private class Year(
        /** 放假的每一天，含本来就放假的周末。 */
        val holidays: Set<LocalDate>,
        /** 调休上班的周末。 */
        val makeups: Set<LocalDate>,
    )

    // 一行抄错（日期不存在、区间倒着写）只让这一年退回“周一至周五”，不让整个定时记账页崩掉；
    // 退回的年份不会出现在 [coveredYears]，页面上的“已收录”也就不会骗人。测试会逐年核对。
    private val table: Map<Int, Year> = SPECS.mapNotNull { (year, spec) ->
        runCatching { parse(year, spec) }.getOrNull()?.let { year to it }
    }.toMap()

    /** 已收录的年份，升序。界面用它提示覆盖到哪一年。 */
    val coveredYears: List<Int> = table.keys.sorted()

    /** 这一天是否在收录范围内；不在范围内时，[isWorkday] 按周一至周五推断。 */
    fun covers(date: LocalDate): Boolean = table.containsKey(date.year)

    /** 法定工作日：周一至周五，去掉法定节假日，加上调休上班的周末。 */
    fun isWorkday(date: LocalDate): Boolean {
        val year = table[date.year] ?: return date.dayOfWeek.value <= 5
        return when {
            date in year.makeups -> true
            date in year.holidays -> false
            else -> date.dayOfWeek.value <= 5
        }
    }

    /** 法定休息日，与 [isWorkday] 互补：不会有一天两边都不算，也不会两边都算。 */
    fun isRestday(date: LocalDate): Boolean = !isWorkday(date)

    private fun parse(year: Int, spec: String): Year {
        val halves = spec.split('|')
        val holidays = halves[0].split(';').filter { it.isNotBlank() }.flatMap { part ->
            val ends = part.split('~')
            datesBetween(date(year, ends.first()), date(year, ends.last()))
        }.toSet()
        val makeups = halves.getOrNull(1).orEmpty()
            .split(',').filter { it.isNotBlank() }.map { date(year, it) }.toSet()
        return Year(holidays, makeups)
    }

    private fun date(year: Int, token: String): LocalDate {
        val parts = token.trim().split('-')
        return LocalDate.of(year, parts[0].toInt(), parts[1].toInt())
    }

    private fun datesBetween(from: LocalDate, to: LocalDate): List<LocalDate> {
        // 倒着写的区间（跨年的假期别写成 12-30~01-02，拆成两年各一行）不能悄悄变成空区间。
        require(!to.isBefore(from)) { "$from ~ $to" }
        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
    }
}
