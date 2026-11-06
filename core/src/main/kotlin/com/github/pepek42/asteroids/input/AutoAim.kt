package com.github.pepek42.asteroids.input

import com.github.pepek42.asteroids.math.shortestAngleDiffRad
import kotlin.math.abs
import kotlin.math.atan2

/** Half-angle of the forward cone (degrees) in which the auto-aim guns pick a target: a 90° wide cone (owner 2026-11-03). */
const val AUTO_AIM_CONE_HALF_DEG = 45f

/** [AUTO_AIM_CONE_HALF_DEG] in radians. */
val AUTO_AIM_CONE_HALF_RAD: Float = Math.toRadians(AUTO_AIM_CONE_HALF_DEG.toDouble()).toFloat()

/**
 * Auto-aim reach (world units). A tuning value, not derived from physics: it assumes the 200 x 200 map and the 48 x 27
 * viewport at default zoom, where the half-diagonal is about 27.5 u. 40 u reaches about 1.5 half-diagonals, so targets
 * just off screen are engaged, but the guns do not empty themselves into the far side of the arena (a fifth of the map).
 * At default c it is about two light-seconds of retarded flight, so leading matters.
 */
const val AUTO_AIM_RANGE = 40f

/**
 * Index of the nearest candidate within [range] of ([sx], [sy]) whose bearing is within [coneHalfRad] of [facingRad], or -1.
 * Candidates are entries [from] until [n] of [xs] and [ys]. Ties go to the lower index. Pure and allocation-free.
 */
fun pickTargetIndex(
    sx: Float, sy: Float, facingRad: Float, coneHalfRad: Float, range: Float, xs: FloatArray, ys: FloatArray, n: Int,
    from: Int = 0,
): Int {
    var best = -1
    var bestDist2 = range * range
    for (i in from until n) {
        val dx = xs[i] - sx
        val dy = ys[i] - sy
        val d2 = dx * dx + dy * dy
        if (d2 > bestDist2) continue
        if (best >= 0 && d2 >= bestDist2) continue
        val bearing = atan2(dy, dx)
        if (abs(shortestAngleDiffRad(facingRad, bearing)) > coneHalfRad) continue
        best = i
        bestDist2 = d2
    }
    return best
}
