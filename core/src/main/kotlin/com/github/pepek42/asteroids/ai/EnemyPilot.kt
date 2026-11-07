package com.github.pepek42.asteroids.ai

import com.github.pepek42.asteroids.math.shortestAngleDiffRad
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/** Standoff distance in light-seconds; the call site uses `standoff = c * ENEMY_STANDOFF_LIGHT_SECONDS`. */
const val ENEMY_STANDOFF_LIGHT_SECONDS = 1f

/** Top closing speed as a fraction of c; the call site uses `vMax = c * ENEMY_VMAX_BETA`. gamma <= 1.155 there. */
const val ENEMY_VMAX_BETA = 0.5f

/** Fraction of the player's engine proper acceleration an enemy uses: `accel = ENEMY_ENGINE_FRACTION * settings.engineProperAccel`. */
const val ENEMY_ENGINE_FRACTION = 0.6f

/** Safety factor on the braking distance: desired speed = sqrt(2 * accel * margin * distance), so it brakes at half power. */
const val ENEMY_BRAKE_MARGIN = 0.5f

/** Velocity error below this fraction of vMax counts as matched (stop thrusting, start aiming). */
const val ENEMY_VEL_DEADBAND = 0.1f

/** Hull must be within this cone (20 degrees) of the wanted heading before the engine fires. */
const val ENEMY_THRUST_CONE_RAD = 20f * Math.PI.toFloat() / 180f

private const val MIN_DISTANCE = 1e-3f

/** Reusable output of [pursuitCommand]; plain holder, not a component. */
class PilotCommand {
    var headingRad = 0f
    var thrust = 0f
    var aiming = false
}

/**
 * Steering law: close to [standoff] from the target estimate, match its velocity, then face it.
 * Relative velocity is Galilean (APPROX for a controller; the physics stays exact).
 * Speeds, accel and standoff are in world units; the caller scales them by c.
 */
fun pursuitCommand(
    sx: Float, sy: Float, svx: Float, svy: Float, hullRad: Float,
    tx: Float, ty: Float, tvx: Float, tvy: Float,
    standoff: Float, accel: Float, vMax: Float,
    out: PilotCommand,
): PilotCommand {
    val rx = tx - sx
    val ry = ty - sy
    val d = sqrt(rx * rx + ry * ry)
    if (d < MIN_DISTANCE) {
        out.aiming = true
        out.thrust = 0f
        out.headingRad = hullRad
        return out
    }
    val ux = rx / d
    val uy = ry / d
    val wx = svx - tvx
    val wy = svy - tvy
    val gap = d - standoff
    val speed = min(vMax, sqrt(2f * accel * ENEMY_BRAKE_MARGIN * abs(gap)))
    val vDes = if (gap >= 0f) speed else -speed
    val ex = ux * vDes - wx
    val ey = uy * vDes - wy
    if (sqrt(ex * ex + ey * ey) < ENEMY_VEL_DEADBAND * vMax) {
        out.aiming = true
        out.thrust = 0f
        out.headingRad = atan2(uy, ux)
    } else {
        out.aiming = false
        out.headingRad = atan2(ey, ex)
        out.thrust = if (abs(shortestAngleDiffRad(hullRad, out.headingRad)) < ENEMY_THRUST_CONE_RAD) 1f else 0f
    }
    return out
}
