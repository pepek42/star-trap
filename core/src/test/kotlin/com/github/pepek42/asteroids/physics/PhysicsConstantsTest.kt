package com.github.pepek42.asteroids.physics

import com.github.pepek42.asteroids.system.PhysicsSystem
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhysicsConstantsTest {

    @Test
    fun speedOfLightShouldStayBelowBox2DTranslationCap() {
        assertTrue(SPEED_OF_LIGHT * PhysicsSystem.PHYSICS_UPDATE_INTERVAL <= 0.75f * 2f)
    }
}
