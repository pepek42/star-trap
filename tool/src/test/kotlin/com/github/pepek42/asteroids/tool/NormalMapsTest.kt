package com.github.pepek42.asteroids.tool

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NormalMapsTest {
    private fun nx(m: FloatArray, w: Int, x: Int, y: Int) = m[(y * w + x) * 4] * 2f - 1f
    private fun ny(m: FloatArray, w: Int, x: Int, y: Int) = m[(y * w + x) * 4 + 1] * 2f - 1f
    private fun nz(m: FloatArray, w: Int, x: Int, y: Int) = m[(y * w + x) * 4 + 2] * 2f - 1f

    @Test
    fun discNormals() {
        val w = 32
        val alpha = FloatArray(w * w) { val dx = it % w - 16; val dy = it / w - 16; if (dx * dx + dy * dy <= 144) 1f else 0f }
        val m = normalMapFromAlpha(w, w, alpha)
        assertEquals(0f, nx(m, w, 16, 16), 0.05f)
        assertEquals(0f, ny(m, w, 16, 16), 0.05f)
        assertEquals(1f, nz(m, w, 16, 16), 0.05f)
        assertTrue(nx(m, w, 28, 16) > 0.5f)
        assertTrue(ny(m, w, 16, 4) > 0.5f)
    }

    @Test
    fun emptyAndSquare() {
        val e = normalMapFromAlpha(8, 8, FloatArray(64))
        assertTrue((0 until 64).all { e[it * 4 + 3] == 0f })
        val m = normalMapFromAlpha(8, 8, FloatArray(64) { 1f })
        for (y in 3..4) for (x in 3..4) {
            assertEquals(0f, nx(m, 8, x, y), 0.01f)
            assertEquals(0f, ny(m, 8, x, y), 0.01f)
        }
    }
}
