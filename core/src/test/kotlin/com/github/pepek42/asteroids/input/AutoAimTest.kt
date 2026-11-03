package com.github.pepek42.asteroids.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class AutoAimTest {
    private val cone = AUTO_AIM_CONE_HALF_RAD
    private val range = AUTO_AIM_RANGE

    private fun polar(dist: Float, angleRad: Float) = Pair(dist * cos(angleRad), dist * sin(angleRad))

    private fun pick(facing: Float, vararg candidates: Pair<Float, Float>, sx: Float = 0f, sy: Float = 0f): Int {
        val xs = FloatArray(candidates.size) { candidates[it].first }
        val ys = FloatArray(candidates.size) { candidates[it].second }
        return pickTargetIndex(sx, sy, facing, cone, range, xs, ys, candidates.size)
    }

    @Test
    fun picksNearestInConeTarget() {
        val far = polar(30f, 0.1f)
        val near = polar(10f, -0.2f)
        assertEquals(1, pick(0f, far, near))
    }

    @Test
    fun skipsNearerTargetOutsideCone() {
        val off = polar(5f, cone + 0.1f) // just outside the half-angle
        val ahead = polar(20f, 0f)
        assertEquals(1, pick(0f, off, ahead))
    }

    @Test
    fun skipsTargetBeyondRange() {
        val beyond = polar(range + 1f, 0f)
        val ahead = polar(range - 1f, 0f)
        assertEquals(1, pick(0f, beyond, ahead))
        assertEquals(-1, pick(0f, beyond))
    }

    @Test
    fun returnsMinusOneForNone() {
        assertEquals(-1, pick(0f))
        assertEquals(-1, pick(0f, polar(10f, (PI / 2).toFloat())))
    }

    @Test
    fun coneWrapsAroundPi() {
        // Facing just below +PI, target just above -PI: 0.2 rad apart through the wrap.
        val target = polar(10f, -3.04f)
        assertEquals(0, pick(3.1f, target))
    }

    @Test
    fun usesShipPositionAndIgnoresEntriesBeyondN() {
        val xs = floatArrayOf(11f, 10.5f)
        val ys = floatArrayOf(5f, 5f)
        assertEquals(0, pickTargetIndex(10f, 5f, 0f, cone, range, xs, ys, 1))
    }
}
