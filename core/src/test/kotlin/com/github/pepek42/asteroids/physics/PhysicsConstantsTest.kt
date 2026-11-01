package com.github.pepek42.asteroids.physics

import com.github.pepek42.asteroids.system.PhysicsSystem
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhysicsConstantsTest {

    @Test
    fun speedOfLightShouldStayBelowBox2DTranslationCap() {
        assertTrue(SPEED_OF_LIGHT * PhysicsSystem.PHYSICS_UPDATE_INTERVAL <= 0.75f * 2f)
    }

    @Test
    fun maxRuntimeCShouldStayBelowBox2DTranslationCap() {
        assertTrue(C_MAX_BOX2D_SAFE * PhysicsSystem.PHYSICS_UPDATE_INTERVAL <= 0.75f * 2f)
    }
}
