package com.github.pepek42.asteroids.tool.sound

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class SoundRecipesTest {
    @Test
    fun deterministic() {
        for (e in SoundRecipes.all.entries) assertArrayEquals(Dsp.toWav(e.value()), Dsp.toWav(e.value()), e.key)
    }

    @Test
    fun validWavAndSize() {
        for (e in SoundRecipes.all.entries) {
            val name = e.key
            val samples = e.value()
            val bytes = Dsp.toWav(samples)
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("RIFF", String(bytes, 0, 4))
            assertEquals("WAVE", String(bytes, 8, 4))
            assertEquals(1, b.getShort(20).toInt(), name)
            assertEquals(1, b.getShort(22).toInt(), name)
            assertEquals(SAMPLE_RATE, b.getInt(24), name)
            assertEquals(16, b.getShort(34).toInt(), name)
            assertEquals(samples.size * 2, b.getInt(40), name)
            assertEquals(bytes.size - 8, b.getInt(4), name)
            assertTrue(bytes.size < 200_000, "$name too big: ${bytes.size}")
            assertTrue(samples.all { abs(it) <= 1f }, name)
            assertTrue(samples.maxOf { abs(it) } > 0.5f, "$name silent")
        }
    }

    @Test
    fun loopEndsMatch() {
        val s = SoundRecipes.engine_loop()
        assertTrue(abs(s.first() - s.last()) < 0.05f, "first=${s.first()} last=${s.last()}")
    }

    @Test
    fun oneShotsStartAndEndAtZeroWithNormalizedPeak() {
        for (e in SoundRecipes.all.entries) {
            if (e.key == "engine_loop") continue
            val s = e.value()
            assertTrue(abs(s.first()) < 1e-6f, e.key)
            assertTrue(abs(s.last()) < 1e-6f, e.key)
            assertEquals(PEAK_MINUS_1_DBFS, s.maxOf { abs(it) }, 0.01f, e.key)
        }
    }
}
