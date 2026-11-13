package com.github.pepek42.asteroids.weapon

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.physics.GAMMA_MAX_MASSIVE
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.thrustAccelWorld
import com.github.pepek42.asteroids.physics.uFromV
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tanh

/** Most rocket flights one [rocketInterceptDir] call integrates; the usual solve needs 3 to 5. */
const val ROCKET_INTERCEPT_ITERATIONS = 16

/**
 * A solution this close counts as a hit (u): half the rocket's long side, so the hull overlaps the target. Derived from
 * the rocket, not tuned: a wider body would need a wider tolerance.
 */
const val ROCKET_INTERCEPT_TOLERANCE = ROCKET_LENGTH / 2f

/** Lengths below this count as zero (float rounding guard). */
private const val MIN_LENGTH = 1e-6f

/** First aim step of the line search (rad): 20°, a sixth of the widest useful correction. */
private const val LINE_SEARCH_STEP = 20f * MathUtils.degreesToRadians

/** Each same-sign step widens the search by this factor, so a far root needs few probes. */
private const val LINE_SEARCH_GROWTH = 1.5f

/** The line search never jumps more than a quarter turn from the last angle with a known miss. */
private const val LINE_SEARCH_MAX = MathUtils.PI / 2f

/** Below this step (rad) the line search gives up instead of probing ever closer angles. */
private const val LINE_SEARCH_MIN = 1e-3f

/** The refinement stops when the bracket is this narrow (rad, about 0.06°), closer than the tolerance needs. */
private const val ANGLE_EPSILON = 1e-3f

/**
 * The endpoint of the last [probeAim] integration (single-threaded game code, so one instance is enough): the flight
 * time and the perpendicular miss at the moment the rocket is as far from the ship as the target.
 */
private class RocketAimProbe {
    var hitTime = 0f
    var miss = 0f
}

private val aimProbe = RocketAimProbe()
private val probeAccel = Vector2()

/** Starting offsets [rocketInterceptDir] tries when the extrapolated-target aim never reaches the target. */
private val FALLBACK_AIM_OFFSETS = floatArrayOf(
    MathUtils.PI / 4f, -MathUtils.PI / 4f, MathUtils.PI / 2f, -MathUtils.PI / 2f, MathUtils.PI,
)

/**
 * Aim direction for an unguided pod rocket (ST-182), solved for the rocket's real flight. The rocket starts with the
 * ship's proper velocity ([shipUx], [shipUy]) and its motor adds [properAccel] along the fixed aim for [burn] seconds of
 * rocket proper time, so its path in the arena frame bends toward the aim: neither a photon lead nor a constant-speed
 * lead hits. This is the computer's true-state fire control (physics-lies row 34), the rocket counterpart of
 * [com.github.pepek42.asteroids.physics.trueLeadDirection].
 *
 * [probeAim] integrates the flight with the very proper-velocity update `RocketMotorSystem` + `RelativisticDynamics` use,
 * at the physics step [stepDt] (no literal: pass `PHYSICS_UPDATE_INTERVAL`). It starts from the straight-line time at the
 * burnout speed, aims at the target extrapolated to that time, re-measures the real flight and rotates the aim by the
 * perpendicular miss it finds (the path bends, so aiming at the extrapolated point alone misses). A line search then
 * brackets the miss's zero and false position refines it. That aim-angle root replaced the card's fixed-point iteration
 * on the hit time: the extrapolated-target seed is only the starting point, because the motor's acceleration bends the
 * path away from the straight line and the plain time iteration misses. At most [ROCKET_INTERCEPT_ITERATIONS] flights,
 * stopping below [ROCKET_INTERCEPT_TOLERANCE].
 *
 * Returns the arena seconds from launch to the hit and writes the unit aim direction into [out]. Returns NaN and leaves
 * [out] untouched when no aim hits within [maxTime] (for example a target faster than the rocket that runs away) or
 * when an argument is unusable. Pure apart from [out] and the file-private probe scratch; allocation-free.
 */
