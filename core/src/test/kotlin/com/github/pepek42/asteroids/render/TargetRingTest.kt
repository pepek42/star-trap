package com.github.pepek42.asteroids.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TargetRingTest {
    @Test
    fun ringClearsTheHullByThePad() {
        val wpp = 0.05f
        assertEquals(2.25f + TARGET_RING_PAD_PX * wpp, targetRingRadius(2.25f, wpp), 1e-5f)
    }

    @Test
    fun tinyOrFarTargetsKeepTheMinimumScreenSize() {
        val wpp = 0.5f
        assertEquals(TARGET_RING_MIN_RADIUS_PX * wpp, targetRingRadius(0.1f, wpp), 1e-5f)
    }

    @Test
    fun coreIsSteadyAndGlowFadesOutwardAndPulses() {
        val q = TARGET_RING_PULSE_PERIOD_S / 4f
        assertEquals(TARGET_RING_LAYER_ALPHA[0], targetRingLayerAlpha(0, q), 1e-6f)
        for (layer in 1 until TARGET_RING_LAYER_ALPHA.size) {
            assertTrue(targetRingLayerAlpha(layer, 0f) < targetRingLayerAlpha(layer - 1, 0f))
            assertTrue(targetRingLayerAlpha(layer, q) > targetRingLayerAlpha(layer, 0f))
        }
    }
}
