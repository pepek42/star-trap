package com.github.pepek42.asteroids.ui

import com.github.pepek42.asteroids.system.speedZoom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HudRelativityTest {
    @Test
    fun speedZoomValues() {
        assertEquals(1f, speedZoom(1f, 0f))
        assertEquals(1.486f, speedZoom(1f, 0.9f), 0.001f)
        assertEquals(2f, speedZoom(2f, 0.9f))
    }

    @Test
    fun warpLabelKeys() {
        assertNull(warpLabelKey(1f, false))
        assertEquals("hud_warp", warpLabelKey(4f, false))
        assertEquals("hud_warp_capped", warpLabelKey(10f, true))
        assertEquals("hud_warp_capped", warpLabelKey(1f, true))
    }

    @Test
    fun rapidityBarClamps() {
        assertEquals(1f, rapidityBarValue(HUD_RAPIDITY_FULL * 2))
        assertEquals(0.5f, rapidityBarValue(HUD_RAPIDITY_FULL / 2), 1e-6f)
    }
}
