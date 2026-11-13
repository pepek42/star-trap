package com.github.pepek42.asteroids.physics

import com.badlogic.gdx.math.Vector2
import kotlin.math.asinh
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * Special relativity helpers (research/relativity-effects.md §2, E3, E4, E7).
 *
 * All functions are pure and allocation-free: they compute in Double and write results into caller-provided vectors.
 * Non-finite input yields the zero vector, or γ = 1 for scalar gamma results. Callers guard NaN themselves (ST-037).
 */

/** Smallest speed treated as "moving". Below this, direction-dependent formulas fall back to the rest case. */
private const val MIN_SPEED = 1e-6

/** Output speeds are capped at this fraction of c so Float rounding can never produce |v| >= c. */
private const val SPEED_FRACTION_LIMIT = 1.0 - 1e-6

/** Largest β² used internally where a speed is not clamped by the caller, keeps γ finite. */
private const val BETA_SQ_LIMIT = 1.0 - 1e-12

private fun finite(x: Double) = !x.isNaN() && !x.isInfinite()

/** γ for a velocity squared, clamped just below c so the result stays finite. Returns 1 for non-finite input. */
private fun gammaFromSpeedSq(speedSq: Double, c: Double): Double {
    if (!finite(speedSq) || !finite(c) || c <= 0.0) return 1.0
    return 1.0 / sqrt(1.0 - min(speedSq / (c * c), BETA_SQ_LIMIT))
}

/** γ = sqrt(1 + |u|²/c²) from proper velocity. Never below 1, no cancellation. */
fun gammaFromU(ux: Float, uy: Float, c: Float = SPEED_OF_LIGHT): Float {
    val x = ux.toDouble()
    val y = uy.toDouble()
    val cd = c.toDouble()
    val g = sqrt(1.0 + (x * x + y * y) / (cd * cd))
    return if (finite(g)) g.toFloat() else 1f
}

/** v = u / γ(u). |out| < c for any finite u. */
fun velocityFromU(ux: Float, uy: Float, c: Float, out: Vector2): Vector2 {
    val x = ux.toDouble()
    val y = uy.toDouble()
    val cd = c.toDouble()
    val g = sqrt(1.0 + (x * x + y * y) / (cd * cd))
    if (!finite(g) || !finite(x) || !finite(y)) return out.setZero()
    var vx = x / g
    var vy = y / g
    val limit = cd * SPEED_FRACTION_LIMIT
    val speed = sqrt(vx * vx + vy * vy)
    if (speed > limit) {
        vx *= limit / speed
        vy *= limit / speed
    }
    return out.set(vx.toFloat(), vy.toFloat())
}

/** γ = 1 / sqrt(1 - min(v²/c², βmax²)). Only for velocities changed by Box2D, since it loses precision near c. */
fun gammaFromV(vx: Float, vy: Float, c: Float, betaMax: Float = BETA_MAX_MASSIVE): Float {
    val x = vx.toDouble()
    val y = vy.toDouble()
    val cd = c.toDouble()
    val b = betaMax.toDouble()
    val sq = (x * x + y * y) / (cd * cd)
    if (!finite(sq) || !finite(b)) return 1f
    val g = 1.0 / sqrt(1.0 - min(sq, b * b))
    return if (finite(g)) g.toFloat() else 1f
}

/** Scales [v] in place so that |v| ≤ [maxSpeed]. */
fun clampSpeed(v: Vector2, maxSpeed: Float): Vector2 {
    val x = v.x.toDouble()
    val y = v.y.toDouble()
    val len = sqrt(x * x + y * y)
    if (!finite(len)) return v.setZero()
    if (len > maxSpeed.toDouble()) {
        val k = maxSpeed.toDouble() / len
        v.set((x * k).toFloat(), (y * k).toFloat())
    }
    return v
}

/** u = γ(v)·v after clamping |v| to βmax·c. */
fun uFromV(vx: Float, vy: Float, c: Float, betaMax: Float, out: Vector2): Vector2 {
    val x = vx.toDouble()
    val y = vy.toDouble()
    val cd = c.toDouble()
    val maxSpeed = betaMax.toDouble() * cd
    val len = sqrt(x * x + y * y)
    if (!finite(len) || !finite(maxSpeed)) return out.setZero()
    val k = if (len > maxSpeed) maxSpeed / len else 1.0
    val cx = x * k
    val cy = y * k
    val sq = (cx * cx + cy * cy) / (cd * cd)
    val g = 1.0 / sqrt(1.0 - min(sq, betaMax.toDouble() * betaMax.toDouble()))
    if (!finite(g)) return out.setZero()
    return out.set((cx * g).toFloat(), (cy * g).toFloat())
}

/** Scales [u] in place so that γ(u) ≤ [gammaMax]: u *= min(1, c·sqrt(γmax² - 1)/|u|). */
fun clampU(u: Vector2, gammaMax: Float, c: Float): Vector2 {
    val x = u.x.toDouble()
    val y = u.y.toDouble()
    val len = sqrt(x * x + y * y)
    if (!finite(len)) return u.setZero()
    val gm = gammaMax.toDouble()
    val uMax = c.toDouble() * sqrt(gm * gm - 1.0)
    if (len > uMax) {
        val k = uMax / len
        u.set((x * k).toFloat(), (y * k).toFloat())
    }
    return u
}

