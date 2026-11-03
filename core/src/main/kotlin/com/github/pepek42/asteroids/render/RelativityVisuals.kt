package com.github.pepek42.asteroids.render

import com.badlogic.gdx.math.Vector2
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/** Visual length contraction strength: 1 is exact L0/γ (research E5). */
const val CONTRACTION_STRENGTH = 1f

/** A sprite never shrinks below this fraction of its rest length along v̂ (readability clamp, there is no γ cap). */
const val CONTRACTION_MIN_SCALE = 0.25f

/** Contraction factor k for [gamma]: `min(CONTRACTION_STRENGTH·(1 − 1/γ), 1 − CONTRACTION_MIN_SCALE)`. */
fun contractionK(gamma: Float): Float =
    min(CONTRACTION_STRENGTH * (1f - 1f / gamma), 1f - CONTRACTION_MIN_SCALE)

/** Writes `o' = o − k·(o·v̂)·v̂` into [out]. v̂ = (vhx, vhy) must be a unit vector. */
fun contractOffset(ox: Float, oy: Float, vhx: Float, vhy: Float, k: Float, out: Vector2): Vector2 {
    val dot = ox * vhx + oy * vhy
    return out.set(ox - k * dot * vhx, oy - k * dot * vhy)
}

/** Doppler colour strength: 1 is the exact factor, smaller values exaggerate less (research E4). */
const val DOPPLER_COLOR_STRENGTH = 1f

/** Lower clamp of the effective Doppler factor (stars never shift redder than a quarter of their frequency). */
const val DOPPLER_D_MIN = 0.25f

/** Upper clamp of the effective Doppler factor. */
const val DOPPLER_D_MAX = 4f

/** Searchlight brightness B = D^exponent; 1 is the mild MVP version of research E10. */
const val BRIGHTNESS_EXPONENT_MVP = 1f

/** Stars behind never dim below this fraction of their rest brightness. */
const val B_MIN = 0.25f

/** Stars ahead never grow beyond this multiple of their rest size. */
const val B_MAX = 3f

/** Below this |β| the starfield uses its cached colours (no Doppler pass). */
const val DOPPLER_MIN_BETA = 1e-4f

/** `D^DOPPLER_COLOR_STRENGTH` clamped to [[DOPPLER_D_MIN], [DOPPLER_D_MAX]]. */
fun effectiveDoppler(d: Float): Float =
    exp(DOPPLER_COLOR_STRENGTH * ln(d.coerceAtLeast(Float.MIN_VALUE))).coerceIn(DOPPLER_D_MIN, DOPPLER_D_MAX)

/**
 * Doppler factor of a static star seen by an observer with velocity β = ([betaX], [betaY]) and Lorentz factor [gamma]:
 * `D = γ(1 + β·ŝ)`. (ŝx, ŝy) is the unit vector from the observer to the star, so flying toward a star blueshifts it
 * (D > 1). This is `dopplerFactor` with a source at rest and n̂ = -ŝ.
 */
fun starDoppler(betaX: Float, betaY: Float, gamma: Float, sx: Float, sy: Float): Float =
    gamma * (1f + betaX * sx + betaY * sy)
