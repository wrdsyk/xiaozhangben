package com.jizhang.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay

class RecurringTest {
    /** A Tuesday. */
    private val today = LocalDate.of(2026, 10, 27)

    private fun rule(
        repeat: Repeat,
        days: Int = 0,
        start: LocalDate = LocalDate.of(2026, 1, 1),
        end: LocalDate? = null,
        last: LocalDate? = null,
        paused: Boolean = false,
        yearDays: List<Int> = emptyList(),
    ) = RecurringRule(1, Category.Home, 300000, "房租", repeat, days, start, end, last, paused = paused, yearDays = yearDays)

    private val allDays = (1..31).toList().toIntArray()

    /** A monthly rule on [days] of the month. */
    private fun monthly(
        vararg days: Int,
        start: LocalDate = LocalDate.of(2026, 1, 1),
        end: LocalDate? = null,
        last: LocalDate? = null,
    ) = rule(Repeat.Monthly, days.fold(0) { bits, d -> bits or RecurringRule.monthlyBit(d) }, start, end, last)

    /** A yearly rule on [dates] (month × 100 + day). */
    private fun yearly(vararg dates: Int, start: LocalDate = LocalDate.of(2026, 1, 1)) =
        rule(Repeat.Yearly, start = start, yearDays = dates.toList())

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    /** Mon 26 Oct … Sun 1 Nov 2026. */
    private val week = (0L..6L).map { d(2026, 10, 26).plusDays(it) }

    // matches --------------------------------------------------------------------------------

    @Test fun dailyMatchesEveryDay() {
        val r = rule(Repeat.Daily)
        assertTrue(week.all(r::matches))
        assertTrue(r.matches(d(2028, 2, 29)))
    }

    @Test fun workdaysAreMondayToFridayInAnOrdinaryWeek() {
        val r = rule(Repeat.Workdays)
        assertEquals(listOf(true, true, true, true, true, false, false), week.map(r::matches))
    }

    @Test fun workdaysSkipHolidaysAndCountMakeupWeekends() {
        val r = rule(Repeat.Workdays)
        // 2026 国庆：10/1–10/7 放假，10/8（周四）、10/9（周五）上班，10/10（周六）调休上班。
        assertEquals(
            listOf(d(2026, 10, 8), d(2026, 10, 9), d(2026, 10, 10)),
            r.dueDates(d(2026, 9, 30), d(2026, 10, 11)),
        )
        // 2026 春节：2/14（周六）调休上班，2/15–2/23 放假，2/24（周二）恢复。
        assertEquals(
            listOf(d(2026, 2, 13), d(2026, 2, 14), d(2026, 2, 24)),
            r.dueDates(d(2026, 2, 12), d(2026, 2, 24)),
        )
    }

    @Test fun workdaysUseThePreviousYearsArrangement() {
        // 2025 国庆中秋 10/1–10/8；9/28（周日）、10/11（周六）调休上班。
        assertEquals(
            listOf(d(2025, 9, 28), d(2025, 9, 29), d(2025, 9, 30), d(2025, 10, 9), d(2025, 10, 10), d(2025, 10, 11)),
            rule(Repeat.Workdays, start = d(2025, 1, 1)).dueDates(d(2025, 9, 27), d(2025, 10, 11)),
        )
    }

    @Test fun everyMakeupWeekendIsAWorkday() {
        // 抄错一个调休日就会在这里断掉。
        listOf(
            d(2026, 1, 4), d(2026, 2, 14), d(2026, 2, 28), d(2026, 5, 9), d(2026, 9, 20), d(2026, 10, 10),
            d(2025, 1, 26), d(2025, 2, 8), d(2025, 4, 27), d(2025, 9, 28), d(2025, 10, 11),
        ).forEach { assertTrue("$it 调休上班", WorkCalendar.isWorkday(it)) }
    }

    @Test fun holidayWeekdaysAreNotWorkdays() {
        listOf(
            d(2026, 1, 1), d(2026, 1, 2), d(2026, 2, 16), d(2026, 5, 1), d(2026, 6, 19),
            d(2026, 9, 25), d(2026, 10, 1), d(2026, 10, 7),
            d(2025, 1, 28), d(2025, 5, 5), d(2025, 10, 8),
        ).forEach { assertFalse("$it 放假", WorkCalendar.isWorkday(it)) }
    }

