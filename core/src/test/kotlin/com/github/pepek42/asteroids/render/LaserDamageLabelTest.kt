package com.github.pepek42.asteroids.render

import com.github.pepek42.asteroids.physics.PHOTON_DAMAGE_D_MAX
import com.github.pepek42.asteroids.physics.PHOTON_DAMAGE_D_MIN
import com.github.pepek42.asteroids.physics.photonEnergyFactor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LaserDamageLabelTest {
    @Test
    fun matchesPhotonEnergyFactor() {
        val raw = photonEnergyFactor(0.2f, 0f, -0.1f, 0f, 1f, 0f)
        val shown = displayedPhotonFactor(true, true, 0.2f, 0f, -0.1f, 0f, 1f, 0f)
        assertNotNull(shown)
        assertEquals(raw.coerceIn(PHOTON_DAMAGE_D_MIN, PHOTON_DAMAGE_D_MAX), shown!!, 1e-6f)
        assertEquals(true, shown > 1f)
    }

    @Test
    fun clampedAtBothEnds() {
        assertEquals(PHOTON_DAMAGE_D_MAX, displayedPhotonFactor(true, true, 0f, 0f, -0.95f, 0f, 1f, 0f)!!, 1e-6f)
        assertEquals(PHOTON_DAMAGE_D_MIN, displayedPhotonFactor(true, true, 0f, 0f, 0.95f, 0f, 1f, 0f)!!, 1e-6f)
    }

    @Test
    fun nullWhenOff() {
        assertNull(displayedPhotonFactor(false, true, 0f, 0f, 0f, 0f, 1f, 0f))
        assertNull(displayedPhotonFactor(true, false, 0f, 0f, 0f, 0f, 1f, 0f))
    }

    @Test
    fun formatsOneDecimal() {
        val sb = StringBuilder("junk")
        formatDamageFactor(1.84f, sb)
        assertEquals("×1.8", sb.toString())
        formatDamageFactor(0.25f, sb)
        assertEquals("×0.3", sb.toString())
        formatDamageFactor(4f, sb)
        assertEquals("×4.0", sb.toString())
    }
}
