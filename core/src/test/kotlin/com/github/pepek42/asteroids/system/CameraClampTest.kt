package com.github.pepek42.asteroids.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CameraClampTest {
    @Test
    fun clampsToLowerEdgeWithMargin() {
        assertEquals(20f, clampCameraAxis(0f, 24f, 100f, 4f), 0f)
    }

    @Test
    fun clampsToUpperEdgeWithMargin() {
        assertEquals(80f, clampCameraAxis(100f, 24f, 100f, 4f), 0f)
    }

    @Test
    fun keepsTargetInsideRange() {
        assertEquals(50f, clampCameraAxis(50f, 24f, 100f, 4f), 0f)
    }

    @Test
    fun centresWhenViewLargerThanMap() {
        assertEquals(50f, clampCameraAxis(0f, 60f, 100f, 4f), 0f)
    }
}
