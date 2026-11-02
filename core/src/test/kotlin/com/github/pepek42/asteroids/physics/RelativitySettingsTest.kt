package com.github.pepek42.asteroids.physics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class RelativitySettingsTest {
    @Test
    fun cIsClampedToSafeRange() {
        assertEquals(67f, RelativitySettings(c = 100f).c)
        assertEquals(C_MIN, RelativitySettings().apply { c = 1f }.c)
    }

    @Test
    fun gammaMaxIsClamped() {
        assertEquals(2f, RelativitySettings(gammaMaxMassive = 1f).gammaMaxMassive)
        assertEquals(GAMMA_MAX_MASSIVE, RelativitySettings().apply { gammaMaxMassive = 1000f }.gammaMaxMassive)
    }

    @Test
    fun engineAccelIsClamped() {
        assertEquals(5f, RelativitySettings(engineProperAccel = 0f).engineProperAccel)
        assertEquals(120f, RelativitySettings().apply { engineProperAccel = 999f }.engineProperAccel)
    }

    @Test
    fun brakeBoostIsClamped() {
        assertEquals(5f, RelativitySettings(brakeBoost = 9f).brakeBoost)
        assertEquals(0f, RelativitySettings().apply { brakeBoost = -1f }.brakeBoost)
    }

    @Test
    fun timeWarpMaxIsClamped() {
        assertEquals(12f, RelativitySettings(timeWarpMax = 50f).timeWarpMax)
        assertEquals(1f, RelativitySettings().apply { timeWarpMax = 0f }.timeWarpMax)
    }

    @Test
    fun defaultsMatchConstants() {
        val s = RelativitySettings()
        assertEquals(SPEED_OF_LIGHT, s.c)
        assertEquals(ENGINE_PROPER_ACCEL, s.engineProperAccel)
        assertEquals(BETA_MAX_MASSIVE, s.betaMaxMassive, 1e-6f)
    }

    @Test
    fun fieldDampingUpdatesParamsAndKeepsTheRest() {
        val s = RelativitySettings(fieldParams = BoundaryFieldParams(skin = 8f))
        s.fieldDamping = 2f
        assertEquals(2f, s.fieldParams.damping)
        assertEquals(2f, s.fieldDamping)
        assertEquals(8f, s.fieldParams.skin)
        s.fieldDamping = 50f
        assertEquals(5f, s.fieldDamping)
    }

    @Test
    fun fieldDampingDoesNotAllocateWhenUnchanged() {
        val s = RelativitySettings()
        val before = s.fieldParams
        s.fieldDamping = before.damping
        assertSame(before, s.fieldParams)
    }
}
