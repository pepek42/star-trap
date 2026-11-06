package com.github.pepek42.asteroids.physics

import com.badlogic.gdx.math.Vector2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Default Schwarzschild radius `r_s` of a black hole, in world units (research E14). Derived radii, all in units of
 * `r_s`: photon sphere 1.5 `r_s`, shadow (critical impact parameter) `b_c` = (3 sqrt(3) / 2) `r_s` = 2.598 `r_s`,
 * ISCO 3 `r_s`, marginally bound orbit 2 `r_s`. The engine can hold a ship still at radius r only while
 * `GM / r^2 <= ENGINE_PROPER_ACCEL`, i.e. outside `r = sqrt(GM / ENGINE_PROPER_ACCEL)` (about 7 u, 3.5 `r_s`, at the
 * default c); closer in, the hole wins.
 */
const val BH_RS = 2f

/**
 * Scale of the source-to-source pull between dynamic gravity sources (owner 2026-11-02): each source feels
 * `SOURCE_COUPLING_DEFAULT * newtonAccel` from the others. FAKE (declared in `physics-lies.md` by ST-092): real holes
 * would pull each other with the full law, which makes a pair of holes collapse in seconds.
 */
const val SOURCE_COUPLING_DEFAULT = 0.1f

/**
 * Gravitational parameter `GM` of the player's ship as a source (owner 2026-11-02). It only pulls the dynamic sources,
 * never other bodies. At 10 u it accelerates a hole by `PLAYER_GM / 10^2` = 0.3 u/s^2.
 */
const val PLAYER_GM = 30f

/** Default of `RelativitySettings.gravityEnabled`: gravity sources act (ST-083). Without sources the system does nothing. */
const val GRAVITY_ENABLED = true

/** Upper bound of `RelativitySettings.playerGm`, the debug slider range (ST-083). */
const val PLAYER_GM_MAX = 200f

/**
 * A photon closer than `PHOTON_SUBSTEP_RADIUS_PER_RS * r_s` to a black hole bends in [PHOTON_SUBSTEPS] substeps per game
 * step. At c = 20 and 60 Hz a photon moves about 0.33 u per step against `r_s` = 2, which is too coarse near the photon
 * sphere (critical impact parameter `b_c` = 2.6 `r_s`). 6 `r_s` is over twice `b_c`: every photon that can still be
 * captured or strongly bent gets the fine integration, and far photons stay at one cheap step.
 */
const val PHOTON_SUBSTEP_RADIUS_PER_RS = 6f

/** Substeps per game step for photons near a hole (a tenth of the step, as `GravityTest`'s fine step). */
const val PHOTON_SUBSTEPS = 10

/** Softening of the player's pull on sources (ST-083): the player is not a point mass. */
const val PLAYER_PULL_SOFTENING = 2f

/** Floor of the source-to-source softening (ST-083); the actual value is `max(radius_i + radius_j, this)`. */
const val SOURCE_PULL_MIN_SOFTENING = 1f

/** `GM` of a hole with Schwarzschild radius [rs]: `r_s = 2 GM / c^2`, so `GM = r_s c^2 / 2` (400 for `BH_RS` at c = 20). */
fun gmFromRs(rs: Float, c: Float): Float = rs * c * c / 2f

/** Inverse of [gmFromRs]: `r_s = 2 GM / c^2` (planets, ST-086). */
fun rsFromGm(gm: Float, c: Float): Float = 2f * gm / (c * c)

/**
 * Proper acceleration `du/dtau` of a massive body at [rx], [ry] (body minus source) with proper velocity [ux], [uy]
 * (research E14): `-(GM / r^3) (1 + 3 L^2 / (c^2 r^2)) r_vec`, `L = |r x u|`. Gives exact Schwarzschild orbit shapes.
 * `r` is floored at [softening]. Callers working in arena time divide the result by gamma. Writes into [out], no allocation.
 */
fun gravityAccelTau(
    rx: Float,
    ry: Float,
    ux: Float,
    uy: Float,
    gm: Float,
    c: Float,
    softening: Float,
    out: Vector2,
): Vector2 {
    val r = max(sqrt(rx * rx + ry * ry), softening)
    val l = rx * uy - ry * ux
    val k = -(gm / (r * r * r)) * (1f + 3f * l * l / (c * c * r * r))
    return out.set(k * rx, k * ry)
}

/** Newtonian pull `-GM / r^3 r_vec` with `r` floored at [softening] (used when `relativityEnabled` is false). */
fun newtonAccel(rx: Float, ry: Float, gm: Float, softening: Float, out: Vector2): Vector2 {
    val r = max(sqrt(rx * rx + ry * ry), softening)
    val k = -gm / (r * r * r)
    return out.set(k * rx, k * ry)
}

/**
 * Bends the photon direction [nx], [ny] (unit) at [rx], [ry] (photon minus source) over [dt] (research E14):
 * `k = c n`, `k += -(3 GM / c^2) (L_k^2 / r^5) r_vec dt` with `L_k = |r x k|`, then renormalises. Renormalising only
 * reparametrises the path, so its shape stays exact. Writes the new unit direction into [out] (may be the same
 * vector the components came from), no allocation. A photon exactly on the source keeps its direction.
 */
fun bendPhoton(
    rx: Float,
    ry: Float,
    nx: Float,
    ny: Float,
    gm: Float,
    c: Float,
    dt: Float,
    out: Vector2,
): Vector2 {
    val r2 = rx * rx + ry * ry
    if (r2 <= 0f) return out.set(nx, ny)
    val lk = c * (rx * ny - ry * nx)
    val r = sqrt(r2)
    val f = -(3f * gm / (c * c)) * (lk * lk / (r2 * r2 * r)) * dt
    val kx = c * nx + f * rx
    val ky = c * ny + f * ry
    val len = sqrt(kx * kx + ky * ky)
    return if (len > 0f) out.set(kx / len, ky / len) else out.set(nx, ny)
}

/**
 * Specific angular momentum `L` of the circular orbit at radius [r] around a hole of Schwarzschild radius [rs]:
 * `sqrt(GM r / (1 - 1.5 r_s / r))`. NaN at and inside the photon sphere (`r <= 1.5 r_s`). The orbit is stable outside
 * the ISCO (3 `r_s`). Tangential proper speed is `L / r`.
 */
fun circularOrbitL(r: Float, rs: Float, gm: Float): Float = sqrt(gm * r / (1f - 1.5f * rs / r))

/**
 * Merges two gravity sources, conserving momentum `m v`. Writes `[m1 + m2, xcm, ycm, vxcm, vycm]` into [out] (size 5):
 * the mass-weighted centre and velocity. For black holes `m` is proportional to `r_s`, so callers pass `r_s` as the
 * mass and the merged hole gets `r_s = r_s1 + r_s2`. Returns [out].
 */
fun mergeSources(
    m1: Float,
    x1: Float,
    y1: Float,
    vx1: Float,
    vy1: Float,
    m2: Float,
    x2: Float,
    y2: Float,
    vx2: Float,
    vy2: Float,
    out: FloatArray,
): FloatArray {
    val m = m1 + m2
    val w1 = if (m != 0f) m1 / m else 0.5f
    val w2 = 1f - w1
    out[0] = m
    out[1] = x1 * w1 + x2 * w2
    out[2] = y1 * w1 + y2 * w2
    out[3] = vx1 * w1 + vx2 * w2
    out[4] = vy1 * w1 + vy2 * w2
    return out
}