    @Test fun restdaysTakeHolidaysAndSkipMakeupWeekends() {
        val r = rule(Repeat.Restdays)
        assertTrue(r.matches(d(2026, 10, 1)))   // 国庆，周四
        assertTrue(r.matches(d(2026, 10, 3)))   // 国庆里的周六
        assertFalse(r.matches(d(2026, 10, 10))) // 调休上班的周六
        assertTrue(r.matches(d(2026, 10, 11)))  // 普通周日
    }

    @Test fun restdayAndWorkdayAreExactComplements() {
        val work = rule(Repeat.Workdays)
        val rest = rule(Repeat.Restdays)
        var d = LocalDate.of(2026, 1, 1)
        while (d.year == 2026) {
            assertTrue("$d 只能是工作日或休息日之一", work.matches(d) != rest.matches(d))
            d = d.plusDays(1)
        }
    }

    @Test fun yearsWithoutAnArrangementFallBackToMondayToFriday() {
        // 收录之外的年份：退回周一至周五，不凭空跳过或补记。
        assertFalse(WorkCalendar.covers(d(2035, 1, 2)))
        assertTrue(rule(Repeat.Workdays).matches(d(2035, 1, 2)))   // 周二
        assertFalse(rule(Repeat.Workdays).matches(d(2035, 1, 6)))  // 周六
        assertTrue(rule(Repeat.Restdays).matches(d(2035, 1, 6)))
    }

    @Test fun restdaysAreTheWeekend() {
        val r = rule(Repeat.Restdays)
        assertEquals(listOf(false, false, false, false, false, true, true), week.map(r::matches))
    }

    @Test fun weeklyFollowsTheMondayFirstBits() {
        val monWedFri = RecurringRule.weekdayBit(DayOfWeek.MONDAY) or
            RecurringRule.weekdayBit(DayOfWeek.WEDNESDAY) or RecurringRule.weekdayBit(DayOfWeek.FRIDAY)
        assertEquals(0b10101, monWedFri)
        val r = rule(Repeat.Weekly, monWedFri)
        assertEquals(listOf(true, false, true, false, true, false, false), week.map(r::matches))
        assertEquals(listOf(false, false, false, false, false, false, true), week.map(rule(Repeat.Weekly, 64)::matches))
        assertFalse(week.any(rule(Repeat.Weekly, 0)::matches))
    }

    @Test fun monthlyMatchesItsDay() {
        val r = monthly(15)
        assertTrue(r.matches(d(2026, 10, 15)))
        assertTrue(r.matches(d(2026, 2, 15)))
        assertFalse(r.matches(d(2026, 10, 14)))
        assertFalse(r.matches(d(2026, 10, 16)))
    }

    @Test fun monthlyBitsAreDayMinusOne() {
        assertEquals(1, RecurringRule.monthlyBit(1))
        assertEquals(1 shl 14, RecurringRule.monthlyBit(15))
        assertEquals(1 shl 30, RecurringRule.monthlyBit(31))
        assertTrue(RecurringRule.monthlyBit(31) > 0) // fits a positive Int
        assertEquals(listOf(1, 15, 31), RecurringRule.monthlyDays(monthly(31, 1, 15).days))
        assertEquals(emptyList<Int>(), RecurringRule.monthlyDays(0))
        assertEquals((1..31).toList(), RecurringRule.monthlyDays(0x7FFFFFFF))
    }

    @Test fun monthlyMatchesSeveralDays() {
        val r = monthly(1, 15, 31)
        val hits = r.dueDates(d(2026, 9, 30), d(2026, 10, 31))
        assertEquals(listOf(d(2026, 10, 1), d(2026, 10, 15), d(2026, 10, 31)), hits)
        assertEquals(
            listOf(d(2026, 2, 1), d(2026, 2, 15), d(2026, 2, 28)),
            r.dueDates(d(2026, 1, 31), d(2026, 2, 28)),
        )
        assertEquals(
            listOf(d(2028, 2, 1), d(2028, 2, 15), d(2028, 2, 29)),
            r.dueDates(d(2028, 1, 31), d(2028, 2, 29)),
        )
        assertEquals(
            listOf(d(2026, 4, 1), d(2026, 4, 15), d(2026, 4, 30)),
            r.dueDates(d(2026, 3, 31), d(2026, 4, 30)),
        )
    }

