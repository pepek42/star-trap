package com.github.pepek42.asteroids.weapon

import com.badlogic.gdx.math.Vector2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Lengths / squared lengths below this count as zero (float rounding guard). */
private const val MIN_LENGTH = 1e-6f

/**
 * Lead direction for a guided lock-on shot (ST-164). [rx], [ry] is the target position minus the lock-on shot now, [vtx], [vty]
 * the target velocity in the arena frame and [speed] the lock-on shot's current speed. Solves `|r + v_t·t| = speed·t`, the
 * same intercept idea as [com.github.pepek42.asteroids.physics.photonInterceptDir] but with the projectile's own speed
 * instead of c, and writes the unit direction to the earliest positive intercept into [out].
 *
 * Returns true when an intercept exists. When the target is faster than the lock-on shot, is already behind the lock-on shot in
 * time, or the lock-on shot is (almost) at rest, there is no lead solution: it writes the unit direction to the target's
 * current position into [out] and returns false, so the caller can still steer directly at it. Returns false and writes
 * the zero vector when the target is at the lock-on shot's position. Pure and allocation-free.
 */
fun lockOnLeadDirection(rx: Float, ry: Float, vtx: Float, vty: Float, speed: Float, out: Vector2): Boolean {
    val k = rx.toDouble() * rx + ry.toDouble() * ry
    if (!k.isFinite() || k < MIN_LENGTH * MIN_LENGTH) {
        out.setZero()
        return false
    }
    val s = speed.toDouble().coerceAtLeast(0.0)
    val vx = vtx.toDouble()
    val vy = vty.toDouble()
    if (!s.isFinite() || !vx.isFinite() || !vy.isFinite()) return aimDirect(rx, ry, out)

    var t = Double.NaN
    if (s * s > MIN_LENGTH * MIN_LENGTH) {
        val a = vx * vx + vy * vy - s * s
        val b = 2.0 * (rx * vx + ry * vy)
        val disc = b * b - 4.0 * a * k
        if (abs(a) < 1e-9) {
            // Target speed equals the lock-on shot speed: the quadratic degenerates to b·t + k = 0.
            if (b < 0.0) t = -k / b
        } else if (disc >= 0.0) {
            val root = sqrt(disc)
            val t1 = (-b - root) / (2.0 * a)
            val t2 = (-b + root) / (2.0 * a)
            if (t1 > 0.0 && t1.isFinite() && (t.isNaN() || t1 < t)) t = t1
            if (t2 > 0.0 && t2.isFinite() && (t.isNaN() || t2 < t)) t = t2
        }
    }
    if (t.isNaN()) return aimDirect(rx, ry, out)

    val px = rx + vx * t
    val py = ry + vy * t
    val length = sqrt(px * px + py * py)
    if (!length.isFinite() || length < MIN_LENGTH) return aimDirect(rx, ry, out)
    out.set((px / length).toFloat(), (py / length).toFloat())
    return true
}

/** Writes the unit direction to the target into [out]; false and zero if the target is at the lock-on shot. */
private fun aimDirect(rx: Float, ry: Float, out: Vector2): Boolean {
    val length = sqrt(rx.toDouble() * rx + ry.toDouble() * ry)
    if (!length.isFinite() || length < MIN_LENGTH) {
        out.setZero()
        return false
    }
    out.set((rx / length).toFloat(), (ry / length).toFloat())
    return false
}

/**
 * Turns the unit heading ([hx], [hy]) toward the unit direction ([dx], [dy]) by at most [maxTurn] radians (ST-164).
 * Writes the new unit heading into [out] and returns true. A zero [desired][dx] (or zero [maxTurn]) keeps the heading;
 * a zero current heading writes the zero vector and returns false. Pure and allocation-free.
 */
fun turnHeadingTowards(
    hx: Float, hy: Float, dx: Float, dy: Float, maxTurn: Float, out: Vector2,
): Boolean {
    val headingLength = sqrt(hx.toDouble() * hx + hy.toDouble() * hy)
    if (!headingLength.isFinite() || headingLength < MIN_LENGTH) {
        out.setZero()
        return false
    }
    val desiredLength = sqrt(dx.toDouble() * dx + dy.toDouble() * dy)
    if (!desiredLength.isFinite() || desiredLength < MIN_LENGTH) {
        out.set((hx / headingLength).toFloat(), (hy / headingLength).toFloat())
        return false
    }
    val ux = hx / headingLength
    val uy = hy / headingLength
    val wx = dx / desiredLength
    val wy = dy / desiredLength
    val dot = ux * wx + uy * wy
    val cross = ux * wy - uy * wx
    val angle = atan2(cross, dot)
    val limit = maxTurn.toDouble().coerceAtLeast(0.0)
    val turn = if (angle.isNaN()) 0.0 else angle.coerceIn(-limit, limit)
    val c = cos(turn)
    val s = sin(turn)
    out.set((ux * c - uy * s).toFloat(), (ux * s + uy * c).toFloat())
    return true
}
