package com.github.pepek42.asteroids.ai

import com.github.pepek42.asteroids.math.shortestAngleDiffRad
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class EnemyPilotTest {
    private val standoff = 20f
    private val accel = 10f
    private val vMax = 10f
    private val out = PilotCommand()

    @Test
    fun farTargetAheadThrustsTowardIt() {
        pursuitCommand(0f, 0f, 0f, 0f, 0f, 100f, 0f, 0f, 0f, standoff, accel, vMax, out)
        assertEquals(1f, out.thrust)
        assertEquals(0f, out.headingRad, 1e-5f)
        assertFalse(out.aiming)
    }

    @Test
    fun atStandoffMatchedVelocityAims() {
        pursuitCommand(0f, 0f, 3f, 1f, 0f, standoff, 0f, 3f, 1f, standoff, accel, vMax, out)
        assertTrue(out.aiming)
        assertEquals(0f, out.thrust)
    }

    @Test
    fun closingTooFastBrakes() {
        pursuitCommand(0f, 0f, 10f, 0f, 0f, 22f, 0f, 0f, 0f, standoff, accel, vMax, out)
        assertTrue(abs(shortestAngleDiffRad(Math.PI.toFloat(), out.headingRad)) < 1e-4f)
    }

    @Test
    fun hullOffHeadingDoesNotThrust() {
        pursuitCommand(0f, 0f, 0f, 0f, Math.PI.toFloat() / 2f, 100f, 0f, 0f, 0f, standoff, accel, vMax, out)
        assertEquals(0f, out.thrust)
    }

    @Test
    fun coincidentAims() {
        pursuitCommand(1f, 1f, 0f, 0f, 0.7f, 1f, 1f, 0f, 0f, standoff, accel, vMax, out)
        assertTrue(out.aiming)
        assertEquals(0.7f, out.headingRad)
    }
}
