package com.github.pepek42.asteroids.weapon

import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.physics.GAMMA_MAX_MASSIVE
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import com.github.pepek42.asteroids.physics.clampU
import com.github.pepek42.asteroids.physics.gammaFromU
import com.github.pepek42.asteroids.physics.thrustAccelWorld
import com.github.pepek42.asteroids.physics.velocityFromU
import com.github.pepek42.asteroids.system.PhysicsSystem
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure homing tests (ST-164): the guidance functions drive a kinematic lock-on shot in a headless loop, and the same
 * acceleration model as [com.github.pepek42.asteroids.system.LockOnSteeringSystem] keeps the proper-velocity speed
 * below c. No Box2D or GL context is involved.
 */
class LockOnSteeringTest {
    private val desired = Vector2()
    private val turned = Vector2()
    private val velocity = Vector2()
    private val accel = Vector2()
    private val dt = PhysicsSystem.PHYSICS_UPDATE_INTERVAL

    @Test
    fun homingBringsLockOnOntoConstantVelocityTarget() {
        val heading = Vector2(1f, 0f)
        val shot = Vector2(0f, 0f)
        val target = Vector2(20f, 0f)
        val targetVelocity = Vector2(0f, 2f)
        val speed = 10f

        var hit = false
        val steps = (5f / dt).toInt()
        for (i in 0 until steps) {
            lockOnLeadDirection(
                target.x - shot.x, target.y - shot.y,
                targetVelocity.x, targetVelocity.y, speed, desired,
            )
            turnHeadingTowards(
                heading.x, heading.y, desired.x, desired.y, LOCK_ON_TURN_RATE * dt, turned,
            )
            heading.set(turned)
            shot.mulAdd(heading, speed * dt)
            target.mulAdd(targetVelocity, dt)
            if (shot.dst(target) < 0.7f) {
                hit = true
                break
            }
        }
        assertTrue(hit, "shot never reached the moving target")
    }

    @Test
    fun lockOnStaysBelowLightSpeedAgainstTargetAtNinetyPercentC() {
        val c = SPEED_OF_LIGHT
        val u = Vector2()
        val position = Vector2()
        val target = Vector2(20f, 0f)
        val targetVelocity = Vector2(0.9f * c, 0f)
        var burnLeft = LOCK_ON_BURN_SECONDS
        var angle = 0f

        val steps = (5f / dt).toInt()
        for (i in 0 until steps) {
            velocityFromU(u.x, u.y, c, velocity)
            lockOnLeadDirection(
                target.x - position.x, target.y - position.y,
                targetVelocity.x, targetVelocity.y, velocity.len(), desired,
            )
            turnHeadingTowards(cos(angle), sin(angle), desired.x, desired.y, LOCK_ON_TURN_RATE * dt, turned)
            angle = atan2(turned.y, turned.x)
            if (burnLeft > 0f) {
                val gamma = gammaFromU(u.x, u.y, c)
                thrustAccelWorld(LOCK_ON_PROPER_ACCEL, desired.x, desired.y, velocity.x, velocity.y, gamma, accel)
                u.mulAdd(accel, dt)
                clampU(u, GAMMA_MAX_MASSIVE, c)
                burnLeft = (burnLeft - dt / gamma).coerceAtLeast(0f)
            }
            velocityFromU(u.x, u.y, c, velocity)
            assertTrue(velocity.len() < c, "shot reached c at step $i: ${velocity.len()}")
            position.mulAdd(velocity, dt)
            target.mulAdd(targetVelocity, dt)
        }
        assertTrue(velocity.len() > 0.5f * c, "shot never accelerated (${velocity.len()})")
        assertTrue(u.x.isFinite() && u.y.isFinite() && position.x.isFinite())
    }

    @Test
    fun leadDirectionAimsAheadOfAMovingTarget() {
        // Target straight to the right, moving up: the intercept point is above it.
        assertTrue(lockOnLeadDirection(10f, 0f, 0f, 5f, 10f, desired))
        assertEquals(1f, desired.len(), 1e-5f)
        assertTrue(desired.y > 0f, "lead must be ahead of the target: $desired")
    }

    @Test
    fun noInterceptFallsBackToDirectAim() {
        // Target faster than the lock-on shot and directly ahead: no positive intercept root.
        assertFalse(lockOnLeadDirection(10f, 0f, 20f, 0f, 10f, desired))
        assertEquals(1f, desired.x, 1e-5f)
        assertEquals(0f, desired.y, 1e-5f)
    }

    @Test
    fun turnIsLimitedToTheConfiguredRate() {
        // 90 degrees off with a small budget: the new heading is the old one rotated by exactly maxTurn.
        turnHeadingTowards(1f, 0f, 0f, 1f, 0.1f, turned)
        assertEquals(cos(0.1f), turned.x, 1e-5f)
        assertEquals(sin(0.1f), turned.y, 1e-5f)
        // A budget above the angle snaps straight to the desired direction.
        turnHeadingTowards(1f, 0f, 0f, 1f, 2f, turned)
        assertEquals(0f, abs(turned.x), 1e-5f)
        assertEquals(1f, turned.y, 1e-5f)
    }

    @Test
    fun zeroDistanceWritesNoDirection() {
        assertFalse(lockOnLeadDirection(0f, 0f, 1f, 0f, 10f, desired))
        assertEquals(0f, desired.len(), 0f)
    }
}
