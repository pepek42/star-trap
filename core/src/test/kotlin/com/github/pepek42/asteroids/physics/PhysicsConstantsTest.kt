package com.github.pepek42.asteroids.physics

import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.MAX_LAG_SECONDS
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.MAX_LAG_STEPS
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.MAX_STEPS_PER_FRAME
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_TICK_RATE
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhysicsConstantsTest {

    @Test
    fun speedOfLightShouldStayBelowBox2DTranslationCap() {
        assertTrue(SPEED_OF_LIGHT * PHYSICS_UPDATE_INTERVAL <= 0.75f * BOX2D_MAX_TRANSLATION)
        assertTrue(SPEED_OF_LIGHT <= C_MAX_BOX2D_SAFE)
    }

    @Test
    fun maxRuntimeCShouldStayBelowBox2DTranslationCap() {
        assertTrue(C_MAX_BOX2D_SAFE * PHYSICS_UPDATE_INTERVAL <= 0.75f * BOX2D_MAX_TRANSLATION + 1e-6f)
    }

    @Test
    fun maxRuntimeCDerivesFromTickRate() {
        assertEquals(0.75f * 2f * PHYSICS_TICK_RATE, C_MAX_BOX2D_SAFE)
        assertEquals(1f / PHYSICS_TICK_RATE, PHYSICS_UPDATE_INTERVAL)
    }

    @Test
    fun defaultTimeWarpFitsInOneFrameAtSixtyFps() {
        assertTrue(MAX_STEPS_PER_FRAME * PHYSICS_UPDATE_INTERVAL * 60f >= TIME_WARP_MAX)
    }

    @Test
    fun lagStepsCoverTheLagBudget() {
        assertTrue(MAX_LAG_STEPS * PHYSICS_UPDATE_INTERVAL >= MAX_LAG_SECONDS - 1e-6f)
    }

    @Test
    fun engineDefaultsAreWithinRuntimeRange() {
        assertTrue(ENGINE_PROPER_ACCEL in ENGINE_ACCEL_MIN..ENGINE_ACCEL_MAX)
        assertTrue(SPEED_OF_LIGHT in C_MIN..C_MAX_BOX2D_SAFE)
    }
}
