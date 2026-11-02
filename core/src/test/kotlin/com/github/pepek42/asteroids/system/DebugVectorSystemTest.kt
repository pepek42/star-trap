package com.github.pepek42.asteroids.system

import com.badlogic.gdx.math.MathUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

class DebugVectorSystemTest {
    private val head = 0.4f
    private val angle = 25f * MathUtils.degreesToRadians

    @Test
    fun arrowHasShaftAndHeads() {
        val out = FloatArray(ARROW_FLOATS)
        arrowSegments(0f, 0f, 4f, 0f, 0.25f, head, angle, out)
        assertEquals(0f, out[0], 1e-5f)
        assertEquals(0f, out[1], 1e-5f)
        assertEquals(1f, out[2], 1e-5f)
        assertEquals(0f, out[3], 1e-5f)
        val hx = 1f - head * cos(angle)
        val hy = head * sin(angle)
        assertEquals(hx, out[6], 1e-5f)
        assertEquals(hx, out[10], 1e-5f)
        assertEquals(1f, out[4], 1e-5f)
        assertEquals(1f, out[8], 1e-5f)
        // The two heads are mirrored, either order.
        assertEquals(hy, Math.abs(out[7]), 1e-5f)
        assertEquals(-out[7], out[11], 1e-5f)
    }

    @Test
    fun zeroVectorDrawsNothing() {
        val out = FloatArray(ARROW_FLOATS) { Float.NaN }
        arrowSegments(3f, 4f, 0f, 0f, 0.25f, head, angle, out)
        assertFalse(out.any { it.isNaN() })
        assertEquals(0f, out.sum(), 0f)
    }
}
