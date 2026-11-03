package com.github.pepek42.asteroids.physics

import com.badlogic.gdx.math.Vector2
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Pilot-frame transforms (research E9). The pilot's rest frame moves at β = (betaX, betaY) in the arena frame.
 * All functions are pure and allocation-free. If |β| < 1e-6 every function is the identity.
 * β is clamped to [BETA_MAX_MASSIVE] before use, so γ stays finite.
 */

/** Below this |β| the boost is treated as zero and every transform is the identity. */
private const val BETA_IDENTITY_EPS = 1e-6f

/** γ = 1/sqrt(1 − β²) with β² clamped to BETA_MAX_MASSIVE². */
private fun gammaOf(betaLen: Float): Float {
    val b = minOf(betaLen, BETA_MAX_MASSIVE)
    return 1f / sqrt(1f - b * b)
}

/**
 * Lorentz boost of the light-reception event into the pilot frame (research E9). Objects slide toward the direction of motion.
 *
 * (rx, ry) is the arena-frame offset from the observer to the source's retarded position. With b̂ = β/|β|,
 * r∥ = r·b̂ and r⊥ = r − r∥·b̂: r'∥ = γ·(r∥ + |β|·|r|), r'⊥ = r⊥, and out = r'∥·b̂ + r'⊥. Then |r'| = D·|r| with D = γ(1 + β·r̂).
 */
fun apparentOffset(rx: Float, ry: Float, betaX: Float, betaY: Float, out: Vector2): Vector2 {
    val betaLen = sqrt(betaX * betaX + betaY * betaY)
    if (betaLen < BETA_IDENTITY_EPS) return out.set(rx, ry)
    val bLen = minOf(betaLen, BETA_MAX_MASSIVE)
    val hx = betaX / betaLen
    val hy = betaY / betaLen
    val rLen = sqrt(rx * rx + ry * ry)
    val rPar = rx * hx + ry * hy
    val perpX = rx - rPar * hx
    val perpY = ry - rPar * hy
    val gamma = gammaOf(bLen)
    val parOut = gamma * (rPar + bLen * rLen)
    return out.set(parOut * hx + perpX, parOut * hy + perpY)
}

/** Splits a unit vector at [angle] into components along and across b̂ = β/|β| and returns the rescaled angle. */
private fun rescaleHullAngle(angle: Float, betaX: Float, betaY: Float, parScale: Float): Float {
    val betaLen = sqrt(betaX * betaX + betaY * betaY)
    if (betaLen < BETA_IDENTITY_EPS) return angle
    val hx = betaX / betaLen
    val hy = betaY / betaLen
    val ux = cos(angle)
    val uy = sin(angle)
    val par = ux * hx + uy * hy
    val perpX = ux - par * hx
    val perpY = uy - par * hy
    val p = par * parScale
    return atan2(p * hy + perpY, p * hx + perpX)
}

/**
 * Hull angle in the pilot frame for a hull at [angleW] in the arena frame (radians). A rod at rest in the ship is contracted
 * in the arena frame, so tan θ_W = γ·tan θ_P, with angles measured from b̂. Multiplies the component along b̂ by γ.
 */
fun worldHullToPilot(angleW: Float, betaX: Float, betaY: Float): Float {
    val betaLen = sqrt(betaX * betaX + betaY * betaY)
    if (betaLen < BETA_IDENTITY_EPS) return angleW
    return rescaleHullAngle(angleW, betaX, betaY, gammaOf(minOf(betaLen, BETA_MAX_MASSIVE)))
}

/**
 * Hull angle in the arena frame for a hull at [angleP] in the pilot frame (radians). Inverse of [worldHullToPilot]:
 * divides the component along b̂ by γ.
 */
fun pilotHullToWorld(angleP: Float, betaX: Float, betaY: Float): Float {
    val betaLen = sqrt(betaX * betaX + betaY * betaY)
    if (betaLen < BETA_IDENTITY_EPS) return angleP
    return rescaleHullAngle(angleP, betaX, betaY, 1f / gammaOf(minOf(betaLen, BETA_MAX_MASSIVE)))
}
