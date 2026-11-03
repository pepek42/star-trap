package com.github.pepek42.asteroids.render

import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RelativityVisualsTest {
    private val out = Vector2()

    @Test
    fun zeroKIsIdentity() {
        contractOffset(3f, -2f, 0.6f, 0.8f, 0f, out)
        assertEquals(3f, out.x, 1e-6f)
        assertEquals(-2f, out.y, 1e-6f)
    }

    @Test
    fun alongVelocityScaledByOneOverGamma() {
        val gamma = 2.294f
        contractOffset(1f, 0f, 1f, 0f, 1f - 1f / gamma, out)
        assertEquals(0.436f, out.x, 1e-3f)
        assertEquals(0f, out.y, 1e-6f)
    }

    @Test
    fun perpendicularUnchanged() {
        contractOffset(0f, 2f, 1f, 0f, 0.7f, out)
        assertEquals(0f, out.x, 1e-6f)
        assertEquals(2f, out.y, 1e-6f)
    }

    @Test
    fun gamma50ClampsToMinScale() {
        contractOffset(1f, 0f, 1f, 0f, contractionK(50f), out)
        assertEquals(CONTRACTION_MIN_SCALE, out.x, 1e-6f)
    }

    @Test
    fun gammaOneNoContraction() {
        assertEquals(0f, contractionK(1f), 1e-6f)
    }
}
