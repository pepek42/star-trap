package com.github.pepek42.asteroids.render

import kotlin.math.abs

/** Most edge arrows shown at once. */
const val MAX_TARGET_POINTERS = 6

/** Distance (u) at and beyond which an arrow is at its faintest. */
const val POINTER_FADE_DIST = 120f
const val POINTER_ALPHA_NEAR = 0.9f
const val POINTER_ALPHA_FAR = 0.3f

/** Arrow alpha for a target [dist] u away: [POINTER_ALPHA_NEAR] at 0, linearly down to [POINTER_ALPHA_FAR] at [POINTER_FADE_DIST]. */
fun pointerAlpha(dist: Float): Float {
    val t = (dist / POINTER_FADE_DIST).coerceIn(0f, 1f)
    return POINTER_ALPHA_NEAR + (POINTER_ALPHA_FAR - POINTER_ALPHA_NEAR) * t
}

/**
 * Picks the off-screen candidates nearest to the camera centre. [candidates] holds x,y pairs (first [count] pairs used).
 * Writes candidate indices into [out], nearest first, at most [maxCount] (and [out] size) of them; returns how many.
 * Does not allocate.
 */
fun selectPointerTargets(
    camX: Float, camY: Float, halfW: Float, halfH: Float,
    candidates: FloatArray, count: Int, maxCount: Int, out: IntArray,
): Int {
    val limit = minOf(maxCount, out.size)
    var n = 0
    for (i in 0 until count) {
        val dx = candidates[2 * i] - camX
        val dy = candidates[2 * i + 1] - camY
        if (abs(dx) <= halfW && abs(dy) <= halfH) continue
        val d = dx * dx + dy * dy
        // Insertion into the sorted prefix of out.
        var pos = n
        while (pos > 0 && dist2(candidates, out[pos - 1], camX, camY) > d) pos--
        if (pos >= limit) continue
        val newN = minOf(n + 1, limit)
        var j = newN - 1
        while (j > pos) {
            out[j] = out[j - 1]
            j--
        }
        out[pos] = i
        n = newN
    }
    return n
}

private fun dist2(c: FloatArray, i: Int, camX: Float, camY: Float): Float {
    val dx = c[2 * i] - camX
    val dy = c[2 * i + 1] - camY
    return dx * dx + dy * dy
}
