package com.jizhang.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountInputTest {
    private fun type(vararg keys: Key): AmountInput? =
        keys.fold(AmountInput() as AmountInput?) { acc, k -> acc?.press(k) }

    private fun digits(s: String): Array<Key> = s.map { ch ->
        when (ch) {
            '.' -> Key.Dot
            '+' -> Key.Plus
            '-' -> Key.Minus
            else -> Key.Digit(ch - '0')
        }
    }.toTypedArray()

    @Test fun plainAmount() {
        val input = type(*digits("32.5"))!!
        assertEquals("32.5", input.text)
        assertEquals(3250, input.totalCents())
    }

    @Test fun addsAndSubtracts() {
        assertEquals(3750, type(*digits("32+5.5"))!!.totalCents())
        assertEquals(2000, type(*digits("30-10"))!!.totalCents())
    }

    @Test fun trailingOperatorAndDotAreIgnored() {
        assertEquals(1200, type(*digits("12+"))!!.totalCents())
        assertEquals(1200, type(*digits("12."))!!.totalCents())
    }

    @Test fun rejectsThirdDecimal() {
        assertNull(type(*digits("1.234")))
    }

    @Test fun rejectsEighthIntegerDigit() {
        assertEquals("1234567", type(*digits("1234567"))!!.text)
        assertNull(type(*digits("12345678")))
    }

    @Test fun rejectsSecondDotInOperand() {
        assertNull(type(*digits("1.2.")))
        assertEquals("1.2+3.4", type(*digits("1.2+3.4"))!!.text)
    }

    @Test fun leadingZeroIsReplaced() {
        assertEquals("5", type(*digits("05"))!!.text)
        assertNull(type(*digits("00")))
    }

    @Test fun dotOnEmptyOperandAddsZero() {
        assertEquals("0.", type(Key.Dot)!!.text)
        assertEquals("3+0.", type(*digits("3+."))!!.text)
    }

    @Test fun operatorReplacesOperator() {
        assertEquals("3-", type(*digits("3+-"))!!.text)
        assertNull(type(Key.Plus))
    }

    @Test fun subtractionOfTwoDecimalAmounts() {
        // Owner's report: "82" of 120.11-17.82 looked untyped. The keys are all accepted key by key
        // (the readout used to cut the end off), and the total is right.
        val keys = listOf(
            Key.Digit(1), Key.Digit(2), Key.Digit(0), Key.Dot, Key.Digit(1), Key.Digit(1),
            Key.Minus, Key.Digit(1), Key.Digit(7), Key.Dot, Key.Digit(8), Key.Digit(2),
        )
        var input = AmountInput()
        val seen = mutableListOf<String>()
        for (k in keys) {
            input = input.press(k) ?: error("key $k rejected after \"${input.text}\"")
            seen += input.text
        }
        assertEquals("120.11-17.82", input.text)
        assertEquals(10229L, input.totalCents())
        assertEquals("120.11-17.8", seen[seen.size - 2])
        assertEquals(listOf("1", "12", "120", "120.", "120.1", "120.11", "120.11-"), seen.take(7))
    }

    @Test fun longExpressionsAreAccepted() {
        // Each operand keeps its 7 integer digits and 2 decimals; only the whole length is capped.
        val sum = "1234567.89+1234567.89+1234567.89"
        val input = type(*digits(sum))!!
        assertEquals(sum, input.text)
        assertEquals(3 * 123456789L, input.totalCents())
    }

    @Test fun longSumOfSmallAmounts() {
        // 30 characters: past the old 24-character cap, and still all typed.
        val expr = "25.5+18+7.9+32+11.2+99+4.75+60"
        val input = type(*digits(expr))!!
        assertEquals(expr, input.text)
        assertEquals(25835L, input.totalCents())
    }

    @Test fun rejectsDigitPastMaxLength() {
        val full = "9.99+".repeat(8)
        assertEquals(AmountInput.MAX_LENGTH, full.length)
        val input = type(*digits(full))!!
        assertEquals(full, input.text)
        assertNull(input.press(Key.Digit(1)))
    }

    @Test fun backspace() {
        assertEquals("1", type(*digits("12"), Key.Back)!!.text)
        assertNull(AmountInput().press(Key.Back))
    }

    @Test fun prefillFromCents() {
        assertEquals("32", AmountInput.fromCents(3200).text)
        assertEquals("32.5", AmountInput.fromCents(3250).text)
        assertEquals("1234.05", AmountInput.fromCents(123405).text)
    }

    @Test fun formatting() {
        assertEquals("1,342.00", formatCents(134200))
        assertEquals("-0.05", formatCents(-5))
        assertEquals("210.5", formatCentsShort(21050))
        assertEquals("4,500", formatCentsShort(450000))
    }
}
