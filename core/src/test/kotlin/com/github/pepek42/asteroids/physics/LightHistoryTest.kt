package com.github.pepek42.asteroids.physics

import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

class LightHistoryTest {
    private val c = SPEED_OF_LIGHT
    private val dt = PHYSICS_UPDATE_INTERVAL
    private val out = RetardedState()

    /** Records `steps + 1` samples at t = i * dt from the path function; returns the time of the last one. */
    private fun fill(
        h: LightHistory,
        steps: Int,
        path: (Float) -> FloatArray, // x, y, angle, vx, vy
    ): Float {
        for (i in 0..steps) {
            val t = i * dt
            val p = path(t)
            h.record(t, p[0], p[1], p[2], p[3], p[4])
        }
        return steps * dt
    }

    private fun cone(ox: Float, oy: Float, now: Float) =
        hypot(out.x - ox, out.y - oy) - c * (now - out.t)

    @Test
    fun emptyHistoryHasNothingToSee() {
        assertFalse(LightHistory().retarded(0f, 0f, 1f, c, out))
    }

    @Test
    fun capacityCoversTheLongestLightTravelAtSlowestC() {
        assertTrue(LIGHT_HISTORY_CAPACITY * dt >= ARENA_DIAGONAL / C_MIN + LIGHT_HISTORY_MARGIN_S)
        assertTrue((LIGHT_HISTORY_CAPACITY - 1) * dt < ARENA_DIAGONAL / C_MIN + LIGHT_HISTORY_MARGIN_S)
    }

    @Test
    fun staticObjectIsSeenWhereItWasAtNowMinusTravelTime() {
        val d = 0.75f * c // 0.75 s of light travel, well inside 2 s of history
        val h = LightHistory()
        val now = fill(h, (2f / dt).toInt()) { floatArrayOf(d, 0f, 0.3f, 0f, 0f) }
        assertTrue(h.retarded(0f, 0f, now, c, out))
        assertEquals(now - d / c, out.t, 1e-4f)
        assertEquals(d, out.x, 1e-4f)
        assertEquals(0f, out.y, 1e-4f)
        assertEquals(0.3f, out.angle, 1e-4f)
    }

    @Test
    fun uniformMotionMatchesResearchE8ClosedForm() {
        val v = c / 3f
        val h = LightHistory()
        val now = fill(h, (2f / dt).toInt()) { t -> floatArrayOf(30f + v * t, 0f, 0f, v, 0f) }
        assertTrue(h.retarded(0f, 0f, now, c, out))
        val r0x = (30f + v * now).toDouble() // position at `now`
        val vd = v.toDouble()
        val c2 = (c * c).toDouble()
        val a = c2 - vd * vd
        val bigT = (-(r0x * vd) + sqrt((r0x * vd) * (r0x * vd) + a * r0x * r0x)) / a
        assertEquals(now - bigT.toFloat(), out.t, 1e-4f)
        assertEquals(0f, cone(0f, 0f, now), 1e-3f)
        assertEquals(30f + v * out.t, out.x, 1e-3f)
        assertEquals(v, out.vx, 1e-4f)
    }

    @Test
    fun uniformMotionTowardTheObserverUsesTheSameSolver() {
        val v = c / 2f
        val h = LightHistory()
        val now = fill(h, (4f / dt).toInt()) { t -> floatArrayOf(30f - v * t, 20f, 0f, -v, 0f) }
        assertTrue(h.retarded(0f, 0f, now, c, out))
        assertEquals(0f, cone(0f, 0f, now), 1e-3f)
    }

    @Test
    fun circularMotionSatisfiesTheLightCone() {
        val radius = 20f
        val omega = 0.5f * c / radius // speed = c / 2
        val h = LightHistory()
        val now = fill(h, (4f / dt).toInt()) { t ->
            val a = omega * t
            floatArrayOf(
                40f + radius * cos(a), radius * sin(a), a + (PI / 2).toFloat(),
                -radius * omega * sin(a), radius * omega * cos(a),
            )
        }
        assertTrue(h.retarded(0f, 0f, now, c, out))
        assertEquals(0f, cone(0f, 0f, now), 1e-2f)
        // The reported position is on the circle.
        assertEquals(radius, hypot(out.x - 40f, out.y), 1e-2f)
    }

