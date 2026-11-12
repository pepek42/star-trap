package com.github.pepek42.asteroids.weapon

import com.github.pepek42.asteroids.physics.ENGINE_PROPER_ACCEL
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import com.github.pepek42.asteroids.provider.MAP_HEIGHT
import com.github.pepek42.asteroids.provider.MAP_WIDTH
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/** Pure tests of the arena- and engine-derived lock-on lifetime (ST-176). */
class LockOnLifetimeTest {
    private val c = SPEED_OF_LIGHT
    private val accel = ENGINE_PROPER_ACCEL

    /** Burnout speed β the formula uses, recomputed here from the constants. */
    private fun defaultBeta(): Float {
        val w = LOCK_ON_ACCEL_FACTOR * ENGINE_PROPER_ACCEL * LOCK_ON_BURN_SECONDS / SPEED_OF_LIGHT
        return w / sqrt(1f + w * w)
    }

    /** Coast time only, i.e. the lifetime minus the burn. */
    private fun coast(c: Float, engineProperAccel: Float, width: Float, height: Float): Float =
        lockOnLifetime(c, engineProperAccel, width, height) - LOCK_ON_BURN_SECONDS

    @Test
    fun defaultLifetimeMatchesFormulaAndCoversLockRange() {
        val expected = LOCK_ON_BURN_SECONDS + LOCK_ON_RANGE_ARENA_FRACTION * MAP_WIDTH / (defaultBeta() * SPEED_OF_LIGHT)
        val lifetime = lockOnLifetime(c, accel, MAP_WIDTH, MAP_HEIGHT)

        assertEquals(expected, lifetime, 1e-4f)
        // The lock range is TARGET_LOCK_RANGE_LIGHT_SECONDS of light travel; it must be covered at burnout speed.
        val lockRangeSeconds = LOCK_ON_BURN_SECONDS + TARGET_LOCK_RANGE_LIGHT_SECONDS * c / (defaultBeta() * c)
        assertTrue(lifetime > lockRangeSeconds, "lifetime $lifetime should exceed $lockRangeSeconds")
    }

    @Test
    fun doublingCAndEngineHalvesCoastInSameArena() {
        val base = coast(c, accel, MAP_WIDTH, MAP_HEIGHT)
        val doubled = coast(2f * c, 2f * accel, MAP_WIDTH, MAP_HEIGHT)

        assertEquals(base / 2f, doubled, 1e-4f)
    }

    @Test
    fun halfSizeArenaHalvesCoast() {
        val base = coast(c, accel, MAP_WIDTH, MAP_HEIGHT)
        val half = coast(c, accel, MAP_WIDTH / 2f, MAP_HEIGHT / 2f)

        assertEquals(base / 2f, half, 1e-4f)
    }

    @Test
    fun zeroEngineGivesFiniteLifetimeFromMinimumBeta() {
        val lifetime = lockOnLifetime(c, 0f, MAP_WIDTH, MAP_HEIGHT)
        val expected = LOCK_ON_BURN_SECONDS + LOCK_ON_RANGE_ARENA_FRACTION * MAP_WIDTH / (LOCK_ON_MIN_COAST_BETA * c)

        assertTrue(lifetime.isFinite())
        assertEquals(expected, lifetime, 1e-4f)
    }
}
