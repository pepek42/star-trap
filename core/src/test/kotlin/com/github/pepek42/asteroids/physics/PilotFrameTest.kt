package com.github.pepek42.asteroids.physics

import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PilotFrameTest {

    private val out = Vector2()
    private val seed = 72L

    private fun gamma(beta: Float): Float = 1f / sqrt(1f - beta * beta)

    /** Random |β| ≤ min(0.95, BETA_MAX_MASSIVE) so the γ clamp never engages in identity checks. */
    private fun randomBeta(rnd: Random): Vector2 {
        val maxBeta = minOf(0.95f, BETA_MAX_MASSIVE)
        val len = rnd.nextFloat() * maxBeta
        val a = rnd.nextDouble() * 2 * PI
        return Vector2((len * cos(a)).toFloat(), (len * sin(a)).toFloat())
    }

    private fun wrap(a: Float): Float {
        var x = a
        while (x > PI) x -= (2 * PI).toFloat()
        while (x <= -PI) x += (2 * PI).toFloat()
        return x
    }

    @Test
    fun apparentOffsetShouldSlideSideObjectForward() {
        apparentOffset(0f, 10f, 0.9f, 0f, out)
        assertEquals(20.65f, out.x, 0.01f)
        assertEquals(10f, out.y, 0.01f)
        assertEquals(22.94f, out.len(), 0.01f)
        assertEquals(25.84, Math.toDegrees(atan2(out.y, out.x).toDouble()), 0.05)
    }

    @Test
    fun apparentOffsetShouldBeIdentityAtZeroBeta() {
        apparentOffset(3f, -4f, 0f, 0f, out)
        assertEquals(3f, out.x, 1e-6f)
        assertEquals(-4f, out.y, 1e-6f)
    }

    @Test
    fun apparentOffsetAheadShouldScaleByDoppler() {
        apparentOffset(10f, 0f, 0.9f, 0f, out)
        assertEquals(43.59f, out.x, 0.01f)
        assertEquals(0f, out.y, 1e-4f)
    }

    @Test
    fun apparentOffsetLengthShouldMatchDopplerFactor() {
        val rnd = Random(seed)
        repeat(20) {
            val rx = rnd.nextFloat() * 20f - 10f
            val ry = rnd.nextFloat() * 20f - 10f
            val b = randomBeta(rnd)
            val bx = b.x
            val by = b.y
            val rLen = sqrt(rx * rx + ry * ry)
            val beta = sqrt(bx * bx + by * by)
            val cosTheta = (bx * rx + by * ry) / (beta * rLen)
            apparentOffset(rx, ry, bx, by, out)
            val expected = gamma(beta) * (1f + beta * cosTheta) * rLen
            assertEquals(1f, out.len() / expected, 1e-3f)
        }
    }

    @Test
    fun worldHullToPilotShouldContractAtFortyFiveDegrees() {
        val angle = worldHullToPilot(Math.toRadians(45.0).toFloat(), 0.9f, 0f)
        assertEquals(23.55, Math.toDegrees(angle.toDouble()), 0.05)
    }

    @Test
    fun pilotHullToWorldShouldStretchAtFortyFiveDegrees() {
        val angle = pilotHullToWorld(Math.toRadians(45.0).toFloat(), 0.9f, 0f)
        assertEquals(66.45, Math.toDegrees(angle.toDouble()), 0.05)
    }

    @Test
    fun hullTransformsShouldRoundTrip() {
        val rnd = Random(seed)
        repeat(20) {
            val a = (rnd.nextFloat() * 2 - 1) * PI.toFloat()
            val b = randomBeta(rnd)
            val bx = b.x
            val by = b.y
            val there = worldHullToPilot(a, bx, by)
            val back = pilotHullToWorld(there, bx, by)
            assertEquals(0f, abs(wrap(back - a)), 1e-5f)
        }
    }

    @Test
    fun hullParallelOrPerpendicularToBetaShouldBeUnchanged() {
        val beta = 0.9f
        assertEquals(0f, worldHullToPilot(0f, beta, 0f), 1e-6f)
        assertEquals(PI.toFloat() / 2, worldHullToPilot(PI.toFloat() / 2, beta, 0f), 1e-6f)
        assertEquals(0f, pilotHullToWorld(0f, beta, 0f), 1e-6f)
        assertEquals(PI.toFloat() / 2, pilotHullToWorld(PI.toFloat() / 2, beta, 0f), 1e-6f)
    }

    @Test
    fun zeroBetaShouldBeIdentityForHulls() {
        assertEquals(0.7f, worldHullToPilot(0.7f, 0f, 0f), 1e-6f)
        assertEquals(0.7f, pilotHullToWorld(0.7f, 0f, 0f), 1e-6f)
    }
}