    @Test
    fun photonMovingAtCStillSolves() {
        val h = LightHistory()
        val now = fill(h, (1f / dt).toInt()) { t -> floatArrayOf(10f + c * t, 0f, 0f, c, 0f) }
        assertTrue(h.retarded(0f, 0f, now, c, out))
        assertEquals(0f, cone(0f, 0f, now), 1e-2f)
        assertEquals((c * now - 10f) / (2f * c), out.t, 1e-3f)
    }

    @Test
    fun youngObjectIsInvisibleUnlessEternal() {
        val d = 1.5f * c // light needs 1.5 s, the history only spans 0.1 s
        val v = 2f
        val h = LightHistory()
        val now = fill(h, (0.1f / dt).toInt()) { t -> floatArrayOf(d + v * t, 0f, 0f, v, 0f) }
        assertFalse(h.wrapped)
        assertFalse(h.retarded(0f, 0f, now, c, out))

        h.eternal = true
        assertTrue(h.retarded(0f, 0f, now, c, out))
        assertTrue(out.t < 0f) // before the first sample
        assertEquals(d + v * out.t, out.x, 1e-3f) // uniform motion extrapolated backward
        assertEquals(0f, cone(0f, 0f, now), 1e-3f)
    }

    @Test
    fun wrapsAndStillLooksUp() {
        val cap = (1.5f / dt).toInt()
        val h = LightHistory(cap)
        val d = 0.5f * c // 0.5 s of travel
        val now = fill(h, cap * 3) { floatArrayOf(d, 0f, 0f, 0f, 0f) }
        assertTrue(h.wrapped)
        assertEquals(cap, h.size)
        assertTrue(h.retarded(0f, 0f, now, c, out))
        assertEquals(now - d / c, out.t, 1e-4f)
        assertEquals(d, out.x, 1e-4f)
    }

    @Test
    fun wrappedBufferExtrapolatesBackwardPastItsOldestSample() {
        val cap = (0.5f / dt).toInt()
        val h = LightHistory(cap)
        val d = 1.5f * c // needs 1.5 s, buffer spans about 0.5 s
        val now = fill(h, cap * 2) { floatArrayOf(d, 0f, 0f, 0f, 0f) }
        assertTrue(h.wrapped)
        assertTrue(h.retarded(0f, 0f, now, c, out))
        assertEquals(now - d / c, out.t, 1e-4f)
    }

    @Test
    fun nowAheadOfBufferExtrapolatesForward() {
        val d = 0.5f * c
        val h = LightHistory()
        val last = fill(h, 30) { floatArrayOf(d, 0f, 0f, 0f, 0f) }
        val now = last + d / c + 0.5f // the newest sample's light already arrived
        assertTrue(h.retarded(0f, 0f, now, c, out))
        assertEquals(now - d / c, out.t, 1e-4f)
        assertTrue(out.t > last)
    }

    @Test
    fun angleLerpTakesTheShortestArc() {
        val h = LightHistory()
        val a0 = (PI - 0.05).toFloat()
        val a1 = (-PI + 0.05).toFloat() // 0.1 rad apart across the +-pi seam
        h.record(0f, 10f, 0f, a0, 0f, 0f)
        h.record(1f, 10f, 0f, a1, 0f, 0f)
        assertTrue(h.retarded(0f, 0f, 1f, c, out))
        // Static at 10 u: emitted 0.5 s before now, halfway between the samples.
        assertEquals(0.5f, out.t, 1e-4f)
        assertEquals(PI.toFloat(), kotlin.math.abs(out.angle), 1e-3f)
    }

    @Test
    fun clearResetsTheBuffer() {
        val h = LightHistory(4)
        fill(h, 10) { floatArrayOf(1f, 0f, 0f, 0f, 0f) }
        assertTrue(h.wrapped)
        h.clear()
        assertEquals(0, h.size)
        assertFalse(h.wrapped)
        assertFalse(h.retarded(0f, 0f, 1f, c, out))
    }
}
