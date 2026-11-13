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
    fun contractionIsExactBelowFloor() {
        assertEquals(0.1f, 1f - contractionK(20f), 1e-6f) // assumes CONTRACTION_MIN_SCALE = 0.1
        assertEquals(1f - 1f / 5f, contractionK(5f), 1e-6f)
    }

    @Test
    fun gammaOneNoContraction() {
        assertEquals(0f, contractionK(1f), 1e-6f)
    }

    @Test
    fun tidalOffsetIsIdentityAtScaleOne() {
        tidalOffset(3f, -2f, 0.6f, 0.8f, 1f, out)
        assertEquals(3f, out.x, 1e-6f)
        assertEquals(-2f, out.y, 1e-6f)
    }

    @Test
    fun tidalOffsetStretchesAlongRadiusAndPreservesArea() {
        val s = TIDAL_STRETCH_MAX
        val rx = 0.6f
        val ry = 0.8f
        // Image of the unit axes of the basis (r, perp): determinant of the linear map.
        tidalOffset(1f, 0f, rx, ry, s, out)
        val ax = out.x
        val ay = out.y
        tidalOffset(0f, 1f, rx, ry, s, out)
        assertEquals(1f, ax * out.y - ay * out.x, 1e-5f)
        tidalOffset(rx, ry, rx, ry, s, out)
        assertEquals(s, out.len(), 1e-5f)
        tidalOffset(-ry, rx, rx, ry, s, out)
        assertEquals(1f / s, out.len(), 1e-5f)
    }

    @Test
    fun tidalStretchIsClamped() {
        assertEquals(1f, tidalStretch(0f), 1e-6f)
        assertEquals(TIDAL_STRETCH_MAX, tidalStretch(100f), 1e-6f)
    }
}
