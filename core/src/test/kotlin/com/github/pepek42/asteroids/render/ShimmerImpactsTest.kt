package com.github.pepek42.asteroids.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShimmerImpactsTest {
    @Test
    fun sameIdWithinRefreshStoresOne() {
        val s = ShimmerImpacts()
        s.emit(1, 0f, 0f, 1f)
        s.time = 0.2f
        s.emit(1, 0f, 0f, 1f)
        assertEquals(1, s.count)
    }

    @Test
    fun sameIdAfterRefreshStoresTwo() {
        val s = ShimmerImpacts()
        s.emit(1, 0f, 0f, 1f)
        s.time = 0.3f
        s.emit(1, 0f, 0f, 1f)
        assertEquals(2, s.count)
    }

    @Test
    fun forceBypassesThrottle() {
        val s = ShimmerImpacts()
        s.emit(1, 0f, 0f, 1f)
        s.emit(1, 0f, 0f, 1f, force = true)
        assertEquals(2, s.count)
    }

    @Test
    fun overflowKeepsCapacityAndEvictsWeakestDecayed() {
        val s = ShimmerImpacts()
        for (i in 0 until 16) {
            s.emit(i, i.toFloat(), 0f, 1f + i) // impact 0 is the weakest
        }
        s.time = 0.1f
        s.emit(100, 99f, 0f, 5f)
        assertEquals(16, s.count)
        assertEquals(99f, s.data[0])
        for (i in 1 until 16) assertEquals(i.toFloat(), s.data[i * 4])
        for (i in 16 until 20) s.emit(200 + i, 0f, 0f, 2f)
        assertEquals(16, s.count)
    }

    @Test
    fun pruneRemovesOldAndCompacts() {
        val s = ShimmerImpacts()
        s.emit(1, 1f, 0f, 1f)
        s.time = 1f
        s.emit(2, 2f, 0f, 1f)
        s.time = 1.6f
        s.prune()
        assertEquals(1, s.count)
        assertEquals(2f, s.data[0])
    }
}
