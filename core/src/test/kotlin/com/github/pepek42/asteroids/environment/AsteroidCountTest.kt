package com.github.pepek42.asteroids.environment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AsteroidCountTest {
    @Test
    fun countIsOnePlusFactorTimesLevelSquared() {
        for (level in 1..6) {
            assertEquals(1 + ASTEROID_COUNT_PER_LEVEL_SQ * level * level, asteroidCount(level))
        }
    }

    @Test
    fun countGrowsEveryLevel() {
        for (level in 1..9) {
            assertTrue(asteroidCount(level + 1) > asteroidCount(level))
        }
    }
}
