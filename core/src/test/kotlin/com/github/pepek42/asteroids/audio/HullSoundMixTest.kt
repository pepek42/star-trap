package com.github.pepek42.asteroids.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HullSoundMixTest {
    @Test
    fun engineVolumeMapsZeroToSilenceAndOneToMax() {
        assertEquals(0f, engineVolume(0f, 0.7f))
        assertEquals(0.7f, engineVolume(1f, 0.7f))
        assertEquals(0.7f, engineVolume(-1f, 0.7f))
        assertEquals(0.7f, engineVolume(5f, 0.7f))
    }

    @Test
    fun engineMixIsMonotonic() {
        var prevV = -1f
        var prevP = -1f
        for (i in 0..20) {
            val t = i / 20f
            assertTrue(engineVolume(t, 1f) >= prevV)
            assertTrue(enginePitch(t) >= prevP)
            prevV = engineVolume(t, 1f)
            prevP = enginePitch(t)
        }
        assertEquals(ENGINE_PITCH_MIN, enginePitch(0f))
        assertEquals(ENGINE_PITCH_MAX, enginePitch(1f))
    }

    @Test
    fun impactVolumeScalesWithSpeedAndScrapeIsSlow() {
        val c = 20f
        assertEquals(1f, impactVolume(100f, c))
        assertEquals(IMPACT_MIN_VOLUME, impactVolume(0f, c))
        assertTrue(impactVolume(3f, c) < impactVolume(4f, c))
        assertTrue(isScrape(0.1f, c))
        assertFalse(isScrape(HIT_FULL_VOLUME_C_FRACTION * c, c))
    }

    @Test
    fun queueDropsWhenFullAndClears() {
        val q = HullSoundQueue(2)
        q.push(HullSoundKind.HULL_HIT, 0.5f)
        q.push(HullSoundKind.LASER_FIRE)
        q.push(HullSoundKind.HULL_SCRAPE)
        assertEquals(2, q.size)
        assertEquals(HullSoundKind.HULL_HIT, q.kindAt(0))
        assertEquals(0.5f, q.volumeAt(0))
        q.clear()
        assertEquals(0, q.size)
    }

    @Test
    fun scrapeVolumeIsZeroBelowThresholdMonotonicAndCapped() {
        val c = 20f
        assertEquals(0f, scrapeVolume(0f, c))
        assertEquals(0f, scrapeVolume(SCRAPE_MIN_C_FRACTION * c * 0.99f, c))
        assertTrue(scrapeVolume(SCRAPE_MIN_C_FRACTION * c, c) > 0f)
        var prev = 0f
        for (i in 0..100) {
            val v = scrapeVolume(i * c / 50f, c)
            assertTrue(v >= prev)
            assertTrue(v <= 1f)
            prev = v
        }
        assertEquals(1f, scrapeVolume(1000f, c))
    }

    @Test
    fun tangentialSpeedDropsTheNormalComponent() {
        assertEquals(3f, tangentialSpeed(3f, 5f, 0f, 2f), 1e-5f)
        assertEquals(0f, tangentialSpeed(0f, 5f, 0f, 2f), 1e-5f)
        assertEquals(5f, tangentialSpeed(3f, 4f, 0f, 0f), 1e-5f)
    }
}
