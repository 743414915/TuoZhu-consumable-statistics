package com.tuozhu.consumablestatistics.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightMathTest {
    @Test
    fun clampRemaining_neverDropsBelowZero() {
        assertEquals(0, WeightMath.clampRemaining(-20, 1000))
    }

    @Test
    fun clampRemaining_neverExceedsInitial() {
        assertEquals(1000, WeightMath.clampRemaining(1200, 1000))
    }

    @Test
    fun progress_returnsFraction() {
        assertEquals(0.5f, WeightMath.progress(500, 1000))
    }

    @Test
    fun lowStock_detectsThresholdOrBelow() {
        assertTrue(WeightMath.isLowStock(150, 150))
        assertTrue(WeightMath.isLowStock(80, 150))
        assertFalse(WeightMath.isLowStock(151, 150))
    }
}

