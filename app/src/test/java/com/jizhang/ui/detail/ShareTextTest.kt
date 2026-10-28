package com.jizhang.ui.detail

import com.jizhang.domain.Category
import com.jizhang.domain.MonthSummary
import com.jizhang.domain.Record
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ShareTextTest {
    @Test
    fun twoDecimalsRoundedHalfUp() {
        assertEquals("33.33%", formatShare(1, 3))
        assertEquals("66.67%", formatShare(2, 3))
        assertEquals("23.70%", formatShare(237, 1000))
        assertEquals("0.42%", formatShare(42, 10_000))
        assertEquals("100.00%", formatShare(500, 500))
        // 0.125% sits exactly on the half: half up gives 0.13, not banker's 0.12.
        assertEquals("0.13%", formatShare(1, 800))
        assertEquals("0.01%", formatShare(1, 20_000)) // 0.005% rounds up to 0.01
    }

    @Test
    fun tinyOrEmptySharesNeverReadAsPlainZeroByMistake() {
        assertEquals("<0.01%", formatShare(1, 1_000_000)) // a real record, but under 0.005%
        assertEquals("0.00%", formatShare(0, 1000))
        assertEquals("0.00%", formatShare(100, 0)) // guarded zero total
        assertEquals("100.00%", formatShare(900, 500)) // never beyond the whole
    }

    private val month = YearMonth.of(2026, 10)
    private var nextId = 1L
    private fun r(cat: Category, cents: Long, day: Int) =
        Record(nextId++, cat, cents, "", LocalDate.of(2026, 10, day).atTime(12, 0))

    // Six spending categories, so the smallest ends up merged into the summary's 其他 slice.
    private val records = listOf(
        r(Category.Food, 6000, 1),
        r(Category.Transit, 5000, 2),
        r(Category.Shop, 4000, 3),
        r(Category.Fun, 3000, 4),
        r(Category.Home, 2000, 5),
        r(Category.Med, 100, 6),
        r(Category.Salary, 20_000, 7),
        r(Category.Salary, 5_000, 8),
    )
    private val summary = MonthSummary.of(month, records, 0, LocalDate.of(2026, 10, 28))

    @Test
    fun categoryCentsFromMonthRecordsCoverEveryCategoryAndIncome() {
        assertEquals(100L, categoryCentsOf(records[5], summary, records))
        assertEquals(6000L, categoryCentsOf(records[0], summary, records))
        assertEquals(25_000L, categoryCentsOf(records[6], summary, records))
    }

    @Test
    fun categoryCentsFromSummaryOnlyKnowsTopFiveSpending() {
        assertEquals(6000L, categoryCentsOf(records[0], summary, null))
        assertNull(categoryCentsOf(records[5], summary, null)) // merged into 其他
        assertNull(categoryCentsOf(records[6], summary, null)) // income isn't broken down
    }
}
