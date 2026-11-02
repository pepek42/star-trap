package com.github.pepek42.asteroids.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TargetPointersTest {
    @Test
    fun picksSixNearestOffScreenNearestFirst() {
        // Camera at 0,0, half extents 10x10. Off-screen i at x = 100 - 5*i (i in 0..9) => i=9 nearest (x=55).
        val c = FloatArray(24)
        for (i in 0 until 10) { c[2 * i] = 100f - 5f * i; c[2 * i + 1] = 0f }
        c[20] = 1f; c[21] = 1f
        c[22] = -9f; c[23] = 9f
        val out = IntArray(MAX_TARGET_POINTERS)
        val n = selectPointerTargets(0f, 0f, 10f, 10f, c, 12, MAX_TARGET_POINTERS, out)
        assertEquals(6, n)
        assertEquals(listOf(9, 8, 7, 6, 5, 4), out.toList())
    }

    @Test
    fun emptyReturnsZero() {
        assertEquals(0, selectPointerTargets(0f, 0f, 10f, 10f, FloatArray(0), 0, 6, IntArray(6)))
    }

    @Test
    fun alphaFades() {
        assertEquals(0.9f, pointerAlpha(0f), 1e-5f)
        assertEquals(0.3f, pointerAlpha(POINTER_FADE_DIST), 1e-5f)
        assertEquals(0.3f, pointerAlpha(1000f), 1e-5f)
    }
}
