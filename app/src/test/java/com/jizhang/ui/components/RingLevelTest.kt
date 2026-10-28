package com.jizhang.ui.components

import com.jizhang.domain.BudgetLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class RingLevelTest {
    @Test
    fun greenUpToHalf() {
        assertEquals(BudgetLevel.Green, ringLevel(0))
        assertEquals(BudgetLevel.Green, ringLevel(50))
    }

    @Test
    fun yellowBetweenHalfAndEighty() {
        assertEquals(BudgetLevel.Yellow, ringLevel(51))
        assertEquals(BudgetLevel.Yellow, ringLevel(79))
    }

    @Test
    fun redFromEightyAndBeyondFull() {
        assertEquals(BudgetLevel.Red, ringLevel(80))
        assertEquals(BudgetLevel.Red, ringLevel(100))
        assertEquals(BudgetLevel.Red, ringLevel(240))
    }
}