fun rocketInterceptDir(
    shipX: Float,
    shipY: Float,
    shipUx: Float,
    shipUy: Float,
    tx: Float,
    ty: Float,
    tvx: Float,
    tvy: Float,
    properAccel: Float,
    burn: Float,
    c: Float,
    maxTime: Float,
    stepDt: Float,
    out: Vector2,
): Float {
    if (c <= 0f || stepDt <= 0f || maxTime < stepDt || properAccel <= 0f || burn < 0f) return Float.NaN
    if (shipX.isNaN() || shipY.isNaN() || shipUx.isNaN() || shipUy.isNaN() || tx.isNaN() || ty.isNaN() ||
        tvx.isNaN() || tvy.isNaN() || properAccel.isNaN() || burn.isNaN() || maxTime.isNaN() ||
        shipX.isInfinite() || shipY.isInfinite() || shipUx.isInfinite() || shipUy.isInfinite() || tx.isInfinite() ||
        ty.isInfinite() || tvx.isInfinite() || tvy.isInfinite() || properAccel.isInfinite() || burn.isInfinite() ||
        maxTime.isInfinite()
    ) {
        return Float.NaN
    }
    val rx = tx - shipX
    val ry = ty - shipY
    val distance = hypot(rx, ry)
    if (distance < MIN_LENGTH) return Float.NaN

    // Straight-line time at the burnout speed of a rocket launched from rest: only a seed for the first aim. The ship's
    // velocity can make the real flight faster (flying toward the target) or slower (flying away); the search corrects it.
    val burnoutSpeed = c * tanh(properAccel * burn / c)
    if (!(burnoutSpeed > MIN_LENGTH)) return Float.NaN
    val seedTime = distance / burnoutSpeed
    var angle = atan2(ty + tvy * seedTime - shipY, tx + tvx * seedTime - shipX)
    if (!angle.isFinite()) angle = atan2(ry, rx)

    var budget = ROCKET_INTERCEPT_ITERATIONS
    budget--
    if (!probeAim(shipX, shipY, shipUx, shipUy, angle, properAccel, burn, c, maxTime, stepDt, tx, ty, tvx, tvy)) {
        // The seed can point so far off that the rocket never reaches the target's distance, or the target flees. Try
        // the direct bearings (forward, the sides, backwards) before calling it impossible.
        val bearing = atan2(ry, rx)
        var found = false
        for (i in FALLBACK_AIM_OFFSETS.indices) {
            if (budget <= 0) break
            budget--
            angle = bearing + FALLBACK_AIM_OFFSETS[i]
            if (probeAim(shipX, shipY, shipUx, shipUy, angle, properAccel, burn, c, maxTime, stepDt, tx, ty, tvx, tvy)) {
                found = true
                break
            }
        }
        if (!found) return Float.NaN
    }
    if (abs(aimProbe.miss) <= ROCKET_INTERCEPT_TOLERANCE) return finishAim(angle, aimProbe.hitTime, out)

    // The target is counter-clockwise of the rocket exactly when the miss is positive, so rotating the aim that way
    // brings the rocket onto it. Walk until the sign flips (a bracket around the root).
    val direction = if (aimProbe.miss > 0f) 1f else -1f
    var nearAngle = angle
    var nearMiss = aimProbe.miss
    var farAngle = Float.NaN
    var farMiss = Float.NaN
    var step = LINE_SEARCH_STEP
    while (budget > 0 && farAngle.isNaN()) {
        budget--
        val next = nearAngle + direction * step
        if (!probeAim(shipX, shipY, shipUx, shipUy, next, properAccel, burn, c, maxTime, stepDt, tx, ty, tvx, tvy)) {
            // No crossing that way: back off instead of leaving the reachable angles.
            step *= 0.5f
            if (step < LINE_SEARCH_MIN) return Float.NaN
            continue
        }
        if (abs(aimProbe.miss) <= ROCKET_INTERCEPT_TOLERANCE) return finishAim(next, aimProbe.hitTime, out)
        if (aimProbe.miss * nearMiss < 0f) {
            farAngle = next
            farMiss = aimProbe.miss
        } else {
            nearAngle = next
            nearMiss = aimProbe.miss
            step = min(step * LINE_SEARCH_GROWTH, LINE_SEARCH_MAX)
        }
    }
    if (farAngle.isNaN()) return Float.NaN

    // False position inside the bracket (the miss is close to linear in the aim angle), bisection when it misbehaves.
    while (budget > 0) {
        budget--
        val span = farAngle - nearAngle
        val slope = farMiss - nearMiss
        var next = if (abs(slope) < MIN_LENGTH) Float.NaN else nearAngle - nearMiss * span / slope
        if (!next.isFinite() || (next - nearAngle) * (next - farAngle) > 0f) next = nearAngle + span * 0.5f
        if (!probeAim(shipX, shipY, shipUx, shipUy, next, properAccel, burn, c, maxTime, stepDt, tx, ty, tvx, tvy)) {
            return Float.NaN
        }
        if (abs(aimProbe.miss) <= ROCKET_INTERCEPT_TOLERANCE) return finishAim(next, aimProbe.hitTime, out)
        if (aimProbe.miss * nearMiss < 0f) {
            farAngle = next
            farMiss = aimProbe.miss
        } else {
            nearAngle = next
            nearMiss = aimProbe.miss
        }
        if (abs(farAngle - nearAngle) < ANGLE_EPSILON) return Float.NaN
    }
    return Float.NaN
}

