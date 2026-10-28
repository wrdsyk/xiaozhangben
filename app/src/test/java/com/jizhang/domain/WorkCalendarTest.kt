package com.jizhang.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** 法定节假日表的整体核对：一整年的日历逐天过一遍，抄错一天就会在这里对不上。 */
class WorkCalendarTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private fun days(year: Int) = generateSequence(d(year, 1, 1)) { it.plusDays(1) }.takeWhile { it.year == year }.toList()

    private fun range(from: LocalDate, to: LocalDate) = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()

    private val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    /** 国办通知里的放假区间（含周末）。 */
    private val holidays2025 =
        listOf(d(2025, 1, 1)) + range(d(2025, 1, 28), d(2025, 2, 4)) + range(d(2025, 4, 4), d(2025, 4, 6)) +
            range(d(2025, 5, 1), d(2025, 5, 5)) + range(d(2025, 5, 31), d(2025, 6, 2)) + range(d(2025, 10, 1), d(2025, 10, 8))
    private val makeups2025 = listOf(d(2025, 1, 26), d(2025, 2, 8), d(2025, 4, 27), d(2025, 9, 28), d(2025, 10, 11))

    private val holidays2026 =
        range(d(2026, 1, 1), d(2026, 1, 3)) + range(d(2026, 2, 15), d(2026, 2, 23)) + range(d(2026, 4, 4), d(2026, 4, 6)) +
            range(d(2026, 5, 1), d(2026, 5, 5)) + range(d(2026, 6, 19), d(2026, 6, 21)) + range(d(2026, 9, 25), d(2026, 9, 27)) +
            range(d(2026, 10, 1), d(2026, 10, 7))
    private val makeups2026 = listOf(d(2026, 1, 4), d(2026, 2, 14), d(2026, 2, 28), d(2026, 5, 9), d(2026, 9, 20), d(2026, 10, 10))

    @Test fun coversExactlyTheYearsThatAreTyped() {
        // 一行解析失败会让这一年悄悄退回周一至周五（不崩），所以要在这里点名。
        assertEquals(listOf(2025, 2026), WorkCalendar.coveredYears)
        assertTrue(WorkCalendar.covers(d(2025, 6, 1)))
        assertTrue(WorkCalendar.covers(d(2026, 12, 31)))
        assertFalse(WorkCalendar.covers(d(2024, 12, 31)))
        assertFalse(WorkCalendar.covers(d(2027, 1, 1)))
    }

    @Test fun everyDayOfTheNoticeIsARestDay() {
        (holidays2025 + holidays2026).forEach { assertFalse("$it 放假", WorkCalendar.isWorkday(it)) }
    }

    @Test fun makeupDaysAreWeekendsOutsideAnyHoliday() {
        (makeups2025 + makeups2026).forEach {
            assertTrue("$it 是周末", it.dayOfWeek in weekend)
            assertFalse("$it 不在放假区间里", it in holidays2025 || it in holidays2026)
            assertTrue("$it 调休上班", WorkCalendar.isWorkday(it))
        }
    }

    @Test fun onlyTheListedWeekendsAreWorkdays() {
        // 周末里的工作日必须恰好是通知里的调休日，不多不少。
        listOf(2025 to makeups2025, 2026 to makeups2026).forEach { (year, makeups) ->
            assertEquals("$year", makeups, days(year).filter { it.dayOfWeek in weekend && WorkCalendar.isWorkday(it) })
        }
    }

    @Test fun onlyTheListedWeekdaysAreRestDays() {
        // 周一至周五里的休息日必须恰好是通知放假区间里落在工作日的那些天。
        listOf(2025 to holidays2025, 2026 to holidays2026).forEach { (year, holidays) ->
            val expected = holidays.filter { it.dayOfWeek !in weekend }
            assertEquals("$year", expected, days(year).filter { it.dayOfWeek !in weekend && !WorkCalendar.isWorkday(it) })
        }
    }

    @Test fun eachYearHasTheNoticesTotals() {
        // 2025 / 2026 都是 13 天法定假日、调休与放假工作日互相抵消：全年 248 个工作日。
        assertEquals(18, holidays2025.count { it.dayOfWeek !in weekend })
        assertEquals(19, holidays2026.count { it.dayOfWeek !in weekend })
        listOf(2025, 2026).forEach { year ->
            assertEquals("$year", 248, days(year).count(WorkCalendar::isWorkday))
            assertEquals("$year", 365 - 248, days(year).count(WorkCalendar::isRestday))
        }
    }

    @Test fun otherYearsAreMondayToFriday() {
        // 没收录的年份不凭空跳过或补记任何一天：周一至周五上班，周末休息。
        listOf(2024, 2027, 2035).forEach { year ->
            days(year).forEach { assertEquals("$it", it.dayOfWeek !in weekend, WorkCalendar.isWorkday(it)) }
        }
        // 例：2027-01-01 是周五，没有 2027 年的通知前只能按上班算。
        assertTrue(WorkCalendar.isWorkday(d(2027, 1, 1)))
    }

    @Test fun workdayAndRestdayNeverOverlap() {
        (days(2024) + days(2025) + days(2026) + days(2027)).forEach {
            assertTrue("$it", WorkCalendar.isWorkday(it) != WorkCalendar.isRestday(it))
        }
    }
}
