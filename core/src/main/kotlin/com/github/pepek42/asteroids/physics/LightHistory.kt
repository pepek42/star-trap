package com.github.pepek42.asteroids.physics

import com.github.pepek42.asteroids.math.shortestAngleDiffRad
import com.github.pepek42.asteroids.provider.MAP_HEIGHT
import com.github.pepek42.asteroids.provider.MAP_WIDTH
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.sqrt

/** Spare history beyond the worst-case light travel time (s), so the oldest sample is never needed right at the edge. */
const val LIGHT_HISTORY_MARGIN_S = 0.5f

/** Longest straight line inside the arena (u): `hypot(MAP_WIDTH, MAP_HEIGHT)`, about 282.8 at the default 200 x 200 map. */
val ARENA_DIAGONAL: Float = hypot(MAP_WIDTH, MAP_HEIGHT)

/**
 * Samples one [LightHistory] holds: `ceil((ARENA_DIAGONAL / C_MIN + LIGHT_HISTORY_MARGIN_S) / PHYSICS_UPDATE_INTERVAL)`.
 * The longest light travel time is the arena diagonal at the slowest runtime c ([C_MIN]), plus a margin, sampled once
 * per physics step. At the defaults (282.8 u, c = 10, 60 Hz) that is about 1730 samples; at 6 floats (24 B) each, about
 * 41 KB per tracked entity. Not a `const` because it is derived with `ceil`/`hypot`; it still follows the map size,
 * [C_MIN] and the tick rate.
 */
val LIGHT_HISTORY_CAPACITY: Int =
    ceil((ARENA_DIAGONAL / C_MIN + LIGHT_HISTORY_MARGIN_S) / PHYSICS_UPDATE_INTERVAL).toInt()

/** Below this fraction of c^2, `c^2 - v^2` counts as "moves at c" and the root is bisected instead. */
private const val PHOTON_C2_FRACTION = 1e-3

/** Bisection steps for the photon case; the segment is one physics step long, so 24 steps give ~1e-9 s. */
private const val PHOTON_BISECT_ITERATIONS = 24

/**
 * Ring buffer of past states of one entity, sampled once per physics step with strictly increasing time [t].
 * Pure data, no ECS, no allocation after construction. Used by [retarded] to find where the pilot sees the entity.
 *
 * Samples are stored oldest-to-newest at logical indices `0 until size`. [head] is the next physical write slot.
 * [eternal] is set by the caller when the entity existed at level start, so before its first sample it is treated
 * as moving uniformly forever.
 */
class LightHistory(val capacity: Int = LIGHT_HISTORY_CAPACITY) {
    val t = FloatArray(capacity)
    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    val angle = FloatArray(capacity)
    val vx = FloatArray(capacity)
    val vy = FloatArray(capacity)

    /** Next physical slot to write. */
    var head = 0
        private set

    var size = 0
        private set

    /** True once a sample has been overwritten, i.e. the real history is older than the oldest kept sample. */
    var wrapped = false
        private set

    var eternal = false

    /**
     * Appends a sample, overwriting the oldest when full. [t] must exceed the previous sample's time.
     * Callers should pass time relative to level start: [t] is stored as Float, which loses resolution over long absolute times.
     */
    fun record(t: Float, x: Float, y: Float, angle: Float, vx: Float, vy: Float) {
        val h = head
        this.t[h] = t
        this.x[h] = x
        this.y[h] = y
        this.angle[h] = angle
        this.vx[h] = vx
        this.vy[h] = vy
        head = if (h + 1 == capacity) 0 else h + 1
        if (size == capacity) wrapped = true else size++
    }

    fun clear() {
        head = 0
        size = 0
        wrapped = false
    }

    /** Physical array slot of logical index [k] (0 = oldest, `size - 1` = newest). */
    fun slot(k: Int): Int {
        val s = head - size + k
        return if (s < 0) s + capacity else s
    }
}

/** Reusable output of [retarded]: the emission time and the state of the entity at that time. */
class RetardedState {
    var t = 0f
    var x = 0f
    var y = 0f
    var angle = 0f
    var vx = 0f
    var vy = 0f
}

/**
 * Finds the state of the entity the observer at ([ox], [oy]) sees at time [now]: the emission time `t_e` with
 * `|x(t_e) - o| = c * (now - t_e)`, written to [out]. Returns false when there is nothing to see (empty history, or the
 * entity is younger than its light travel time and neither `wrapped` nor `eternal`).
 *
 * `h(t_e) = |x(t_e) - o| - c * (now - t_e)` increases with `t_e` since |v| < c. Bisect over samples for the last one
 * with `h <= 0`, then solve the light cone exactly inside that segment assuming constant velocity. Looking back `s`
 * from the later sample, with `r1 = x - o`, `tau1 = now - t`, `v` the segment velocity:
 * `(c^2 - v^2) s^2 + 2 (r1.v + c^2 tau1) s + (c^2 tau1^2 - r1^2) = 0`. With `tau1 = 0` this is research E8.
 * Beyond the ends of the buffer it extrapolates with the sample's own velocity. Photons (`c^2 - v^2` near 0) bisect.
 * Angle is lerped on the shortest arc, and is held constant when extrapolating.
 */