    @Test fun monthlyDaysPastTheEndFallOnTheLastDay() {
        val r31 = monthly(31)
        assertTrue(r31.matches(d(2026, 1, 31)))
        assertFalse(r31.matches(d(2026, 1, 30)))
        assertTrue(r31.matches(d(2026, 2, 28))) // non-leap February
        assertTrue(r31.matches(d(2028, 2, 29))) // leap February
        assertFalse(r31.matches(d(2028, 2, 28)))
        assertTrue(r31.matches(d(2026, 4, 30)))
        assertFalse(r31.matches(d(2026, 4, 29)))

        val r30 = monthly(30)
        assertTrue(r30.matches(d(2026, 2, 28)))
        assertTrue(r30.matches(d(2026, 3, 30)))
        assertFalse(r30.matches(d(2026, 3, 31)))

        val r29 = monthly(29)
        assertTrue(r29.matches(d(2026, 2, 28)))
        assertTrue(r29.matches(d(2028, 2, 29)))
        assertFalse(r29.matches(d(2028, 2, 28)))
    }

    @Test fun monthlyPicksThatClampToTheSameDateWriteOneRecord() {
        // 29, 30 and 31 all land on 28 February 2026 - one record, not three.
        val r = monthly(28, 29, 30, 31)
        assertEquals(listOf(d(2026, 2, 28)), r.dueDates(d(2026, 1, 31), d(2026, 2, 28)))
        // 30 and 31 both land on 30 April.
        val late = monthly(30, 31)
        assertEquals(listOf(d(2026, 4, 30)), late.dueDates(d(2026, 4, 1), d(2026, 4, 30)))
        // ... but stay two separate days in a 31-day month.
        assertEquals(listOf(d(2026, 5, 30), d(2026, 5, 31)), late.dueDates(d(2026, 5, 1), d(2026, 5, 31)))
        // A leap February: 29 is real, 30 and 31 fall on it too.
        assertEquals(listOf(d(2028, 2, 28), d(2028, 2, 29)), r.dueDates(d(2028, 2, 27), d(2028, 2, 29)))
    }

    @Test fun monthlyRunsExactlyOncePerMonth() {
        listOf(1, 15, 28, 29, 30, 31).forEach { day ->
            val r = monthly(day, start = d(2026, 1, 1))
            val hits = r.dueDates(d(2025, 12, 31), d(2028, 12, 31))
            assertEquals("day $day", 36, hits.size)
            assertEquals("day $day", 36, hits.map { it.withDayOfMonth(1) }.distinct().size)
        }
    }

    @Test fun monthlyWithEveryDayRunsDaily() {
        val r = monthly(*allDays, start = d(2026, 1, 1))
        val hits = r.dueDates(d(2025, 12, 31), d(2026, 12, 31))
        assertEquals(365, hits.size)
        assertEquals(hits.distinct(), hits)
    }

    @Test fun monthlyWithTwoDaysRunsTwicePerMonth() {
        val r = monthly(10, 25, start = d(2026, 1, 1))
        val hits = r.dueDates(d(2025, 12, 31), d(2026, 12, 31))
        assertEquals(24, hits.size)
        assertEquals(12, hits.count { it.dayOfMonth == 10 })
        assertEquals(12, hits.count { it.dayOfMonth == 25 })
    }

    @Test fun yearlyMatchesMonthAndDay() {
        val r = yearly(RecurringRule.yearlyDays(MonthDay.of(3, 8)))
        assertEquals(listOf(308), r.yearDays)
        assertTrue(r.matches(d(2026, 3, 8)))
        assertTrue(r.matches(d(2031, 3, 8)))
        assertFalse(r.matches(d(2026, 4, 8)))
        assertFalse(r.matches(d(2026, 3, 9)))
    }