/** η = asinh(|u|/c). Additive for collinear boosts. */
fun rapidity(ux: Float, uy: Float, c: Float): Float {
    val x = ux.toDouble()
    val y = uy.toDouble()
    val r = asinh(sqrt(x * x + y * y) / c.toDouble())
    return if (finite(r)) r.toFloat() else 0f
}

/**
 * Relativistic velocity addition. The shooter moves at [v] in W and the muzzle velocity [w] is given in the shooter frame.
 * out = (w∥ + v + w⊥/γv) / (1 + v·w/c²), with ∥ along v̂. [out] may alias [w] but not [v].
 */
fun addVelocity(v: Vector2, w: Vector2, c: Float, out: Vector2): Vector2 {
    val vx = v.x.toDouble()
    val vy = v.y.toDouble()
    val wx = w.x.toDouble()
    val wy = w.y.toDouble()
    val cd = c.toDouble()
    if (!finite(vx) || !finite(vy) || !finite(wx) || !finite(wy)) return out.setZero()
    val vLen = sqrt(vx * vx + vy * vy)
    if (vLen < MIN_SPEED) return out.set(wx.toFloat(), wy.toFloat())
    val c2 = cd * cd
    val gamma = gammaFromSpeedSq(vLen * vLen, cd)
    val hx = vx / vLen
    val hy = vy / vLen
    val wPar = wx * hx + wy * hy
    val wPerpX = wx - wPar * hx
    val wPerpY = wy - wPar * hy
    val denom = 1.0 + (vx * wx + vy * wy) / c2
    if (denom <= 1e-12) return out.setZero()
    val ox = (wPar * hx + vx + wPerpX / gamma) / denom
    val oy = (wPar * hy + vy + wPerpY / gamma) / denom
    if (!finite(ox) || !finite(oy)) return out.setZero()
    return out.set(ox.toFloat(), oy.toFloat())
}

/**
 * Photon aberration at the muzzle. (nx, ny) is the unit direction in the shooter frame, (betaX, betaY) the shooter velocity
 * over c in W. Writes the unit direction in W into [out].
 */
fun aberratePhoton(betaX: Float, betaY: Float, nx: Float, ny: Float, out: Vector2): Vector2 {
    val bx = betaX.toDouble()
    val by = betaY.toDouble()
    val px = nx.toDouble()
    val py = ny.toDouble()
    if (!finite(bx) || !finite(by) || !finite(px) || !finite(py)) return out.setZero()
    val nLen = sqrt(px * px + py * py)
    if (nLen < MIN_SPEED) return out.setZero()
    val ux = px / nLen
    val uy = py / nLen
    val bLen = sqrt(bx * bx + by * by)
    if (bLen < MIN_SPEED) return out.set(ux.toFloat(), uy.toFloat())
    val bSq = min(bLen * bLen, BETA_SQ_LIMIT)
    val beta = sqrt(bSq)
    val gamma = 1.0 / sqrt(1.0 - bSq)
    val hx = bx / bLen
    val hy = by / bLen
    val nPar = ux * hx + uy * hy
    val perpX = ux - nPar * hx
    val perpY = uy - nPar * hy
    val k = 1.0 + beta * nPar
    val outPar = (nPar + beta) / k
    val perpScale = 1.0 / (gamma * k)
    val rx = outPar * hx + perpX * perpScale
    val ry = outPar * hy + perpY * perpScale
    val len = sqrt(rx * rx + ry * ry)
    if (!finite(len) || len < MIN_SPEED) return out.setZero()
    return out.set((rx / len).toFloat(), (ry / len).toFloat())
}

/**
 * Doppler factor D = f_observed / f_emitted. [nx], [ny] is the unit propagation direction from source to observer in W.
 * D = γo(1 - βo·n̂) / (γs(1 - βs·n̂)). D > 1 is a blueshift. Returns 1 for non-finite input.
 */
fun dopplerFactor(betaObs: Vector2, betaSrc: Vector2, nx: Float, ny: Float): Float {
    val ox = betaObs.x.toDouble()
    val oy = betaObs.y.toDouble()
    val sx = betaSrc.x.toDouble()
    val sy = betaSrc.y.toDouble()
    val px = nx.toDouble()
    val py = ny.toDouble()
    if (!finite(ox) || !finite(oy) || !finite(sx) || !finite(sy) || !finite(px) || !finite(py)) return 1f
    val gammaO = gammaFromSpeedSq(ox * ox + oy * oy, 1.0)
    val gammaS = gammaFromSpeedSq(sx * sx + sy * sy, 1.0)
    val den = gammaS * (1.0 - (sx * px + sy * py))
    if (den <= 1e-12) return 1f
    val d = gammaO * (1.0 - (ox * px + oy * py)) / den
    return if (finite(d)) d.toFloat() else 1f
}

