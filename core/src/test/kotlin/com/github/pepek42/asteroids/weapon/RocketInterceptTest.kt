package com.github.pepek42.asteroids.weapon

import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.tanh

/**
 * ST-182: the pod-rocket fire computer solves the intercept for the rocket's own accelerating flight. These are pure
 * solver tests; `RocketInterceptIntegrationTest` fires a real Box2D rocket along the solved aim.
 */
class RocketInterceptTest {
    private val settings = RelativitySettings()
    private val c = settings.c
    private val properAccel = ROCKET_ACCEL_FACTOR * settings.engineProperAccel

    private fun solve(
        shipX: Float,
        shipY: Float,
        shipUx: Float,
        shipUy: Float,
        tx: Float,
        ty: Float,
        tvx: Float,
        tvy: Float,
        out: Vector2,
    ): Float = rocketInterceptDir(
        shipX, shipY, shipUx, shipUy, tx, ty, tvx, tvy,
        properAccel, ROCKET_BURN_SECONDS, c, ROCKET_INTERCEPT_MAX_SECONDS, PHYSICS_UPDATE_INTERVAL, out,
    )

    @Test
    fun staticTargetStraightAheadIsAimedAtDeadAhead() {
        val out = Vector2()
        val hitTime = solve(0f, 0f, 0f, 0f, 0f, 30f, 0f, 0f, out)
        assertTrue(hitTime.isFinite(), "no solution for a static target ahead")
        assertTrue(hitTime > 0f && hitTime < ROCKET_INTERCEPT_MAX_SECONDS, "hit time $hitTime")
        assertEquals(0f, out.x, 1e-4f)
        assertEquals(1f, out.y, 1e-4f)
        assertEquals(1f, out.len(), 1e-4f)
    }

    @Test
    fun staticTargetOffTheHullIsAimedAtItsBearing() {
        val out = Vector2()
        val hitTime = solve(0f, 0f, 0f, 0f, 18f, 24f, 0f, 0f, out)
        assertTrue(hitTime.isFinite(), "no solution for a static target to the upper right")
        // From rest the rocket flies a straight line, so the bearing is the answer: 18-24-30 triangle.
        assertEquals(0.6f, out.x, 1e-3f)
        assertEquals(0.8f, out.y, 1e-3f)
    }

    @Test
    fun aTargetOutrunningTheRocketHasNoSolutionAndLeavesTheOutVectorAlone() {
        // The rocket launched at rest never exceeds its burnout speed, so a target running straight away at 1.15x it
        // can never be reached, whatever the aim.
        val burnoutSpeed = c * tanh(properAccel * ROCKET_BURN_SECONDS / c)
        val out = Vector2(0.25f, -0.5f)
        val hitTime = solve(0f, 0f, 0f, 0f, 0f, 30f, 0f, 1.15f * burnoutSpeed, out)
        assertTrue(hitTime.isNaN(), "the solver claimed a hit on an unreachable target")
        assertEquals(0.25f, out.x, 0f)
        assertEquals(-0.5f, out.y, 0f)
    }
}
