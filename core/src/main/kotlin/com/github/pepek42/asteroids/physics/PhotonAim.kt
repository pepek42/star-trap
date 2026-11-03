package com.github.pepek42.asteroids.physics

import com.badlogic.gdx.math.Vector2
import kotlin.math.sqrt

/**
 * Photon intercept in the arena frame W. Solves |r + v_t·t| = c·t for the earliest positive t, where [rx], [ry] is the
 * target position minus the shooter (now) and [vtx], [vty] the target velocity in W. Writes the unit direction of the
 * photon, (r + v_t·t)/(c·t), into [out] and returns t. Returns NaN and leaves [out] untouched if t is not finite or t ≤ 0
 * (for example r = 0). The shooter velocity does not enter, because the photon moves at c in W anyway.
 */
fun photonInterceptDir(rx: Float, ry: Float, vtx: Float, vty: Float, c: Float, out: Vector2): Float {
    val r2x = rx.toDouble()
    val r2y = ry.toDouble()
    val vx = vtx.toDouble()
    val vy = vty.toDouble()
    val cd = c.toDouble()
    val a = vx * vx + vy * vy - cd * cd
    val b = 2.0 * (r2x * vx + r2y * vy)
    val k = r2x * r2x + r2y * r2y
    val disc = b * b - 4.0 * a * k
    val t = (-b - sqrt(disc)) / (2.0 * a)
    if (!t.isFinite() || t <= 0.0) return Float.NaN
    val dx = (r2x + vx * t) / (cd * t)
    val dy = (r2y + vy * t) / (cd * t)
    if (!dx.isFinite() || !dy.isFinite()) return Float.NaN
    out.set(dx.toFloat(), dy.toFloat())
    return t.toFloat()
}

/**
 * Shooter-frame muzzle direction that makes the photon fly along the world direction (nWx, nWy). This is the inverse of
 * [aberratePhoton]: the forward aberration uses the shooter velocity β, so the inverse uses −β. Writes the unit direction
 * in the shooter frame into [out] and returns it.
 */
fun muzzleDirForWorldDir(betaX: Float, betaY: Float, nWx: Float, nWy: Float, out: Vector2): Vector2 =
    aberratePhoton(-betaX, -betaY, nWx, nWy, out)
