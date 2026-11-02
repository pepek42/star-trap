package com.github.pepek42.asteroids.ui

import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.debug.DebugSettings
import com.github.pepek42.asteroids.system.DEBUG_PANEL_REFRESH_SECONDS
import com.github.pepek42.asteroids.system.RefreshPacer
import com.github.pepek42.asteroids.physics.BRAKE_BOOST_K
import com.github.pepek42.asteroids.physics.ENGINE_PROPER_ACCEL
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import kotlin.math.atanh
import kotlin.math.sqrt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DebugPanelTest {
    private fun fixed1(v: Float) = appendFixed(StringBuilder(), v, 1).toString()


    @Test
    fun formatReadoutsShowsRelativisticQuantities() {
        val c = SPEED_OF_LIGHT
        val beta = 0.9f
        val gamma = 1f / sqrt(1f - beta * beta)
        val observer = ObserverState().apply {
            betaRaw.set(beta, 0f)
            gammaRaw = gamma
            rapidity = atanh(beta.toDouble()).toFloat()
        }
        val rel = RelativisticComponent().apply { u.set(gamma * beta * c, 0f) }
        val out = StringBuilder()
        formatReadouts(observer, rel, c, ENGINE_PROPER_ACCEL, out)
        val s = out.toString()
        assertTrue("beta = 0.900" in s, s)
        assertTrue("gamma = 2.29" in s, s)
        assertTrue("|u| = gamma|v| = ${fixed1(gamma * beta * c)}" in s, s)
        assertTrue(s.all { it.code < 128 }, "readout must stay ASCII for the UI font")
        assertTrue("ship-frame speed = ${fixed1(observer.rapidity * c)}" in s, s)
    }

    @Test
    fun formatReadoutsShowsWarpLine() {
        val clock = com.github.pepek42.asteroids.physics.GameClock().apply { warp = 4f; stepsCapped = true }
        val out = StringBuilder()
        formatReadouts(ObserverState(), null, SPEED_OF_LIGHT, ENGINE_PROPER_ACCEL, out, clock)
        assertTrue("warp = ×4.0 (capped)" in out.toString(), out.toString())
        clock.stepsCapped = false
        formatReadouts(ObserverState(), null, SPEED_OF_LIGHT, ENGINE_PROPER_ACCEL, out, clock)
        assertTrue(out.toString().endsWith("warp = ×4.0"), out.toString())
    }

    @Test
    fun formatReadoutsShowsSpeedPercentVectorAndMass() {
        val c = SPEED_OF_LIGHT
        val observer = ObserverState().apply {
            betaRaw.set(0.75f, 0f)
            gammaRaw = 1.5f
        }
        val out = StringBuilder()
        formatReadouts(observer, null, c, ENGINE_PROPER_ACCEL, out, null, 2f)
        val s = out.toString()
        assertTrue("|v| = ${fixed1(0.75f * c)} u/s (75.0 % c)" in s, s)
        assertTrue("v = (${fixed1(0.75f * c)}, 0.0) u/s" in s, s)
        assertTrue("m = 2.0   gamma*m = 3.0" in s, s)
        formatReadouts(observer, null, c, ENGINE_PROPER_ACCEL, out)
        assertTrue("m =" !in out.toString(), out.toString())
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
        assertEquals("60", appendFixed(StringBuilder(), 60f, 0).toString()) // formatting input, not a game constant
    }

    @Test
    fun resetRestoresDefaults() {
        val rel = RelativitySettings(c = 30f, engineAccelPerC = 1.5f, fieldEnabled = false)
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
    fun togglingPanelTwiceRestoresVisibility() {
        val debug = DebugSettings(panelAllowed = true, panelVisible = false)
        assertTrue(flipPanelVisible(debug))
        assertTrue(debug.panelVisible)
        assertEquals(false, flipPanelVisible(debug))
        assertEquals(false, debug.panelVisible)
    }

    @Test
    fun refreshPacerFiresOnRealTimeInterval() {
        val pacer = RefreshPacer(DEBUG_PANEL_REFRESH_SECONDS)
        // First call refreshes immediately (timer starts full), then sub-interval slices accumulate.
        assertTrue(pacer.due(0f))
        assertEquals(false, pacer.due(DEBUG_PANEL_REFRESH_SECONDS / 2))
        assertTrue(pacer.due(DEBUG_PANEL_REFRESH_SECONDS / 2))
        assertEquals(false, pacer.due(DEBUG_PANEL_REFRESH_SECONDS / 2))
    }

    @Test
    fun minimapShiftsLeftByPanelWidth() {
        val plain = minimapBounds(1000, 600)
        val shifted = minimapBounds(1000, 600, DEBUG_PANEL_WIDTH)
        assertEquals(plain.x - DEBUG_PANEL_WIDTH, shifted.x, 0.001f)
        assertEquals(plain.width, shifted.width, 0.001f)
    }

    @Test
    fun resetRestoresBrakeBoost() {
        val rel = RelativitySettings(brakeBoost = 0f)
        resetDebugDefaults(rel, DebugSettings(panelAllowed = true, panelVisible = true, timeScale = 1f, showVectors = false))
        assertEquals(BRAKE_BOOST_K, rel.brakeBoost)
    }
}
