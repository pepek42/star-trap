package com.github.pepek42.asteroids.render

import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoundaryOutlineTest {
    private val w = 120f
    private val h = 80f

    private fun outline(segments: Int, aberration: Boolean, bx: Float, by: Float): FloatArray {
        val out = FloatArray(outlineVertexFloats(segments))
        val n = buildOutlineVertices(out, w, h, segments, aberration, 40f, 20f, bx, by, Vector2())
        assertEquals(out.size, n)
        return out
    }

    @Test
    fun pilotViewVerticesAreThePilotCornerOfTheArenaPoints() {
        val v = outline(PILOT_BAND_SEGMENTS, true, 0.9f, 0.1f)
        val tmp = Vector2()
        val seg = PILOT_BAND_SEGMENTS
        for (i in 0..4 * seg) {
            val side = (i / seg).coerceAtMost(3)
            val t = (i - side * seg).toFloat() / seg
            val pt = when (side) {
                0 -> w * t to 0f
                1 -> w to h * t
                2 -> (w - w * t) to h
                else -> 0f to (h - h * t)
            }
            pilotCorner(pt.first, pt.second, 40f, 20f, 0.9f, 0.1f, true, tmp)
            assertEquals(tmp.x, v[2 * i], 1e-3f, "x $i")
            assertEquals(tmp.y, v[2 * i + 1], 1e-3f, "y $i")
        }
        // The loop closes and the boost really moves the outline.
        assertEquals(v[0], v[v.size - 2])
        assertEquals(v[1], v[v.size - 1])
        assertTrue(Math.abs(v[2 * seg] - w) > 1f)
    }

    @Test
    fun worldViewIsTheStraightRectangle() {
        val v = outline(1, false, 0.9f, 0.1f)
        val expected = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h, 0f, 0f)
        assertEquals(expected.toList(), v.toList())
        // Even when subdivided, no aberration means arena points on the straight sides.
        val sub = outline(PILOT_BAND_SEGMENTS, false, 0.9f, 0.1f)
        for (i in 0 until PILOT_BAND_SEGMENTS) assertEquals(0f, sub[2 * i + 1])
    }
}