    @Test fun yearlyMatchesSeveralDates() {
        val r = yearly(308, 1225)
        val hits = r.dueDates(d(2025, 12, 31), d(2027, 12, 31))
        assertEquals(listOf(d(2026, 3, 8), d(2026, 12, 25), d(2027, 3, 8), d(2027, 12, 25)), hits)
        assertFalse(r.matches(d(2026, 3, 25)))
        assertFalse(r.matches(d(2026, 12, 8)))
    }

    @Test fun yearlyFeb29FallsOnFeb28InOtherYears() {
        val r = yearly(229)
        assertTrue(r.matches(d(2028, 2, 29)))
        assertFalse(r.matches(d(2028, 2, 28)))
        assertTrue(r.matches(d(2026, 2, 28)))
        assertFalse(r.matches(d(2026, 3, 1)))
        val hits = r.dueDates(d(2025, 12, 31), d(2029, 12, 31))
        assertEquals(listOf(d(2026, 2, 28), d(2027, 2, 28), d(2028, 2, 29), d(2029, 2, 28)), hits)
    }

    @Test fun yearlyFeb28AndFeb29WriteOneRecordInOtherYears() {
        val r = yearly(228, 229)
        val hits = r.dueDates(d(2025, 12, 31), d(2029, 12, 31))
        // 28 Feb 2026, 2027, 2029 once each (29 clamps onto 28); both days exist in the leap year 2028.
        assertEquals(listOf(d(2026, 2, 28), d(2027, 2, 28), d(2028, 2, 28), d(2028, 2, 29), d(2029, 2, 28)), hits)
    }

    @Test fun yearlyWithoutDatesNeverRuns() {
        val r = rule(Repeat.Yearly)
        assertEquals(emptyList<LocalDate>(), r.dueDates(d(2025, 12, 31), d(2027, 12, 31)))
        assertNull(r.next(today))
    }

    // dueDates -------------------------------------------------------------------------------

    @Test fun dueDatesExcludeAfterAndIncludeUntil() {
        val r = rule(Repeat.Daily, start = d(2026, 10, 1))
        assertEquals(listOf(d(2026, 10, 6), d(2026, 10, 7), d(2026, 10, 8)), r.dueDates(d(2026, 10, 5), d(2026, 10, 8)))
        assertEquals(emptyList<LocalDate>(), r.dueDates(d(2026, 10, 8), d(2026, 10, 8)))
        assertEquals(emptyList<LocalDate>(), r.dueDates(d(2026, 10, 9), d(2026, 10, 8)))
    }

    @Test fun dueDatesRespectStartAndEnd() {
        val r = rule(Repeat.Daily, start = d(2026, 10, 1), end = d(2026, 10, 10))
        val all = r.dueDates(d(2026, 9, 1), d(2026, 10, 31))
        assertEquals(d(2026, 10, 1), all.first())
        assertEquals(d(2026, 10, 10), all.last()) // end is inclusive
        assertEquals(10, all.size)
        assertEquals(emptyList<LocalDate>(), r.dueDates(d(2026, 10, 10), d(2026, 10, 31)))
    }

    @Test fun dueDatesOnlyHoldMatchingDays() {
        val r = rule(Repeat.Workdays, start = d(2026, 10, 1))
        // 26 Oct – 1 Nov: Monday to Friday.
        assertEquals(week.take(5), r.dueDates(d(2026, 10, 25), d(2026, 11, 1)))
    }

    @Test fun dueDatesStopAtTheLimit() {
        val r = rule(Repeat.Daily, start = d(2026, 1, 1))
        assertEquals(listOf(d(2026, 1, 1), d(2026, 1, 2), d(2026, 1, 3)), r.dueDates(d(2025, 1, 1), today, limit = 3))
    }

    // pending --------------------------------------------------------------------------------

    @Test fun pendingStartsAtStartWhenNeverRun() {
        val r = rule(Repeat.Daily, start = d(2026, 10, 25))
        assertEquals(listOf(d(2026, 10, 25), d(2026, 10, 26), today), r.pending(today))
    }

