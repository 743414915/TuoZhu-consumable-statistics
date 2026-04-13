package com.tuozhu.consumablestatistics.domain

object WeightMath {
    fun clampRemaining(remaining: Int, initial: Int): Int {
        return remaining.coerceIn(0, initial.coerceAtLeast(0))
    }

    fun progress(remaining: Int, initial: Int): Float {
        if (initial <= 0) return 0f
        return (remaining.toFloat() / initial.toFloat()).coerceIn(0f, 1f)
    }

    fun isLowStock(remaining: Int, threshold: Int): Boolean {
        return remaining <= threshold.coerceAtLeast(0)
    }
}