/**
 * Integrates one rocket flight along the unit aim at [angle] from the ship at ([shipX], [shipY]) with proper velocity
 * ([shipUx], [shipUy]), and records in [aimProbe] the time when the rocket is as far from the ship as the target is at
 * that same time, plus the perpendicular miss there. Returns false when the rocket never gets that far within [maxTime].
 *
 * The step follows `RocketMotorSystem.beforeStep` + `RelativisticDynamics.beforeStep` step for step: the burn ticks on the
 * rocket's own clock (`dt/γ`), the motor adds [properAccel] along the aim through the same [thrustAccelWorld], `u` is
 * clamped to [GAMMA_MAX_MASSIVE], and the position advances with the post-kick `v = u/γ`, as Box2D does. It models the
 * motor only: `GravitySystem` also adds to a rocket's `rel.accel` (and the boundary field acts on the other massive
 * bodies but skips rockets, which detonate at the wall), so a flight through a gravity field is not planned exactly. The
 * computer also assumes the relativistic motor (the default); with the debug switch off the game's rocket is Newtonian
 * instead.
 */
@Suppress("LongParameterList")
private fun probeAim(
    shipX: Float,
    shipY: Float,
    shipUx: Float,
    shipUy: Float,
    angle: Float,
    properAccel: Float,
    burn: Float,
    c: Float,
    maxTime: Float,
    stepDt: Float,
    tx: Float,
    ty: Float,
    tvx: Float,
    tvy: Float,
): Boolean {
    val headingX = cos(angle)
    val headingY = sin(angle)
    var ux = shipUx
    var uy = shipUy
    var px = shipX
    var py = shipY
    var burnLeft = burn
    var t = 0f
    var previousT = 0f
    var previousX = shipX
    var previousY = shipY
    val c2 = c * c
    val uMax2 = c2 * (GAMMA_MAX_MASSIVE * GAMMA_MAX_MASSIVE - 1f)
    val invDt = 1f / stepDt
    while (t < maxTime) {
        val gamma = sqrt(1f + (ux * ux + uy * uy) / c2)
        if (burnLeft > 0f) {
            // Only the remaining burnLeft burns on the last step, like RocketMotorSystem's fraction.
            val fraction = min(1f, burnLeft * gamma * invDt)
            thrustAccelWorld(properAccel * fraction, headingX, headingY, ux / gamma, uy / gamma, gamma, probeAccel)
            ux += probeAccel.x * stepDt
            uy += probeAccel.y * stepDt
            burnLeft = max(0f, burnLeft - stepDt / gamma)
            val u2 = ux * ux + uy * uy
            if (u2 > uMax2) {
                val scale = sqrt(uMax2 / u2)
                ux *= scale
                uy *= scale
            }
        }
        val gammaAfter = sqrt(1f + (ux * ux + uy * uy) / c2)
        previousX = px
        previousY = py
        previousT = t
        px += ux / gammaAfter * stepDt
        py += uy / gammaAfter * stepDt
        t += stepDt

        val ex = tx + tvx * t
        val ey = ty + tvy * t
        val rocketRange2 = (px - shipX) * (px - shipX) + (py - shipY) * (py - shipY)
        val targetRange2 = (ex - shipX) * (ex - shipX) + (ey - shipY) * (ey - shipY)
        if (rocketRange2 < targetRange2) continue

        // First sample at or beyond the target's range: interpolate the exact crossing of the two ranges, so the hit
        // time does not depend on where the physics steps happen to land.
        val previousEx = tx + tvx * previousT
        val previousEy = ty + tvy * previousT
        val rocketRangeBefore = hypot(previousX - shipX, previousY - shipY)
        val targetRangeBefore = hypot(previousEx - shipX, previousEy - shipY)
        val rocketRange = sqrt(rocketRange2)
        val targetRange = sqrt(targetRange2)
        val slope = (rocketRange - rocketRangeBefore) - (targetRange - targetRangeBefore)
        val fraction = if (abs(slope) < MIN_LENGTH) 1f else (targetRangeBefore - rocketRangeBefore) / slope
        val clampedFraction = fraction.coerceIn(0f, 1f)
        val hitT = previousT + (t - previousT) * clampedFraction
        val rocketX = previousX + (px - previousX) * clampedFraction
        val rocketY = previousY + (py - previousY) * clampedFraction
        val hitX = tx + tvx * hitT
        val hitY = ty + tvy * hitT
        val range = hypot(hitX - shipX, hitY - shipY)
        aimProbe.hitTime = hitT
        if (range < MIN_LENGTH) {
            aimProbe.miss = 0f
            return true
        }
        // Perpendicular part of (hit - rocket), signed positive when the target is counter-clockwise of the rocket.
        aimProbe.miss = (hitX - shipX) / range * (hitY - rocketY) - (hitY - shipY) / range * (hitX - rocketX)
        return true
    }
    return false
}