    @Test fun pendingContinuesAfterLast() {
        val r = rule(Repeat.Daily, start = d(2026, 10, 1), last = d(2026, 10, 25))
        assertEquals(listOf(d(2026, 10, 26), today), r.pending(today))
        assertEquals(emptyList<LocalDate>(), r.copy(last = today).pending(today))
    }

    @Test fun pendingIsEmptyWhenPausedOrNotStarted() {
        assertEquals(emptyList<LocalDate>(), rule(Repeat.Daily, start = d(2026, 10, 1), paused = true).pending(today))
        assertEquals(emptyList<LocalDate>(), rule(Repeat.Daily, start = today.plusDays(1)).pending(today))
    }

    @Test fun pendingSkipsDaysAfterEnd() {
        val r = rule(Repeat.Daily, start = d(2026, 10, 1), end = d(2026, 10, 3))
        assertEquals(listOf(d(2026, 10, 1), d(2026, 10, 2), d(2026, 10, 3)), r.pending(today))
    }

    @Test fun pendingBackFillsEverySelectedMonthlyDay() {
        val r = monthly(1, 15, 31, start = d(2026, 8, 20))
        assertEquals(listOf(d(2026, 8, 31), d(2026, 9, 1), d(2026, 9, 15), d(2026, 9, 30), d(2026, 10, 1), d(2026, 10, 15)), r.pending(today))
        // Nothing is repeated once the days are handled.
        assertEquals(emptyList<LocalDate>(), r.copy(last = today).pending(today))
        assertEquals(listOf(d(2026, 10, 31)), r.copy(last = today).dueDates(today, d(2026, 10, 31)))
    }

    @Test fun catchUpIsCapped() {
        val r = rule(Repeat.Daily, start = d(2020, 1, 1))
        val due = r.pending(today)
        assertEquals(RecurringRule.MAX_CATCH_UP, due.size)
        assertEquals(d(2020, 1, 1), due.first())
        assertEquals(d(2020, 1, 1).plusDays(RecurringRule.MAX_CATCH_UP - 1L), due.last())
        // The next batch picks up where the capped one stopped.
        assertEquals(due.last().plusDays(1), r.copy(last = due.last()).pending(today).first())
    }

    // next -----------------------------------------------------------------------------------

    @Test fun nextIsInclusiveOfFrom() {
        assertEquals(today, rule(Repeat.Daily).next(today))
        assertEquals(d(2026, 10, 28), rule(Repeat.Weekly, 0b100).next(today)) // Wednesday
        assertEquals(d(2026, 11, 2), rule(Repeat.Weekly, 0b1).next(today)) // Monday
        assertEquals(d(2026, 10, 30), rule(Repeat.Workdays).next(d(2026, 10, 30)))
        assertEquals(d(2026, 11, 2), rule(Repeat.Workdays).next(d(2026, 10, 31)))
    }

    @Test fun nextSkipsDaysAlreadyHandled() {
        assertEquals(d(2026, 10, 28), rule(Repeat.Daily, last = today).next(today))
        assertEquals(d(2026, 11, 15), monthly(15, last = d(2026, 10, 15)).next(d(2026, 10, 1)))
        // Several days: the next one after the last handled.
        assertEquals(d(2026, 10, 31), monthly(1, 15, 31, last = d(2026, 10, 15)).next(d(2026, 10, 1)))
        assertEquals(d(2026, 11, 1), monthly(1, 15, 31, last = d(2026, 10, 31)).next(d(2026, 10, 1)))
    }

    @Test fun nextWaitsForStart() {
        assertEquals(d(2027, 1, 1), rule(Repeat.Daily, start = d(2027, 1, 1)).next(today))
        assertEquals(d(2027, 1, 15), monthly(15, start = d(2027, 1, 1)).next(today))
        assertEquals(d(2027, 3, 8), yearly(308, 1225, start = d(2027, 1, 1)).next(today))
    }

    @Test fun nextClampsShortMonthsAndLeapDays() {
        assertEquals(d(2026, 2, 28), monthly(31).next(d(2026, 2, 1)))
        assertEquals(d(2026, 4, 30), monthly(31).next(d(2026, 4, 1)))
        assertEquals(d(2026, 2, 28), monthly(30, 31).next(d(2026, 2, 1)))
        assertEquals(d(2027, 2, 28), yearly(229).next(d(2026, 3, 1)))
        assertEquals(d(2028, 2, 29), yearly(229).next(d(2027, 3, 1)))
    }

