package com.jizhang.domain

import java.time.LocalDate
import java.time.YearMonth

data class CategoryShare(
    val category: Category?,
    val cents: Long,
    val fraction: Float,
    /** How many categories this slice stands for when [category] is null; 0 for a named one. */
    val merged: Int = 0,
) {
    /**
     * A named category's label, or "其余 3 类" for the merged slice after the top five. Never the
     * bare word "其他", so it cannot be mistaken for a real category of that name.
     */
    val label: String get() = category?.label ?: "其余 $merged 类"

    companion object {
        /** Top five categories by amount, the rest merged into one slice (a null category). */
        fun topOf(expenses: List<Record>, total: Long): List<CategoryShare> {
            if (total <= 0) return emptyList()
            val sorted = expenses.groupBy { it.category }
                .map { (cat, list) -> cat to list.sumOf { it.amountCents } }
                .sortedByDescending { it.second }
            val top = sorted.take(5).map { (cat, cents) -> CategoryShare(cat, cents, cents.toFloat() / total) }
            val rest = sorted.drop(5)
            val restCents = rest.sumOf { it.second }
            return if (restCents > 0) {
                top + CategoryShare(null, restCents, restCents.toFloat() / total, merged = rest.size)
            } else {
                top
            }
        }
    }
}

data class DayGroup(val date: LocalDate, val records: List<Record>) {
    val expenseCents: Long get() = records.filter { it.kind == EntryKind.Expense }.sumOf { it.amountCents }
    val incomeCents: Long get() = records.filter { it.kind == EntryKind.Income }.sumOf { it.amountCents }
}

data class MonthSummary(
    val month: YearMonth,
    val expenseCents: Long,
    val incomeCents: Long,
    val budgetCents: Long,
    /** Expense per day, index 0 = day 1, up to today for the current month. */
    val daily: List<Long>,
    val shares: List<CategoryShare>,
    /** What can still be spent per day until month end; null for past months or when over budget. */
    val perDayLeftCents: Long?,
) {
    val balanceCents: Long get() = incomeCents - expenseCents
    val budgetLeftCents: Long get() = budgetCents - expenseCents
    val budgetUsed: Float get() = if (budgetCents <= 0) 0f else expenseCents.toFloat() / budgetCents
    val avgPerDayCents: Long get() = if (daily.isEmpty()) 0 else expenseCents / daily.size

    companion object {
        fun of(month: YearMonth, records: List<Record>, budgetCents: Long, today: LocalDate): MonthSummary {
            val inMonth = records.filter { YearMonth.from(it.date) == month }
            val expenses = inMonth.filter { it.kind == EntryKind.Expense }
            val expenseTotal = expenses.sumOf { it.amountCents }
            val isCurrent = YearMonth.from(today) == month
            val days = when {
                isCurrent -> today.dayOfMonth
                month.isAfter(YearMonth.from(today)) -> 0
                else -> month.lengthOfMonth()
            }
            val daily = LongArray(days)
            expenses.forEach { r -> if (r.date.dayOfMonth <= days) daily[r.date.dayOfMonth - 1] += r.amountCents }

            val perDayLeft = if (isCurrent && budgetCents - expenseTotal > 0) {
                val remainingDays = month.lengthOfMonth() - today.dayOfMonth + 1
                (budgetCents - expenseTotal) / remainingDays
            } else null

            return MonthSummary(
                month = month,
                expenseCents = expenseTotal,
                incomeCents = inMonth.filter { it.kind == EntryKind.Income }.sumOf { it.amountCents },
                budgetCents = budgetCents,
                daily = daily.toList(),
                shares = CategoryShare.topOf(expenses, expenseTotal),
                perDayLeftCents = perDayLeft,
            )
        }

        fun groupByDay(records: List<Record>): List<DayGroup> =
            records.sortedByDescending { it.occurredAt }
                .groupBy { it.date }
                .map { (date, list) -> DayGroup(date, list) }
    }
}
