package com.jizhang.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class TrendTest {
    /** A Wednesday, in ISO week 44 (Monday 26 October to Sunday 1 November). */
    private val today = LocalDate.of(2026, 10, 28)
    private var nextId = 1L

    private fun r(cat: Category, cents: Long, date: LocalDate, hour: Int = 12) =
        Record(nextId++, cat, cents, "", date.atTime(hour, 0))

    private fun d(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    private fun year(y: Int = 2026) = Period.containing(Granularity.Year, d(1, 1, y))

    private fun month(m: Int, y: Int = 2026) = Period.containing(Granularity.Month, d(m, 1, y))

    private fun week(date: LocalDate) = Period.containing(Granularity.Week, date)

    private fun trend(records: List<Record>, period: Period, now: LocalDate = today) = Trend.of(records, period, now)

    private val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    // --- granularity and periods -------------------------------------------------------------

    @Test fun granularityLabels() {
        assertEquals(listOf("年", "月", "周"), Granularity.entries.map { it.label })
        assertEquals(listOf("月均", "日均", "日均"), Granularity.entries.map { it.averageLabel })
        assertEquals(listOf("月", "日", "日"), Granularity.entries.map { it.bucketUnit })
    }

    @Test fun periodsSpanTheirUnit() {
        assertEquals(d(1, 1), year().start)
        assertEquals(d(12, 31), year().endInclusive)
        assertEquals(d(10, 1), month(10).start)
        assertEquals(d(10, 31), month(10).endInclusive)
        assertEquals(d(2, 28), month(2).endInclusive)
        assertEquals(d(2, 29, 2024), month(2, 2024).endInclusive)

        val w = week(today)
        assertEquals(d(10, 26), w.start)
        assertEquals(DayOfWeek.MONDAY, w.start.dayOfWeek)
        assertEquals(d(11, 1), w.endInclusive)
        assertEquals(DayOfWeek.SUNDAY, w.endInclusive.dayOfWeek)
        assertEquals(w, week(d(10, 26)))
        assertEquals(w, week(d(11, 1))) // a Sunday closes its week rather than opening the next
        assertTrue(d(10, 26) in w && d(11, 1) in w)
        assertFalse(d(10, 25) in w || d(11, 2) in w)
    }

    @Test fun anyDayOfAPeriodFindsThatPeriod() {
        for (g in Granularity.entries) {
            val p = Period.containing(g, today)
            var day = p.start
            while (!day.isAfter(p.endInclusive)) {
                assertEquals(p, Period.containing(g, day))
                day = day.plusDays(1)
            }
            assertEquals(p, Period.containing(g, LocalDate.ofEpochDay(p.start.toEpochDay())))
        }
    }

    @Test fun isoWeekNumbersAtTheYearEdges() {
        val now = week(today)
        assertEquals(2026, now.weekYear)
        assertEquals(44, now.weekNumber)

        // 1 January 2026 is a Thursday: its week is already week 1, and starts in December.
        for (day in listOf(d(12, 29, 2025), d(12, 31, 2025), d(1, 1), d(1, 4))) {
            val w = week(day)
            assertEquals(d(12, 29, 2025), w.start)
            assertEquals(2026, w.weekYear)
            assertEquals(1, w.weekNumber)
        }
        assertEquals(2, week(d(1, 5)).weekNumber)

        // 1 January 2027 (Friday) still belongs to week 53 of 2026; 2026 has 53 weeks.
        week(d(1, 1, 2027)).let {
            assertEquals(d(12, 28, 2026), it.start)
            assertEquals(2026, it.weekYear)
            assertEquals(53, it.weekNumber)
        }
        // 1 January 2022 (Saturday) is week 52 of 2021; 1 January 2023 (Sunday) week 52 of 2022.
        week(d(1, 1, 2022)).let {
            assertEquals(d(12, 27, 2021), it.start)
            assertEquals(2021, it.weekYear)
            assertEquals(52, it.weekNumber)
        }
        week(d(1, 1, 2023)).let {
            assertEquals(d(12, 26, 2022), it.start)
            assertEquals(2022, it.weekYear)
            assertEquals(52, it.weekNumber)
        }
        // 2020 was a leap year starting on a Wednesday: 53 weeks, the last one ending 3 January 2021.
        week(d(1, 1, 2021)).let {
            assertEquals(d(12, 28, 2020), it.start)
            assertEquals(2020, it.weekYear)
            assertEquals(53, it.weekNumber)
        }
        // 30 December 2024 (Monday) opens week 1 of 2025.
        week(d(12, 31, 2024)).let {
            assertEquals(d(12, 30, 2024), it.start)
            assertEquals(2025, it.weekYear)
            assertEquals(1, it.weekNumber)
        }
    }

    @Test fun yearLabelsFollowToday() {
        assertEquals("今年", year().label(today))
        assertEquals("今年支出", year().title(today))
        assertEquals("去年", year(2025).label(today))
        assertEquals("2024年", year(2024).label(today))
        assertEquals("2024年支出", year(2024).title(today))
    }

    @Test fun monthLabelsFollowToday() {
        assertEquals("本月", month(10).label(today))
        assertEquals("本月支出", month(10).title(today))
        assertEquals("上月", month(9).label(today))
        assertEquals("8月", month(8).label(today))
        assertEquals("8月支出", month(8).title(today))
        assertEquals("2025年12月", month(12, 2025).label(today))
        // Early in January the month before is last year's December.
        val january = d(1, 15)
        assertEquals("本月", month(1).label(january))
        assertEquals("上月", month(12, 2025).label(january))
        assertEquals("2025年11月", month(11, 2025).label(january))
    }

    @Test fun weekLabelsUseIsoNumbers() {
        assertEquals("本周", week(today).label(today))
        assertEquals("本周支出", week(today).title(today))
        assertEquals("上周", week(d(10, 19)).label(today))
        assertEquals("第41周", week(d(10, 5)).label(today))
        assertEquals("第41周支出", week(d(10, 5)).title(today))
        // The week of 29 December 2025 is week 1 of 2026, the same week-year as today's.
        assertEquals("第1周", week(d(12, 29, 2025)).label(today))
        // Week 52 of 2025 belongs to another week-year.
        assertEquals("2025年第52周", week(d(12, 22, 2025)).label(today))
        assertEquals("2025年第52周支出", week(d(12, 25, 2025)).title(today))

        // Across New Year the week before the current one is 上周 whatever its number.
        assertEquals("上周", week(d(12, 22, 2025)).label(d(1, 2)))
        assertEquals("上周", week(d(12, 29, 2025)).label(d(1, 5)))
        assertEquals("2025年第51周", week(d(12, 15, 2025)).label(d(1, 2)))
        // 1 January 2027 is still week 53 of 2026.
        assertEquals("本周", week(d(1, 1, 2027)).label(d(1, 1, 2027)))
        // ... while by 7 January 2027 (week 1 of 2027) week 52 of 2026 is a week of another week-year.
        assertEquals("2026年第52周", week(d(12, 21, 2026)).label(d(1, 7, 2027)))
        assertEquals("上周", week(d(12, 28, 2026)).label(d(1, 7, 2027)))
    }

    @Test fun weekRangeLabels() {
        assertEquals("10月26日–11月1日", week(today).rangeLabel)
        assertEquals("10月5日–11日", week(d(10, 7)).rangeLabel)
        assertEquals("9月28日–10月4日", week(d(10, 1)).rangeLabel)
        assertEquals("2025年12月29日–2026年1月4日", week(d(1, 1)).rangeLabel)
    }

    @Test fun detailExplainsOnlyTheRelativeLabels() {
        assertEquals("2026年", year().detail(today))
        assertEquals("2025年", year(2025).detail(today))
        assertNull(year(2024).detail(today))
        assertEquals("2026年10月", month(10).detail(today))
        assertEquals("2026年9月", month(9).detail(today))
        assertNull(month(8).detail(today))
        assertNull(month(12, 2025).detail(today))
        assertEquals("2025年12月", month(12, 2025).detail(d(1, 15)))
        assertEquals("10月26日–11月1日", week(today).detail(today))
        assertEquals("10月5日–11日", week(d(10, 5)).detail(today))
    }

    // --- options -----------------------------------------------------------------------------

    @Test fun optionsListOnlyPeriodsWithRecordsPlusTheCurrentOne() {
        val records = listOf(
            r(Category.Food, 100, d(5, 1, 2024)),
            r(Category.Food, 100, d(2, 1)),
            r(Category.Food, 100, d(10, 1)),
            r(Category.Salary, 100, d(4, 1)), // income counts: it is a bill
            r(Category.Food, 100, d(1, 1, 2027)), // after today: picks nothing
        )
        assertEquals(listOf(d(1, 1), d(1, 1, 2024)), Period.options(records, Granularity.Year, today).map { it.start })
        assertEquals(
            listOf(d(10, 1), d(4, 1), d(2, 1), d(5, 1, 2024)),
            Period.options(records, Granularity.Month, today).map { it.start },
        )
        assertEquals(
            listOf(d(10, 26), d(9, 28), d(3, 30), d(1, 26), d(4, 29, 2024)),
            Period.options(records, Granularity.Week, today).map { it.start },
        )
    }

    @Test fun optionsAlwaysIncludeTheCurrentPeriodNewestFirst() {
        for (g in Granularity.entries) {
            assertEquals(listOf(Period.containing(g, today)), Period.options(emptyList(), g, today))
        }
        val old = listOf(r(Category.Food, 100, d(3, 9, 2025)), r(Category.Food, 100, d(3, 10, 2025)))
        // Two bills in one month make one option; the current month is added on top.
        assertEquals(listOf(d(10, 1), d(3, 1, 2025)), Period.options(old, Granularity.Month, today).map { it.start })
        assertEquals(listOf(d(1, 1), d(1, 1, 2025)), Period.options(old, Granularity.Year, today).map { it.start })
        // 9 and 10 March 2025 are a Sunday and a Monday: two different weeks.
        assertEquals(
            listOf(d(10, 26), d(3, 10, 2025), d(3, 3, 2025)),
            Period.options(old, Granularity.Week, today).map { it.start },
        )
        val sorted = Period.options(old + r(Category.Food, 1, d(8, 8)), Granularity.Month, today).map { it.start }
        assertEquals(sorted.sortedDescending(), sorted)
    }

    @Test fun optionsAcrossTheWeekYearBoundary() {
        // 1 January 2026 and 31 December 2025 sit in the same ISO week: one option, not two.
        val records = listOf(r(Category.Food, 100, d(12, 31, 2025)), r(Category.Food, 100, d(1, 1)))
        assertEquals(listOf(d(10, 26), d(12, 29, 2025)), Period.options(records, Granularity.Week, today).map { it.start })
        assertEquals(listOf(d(1, 1), d(1, 1, 2025)), Period.options(records, Granularity.Year, today).map { it.start })
    }

    // --- year: months ------------------------------------------------------------------------

    @Test fun yearBucketsAreMonthsUpToThisOne() {
        val records = listOf(
            r(Category.Food, 1000, d(1, 5)),
            r(Category.Shop, 4000, d(10, 20)),
            r(Category.Salary, 900000, d(3, 10)),
            r(Category.Food, 7000, d(12, 3, 2025)), // another year
            r(Category.Food, 500, d(11, 15)), // after today
        )
        val t = trend(records, year())
        assertEquals(10, t.buckets.size) // January..October
        assertEquals("1月", t.buckets.first().label)
        assertEquals("10月", t.buckets.last().label)
        assertEquals(d(10, 1), t.buckets.last().start)
        assertEquals(d(10, 31), t.buckets.last().endInclusive)
        assertEquals((1..12).map { "${it}月" }, t.axisLabels) // the axis still shows the whole year
        assertEquals(listOf(1000L, 0L, 0L), t.buckets.take(3).map { it.expenseCents })
        assertEquals(4000, t.buckets.last().expenseCents)
        assertEquals(5000, t.totalCents)
        assertEquals(500, t.averageCents) // ÷ 10 months, the current one included
        assertEquals("月均", t.averageLabel)
        assertEquals(900000, t.incomeCents)
    }

    @Test fun pastYearAveragesOverTwelveMonths() {
        val records = listOf(r(Category.Food, 7000, d(12, 3, 2025)), r(Category.Food, 1000, d(1, 5)))
        val t = trend(records, year(2025))
        assertEquals(12, t.buckets.size)
        assertEquals(12, t.axisLabels.size)
        assertEquals(7000, t.totalCents)
        assertEquals(7000L / 12, t.averageCents)
        assertEquals(7000, t.buckets.last().expenseCents)

        // A year with no bills is still a valid (if unlisted) period.
        val none = trend(records, year(2024))
        assertEquals(12, none.buckets.size)
        assertEquals(0, none.totalCents)
        assertEquals(0, none.averageCents)
    }

    @Test fun yearEdgesBelongToTheirOwnYear() {
        val records = listOf(
            r(Category.Food, 100, d(12, 31, 2024)),
            r(Category.Food, 200, d(1, 1, 2025)),
            r(Category.Food, 400, d(12, 31, 2025)),
            r(Category.Food, 800, d(1, 1)),
        )
        assertEquals(600, trend(records, year(2025)).totalCents)
        assertEquals(800, trend(records, year()).totalCents)
        assertEquals(100, trend(records, year(2024)).totalCents)
    }

    // --- month: days -------------------------------------------------------------------------

    @Test fun monthBucketsAreDaysUpToToday() {
        val records = listOf(
            r(Category.Food, 2800, d(10, 1)),
            r(Category.Food, 900, d(10, 28)),
            r(Category.Food, 5000, d(10, 30)), // after today
            r(Category.Salary, 100000, d(10, 2)),
            r(Category.Food, 7000, d(9, 30)), // another month
        )
        val t = trend(records, month(10))
        assertEquals(28, t.buckets.size)
        assertEquals(31, t.axisLabels.size)
        assertEquals("1日", t.axisLabels.first())
        assertEquals("31日", t.axisLabels.last())
        assertEquals("10月1日", t.buckets.first().label)
        assertEquals("10月28日", t.buckets.last().label)
        assertEquals(d(10, 28), t.buckets.last().start)
        assertEquals(d(10, 28), t.buckets.last().endInclusive)
        assertEquals(2800, t.buckets[0].expenseCents)
        assertEquals(900, t.buckets[27].expenseCents)
        assertEquals(3700, t.totalCents)
        assertEquals(3700L / 28, t.averageCents) // ÷ 28 days elapsed
        assertEquals("日均", t.averageLabel)
        assertEquals(100000, t.incomeCents)
    }

    @Test fun pastMonthAveragesOverItsLength() {
        val sept = trend(listOf(r(Category.Food, 3000, d(9, 30)), r(Category.Food, 40, d(10, 1))), month(9))
        assertEquals(30, sept.buckets.size)
        assertEquals(30, sept.axisLabels.size)
        assertEquals(3000, sept.totalCents)
        assertEquals(100, sept.averageCents)

        val aug = trend(listOf(r(Category.Food, 3100, d(8, 31))), month(8))
        assertEquals(31, aug.buckets.size)
        assertEquals(100, aug.averageCents)

        assertEquals(29, trend(emptyList(), month(2, 2024)).buckets.size)
        assertEquals(28, trend(emptyList(), month(2, 2025)).axisLabels.size)
    }

    @Test fun monthEdgesBelongToTheirOwnMonth() {
        val records = listOf(
            r(Category.Food, 100, d(8, 31)),
            r(Category.Food, 200, d(9, 1)),
            r(Category.Food, 400, d(9, 30)),
            r(Category.Food, 800, d(10, 1)),
        )
        assertEquals(600, trend(records, month(9)).totalCents)
        assertEquals(200, trend(records, month(9)).buckets.first().expenseCents)
        assertEquals(400, trend(records, month(9)).buckets.last().expenseCents)
    }

    // --- week: weekdays ----------------------------------------------------------------------

    @Test fun currentWeekRunsMondayToToday() {
        val records = listOf(
            r(Category.Food, 300, d(10, 26)),
            r(Category.Shop, 1200, d(10, 28)),
            r(Category.Food, 999, d(10, 29)), // after today
            r(Category.Food, 888, d(10, 25)), // the Sunday before
        )
        val t = trend(records, week(today))
        assertEquals(listOf("周一", "周二", "周三"), t.buckets.map { it.label })
        assertEquals(weekdays, t.axisLabels)
        assertEquals(listOf(300L, 0L, 1200L), t.buckets.map { it.expenseCents })
        assertEquals(d(10, 26), t.buckets.first().start)
        assertEquals(1500, t.totalCents)
        assertEquals(500, t.averageCents) // ÷ 3 days elapsed
        assertEquals("日均", t.averageLabel)
    }

    @Test fun pastWeekAveragesOverSevenDays() {
        val records = listOf(
            r(Category.Food, 700, d(10, 19)), // Monday
            r(Category.Food, 1400, d(10, 25)), // Sunday
            r(Category.Food, 5000, d(10, 18)), // the Sunday before
            r(Category.Food, 100, d(10, 26)), // next Monday
        )
        val t = trend(records, week(d(10, 22)))
        assertEquals(7, t.buckets.size)
        assertEquals(weekdays, t.buckets.map { it.label })
        assertEquals(listOf(700L, 0L, 0L, 0L, 0L, 0L, 1400L), t.buckets.map { it.expenseCents })
        assertEquals(d(10, 19), t.buckets.first().start)
        assertEquals(d(10, 25), t.buckets.last().endInclusive)
        assertEquals(2100, t.totalCents)
        assertEquals(300, t.averageCents)
    }

    @Test fun weekAcrossNewYearCollectsBothYears() {
        val records = listOf(
            r(Category.Food, 100, d(12, 31, 2025)),
            r(Category.Food, 200, d(1, 1)),
            r(Category.Food, 300, d(1, 4)),
            r(Category.Food, 999, d(1, 5)), // next week
            r(Category.Food, 888, d(12, 28, 2025)), // the week before
        )
        val t = trend(records, week(d(1, 1)))
        assertEquals(7, t.buckets.size)
        assertEquals(d(12, 29, 2025), t.buckets.first().start)
        assertEquals(d(1, 4), t.buckets.last().endInclusive)
        assertEquals(600, t.totalCents)
        assertEquals(listOf(0L, 0L, 100L, 200L, 0L, 0L, 300L), t.buckets.map { it.expenseCents })
    }

    // --- averages on the first day -----------------------------------------------------------

    @Test fun averagesOnTheFirstDayDivideByOne() {
        val newYear = d(1, 1, 2027) // a Friday: the fifth day of ISO week 53 of 2026
        val records = listOf(r(Category.Food, 600, newYear))
        val y = trend(records, Period.containing(Granularity.Year, newYear), newYear)
        assertEquals(1, y.buckets.size)
        assertEquals(600, y.averageCents)
        val m = trend(records, Period.containing(Granularity.Month, newYear), newYear)
        assertEquals(1, m.buckets.size)
        assertEquals(600, m.averageCents)
        val w = trend(records, Period.containing(Granularity.Week, newYear), newYear)
        assertEquals(5, w.buckets.size) // Monday 28 December .. Friday 1 January
        assertEquals(120, w.averageCents)
        assertEquals(53, w.period.weekNumber)
        assertEquals("本周", w.period.label(newYear))
    }

    @Test fun periodsNotYetBegunHaveNoBuckets() {
        val t = trend(listOf(r(Category.Food, 100, d(11, 2))), month(11))
        assertTrue(t.isEmpty)
        assertEquals(0, t.totalCents) // the bill is dated after today
        assertEquals(0, t.averageCents)
        assertEquals(30, t.axisLabels.size)
        assertTrue(t.shares.isEmpty())
        assertTrue(t.topExpenses(3).isEmpty())
    }

    @Test fun emptyLedgerHasZeroAverages() {
        val counts = mapOf(Granularity.Year to 10, Granularity.Month to 28, Granularity.Week to 3)
        for (g in Granularity.entries) {
            val t = trend(emptyList(), Period.containing(g, today))
            assertEquals(counts.getValue(g), t.buckets.size)
            assertEquals(0, t.totalCents)
            assertEquals(0, t.averageCents)
            assertEquals(0, t.incomeCents)
            assertTrue(t.topExpenses(3).isEmpty())
            assertTrue(t.shares.isEmpty())
        }
    }

    @Test fun bucketsAreContiguous() {
        for (g in Granularity.entries) {
            for (p in listOf(Period.containing(g, today), Period.containing(g, d(2, 10, 2024)))) {
                val b = trend(emptyList(), p).buckets
                assertEquals(p.start, b.first().start)
                b.zipWithNext { a, c -> assertEquals(a.endInclusive.plusDays(1), c.start) }
                b.forEach { assertFalse(it.endInclusive.isBefore(it.start)) }
            }
        }
    }

    // --- buckets, shares and ranking ---------------------------------------------------------

    @Test fun topThreeLargestFirstExpensesOnly() {
        val day = d(10, 8)
        val t = trend(
            listOf(
                r(Category.Food, 500, day, 8),
                r(Category.Shop, 3000, day, 9),
                r(Category.Salary, 999999, day, 10),
                r(Category.Fun, 1200, day, 11),
                r(Category.Transit, 3000, day, 20),
            ),
            month(10),
        )
        val b = t.buckets[7]
        assertEquals(7700, b.expenseCents)
        assertEquals(listOf(3000L, 3000L, 1200L), b.top.map { it.amountCents })
        assertEquals(Category.Transit, b.top[0].category) // equal amounts: the later one first
        assertTrue(b.top.all { it.kind == EntryKind.Expense })
    }

    @Test fun incomeNeverReachesBucketsOrRanking() {
        val t = trend(listOf(r(Category.Salary, 500000, d(10, 3)), r(Category.Food, 100, d(10, 3))), month(10))
        assertEquals(100, t.buckets[2].expenseCents)
        assertEquals(listOf(100L), t.buckets[2].top.map { it.amountCents })
        assertEquals(listOf(100L), t.topExpenses(10).map { it.amountCents })
        assertEquals(500000, t.incomeCents)
    }

    @Test fun rankingIsThePeriodsLargestSingleExpenses() {
        val records = listOf(
            r(Category.Food, 100, d(10, 3)),
            r(Category.Food, 900, d(10, 6)),
            r(Category.Food, 400, d(10, 20)),
            r(Category.Food, 400, d(10, 21)),
            r(Category.Food, 50000, d(9, 30)), // last month
            r(Category.Salary, 999999, d(10, 5)), // income
        )
        val m = trend(records, month(10))
        assertEquals(listOf(900L, 400L), m.topExpenses(2).map { it.amountCents })
        assertEquals(4, m.topExpenses(10).size)
        // Equal amounts: the later one ranks first.
        assertEquals(listOf(d(10, 6), d(10, 21), d(10, 20), d(10, 3)), m.topExpenses(10).map { it.date })

        // The year takes in last month's big bill; the week only its own bills.
        assertEquals(listOf(50000L, 900L), trend(records, year()).topExpenses(2).map { it.amountCents })
        assertEquals(listOf(d(10, 21), d(10, 20)), trend(records, week(d(10, 19))).topExpenses(10).map { it.date })
        assertEquals(listOf(50000L), trend(records, month(9)).topExpenses(10).map { it.amountCents })
        assertTrue(trend(records, week(d(10, 12))).topExpenses(10).isEmpty())
    }

    @Test fun sharesFollowThePeriod() {
        val cats = listOf(Category.Food, Category.Transit, Category.Shop, Category.Daily, Category.Fun, Category.Home, Category.Med)
        val records = cats.mapIndexed { i, c -> r(c, (7 - i) * 1000L, d(10, 2)) } + r(Category.Food, 50000, d(5, 2))
        val month = trend(records, month(10)).shares
        assertEquals(6, month.size)
        assertEquals(3000, month.last().cents)
        assertEquals(1f, month.sumOf { it.fraction.toDouble() }.toFloat(), 1e-4f)

        val year = trend(records, year()).shares
        assertEquals(Category.Food, year.first().category)
        assertEquals(57000, year.first().cents)

        // The week of 2 October holds the seven bills; the week of 26 October none.
        assertEquals(6, trend(records, week(d(10, 2))).shares.size)
        assertTrue(trend(records, week(today)).shares.isEmpty())
    }

    @Test fun rankingCanBeNarrowedToOneSlice() {
        val records = listOf(
            r(Category.Food, 900, d(10, 6)),
            r(Category.Food, 300, d(10, 7)),
            r(Category.Transit, 5000, d(10, 8)),
            r(Category.Food, 700, d(9, 30)), // last month
            r(Category.Salary, 999999, d(10, 5)), // income never counts
        )
        val t = trend(records, month(10))
        val food = t.shares.first { it.category == Category.Food }
        assertEquals(listOf(900L, 300L), t.expensesOf(food).map { it.amountCents })
        assertEquals(listOf(900L), t.topExpenses(1, food).map { it.amountCents })
        assertTrue(t.expensesOf(food).all { it.category == Category.Food })

        // No slice picked: the whole period, as before.
        assertEquals(t.topExpenses(10), t.topExpenses(10, null))
        assertEquals(listOf(5000L, 900L, 300L), t.topExpenses(10, null).map { it.amountCents })

        // The slices together hold every expense once.
        assertEquals(t.expenses.size, t.shares.sumOf { t.expensesOf(it).size })
        assertEquals(t.totalCents, t.shares.sumOf { s -> t.expensesOf(s).sumOf { it.amountCents } })
    }

    @Test fun theMergedSliceRanksEverythingOutsideTheTopFive() {
        val cats = listOf(Category.Food, Category.Transit, Category.Shop, Category.Daily, Category.Fun, Category.Home, Category.Med)
        val records = cats.mapIndexed { i, c -> r(c, (7 - i) * 1000L, d(10, 2)) } + r(Category.Med, 500, d(10, 9))
        val t = trend(records, month(10))
        val rest = t.shares.last()
        assertNull(rest.category)
        // 居住 (2000) and 医疗 (1000 + 500) are the ones the top five left out.
        assertEquals(listOf(2000L, 1000L, 500L), t.expensesOf(rest).map { it.amountCents })
        assertEquals(setOf(Category.Home, Category.Med), t.expensesOf(rest).map { it.category }.toSet())
        assertEquals(rest.cents, t.expensesOf(rest).sumOf { it.amountCents })
        assertEquals(listOf(2000L, 1000L), t.topExpenses(2, rest).map { it.amountCents })
    }

    @Test fun aRealOtherCategoryAndTheMergedSliceStayApart() {
        val big = listOf(Category.Food, Category.Transit, Category.Shop, Category.Daily, Category.Other)
        val records = big.mapIndexed { i, c -> r(c, (9 - i) * 1000L, d(10, 2)) } +
            r(Category.Fun, 800, d(10, 3)) + r(Category.Home, 700, d(10, 4))
        val t = trend(records, month(10))
        val real = t.shares.first { it.category == Category.Other }
        val merged = t.shares.last()
        assertNull(merged.category)
        assertEquals(listOf(5000L), t.expensesOf(real).map { it.amountCents })
        assertEquals(listOf(800L, 700L), t.expensesOf(merged).map { it.amountCents })
    }
}
