package com.github.pepek42.asteroids.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MinimapBoundsTest {
    @Test
    fun bounds800x600() {
        val b = minimapBounds(800, 600)
        assertEquals(132f, b.width, 0.001f)
        assertEquals(132f, b.height, 0.001f)
        assertEquals(800f - 12f - 132f, b.x, 0.001f)
        assertEquals(12f, b.y, 0.001f)
    }

    @Test
    fun bounds1920x1080() {
        val b = minimapBounds(1920, 1080)
        assertEquals(237.6f, b.width, 0.001f)
        assertEquals(237.6f, b.height, 0.001f)
        assertEquals(1920f - 12f - 237.6f, b.x, 0.001f)
        assertEquals(12f, b.y, 0.001f)
    }

    @Test
    fun smallWindowStaysInsideScreen() {
        val b = minimapBounds(200, 150)
        assertTrue(b.x >= 0f && b.y >= 0f)
        assertTrue(b.x + b.width <= 200f)
        assertTrue(b.y + b.height <= 150f)
    }
}
