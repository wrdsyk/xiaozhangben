package com.jizhang.domain

import java.time.LocalDate

enum class BudgetPeriod(val key: String, val label: String, val scope: String) {
    Day("day", "每日", "今天"),
    Month("month", "每月", "本月"),
    Year("year", "每年", "今年");

    fun contains(date: LocalDate, today: LocalDate): Boolean = when (this) {
        Day -> date == today
        Month -> date.year == today.year && date.monthValue == today.monthValue
        Year -> date.year == today.year
    }

    companion object {
        fun of(key: String): BudgetPeriod = entries.firstOrNull { it.key == key } ?: Month
    }
}

/**
 * A custom budget on top of the main monthly one: a cap per day, month or year, either on all
 * spending ([category] null) or on one expense category.
 */
data class Budget(
    val id: Long,
    val name: String,
    val period: BudgetPeriod,
    val category: Category?,
    val amountCents: Long,
) {
    val scopeLabel: String get() = category?.label ?: "全部支出"

    /** The user's name, or "餐饮 · 每日" when they left it blank. */
    val displayName: String get() = name.ifBlank { "$scopeLabel · ${period.label}" }
}

/** Progress colour band for bars and the ring: up to 50% green, 50–80% yellow, 80% and over red. */
enum class BudgetLevel {
    Green, Yellow, Red;

    companion object {
        /** The owner's bands, shared by the budget bars and the ring: up to 50%, under 80%, 80%+. */
        fun of(fraction: Float): BudgetLevel = when {
            fraction <= .5f -> Green
            fraction < .8f -> Yellow
            else -> Red
        }
    }
}

data class BudgetProgress(val budget: Budget, val spentCents: Long) {
    val leftCents: Long get() = budget.amountCents - spentCents
    val over: Boolean get() = spentCents > budget.amountCents
    val fraction: Float get() = if (budget.amountCents <= 0) 0f else spentCents.toFloat() / budget.amountCents
    val level: BudgetLevel get() = BudgetLevel.of(fraction)

    companion object {
        /** [records] must cover at least the current year for yearly budgets to be right. */
        fun of(budgets: List<Budget>, records: List<Record>, today: LocalDate): List<BudgetProgress> {
            val expenses = records.filter { it.kind == EntryKind.Expense }
            return budgets.map { b ->
                val spent = expenses
                    .filter { b.period.contains(it.date, today) && (b.category == null || it.category.key == b.category.key) }
                    .sumOf { it.amountCents }
                BudgetProgress(b, spent)
            }
        }
    }
}

/**
 * Order of the budget tab's lists. 可用 puts the most money left first (so over-budget ones end
 * up last); 已用 puts the highest share used first. Ties keep creation order.
 */
enum class BudgetSort(val label: String, val hint: String) {
    Available("可用", "剩得多的在前"),
    Used("已用", "用得多的在前");

    fun apply(list: List<BudgetProgress>): List<BudgetProgress> = when (this) {
        Available -> list.sortedWith(compareByDescending<BudgetProgress> { it.leftCents }.thenBy { it.budget.id })
        Used -> list.sortedWith(
            compareByDescending<BudgetProgress> { it.fraction }.thenByDescending { it.spentCents }.thenBy { it.budget.id },
        )
    }
}

/**
 * The overall budget for this year, month or day (all spending). [amountCents] 0 means the
 * user hasn't set that one yet. The month total is the one the home ring uses.
 */
data class TotalBudget(val period: BudgetPeriod, val amountCents: Long, val spentCents: Long) {
    val isSet: Boolean get() = amountCents > 0
    val leftCents: Long get() = amountCents - spentCents
    val over: Boolean get() = isSet && spentCents > amountCents
    val fraction: Float get() = if (!isSet) 0f else spentCents.toFloat() / amountCents
    val level: BudgetLevel get() = BudgetLevel.of(fraction)

    companion object {
        /** Year, month and day totals, in that order (the budget tab's pager order). */
        fun of(yearCents: Long, monthCents: Long, dayCents: Long, records: List<Record>, today: LocalDate): List<TotalBudget> {
            val expenses = records.filter { it.kind == EntryKind.Expense }
            fun spent(p: BudgetPeriod) = expenses.filter { p.contains(it.date, today) }.sumOf { it.amountCents }
            return listOf(
                TotalBudget(BudgetPeriod.Year, yearCents, spent(BudgetPeriod.Year)),
                TotalBudget(BudgetPeriod.Month, monthCents, spent(BudgetPeriod.Month)),
                TotalBudget(BudgetPeriod.Day, dayCents, spent(BudgetPeriod.Day)),
            )
        }
    }
}
