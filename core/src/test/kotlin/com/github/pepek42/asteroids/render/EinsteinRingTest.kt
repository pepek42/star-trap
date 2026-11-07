package com.github.pepek42.asteroids.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EinsteinRingTest {
    @Test
    fun radiusIsTheLensEinsteinRadius() {
        val rs = 2.5f
        assertEquals(LENS_EINSTEIN_RADIUS_RS * rs, einsteinRingRadius(rs), 1e-5f)
    }

    @Test
    fun ringLiesOutsideTheShadowAndDefaultsOn() {
        assertTrue(einsteinRingRadius(1f) > BH_SHADOW_PER_RS)
        assertTrue(EINSTEIN_RING_ENABLED)
    }
}
