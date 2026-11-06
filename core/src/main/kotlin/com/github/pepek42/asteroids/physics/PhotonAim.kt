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

/**
 * Honest fire control (ST-078): the shooter-frame direction n̂' that makes a photon hit a target the pilot sees only as
 * its retarded state [ret], extrapolated at constant velocity to arena time [now] (`x_e = ret.x + ret.vx·(now − ret.t)`).
 * It never reads the target's true position. The shooter is at ([sx], [sy]) with velocity over c ([betaX], [betaY]).
 * Solves the intercept in W with [photonInterceptDir], then converts the world direction to the muzzle direction with
 * [muzzleDirForWorldDir]. Writes the unit direction into [out] and returns true; returns false (and leaves [out] untouched)
 * when there is no intercept.
 */
fun leadDirection(
    sx: Float, sy: Float, betaX: Float, betaY: Float, ret: RetardedState, now: Float, c: Float, out: Vector2,
): Boolean {
    val age = now - ret.t
    val rx = ret.x + ret.vx * age - sx
    val ry = ret.y + ret.vy * age - sy
    if (photonInterceptDir(rx, ry, ret.vx, ret.vy, c, out).isNaN()) return false
    muzzleDirForWorldDir(betaX, betaY, out.x, out.y, out)
    return true
}

/**
 * True-state fire control (ST-125, tactical computer tier 2): the shooter-frame direction n̂' that makes a photon hit a
 * target at its true arena position ([tx], [ty]) with velocity ([tvx], [tvy]), from a shooter at ([sx], [sy]) with velocity
 * over c ([betaX], [betaY]). This is the supercomputer cheat (the pilot never sees the true state, see physics-lies row 34).
 * Solves the intercept in W with [photonInterceptDir], then converts the world direction to the muzzle direction with
 * [muzzleDirForWorldDir]; writes the unit direction n̂' into [out]. Returns the arena seconds to impact, or NaN (leaving
 * [out] untouched) when there is no intercept.
 */
fun trueLeadDirection(
    sx: Float, sy: Float, betaX: Float, betaY: Float, tx: Float, ty: Float, tvx: Float, tvy: Float, c: Float, out: Vector2,
): Float {
    val t = photonInterceptDir(tx - sx, ty - sy, tvx, tvy, c, out)
    if (t.isNaN()) return Float.NaN
    muzzleDirForWorldDir(betaX, betaY, out.x, out.y, out)
    return t
}
