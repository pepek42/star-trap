package com.github.pepek42.asteroids.component

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RocketPlumeTest {
    @Test
    fun edges() {
        assertTrue(plumeVisible(2f, 2f, 3.5f))
        assertFalse(plumeVisible(1.99f, 2f, 3.5f))
        assertFalse(plumeVisible(3.5f, 2f, 3.5f))
        assertTrue(plumeVisible(3.49f, 2f, 3.5f))
        assertTrue(plumeVisible(100f, 2f, Float.POSITIVE_INFINITY))
    }

    @Test
    fun lightDelayedBurnout() {
        val lag = 0.8f
        assertTrue(plumeVisible(1.5f - lag, 0f, 1f))
        assertFalse(plumeVisible(1.9f - lag, 0f, 1f))
    }

    @Test
    fun resetRestoresBurning() {
        val r = RocketComponent().apply { burnEndTime = 1f; launchTime = 2f }
        r.reset()
        assertTrue(r.burnEndTime == Float.POSITIVE_INFINITY && r.launchTime == 0f)
    }
}