fun LightHistory.retarded(ox: Float, oy: Float, now: Float, c: Float, out: RetardedState): Boolean {
    if (size == 0) return false
    val last = size - 1
    // Light from the newest sample has already arrived: the emission is after it, extrapolate forward (s <= 0).
    if (lightCone(slot(last), ox, oy, now, c) <= 0.0) {
        extrapolate(slot(last), ox, oy, now, c, out)
        return true
    }
    // The root is older than the oldest sample.
    if (lightCone(slot(0), ox, oy, now, c) > 0.0) {
        if (!wrapped && !eternal) return false
        extrapolate(slot(0), ox, oy, now, c, out)
        return true
    }
    // Invariant: h(lo) <= 0 < h(hi).
    var lo = 0
    var hi = last
    while (hi - lo > 1) {
        val mid = (lo + hi) ushr 1
        if (lightCone(slot(mid), ox, oy, now, c) <= 0.0) lo = mid else hi = mid
    }
    interpolate(slot(lo), slot(hi), ox, oy, now, c, out)
    return true
}

/** `h` at one sample, in double to limit cancellation. */
private fun LightHistory.lightCone(slot: Int, ox: Float, oy: Float, now: Float, c: Float): Double =
    hypot((x[slot] - ox).toDouble(), (y[slot] - oy).toDouble()) - c.toDouble() * (now.toDouble() - t[slot].toDouble())

/** Solves the light-cone quadratic looking back `s` from a point with offset (r1x, r1y), age tau1 and velocity (vx, vy). */
private fun solveBack(r1x: Double, r1y: Double, tau1: Double, vx: Double, vy: Double, c: Double): Double {
    val c2 = c * c
    val a = c2 - (vx * vx + vy * vy)
    val b = r1x * vx + r1y * vy + c2 * tau1
    val k = c2 * tau1 * tau1 - (r1x * r1x + r1y * r1y)
    if (a < PHOTON_C2_FRACTION * c2) {
        // Degenerate (moves at c): the quadratic collapses to the linear 2 b s + k = 0.
        return if (abs(b) < 1e-12) 0.0 else -k / (2.0 * b)
    }
    val disc = b * b - a * k
    return (-b + sqrt(if (disc > 0.0) disc else 0.0)) / a
}

/** Extrapolates from the sample in [slot] with its own velocity (backward for the oldest, forward for the newest). */
private fun LightHistory.extrapolate(slot: Int, ox: Float, oy: Float, now: Float, c: Float, out: RetardedState) {
    val vxs = vx[slot].toDouble()
    val vys = vy[slot].toDouble()
    val s = solveBack(
        (x[slot] - ox).toDouble(), (y[slot] - oy).toDouble(), (now - t[slot]).toDouble(), vxs, vys, c.toDouble(),
    )
    out.t = (t[slot] - s).toFloat()
    out.x = (x[slot] - vxs * s).toFloat()
    out.y = (y[slot] - vys * s).toFloat()
    out.angle = angle[slot]
    out.vx = vx[slot]
    out.vy = vy[slot]
}

/** Solves inside the segment [lo, hi] (h(lo) <= 0 < h(hi)) with constant velocity and lerps the output. */
private fun LightHistory.interpolate(lo: Int, hi: Int, ox: Float, oy: Float, now: Float, c: Float, out: RetardedState) {
    val dt = (t[hi] - t[lo]).toDouble()
    if (dt <= 0.0) {
        // Equal sample times leave no segment to solve (would divide by zero); report the newer sample.
        out.t = t[hi]
        out.x = x[hi]
        out.y = y[hi]
        out.angle = angle[hi]
        out.vx = vx[hi]
        out.vy = vy[hi]
        return
    }
    val vxs = (x[hi] - x[lo]) / dt
    val vys = (y[hi] - y[lo]) / dt
    val r1x = (x[hi] - ox).toDouble()
    val r1y = (y[hi] - oy).toDouble()
    val tau1 = (now - t[hi]).toDouble()
    val cd = c.toDouble()
    val c2 = cd * cd
    var s: Double
    if (c2 - (vxs * vxs + vys * vys) < PHOTON_C2_FRACTION * c2) {
        // g(s) = |r1 - v s| - c (tau1 + s) decreases from g(0) > 0 to g(dt) <= 0.
        var sLo = 0.0
        var sHi = dt
        repeat(PHOTON_BISECT_ITERATIONS) {
            val m = 0.5 * (sLo + sHi)
            if (hypot(r1x - vxs * m, r1y - vys * m) - cd * (tau1 + m) > 0.0) sLo = m else sHi = m
        }
        s = 0.5 * (sLo + sHi)
    } else {
        s = solveBack(r1x, r1y, tau1, vxs, vys, cd)
    }
    if (s < 0.0) s = 0.0 else if (s > dt) s = dt
    val f = (1.0 - s / dt).toFloat() // 0 at lo, 1 at hi
    out.t = (t[hi] - s).toFloat()
    out.x = x[lo] + (x[hi] - x[lo]) * f
    out.y = y[lo] + (y[hi] - y[lo]) * f
    out.vx = vx[lo] + (vx[hi] - vx[lo]) * f
    out.vy = vy[lo] + (vy[hi] - vy[lo]) * f
    out.angle = angle[lo] + shortestAngleDiffRad(angle[lo], angle[hi]) * f
}
