package com.jizhang.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.abs

/** Which way spending went against the period before. */
enum class Movement { Up, Down, Flat }

/**
 * How a period's expenses compare with the whole [previous] period of the same kind (week with
 * week, month with month, year with year), as the stats page words it: "相比上周 +¥200.00（+15.85%）".
 * Both sides are whole-period totals. The running period counts what has been spent so far, so
 * early in a month or a week it usually reads as a drop; that is the honest figure, not a forecast.
 */
data class TrendChange(
    val previous: Period,
    val currentCents: Long,
    val previousCents: Long,
) {
    /** Positive when this period spent more. */
    val deltaCents: Long = currentCents - previousCents

    val movement: Movement = when {
        deltaCents > 0 -> Movement.Up
        deltaCents < 0 -> Movement.Down
        else -> Movement.Flat
    }

    /** Neither period has an expense: there is nothing to compare. */
    val isEmpty: Boolean get() = currentCents == 0L && previousCents == 0L

    /**
     * The change as a percentage of the previous total, two decimals, rounded half up (away from
     * zero when negative); null when the previous period spent nothing, since nothing is the base.
     */
    val percent: BigDecimal? =
        if (previousCents == 0L) {
            null
        } else {
            BigDecimal.valueOf(deltaCents).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(previousCents), 2, RoundingMode.HALF_UP)
        }

    /** "+¥200.00", "-¥35.50", "¥0.00" when equal. */
    val amountText: String get() = sign + "¥" + formatCents(abs(deltaCents))

    /**
     * "+15.85%", "-3.20%"; null without a base ([percent]). The sign follows [movement], so a rise
     * too small to show in two decimals still reads "+0.00%" and not as no change at all.
     */
    val percentText: String? get() = percent?.let { sign + it.abs().toPlainString() + "%" }

    /** "相比上周", "相比上月", "相比去年"; for a period further back, "相比8月", "相比2024年". */
    fun label(today: LocalDate): String = "相比" + previous.label(today)

    private val sign: String
        get() = when (movement) {
            Movement.Up -> "+"
            Movement.Down -> "-"
            Movement.Flat -> ""
        }

    companion object {
        private val HUNDRED = BigDecimal(100)

        /**
         * [trend]'s total against the period before it, whose expenses come from [records] by the
         * rule [Trend.of] uses (dated inside the period and not after [today]).
         */
        fun of(trend: Trend, records: List<Record>, today: LocalDate): TrendChange {
            val before = trend.period.previous
            val spent = records.sumOf { r ->
                if (r.kind == EntryKind.Expense && r.date in before && !r.date.isAfter(today)) r.amountCents else 0L
            }
            return TrendChange(before, trend.totalCents, spent)
        }
    }
}
