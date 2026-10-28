package com.jizhang.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthSummaryTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val october = YearMonth.of(2026, 10)
    private var nextId = 1L

    private fun r(cat: Category, cents: Long, day: Int, month: Int = 10) =
        Record(nextId++, cat, cents, "", LocalDate.of(2026, month, day).atTime(12, 0))

    @Test fun totalsAndDailySeries() {
        val s = MonthSummary.of(
            october,
            listOf(r(Category.Food, 3200, 1), r(Category.Food, 800, 1), r(Category.Transit, 600, 25), r(Category.Salary, 500000, 10)),
            budgetCents = 450000,
            today = today,
        )
        assertEquals(4600, s.expenseCents)
        assertEquals(500000, s.incomeCents)
        assertEquals(25, s.daily.size)
        assertEquals(4000, s.daily[0])
        assertEquals(600, s.daily[24])
        assertEquals(184, s.avgPerDayCents)
    }

    @Test fun perDayLeftCountsTodayAsRemaining() {
        val s = MonthSummary.of(october, listOf(r(Category.Food, 50000, 3)), budgetCents = 120000, today = today)
        // 70000 left over Oct 25..31 = 7 days
        assertEquals(10000L, s.perDayLeftCents)
    }

    @Test fun noPerDayLeftWhenOverBudgetOrPastMonth() {
        assertNull(MonthSummary.of(october, listOf(r(Category.Food, 500000, 3)), 450000, today).perDayLeftCents)
        val sept = MonthSummary.of(YearMonth.of(2026, 9), listOf(r(Category.Food, 100, 3, month = 9)), 450000, today)
        assertNull(sept.perDayLeftCents)
        assertEquals(30, sept.daily.size)
    }

    @Test fun topFivePlusOther() {
        val cats = listOf(Category.Food, Category.Transit, Category.Shop, Category.Daily, Category.Fun, Category.Home, Category.Med)
        val records = cats.mapIndexed { i, c -> r(c, (7 - i) * 1000L, 2) }
        val shares = MonthSummary.of(october, records, 450000, today).shares
        assertEquals(6, shares.size)
        assertEquals(Category.Food, shares.first().category)
        assertNull(shares.last().category)
        assertEquals(3000, shares.last().cents) // Home 2000 + Med 1000
        assertEquals(1f, shares.sumOf { it.fraction.toDouble() }.toFloat(), 1e-4f)
    }

    @Test fun groupsByDayNewestFirst() {
        val days = MonthSummary.groupByDay(listOf(r(Category.Food, 1, 3), r(Category.Food, 1, 5), r(Category.Food, 1, 3)))
        assertEquals(listOf(5, 3), days.map { it.date.dayOfMonth })
        assertEquals(2, days[1].records.size)
    }
}