    @Test fun nextPicksTheEarliestYearlyDate() {
        val r = yearly(308, 1225)
        assertEquals(d(2026, 12, 25), r.next(today))
        assertEquals(d(2027, 3, 8), r.next(d(2026, 12, 26)))
        assertEquals(d(2027, 3, 8), r.copy(last = d(2026, 12, 25)).next(today))
    }

    @Test fun nextIsNullOnceEnded() {
        assertNull(rule(Repeat.Daily, end = d(2026, 10, 26)).next(today))
        assertEquals(today, rule(Repeat.Daily, end = today).next(today)) // end is inclusive
        assertNull(rule(Repeat.Daily, end = today, last = today).next(today))
        // The only matching day before the end has passed.
        assertNull(monthly(15, end = d(2026, 11, 14)).next(today))
        assertEquals(d(2026, 11, 15), monthly(15, end = d(2026, 11, 15)).next(today))
        assertNull(rule(Repeat.Weekly, 0).next(today))
    }

    // Text, defaults, keys -------------------------------------------------------------------

    @Test fun descriptions() {
        assertEquals("每天", rule(Repeat.Daily).description)
        assertEquals("工作日（按法定节假日和调休）", rule(Repeat.Workdays).description)
        assertEquals("休息日（周末和法定节假日）", rule(Repeat.Restdays).description)
        assertEquals("每周一、三、五", rule(Repeat.Weekly, 0b10101).description)
        assertEquals("每周日", rule(Repeat.Weekly, 64).description)
        assertEquals("每天", rule(Repeat.Weekly, 127).description)
        assertEquals("每周", rule(Repeat.Weekly, 0).description)
        assertEquals("每月 15 日", monthly(15).description)
        assertEquals("每月 31 日", monthly(31).description)
        assertEquals("每月 1、15、31 日", monthly(31, 1, 15).description)
        assertEquals("每月 5、10、25 日", monthly(25, 5, 10).description)
        assertEquals("每天", monthly(*allDays).description)
        assertEquals("每月", rule(Repeat.Monthly, 0).description)
        assertEquals("每年 3月8日", yearly(308).description)
        assertEquals("每年 12月25日", yearly(1225).description)
        assertEquals("每年 3月8日、12月25日", yearly(1225, 308).description)
        assertEquals("每年 2月29日", yearly(229).description)
        assertEquals("每年", rule(Repeat.Yearly).description)
    }

    @Test fun defaultDaysFollowTheStart() {
        assertEquals(RecurringRule.weekdayBit(DayOfWeek.TUESDAY), RecurringRule.defaultDays(Repeat.Weekly, today))
        assertEquals(RecurringRule.monthlyBit(27), RecurringRule.defaultDays(Repeat.Monthly, today))
        assertEquals(listOf(27), RecurringRule.monthlyDays(RecurringRule.defaultDays(Repeat.Monthly, today)))
        assertEquals(0, RecurringRule.defaultDays(Repeat.Yearly, today)) // yearly keeps its dates in yearDays
        assertEquals(listOf(1027), RecurringRule.defaultYearDays(today))
        assertEquals(listOf(229), RecurringRule.defaultYearDays(d(2028, 2, 29)))
        assertEquals(0, RecurringRule.defaultDays(Repeat.Daily, today))
        assertEquals(0, RecurringRule.defaultDays(Repeat.Workdays, today))
        assertEquals(1, RecurringRule.weekdayBit(DayOfWeek.MONDAY))
        assertEquals(64, RecurringRule.weekdayBit(DayOfWeek.SUNDAY))
        assertEquals(229, RecurringRule.yearlyDays(MonthDay.of(2, 29)))
    }

    // Year date helpers & the pre-v5 upgrade ---------------------------------------------------

