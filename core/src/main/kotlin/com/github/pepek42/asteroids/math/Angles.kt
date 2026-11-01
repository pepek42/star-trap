package com.github.pepek42.asteroids.math

import com.badlogic.gdx.math.MathUtils
import kotlin.math.floor

/**
 * Wraps an angle in radians into the range [-PI, PI).
 */
fun wrapAngleRad(angle: Float): Float =
    angle - MathUtils.PI2 * floor((angle + MathUtils.PI) / MathUtils.PI2)

/**
 * Returns the signed shortest rotation in radians from [from] to [to], in the range [-PI, PI).
 */
fun shortestAngleDiffRad(from: Float, to: Float): Float = wrapAngleRad(to - from)
