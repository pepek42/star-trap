package com.github.pepek42.asteroids.render

import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FieldBandTest {
    private val w = 100f
    private val h = 60f
    private val outside = 1.5f
    private val depth = 6f
    private val color = 1f
    private val quadFloats = SPRITE_VERTEX_FLOATS

    private fun build(aberration: Boolean, bx: Float = 0.9f, by: Float = 0.1f): FloatArray {
        val out = FloatArray(bandVertexFloats(PILOT_BAND_SEGMENTS))
        val n = buildBandVertices(out, w, h, outside, depth, PILOT_BAND_SEGMENTS, color, aberration, 40f, 20f, bx, by, Vector2())
        assertEquals(out.size, n)
        return out
    }

    @Test
    fun emitsSixtyFourQuadsPerSide() {
        val v = build(true)
        assertEquals(4 * PILOT_BAND_SEGMENTS * quadFloats, v.size)
        // Quads of one strip are contiguous and span the strip length: bottom strip covers x from -outside to w + outside.
        val bottom = v.copyOfRange(0, PILOT_BAND_SEGMENTS * quadFloats)
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        for (i in 0 until PILOT_BAND_SEGMENTS * 4) {
            minX = minOf(minX, bottom[i * SPRITE_VERTEX_STRIDE + 3])
            maxX = maxOf(maxX, bottom[i * SPRITE_VERTEX_STRIDE + 3])
        }
        assertEquals(-outside, minX, 0f)
        assertEquals(w + outside, maxX, 0f)
    }

    @Test
    fun texcoordsAreArenaPositionsAndPositionsAreBoosted() {
        val v = build(true)
        val tmp = Vector2()
        var i = 0
        while (i < v.size) {
            val u = v[i + 3]
            val t = v[i + 4]
            pilotCorner(u, t, 40f, 20f, 0.9f, 0.1f, true, tmp)
            assertEquals(tmp.x, v[i], 1e-4f)
            assertEquals(tmp.y, v[i + 1], 1e-4f)
            assertEquals(color, v[i + 2], 0f)
            i += SPRITE_VERTEX_STRIDE
        }
    }

    @Test
    fun worldViewKeepsTexcoordsEqualToPositions() {
        val v = build(false)
        var i = 0
        while (i < v.size) {
            assertEquals(v[i + 3], v[i], 0f)
            assertEquals(v[i + 4], v[i + 1], 0f)
            i += SPRITE_VERTEX_STRIDE
        }
    }

    @Test
    fun worldViewCoversTheSameAreaAsTheFourRectangles() {
        val v = build(false)
        var area = 0f
        var i = 0
        while (i < v.size) {
            // Axis-aligned quad: corner 0 (bottom-left) to corner 2 (top-right).
            area += (v[i + 2 * SPRITE_VERTEX_STRIDE] - v[i]) * (v[i + 2 * SPRITE_VERTEX_STRIDE + 1] - v[i + 1])
            i += quadFloats
        }
        val side = outside + depth
        val expected = 2 * (w + 2 * outside) * side + 2 * side * (h - 2 * depth)
        assertEquals(expected, area, expected * 1e-4f)
    }
}