    @Test fun yearDaysAreStoredSortedAndDistinct() {
        assertEquals("308,1225", RecurringRule.formatYearDays(listOf(1225, 308, 1225)))
        assertEquals("", RecurringRule.formatYearDays(emptyList()))
        assertEquals(listOf(308, 1225), RecurringRule.parseYearDays("1225, 308,308"))
        assertEquals(emptyList<Int>(), RecurringRule.parseYearDays(""))
        // Not dates: month 0 / 13, day 0 / 32, 30 February, text.
        assertEquals(listOf(229), RecurringRule.parseYearDays("0,13,100,132,230,abc,229,1301"))
        assertEquals("3月8日", RecurringRule.yearDayText(308))
        assertEquals("12月25日", RecurringRule.yearDayText(1225))
        assertTrue(RecurringRule.isYearDay(229))
        assertFalse(RecurringRule.isYearDay(230))
        assertFalse(RecurringRule.isYearDay(431))
    }

    @Test fun legacyRulesUpgradeToTheNewEncodings() {
        // monthly: the day number becomes a bit.
        assertEquals(RecurringRule.monthlyBit(15) to emptyList<Int>(), RecurringRule.upgradeLegacy(Repeat.Monthly, 15))
        assertEquals((1 shl 30) to emptyList<Int>(), RecurringRule.upgradeLegacy(Repeat.Monthly, 31))
        assertEquals(1 to emptyList<Int>(), RecurringRule.upgradeLegacy(Repeat.Monthly, 1))
        // yearly: month × 100 + day moves to yearDays and days is unused.
        assertEquals(0 to listOf(308), RecurringRule.upgradeLegacy(Repeat.Yearly, 308))
        assertEquals(0 to listOf(229), RecurringRule.upgradeLegacy(Repeat.Yearly, 229))
        // everything else keeps its days.
        assertEquals(0b10101 to emptyList<Int>(), RecurringRule.upgradeLegacy(Repeat.Weekly, 0b10101))
        assertEquals(0 to emptyList<Int>(), RecurringRule.upgradeLegacy(Repeat.Daily, 0))
    }

    @Test fun anUpgradedLegacyRuleRunsOnTheSameDays() {
        val (days, dates) = RecurringRule.upgradeLegacy(Repeat.Monthly, 31)
        val monthlyRule = rule(Repeat.Monthly, days, yearDays = dates)
        assertEquals(
            listOf(d(2026, 1, 31), d(2026, 2, 28), d(2026, 3, 31), d(2026, 4, 30)),
            monthlyRule.dueDates(d(2025, 12, 31), d(2026, 4, 30)),
        )
        val (days2, dates2) = RecurringRule.upgradeLegacy(Repeat.Yearly, 308)
        val yearlyRule = rule(Repeat.Yearly, days2, yearDays = dates2)
        assertEquals(listOf(d(2026, 3, 8), d(2027, 3, 8)), yearlyRule.dueDates(d(2025, 12, 31), d(2027, 12, 31)))
    }

    @Test fun recordsAreWrittenAtNineByDefault() {
        val r = rule(Repeat.Daily)
        assertEquals(LocalTime.of(9, 0), r.time)
        assertEquals(today.atTime(9, 0), r.at(today))
        assertEquals(today.atTime(18, 30), r.copy(minuteOfDay = 18 * 60 + 30).at(today))
    }

    @Test fun anOutOfRangeTimeIsClampedNotThrownOn() {
        val r = rule(Repeat.Daily)
        assertEquals(LocalTime.of(0, 0), r.copy(minuteOfDay = -5).time)
        assertEquals(LocalTime.of(23, 59), r.copy(minuteOfDay = 24 * 60).time)
        assertEquals(LocalTime.of(23, 59), r.copy(minuteOfDay = Int.MAX_VALUE).time)
        assertEquals(today.atTime(23, 59), r.copy(minuteOfDay = 99_999).at(today))
    }

    @Test fun repeatKeysRoundTrip() {
        Repeat.entries.forEach { assertEquals(it, Repeat.of(it.key)) }
        assertEquals(Repeat.Daily, Repeat.of("nope"))
        assertEquals(listOf("每天", "工作日", "休息日", "每周", "每月", "每年"), Repeat.entries.map { it.label })
    }
}
