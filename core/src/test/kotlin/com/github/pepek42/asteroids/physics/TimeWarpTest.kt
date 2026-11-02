package com.github.pepek42.asteroids.physics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimeWarpTest {
    @Test
    fun warpFactorIsGammaClampedToRange() {
        assertEquals(3f, timeWarpFactor(3f, true, 10f))
        assertEquals(10f, timeWarpFactor(25f, true, 10f))
        assertEquals(1f, timeWarpFactor(0.5f, true, 10f))
        assertEquals(1f, timeWarpFactor(3f, false, 10f))
        assertEquals(1f, timeWarpFactor(Float.NaN, true, 10f))
    }

    @Test
    fun warpCapIsDetected() {
        assertTrue(isWarpCapped(25f, true, 10f))
        assertFalse(isWarpCapped(10f, true, 10f))
        assertFalse(isWarpCapped(25f, false, 10f))
    }

    @Test
    fun worldDeltaIsLimitedByMaxStep() {
        assertEquals(1f / 6f, worldDelta(1f / 60f, 10f, 10f / 45f), 1e-5f)
        assertEquals(10f / 45f, worldDelta(0.1f, 10f, 10f / 45f))
    }

    @Test
    fun gameClockResetRestoresDefaults() {
        val clock = GameClock().apply {
            realDt = 0.1f
            warp = 5f
            worldDt = 0.2f
            warpCapped = true
            stepsCapped = true
            worldTime = 3f
            interpolationAlpha = 0.5f
        }
        clock.reset()
        assertEquals(0f, clock.realDt)
        assertEquals(1f, clock.warp)
        assertEquals(0f, clock.worldDt)
        assertFalse(clock.warpCapped)
        assertFalse(clock.stepsCapped)
        assertEquals(0f, clock.worldTime)
        assertEquals(0f, clock.interpolationAlpha)
    }
}
