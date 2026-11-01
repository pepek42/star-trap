package com.github.pepek42.asteroids.render

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StarfieldTest {
    @Test
    fun blueToRedRatioIsMonotoneInTemperature() {
        var prev = -1f
        var k = STAR_MIN_KELVIN
        while (k <= STAR_MAX_KELVIN) {
            val rgb = blackbodyRgb(k)
            val ratio = rgb[2] / rgb[0]
            assertTrue(ratio >= prev, "ratio dropped at $k K")
            prev = ratio
            k += 100f
        }
        assertTrue(blackbodyRgb(STAR_MAX_KELVIN)[2] > blackbodyRgb(STAR_MIN_KELVIN)[2])
    }

    @Test
    fun sameSeedGivesSameStars() {
        val a = StarData.generate(7L, 200)
        val b = StarData.generate(7L, 200)
        assertArrayEquals(a.x, b.x)
        assertArrayEquals(a.y, b.y)
        assertArrayEquals(a.kelvin, b.kelvin)
        assertArrayEquals(a.layer, b.layer)
        val c = StarData.generate(8L, 200)
        assertTrue(!a.x.contentEquals(c.x))
    }

    @Test
    fun starsStayInRanges() {
        val d = StarData.generate(1L)
        assertTrue(d.kelvin.all { it in STAR_MIN_KELVIN..STAR_MAX_KELVIN })
        assertTrue(d.x.all { it in 0f..STAR_TILE_SIZE })
        assertTrue(d.layer.all { it in 0..2 })
    }

    @Test
    fun wrappedOffsetStaysInHalfTile() {
        for (cam in listOf(-300f, -1f, 0f, 49f, 513f)) {
            val o = wrappedOffset(10f, cam, 0.4f)
            assertTrue(o >= -STAR_TILE_SIZE / 2 && o < STAR_TILE_SIZE / 2)
        }
    }

    @Test
    fun wrappedOffsetIsPeriodicInCamera() {
        for (p in STAR_LAYER_PARALLAX) {
            val a = wrappedOffset(12f, 5f, p)
            val b = wrappedOffset(12f, 5f + STAR_TILE_SIZE / p, p)
            assertEquals(a, b, 1e-2f)
        }
    }

    @Test
    fun parallaxScalesScreenMotion() {
        // Small camera move, no wrap: offset changes by -move * parallax.
        for (p in STAR_LAYER_PARALLAX) {
            val d = wrappedOffset(0f, 1f, p) - wrappedOffset(0f, 0f, p)
            assertEquals(-p, d, 1e-5f)
        }
    }
}
