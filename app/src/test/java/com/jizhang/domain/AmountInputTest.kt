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
