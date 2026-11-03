package com.github.pepek42.asteroids.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BlackbodyLutTest {
    @Test
    fun lutMatchesBlackbodyRgbWithinOneByte() {
        val out = FloatArray(3)
        for (k in listOf(2000f, 6500f, 20000f, 1234f, 45000f)) {
            BlackbodyLut.rgbInto(k, out)
            val ref = blackbodyRgb(k)
            for (c in 0..2) assertEquals(ref[c], out[c], 1f / 255f, "channel $c at $k K")
        }
    }

    @Test
    fun lutClampsOutsideRange() {
        val out = FloatArray(3)
        BlackbodyLut.rgbInto(10f, out)
        val low = blackbodyRgb(BLACKBODY_LUT_MIN_K)
        for (c in 0..2) assertEquals(low[c], out[c], 1f / 255f)
        BlackbodyLut.rgbInto(1e7f, out)
        val high = blackbodyRgb(BLACKBODY_LUT_MAX_K)
        for (c in 0..2) assertEquals(high[c], out[c], 1f / 255f)
    }
}