/**
 * How far past the caller's targeting reach the fire computer still plans a rocket flight, as a multiple of that reach.
 * A tuning factor: the target may flee and the ship may fly away from it, so the meeting point can lie well beyond the
 * current range. 2 gives about 5.4 s for the 40 u auto-aim reach at the default c and engine (the 6 s fixed cap ST-182
 * shipped with), and the time follows c and the engine slider through [rocketStraightFlightTime].
 */
const val ROCKET_INTERCEPT_REACH_FACTOR = 2f

/**
 * Arena seconds a rocket launched from rest needs to cover [distance] in a straight line. During the burn it is
 * hyperbolic motion at proper acceleration a: `x = c²/a·(cosh(aτ/c) − 1)`, `t = c/a·sinh(aτ/c)`; after [burn] seconds of
 * proper time it coasts at the burnout speed `c·tanh(a·burn/c)` ([rocketBurnoutBeta]).
 */
fun rocketStraightFlightTime(distance: Float, c: Float, properAccel: Float, burn: Float): Float {
    val k = c * c / properAccel
    val burnRapidity = properAccel * burn / c
    val burnDistance = k * (cosh(burnRapidity) - 1f)
    if (distance <= burnDistance) {
        val ch = 1f + distance / k
        return c / properAccel * sqrt(ch * ch - 1f)
    }
    return c / properAccel * sinh(burnRapidity) + (distance - burnDistance) / (c * tanh(burnRapidity))
}

/**
 * Longest flight (arena s) the fire computer plans for a target within [reach]: the straight flight from rest over
 * [ROCKET_INTERCEPT_REACH_FACTOR]·[reach]. Derived from the runtime c and rocket acceleration, so a lower c or a weaker
 * engine plans longer flights instead of never firing.
 */
fun rocketInterceptMaxTime(reach: Float, c: Float, properAccel: Float): Float =
    rocketStraightFlightTime(reach * ROCKET_INTERCEPT_REACH_FACTOR, c, properAccel, ROCKET_BURN_SECONDS)

private val launchU = Vector2()

/**
 * Pod-rocket aim from the ship as `RocketProvider` launches it (ST-182). The single entry point for auto-aim and the
 * intercept ring, so both show the same aim. The rocket inherits the ship's proper velocity, rebuilt here from the
 * ship's arena velocity ([shipVx], [shipVy]). It spawns at `muzzle + dir·ROCKET_LENGTH`, so the aim is solved once from
 * the muzzle and then again from that spawn point. The flight is planned up to [rocketInterceptMaxTime] of [reach] (the
 * caller's targeting range).
 * Returns the hit time in arena seconds and writes the unit aim into [out], or returns NaN.
 * Allocation-free; single-threaded scratch.
 */
@Suppress("LongParameterList")
fun rocketLaunchAim(
    muzzleX: Float,
    muzzleY: Float,
    shipVx: Float,
    shipVy: Float,
    tx: Float,
    ty: Float,
    tvx: Float,
    tvy: Float,
    settings: RelativitySettings,
    reach: Float,
    out: Vector2,
): Float {
    val c = settings.c
    uFromV(shipVx, shipVy, c, settings.betaMaxMassive, launchU)
    val accel = ROCKET_ACCEL_FACTOR * settings.engineProperAccel
    val maxTime = rocketInterceptMaxTime(reach, c, accel)
    val first = rocketInterceptDir(
        muzzleX, muzzleY, launchU.x, launchU.y, tx, ty, tvx, tvy,
        accel, ROCKET_BURN_SECONDS, c, maxTime, PHYSICS_UPDATE_INTERVAL, out,
    )
    if (first.isNaN()) return Float.NaN
    return rocketInterceptDir(
        muzzleX + out.x * ROCKET_LENGTH, muzzleY + out.y * ROCKET_LENGTH, launchU.x, launchU.y, tx, ty, tvx, tvy,
        accel, ROCKET_BURN_SECONDS, c, maxTime, PHYSICS_UPDATE_INTERVAL, out,
    )
}

/** Writes the unit aim direction into [out] and returns the hit time. */
private fun finishAim(angle: Float, hitTime: Float, out: Vector2): Float {
    out.set(cos(angle), sin(angle))
    return hitTime
}
