package com.jizhang.domain

sealed interface Key {
    data class Digit(val d: Int) : Key
    data object Dot : Key
    data object Plus : Key
    data object Minus : Key
    data object Back : Key
}

/**
 * The keypad's expression, e.g. "12.5+3-1". Each operand allows at most 7 integer digits and
 * 2 decimals, the whole expression [MAX_LENGTH] characters (the readout shrinks its type to fit,
 * so a long sum is never cut off). [press] returns null when a key is rejected so the UI can
 * shake and buzz.
 */
data class AmountInput(val text: String = "") {

    val isEmpty: Boolean get() = text.isEmpty()

    /** True once there is something to add up, so the UI shows "= total" under the input. */
    val hasOperator: Boolean get() = text.any { it == '+' || it == '-' }

    fun press(key: Key): AmountInput? = when (key) {
        is Key.Digit -> digit(key.d)
        Key.Dot -> dot()
        Key.Plus -> operator('+')
        Key.Minus -> operator('-')
        Key.Back -> if (text.isEmpty()) null else AmountInput(text.dropLast(1))
    }

    /** Sum of all operands in cents; a trailing operator or dot is ignored. */
    fun totalCents(): Long {
        var total = 0L
        var sign = 1
        val operand = StringBuilder()
        for (ch in text + '+') {
            if (ch == '+' || ch == '-') {
                total += sign * operandCents(operand.toString())
                operand.clear()
                sign = if (ch == '+') 1 else -1
            } else {
                operand.append(ch)
            }
        }
        return total
    }

    private val operand: String get() = text.substringAfterLast('+').substringAfterLast('-')

    private fun digit(d: Int): AmountInput? {
        if (text.length >= MAX_LENGTH) return null
        val op = operand
        val dotAt = op.indexOf('.')
        return when {
            op == "0" -> if (d == 0) null else AmountInput(text.dropLast(1) + d)
            dotAt >= 0 && op.length - dotAt - 1 >= 2 -> null
            dotAt < 0 && op.length >= MAX_INT_DIGITS -> null
            else -> AmountInput(text + d)
        }
    }

    private fun dot(): AmountInput? {
        val op = operand
        return when {
            op.contains('.') -> null
            op.isEmpty() -> AmountInput(text + "0.")
            else -> AmountInput("$text.")
        }
    }

    private fun operator(op: Char): AmountInput? {
        var base = text.trimEnd('.')
        if (base.isNotEmpty() && (base.last() == '+' || base.last() == '-')) base = base.dropLast(1)
        if (base.isEmpty()) return null
        return AmountInput(base + op)
    }

    companion object {
        const val MAX_INT_DIGITS = 7
        const val MAX_LENGTH = 40

        /** Prefills the keypad when editing: 3200 → "32", 3250 → "32.5". */
        fun fromCents(cents: Long): AmountInput = AmountInput(formatCentsShort(cents).replace(",", ""))

        private fun operandCents(s: String): Long {
            if (s.isEmpty() || s == ".") return 0
            val whole = s.substringBefore('.').ifEmpty { "0" }.toLong()
            val frac = s.substringAfter('.', "").padEnd(2, '0').take(2).toLong()
            return whole * 100 + frac
        }
    }
}
