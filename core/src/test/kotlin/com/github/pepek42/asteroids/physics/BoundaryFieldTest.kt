package com.github.pepek42.asteroids.physics

import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.max

class BoundaryFieldTest {
    private val p = BoundaryFieldParams(skin = 8f, exponent = 2f, accelMax = 3000f, damping = 0f, useBodyRadius = true)
    private val pDamped = p.copy(damping = 1f)
    private val out = Vector2()

    private fun acc(x: Float, y: Float, vx: Float = 0f, vy: Float = 0f, r: Float = 0f, params: BoundaryFieldParams = p) =
        boundaryAcceleration(x, y, vx, vy, r, 100f, 100f, params, Vector2())

    @Test
    fun zeroInInterior() {
        assertEquals(0f, acc(50f, 50f).len(), 0f)
        assertEquals(0f, acc(8.01f, 50f).len(), 0f)
        assertEquals(0f, acc(8f + 2.25f + 0.01f, 50f, r = 2.25f).len(), 0f)
    }

    @Test
    fun returnsOutAndDoesNotNeedAllocation() {
        assertTrue(boundaryAcceleration(1f, 50f, 0f, 0f, 0f, 100f, 100f, p, out) === out)
    }

    @Test
    fun symmetric() {
        var x = 0.5f
        while (x <= 7.5f) {
            val a = acc(x, 50f)
            val b = acc(100f - x, 50f)
            assertEquals(a.x, -b.x, 1e-2f)
            assertEquals(a.y, -b.y, 1e-2f)
            x += 0.25f
        }
    }

    @Test
    fun monotonicInDepth() {
        var prev = 0f
        var x = 8f
        while (x >= 0f) {
            val m = acc(x, 50f).len()
            assertTrue(m >= prev - 1e-3f, "x=$x m=$m prev=$prev")
            prev = m
            x -= 0.1f
        }
    }

    @Test
    fun corner() {
        val a = acc(2f, 2f)
        val single = acc(2f, 50f)
        assertTrue(a.x > 0f)
        assertEquals(a.x, a.y, 1e-3f)
        assertEquals(single.x, a.x, 1e-3f)
    }

    @Test
    fun bodyRadius() {
        assertTrue(acc(9f, 50f, r = 2.25f).x > 0f)
        assertEquals(0f, acc(9f, 50f, r = 2.25f, params = p.copy(useBodyRadius = false)).x, 0f)
    }

    @Test
    fun turnAroundDepth() {
        assertEquals(1.47f, newtonianTurnAroundDepth(10f, p), 0.02f)
        assertEquals(3.06f, newtonianTurnAroundDepth(30f, p), 0.02f)
        // Card says 7.02, but its own formula gives 4.87 for 60 u/s (7.02 is not reachable from it).
        assertEquals(4.87f, newtonianTurnAroundDepth(60f, p), 0.02f)
        assertEquals(8f, newtonianTurnAroundDepth(500f, p), 0f)
    }

    @Test
    fun potentialMatchesTurnAround() {
        val q = newtonianTurnAroundDepth(30f, p) / p.skin
        assertEquals(30f * 30f / 2f, boundaryPotential(q, p), 0.5f)
        assertEquals(1f, boundaryDepthQ(0f, 50f, 0f, 100f, 100f, p), 0f)
        assertEquals(0.5f, boundaryDepthQ(4f, 50f, 0f, 100f, 100f, p), 1e-6f)
    }

    /** Returns (max depth, exit speed). */
    private fun simulate(v: Float, params: BoundaryFieldParams): Pair<Float, Float> {
        val dt = PHYSICS_UPDATE_INTERVAL
        var x = 20f
        var vx = -v
        var maxDepth = 0f
        val a = Vector2()
        var steps = 0
        while (steps++ < 100000) {
            boundaryAcceleration(x, 50f, vx, 0f, 0f, 100f, 100f, params, a)
            vx += a.x * dt
            x += vx * dt
            maxDepth = max(maxDepth, params.skin - x)
            if (vx > 0f && x > 20f) break
        }
        return maxDepth to abs(vx)
    }

    @Test
    fun simulationMatchesTurnAround() {
        for (v in listOf(10f, 30f, 60f)) {
            val r = simulate(v, p)
            val depth = r.first
            val exit = r.second
            assertEquals(newtonianTurnAroundDepth(v, p), depth, v * PHYSICS_UPDATE_INTERVAL, "depth v=$v")
            assertEquals(v, exit, v * 0.02f, "exit v=$v")
        }
    }

    @Test
    fun dampingLosesSomeSpeed() {
        val exit = simulate(30f, pDamped).second
        assertTrue(exit < 30f)
        assertTrue(exit > 15f)
    }
}
