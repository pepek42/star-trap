package com.github.pepek42.asteroids.render

import com.badlogic.gdx.math.Vector2
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
