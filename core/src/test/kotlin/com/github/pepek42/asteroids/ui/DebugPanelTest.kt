package com.github.pepek42.asteroids.ui

import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.debug.DebugSettings
import com.github.pepek42.asteroids.physics.BRAKE_BOOST_K
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import kotlin.math.atanh
import kotlin.math.sqrt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DebugPanelTest {
    @Test
    fun formatReadoutsShowsRelativisticQuantities() {
        val c = 60f
        val beta = 0.9f
        val gamma = 1f / sqrt(1f - beta * beta)
        val observer = ObserverState().apply {
            betaRaw.set(beta, 0f)
            gammaRaw = gamma
            rapidity = atanh(beta.toDouble()).toFloat()
        }
        val rel = RelativisticComponent().apply { u.set(gamma * beta * c, 0f) }
        val out = StringBuilder()
        formatReadouts(observer, rel, c, 25f, out)
        val s = out.toString()
        assertTrue("β = 0.900" in s, s)
        assertTrue("γ = 2.29" in s, s)
        assertTrue("|u| = γ|v| = 123.9" in s, s)
        assertTrue("ship-frame speed = 88.3" in s, s)
    }

    @Test
    fun formatReadoutsShowsWarpLine() {
        val clock = com.github.pepek42.asteroids.physics.GameClock().apply { warp = 4f; stepsCapped = true }
        val out = StringBuilder()
        formatReadouts(ObserverState(), null, 60f, 25f, out, clock)
        assertTrue("warp = ×4.0 (capped)" in out.toString(), out.toString())
        clock.stepsCapped = false
        formatReadouts(ObserverState(), null, 60f, 25f, out, clock)
        assertTrue(out.toString().endsWith("warp = ×4.0"), out.toString())
    }

    @Test
    fun resetRestoresTimeWarp() {
        val rel = RelativitySettings(timeWarpEnabled = false, timeWarpMax = 3f)
        resetDebugDefaults(rel, DebugSettings(panelAllowed = true, panelVisible = true, timeScale = 1f, showVectors = false))
        assertTrue(rel.timeWarpEnabled)
        assertEquals(com.github.pepek42.asteroids.physics.TIME_WARP_MAX, rel.timeWarpMax)
    }

    @Test
    fun appendFixedIsLocaleIndependentAndPadsDecimals() {
        assertEquals("0.050", appendFixed(StringBuilder(), 0.05f, 3).toString())
        assertEquals("-1.5", appendFixed(StringBuilder(), -1.5f, 1).toString())
        assertEquals("60", appendFixed(StringBuilder(), 60f, 0).toString())
    }

    @Test
    fun resetRestoresDefaults() {
        val rel = RelativitySettings(c = 30f, engineProperAccel = 80f, fieldEnabled = false)
        val debug = DebugSettings(panelAllowed = true, panelVisible = true, timeScale = 0.25f, showVectors = true,
            gridEnabled = false, targetPointersEnabled = false)
        resetDebugDefaults(rel, debug)
        assertTrue(debug.gridEnabled)
        assertTrue(debug.targetPointersEnabled)
        assertEquals(SPEED_OF_LIGHT, rel.c)
        assertEquals(1f, debug.timeScale)
        assertTrue(rel.fieldEnabled)
    }

    @Test
    fun resetRestoresBrakeBoost() {
        val rel = RelativitySettings(brakeBoost = 0f)
        resetDebugDefaults(rel, DebugSettings(panelAllowed = true, panelVisible = true, timeScale = 1f, showVectors = false))
        assertEquals(BRAKE_BOOST_K, rel.brakeBoost)
    }
}
