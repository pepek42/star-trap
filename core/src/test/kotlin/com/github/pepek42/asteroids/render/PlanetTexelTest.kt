package com.github.pepek42.asteroids.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class PlanetTexelTest {
    @Test
    fun transparentOutsideTheDiscAndOpaqueInside() {
        assertEquals(0, planetTexel(0, 7L, 0.99f, 0.99f))
        assertEquals(0xFF, planetTexel(0, 7L, 0.5f, 0.5f) and 0xFF)
    }

    @Test
    fun deterministicForTheSameSeed() {
        for (variant in 0 until PLANET_VARIANTS) {
            assertEquals(planetTexel(variant, 42L, 0.3f, 0.6f), planetTexel(variant, 42L, 0.3f, 0.6f))
        }
    }

    @Test
    fun rimIsDarkerThanTheInterior() {
        val rim = planetTexel(1, 5L, 0.5f, 0.001f + 1f / PLANET_TEXTURE_SIZE * 0.25f)
        val inner = planetTexel(1, 5L, 0.5f, 0.1f)
        assertNotEquals(0, rim and 0xFF)
        assertNotEquals(inner, rim)
    }
}
