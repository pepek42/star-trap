package com.github.pepek42.asteroids.render

import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EdgePointerTest {
    private val out = Vector2()

    @Test
    fun targetToTheRightPointsAtRightInsetEdge() {
        val angle = edgePointer(10f, 5f, 24f, 13.5f, 100f, 5f, 2f, out)
        assertEquals(0f, angle, 1e-5f)
        assertEquals(10f + 24f - 2f, out.x, 1e-4f)
        assertEquals(5f, out.y, 1e-4f)
    }

    @Test
    fun diagonalTargetLandsOnCornerRegion() {
        val angle = edgePointer(0f, 0f, 24f, 12f, 1000f, 1000f, 2f, out)
        assertEquals(Math.PI.toFloat() / 4f, angle, 1e-5f)
        // Limited by the shorter (vertical) inset half extent.
        assertEquals(10f, out.y, 1e-4f)
        assertTrue(out.x in 9.9f..22f)
    }

    @Test
    fun onScreenTargetIsFlagged() {
        assertTrue(edgePointer(0f, 0f, 24f, 12f, 5f, -3f, 2f, out).isNaN())
    }

    @Test
    fun gridLinesAreMultiplesOfStepInsideRange() {
        assertEquals(listOf(20f, 40f, 60f), gridLinePositions(15f, 65f, 20f))
        assertEquals(listOf(0f, 20f), gridLinePositions(0f, 20f, 20f))
    }
}
