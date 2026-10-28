package com.jizhang.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetTest {
    private val today = LocalDate.of(2026, 10, 27)
    private var nextId = 1L

    private fun r(cat: Category, cents: Long, date: LocalDate) = Record(nextId++, cat, cents, "", date.atTime(12, 0))

    private val records = listOf(
        r(Category.Food, 3000, today),
        r(Category.Food, 2000, today.minusDays(1)),
        r(Category.Transit, 500, today),
        r(Category.Food, 10000, LocalDate.of(2026, 3, 1)),
        r(Category.Food, 99999, LocalDate.of(2025, 12, 31)),
        r(Category.Salary, 500000, today),
    )

    @Test fun periodsAndScopes() {
        val budgets = listOf(
            Budget(1, "", BudgetPeriod.Day, Category.Food, 8000),
            Budget(2, "", BudgetPeriod.Month, null, 100000),
            Budget(3, "", BudgetPeriod.Year, Category.Food, 10000),
        )
        val p = BudgetProgress.of(budgets, records, today)
        assertEquals(3000, p[0].spentCents) // today's food only
        assertEquals(5500, p[1].spentCents) // October, all expenses, income ignored
        assertEquals(15000, p[2].spentCents) // 2026 food, last year's excluded
        assertTrue(p[2].over)
        assertEquals(-5000, p[2].leftCents)
    }

    @Test fun generatedName() {
        assertEquals("餐饮 · 每日", Budget(1, "", BudgetPeriod.Day, Category.Food, 1).displayName)
        assertEquals("全部支出 · 每年", Budget(1, " ", BudgetPeriod.Year, null, 1).displayName)
        assertEquals("吃饭", Budget(1, "吃饭", BudgetPeriod.Day, Category.Food, 1).displayName)
    }

    @Test fun levelThresholds() {
        assertEquals(BudgetLevel.Green, BudgetLevel.of(0f))
        assertEquals(BudgetLevel.Green, BudgetLevel.of(.59f))
        assertEquals(BudgetLevel.Yellow, BudgetLevel.of(.6f))
        assertEquals(BudgetLevel.Yellow, BudgetLevel.of(.9f))
        assertEquals(BudgetLevel.Red, BudgetLevel.of(.91f))
        assertEquals(BudgetLevel.Red, BudgetLevel.of(1.4f))
    }

    @Test fun totalsYearMonthDay() {
        val t = TotalBudget.of(yearCents = 20000, monthCents = 5000, dayCents = 0, records = records, today = today)
        assertEquals(listOf(BudgetPeriod.Year, BudgetPeriod.Month, BudgetPeriod.Day), t.map { it.period })
        assertEquals(15500, t[0].spentCents) // 2026 expenses only: no 2025, no salary
        assertEquals(5500, t[1].spentCents)
        assertEquals(3500, t[2].spentCents)
        assertTrue(t[1].over)
        assertFalse(t[2].isSet)
        assertFalse(t[2].over)
        assertEquals(0f, t[2].fraction, 0f)
    }

    @Test fun sortAvailableAndUsed() {
        val list = listOf(
            BudgetProgress(Budget(1, "", BudgetPeriod.Day, null, 10000), 9000), // 1000 left, 90%
            BudgetProgress(Budget(2, "", BudgetPeriod.Day, null, 100000), 50000), // 50000 left, 50%
            BudgetProgress(Budget(3, "", BudgetPeriod.Day, null, 1000), 3000), // over, 300%
        )
        assertEquals(listOf(2L, 1L, 3L), BudgetSort.Available.apply(list).map { it.budget.id })
        assertEquals(listOf(3L, 1L, 2L), BudgetSort.Used.apply(list).map { it.budget.id })
    }
}
