package com.github.pepek42.asteroids.physics

import com.badlogic.gdx.math.Vector2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Skin width `s` in world units. */
const val FIELD_SKIN = 10f
const val FIELD_EXPONENT = 2f

/** Peak field acceleration `A` in u/s^2. */
const val FIELD_ACCEL_MAX = 3000f

/** Outward-velocity damping `k_d` in 1/s. */
const val FIELD_DAMPING = 1f
const val FIELD_BACKSTOP_RESTITUTION = 0.9f
const val FIELD_PHOTON_MIRROR_Q = 0.5f
const val FIELD_USE_BODY_RADIUS = true

data class BoundaryFieldParams(
    val skin: Float = FIELD_SKIN,
    val exponent: Float = FIELD_EXPONENT,
    val accelMax: Float = FIELD_ACCEL_MAX,
    val damping: Float = FIELD_DAMPING,
    val useBodyRadius: Boolean = FIELD_USE_BODY_RADIUS,
)

private fun wallQ(d: Float, skin: Float): Float = ((skin - d) / skin).coerceIn(0f, 1f)

private fun BoundaryFieldParams.radius(rBody: Float) = if (useBodyRadius) rBody else 0f

/**
 * Acceleration of the arena boundary field (research section 3) for a body at (x, y) with velocity (vx, vy)
 * in the arena [0, mapW] x [0, mapH]. Writes into [out] and returns it. Does not allocate.
 */
fun boundaryAcceleration(
    x: Float,
    y: Float,
    vx: Float,
    vy: Float,
    rBody: Float,
    mapW: Float,
    mapH: Float,
    params: BoundaryFieldParams,
    out: Vector2,
): Vector2 {
    val r = params.radius(rBody)
    val s = params.skin
    // Left (+x), right (-x), bottom (+y), top (-y).
    val qLeft = wallQ(x - r, s)
    val qRight = wallQ(mapW - x - r, s)
    val qBottom = wallQ(y - r, s)
    val qTop = wallQ(mapH - y - r, s)

    var ax = params.accelMax * (qLeft.pow(params.exponent) - qRight.pow(params.exponent))
    var ay = params.accelMax * (qBottom.pow(params.exponent) - qTop.pow(params.exponent))
    // Damping acts only while moving outward (v . n < 0); n is the inward normal.
    ax -= params.damping * (qLeft * min(0f, vx) - qRight * min(0f, -vx))
    ay -= params.damping * (qBottom * min(0f, vy) - qTop * min(0f, -vy))
    return out.set(ax, ay)
}

/** Deepest penetration into the skin, 0 (outside the skin) to 1 (at a wall). */
fun boundaryDepthQ(x: Float, y: Float, rBody: Float, mapW: Float, mapH: Float, params: BoundaryFieldParams): Float {
    val r = params.radius(rBody)
    val s = params.skin
    return max(
        max(wallQ(x - r, s), wallQ(mapW - x - r, s)),
        max(wallQ(y - r, s), wallQ(mapH - y - r, s)),
    )
}

/** Potential per unit mass: A s q^(n+1) / (n+1). */
fun boundaryPotential(q: Float, params: BoundaryFieldParams): Float =
    params.accelMax * params.skin * q.pow(params.exponent + 1f) / (params.exponent + 1f)

/** Newtonian turn-around depth (distance into the skin) for normal speed [vNormal], capped at the skin width. */
fun newtonianTurnAroundDepth(vNormal: Float, params: BoundaryFieldParams): Float {
    val n1 = params.exponent + 1f
    val qStar = (n1 * vNormal * vNormal / (2f * params.accelMax * params.skin)).pow(1f / n1)
    return min(params.skin * qStar, params.skin)
}
