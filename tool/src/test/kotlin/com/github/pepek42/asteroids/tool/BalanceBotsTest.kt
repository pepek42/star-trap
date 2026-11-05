package com.github.pepek42.asteroids.tool

import kotlin.math.PI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BalanceBotsTest {
    private val c = 20f
    private val command = BotCommand()

    private fun view(vx: Float = 0f, vy: Float = 0f, hull: Float = 0f, vararg asteroids: Pair<Float, Float>): BotView {
        val v = BotView(2)
        v.px = 100f
        v.py = 100f
        v.vx = vx
        v.vy = vy
        v.hullAngle = hull
        v.c = c
        asteroids.forEach { v.addAsteroid(100f + it.first, 100f + it.second) }
        return v
    }

    @Test
    fun nearestPicksClosestAndGrowsPastCapacity() {
        val v = view(asteroids = arrayOf(30f to 0f, 10f to 0f, 20f to 0f))
        assertEquals(1, v.nearest())
        assertEquals(3, v.asteroidCount)
    }

    @Test
    fun cautiousHuntsFarTargetAndThrustsWhenAligned() {
        CautiousBot().decide(view(hull = 0f, asteroids = arrayOf(35f to 0f)), command)
        assertEquals(0f, command.aimRad!!, 1e-6f)
        assertEquals(1f, command.thrust)
    }

    @Test
    fun cautiousDoesNotThrustUntilAligned() {
        CautiousBot().decide(view(hull = 1.5f, asteroids = arrayOf(35f to 0f)), command)
        assertEquals(0f, command.thrust)
    }

    @Test
    fun cautiousSteersAwayFromThreatInsideRadius() {
        CautiousBot().decide(view(hull = PI.toFloat(), asteroids = arrayOf(10f to 0f)), command)
        assertEquals(PI.toFloat(), command.aimRad!!, 1e-5f)
        assertEquals(1f, command.thrust)
    }

    @Test
    fun cautiousBrakesRetrogradeWithMainEngineAboveBetaLimit() {
        // beta 0.6 along +x, hull already retrograde: burn forward against the motion.
        CautiousBot().decide(view(vx = 0.6f * c, hull = PI.toFloat(), asteroids = arrayOf(35f to 0f)), command)
        assertEquals(PI.toFloat(), command.aimRad!!, 1e-5f)
        assertEquals(1f, command.thrust)
    }

    @Test
    fun cautiousUsesReverseThrustersWhileHullStillPointsAlongTheMotion() {
        CautiousBot().decide(view(vx = 0.6f * c, hull = 0f, asteroids = arrayOf(35f to 0f)), command)
        assertEquals(-1f, command.thrust)
    }

    @Test
    fun cautiousNeverAcceleratesAtOrAboveBetaLimit() {
        CautiousBot().decide(view(vx = 0.5f * c, hull = 0f, asteroids = arrayOf(35f to 0f)), command)
        assertTrue(command.thrust <= 0f)
    }

    @Test
    fun cautiousFiresOnlyInsideThirtyDegreeCone() {
        val inside = view(hull = 0.2f, asteroids = arrayOf(30f to 0f))
        CautiousBot().decide(inside, command)
        assertTrue(command.fire)
        val outside = view(hull = 0.3f, asteroids = arrayOf(30f to 0f))
        CautiousBot().decide(outside, command)
        assertFalse(command.fire)
        val tooFar = view(hull = 0f, asteroids = arrayOf(60f to 0f))
        CautiousBot().decide(tooFar, command)
        assertFalse(command.fire)
    }

    @Test
    fun cautiousWithoutTargetsJustBrakes() {
        CautiousBot().decide(view(vx = 0.3f * c, hull = PI.toFloat()), command)
        assertFalse(command.fire)
        assertEquals(1f, command.thrust)
    }

    @Test
    fun aggressiveThrustsAndFiresAtNearest() {
        AggressiveBot().decide(view(asteroids = arrayOf(0f to 20f, 0f to 5f)), command)
        assertEquals((PI / 2).toFloat(), command.aimRad!!, 1e-5f)
        assertEquals(1f, command.thrust)
        assertTrue(command.fire)
    }

    @Test
    fun idleDoesNothing() {
        IdleBot().decide(view(asteroids = arrayOf(5f to 0f)), command)
        assertEquals(0f, command.thrust)
        assertFalse(command.fire)
        assertNull(command.aimRad)
    }

    @Test
    fun botByNameKnowsTheExpert() {
        assertEquals("expert", botByName("expert").name)
    }

    @Test
    fun botByNameKnowsTheThreeBots() {
        assertEquals(listOf("cautious", "aggressive", "idle"), listOf("cautious", "AGGRESSIVE", "idle").map { botByName(it).name })
    }
}
