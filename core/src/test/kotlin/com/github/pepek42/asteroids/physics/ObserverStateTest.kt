package com.github.pepek42.asteroids.physics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ObserverStateTest {
    @Test
    fun reachesSixtyThreePercentAfterOneTimeConstant() {
        val r = smoothToward(0f, 1f, 0.15f, 0.15f)
        assertEquals(0.632f, r, 0.01f)
    }

    @Test
    fun stableForHugeDt() {
        val r = smoothToward(0f, 1f, 1000f, 0.15f)
        assertTrue(r in 0.999f..1f)
    }

    @Test
    fun resetClearsState() {
        val s = ObserverState()
        s.gammaRaw = 3f
        s.betaVisual.set(1f, 1f)
        s.gravFactor = 0.4f
        s.reset()
        assertEquals(1f, s.gravFactor)
        assertEquals(1f, s.gammaRaw)
        assertEquals(0f, s.betaVisual.x)
    }
}