/**
 * Energy of a photon as absorbed by the target relative to the energy the shooter emitted (research E12, exact):
 * `γt(1 − βt·n̂) / (γs(1 − βs·n̂))`, the Doppler factor from shooter to target. [bsx], [bsy] is the shooter's velocity over c
 * at emission, [btx], [bty] the target's velocity over c at the hit, [nx], [ny] the photon's arena direction. Each β is
 * clamped to [BETA_MAX_MASSIVE] so γ stays finite. Returns 1 for non-finite input.
 */
fun photonEnergyFactor(bsx: Float, bsy: Float, btx: Float, bty: Float, nx: Float, ny: Float): Float {
    val sx = bsx.toDouble()
    val sy = bsy.toDouble()
    val tx = btx.toDouble()
    val ty = bty.toDouble()
    val px = nx.toDouble()
    val py = ny.toDouble()
    if (!finite(sx) || !finite(sy) || !finite(tx) || !finite(ty) || !finite(px) || !finite(py)) return 1f
    val limit = BETA_MAX_MASSIVE.toDouble()
    val sLen = sqrt(sx * sx + sy * sy)
    val tLen = sqrt(tx * tx + ty * ty)
    val sk = if (sLen > limit) limit / sLen else 1.0
    val tk = if (tLen > limit) limit / tLen else 1.0
    val bs = min(sLen, limit)
    val bt = min(tLen, limit)
    val gammaS = 1.0 / sqrt(1.0 - bs * bs)
    val gammaT = 1.0 / sqrt(1.0 - bt * bt)
    val den = gammaS * (1.0 - sk * (sx * px + sy * py))
    if (den <= 1e-12) return 1f
    val f = gammaT * (1.0 - tk * (tx * px + ty * py)) / den
    return if (finite(f)) f.toFloat() else 1f
}

/**
 * Photon damage multiplier (ST-081, ST-196): [photonEnergyFactor] times the gravitational term [gs] / [gt] (shooter and
 * target `gravFactor`, 1 when absent), clamped to [PHOTON_DAMAGE_D_MIN]..[PHOTON_DAMAGE_D_MAX]. Shared by the hit and the overlay label.
 */
fun clampedPhotonDamageFactor(
    bsx: Float, bsy: Float, btx: Float, bty: Float, nx: Float, ny: Float, gs: Float, gt: Float,
): Float = (photonEnergyFactor(bsx, bsy, btx, bty, nx, ny) * gs / gt).coerceIn(PHOTON_DAMAGE_D_MIN, PHOTON_DAMAGE_D_MAX)

/**
 * World-frame acceleration of a thruster firing along unit direction (nx, ny) in the ship frame:
 * a = a0·[(n̂'·v̂)v̂ + (n̂' - (n̂'·v̂)v̂)/γ]. Falls back to a0·n̂' when |v| < 1e-6.
 */
fun thrustAccelWorld(a0: Float, nx: Float, ny: Float, vx: Float, vy: Float, gamma: Float, out: Vector2): Vector2 {
    val a = a0.toDouble()
    val px = nx.toDouble()
    val py = ny.toDouble()
    val x = vx.toDouble()
    val y = vy.toDouble()
    val g = gamma.toDouble()
    if (!finite(a) || !finite(px) || !finite(py) || !finite(x) || !finite(y) || !finite(g) || g < 1.0) return out.setZero()
    val vLen = sqrt(x * x + y * y)
    if (vLen < MIN_SPEED) return out.set((a * px).toFloat(), (a * py).toFloat())
    val hx = x / vLen
    val hy = y / vLen
    val par = px * hx + py * hy
    val ax = a * (par * hx + (px - par * hx) / g)
    val ay = a * (par * hy + (py - par * hy) / g)
    return out.set(ax.toFloat(), ay.toFloat())
}

/**
 * Braking boost multiplier for an engine firing along unit direction (dirX, dirY): 1 + k·max(0, −cos θ), θ the angle
 * between the thrust direction and v. Returns 1 when v is (near) zero.
 */
fun brakeBoostFactor(dirX: Float, dirY: Float, vx: Float, vy: Float, k: Float): Float {
    val speed = sqrt(vx * vx + vy * vy)
    if (speed < 1e-6f) return 1f
    val cos = (dirX * vx + dirY * vy) / speed
    return 1f + k * max(0f, -cos)
}

/** Relative Lorentz factor of two bodies: γa·γb·(1 - va·vb/c²). Used for impact energy K = (γrel - 1)·m·c². */
fun gammaRel(va: Vector2, vb: Vector2, c: Float): Float {
    val ax = va.x.toDouble()
    val ay = va.y.toDouble()
    val bx = vb.x.toDouble()
    val by = vb.y.toDouble()
    val cd = c.toDouble()
    if (!finite(ax) || !finite(ay) || !finite(bx) || !finite(by) || !finite(cd) || cd <= 0.0) return 1f
    val ga = gammaFromSpeedSq(ax * ax + ay * ay, cd)
    val gb = gammaFromSpeedSq(bx * bx + by * by, cd)
    val g = ga * gb * (1.0 - (ax * bx + ay * by) / (cd * cd))
    return if (finite(g)) g.toFloat() else 1f
}
