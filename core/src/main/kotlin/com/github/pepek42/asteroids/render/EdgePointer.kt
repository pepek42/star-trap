package com.github.pepek42.asteroids.render

import com.badlogic.gdx.math.Vector2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min

/**
 * Where to draw an edge arrow pointing at (tx, ty) for a camera centred on (camX, camY) with half extents
 * halfW x halfH. Returns the angle (radians, 0 = +x) from the camera to the target and writes the point on the
 * screen rectangle inset by [margin] into [out]. Returns NaN (and leaves [out] untouched) when the target is on-screen.
 */
fun edgePointer(
    camX: Float, camY: Float, halfW: Float, halfH: Float,
    tx: Float, ty: Float, margin: Float, out: Vector2,
): Float {
    val dx = tx - camX
    val dy = ty - camY
    if (abs(dx) <= halfW && abs(dy) <= halfH) return Float.NaN
    val insetW = (halfW - margin).coerceAtLeast(0f)
    val insetH = (halfH - margin).coerceAtLeast(0f)
    val scaleX = if (dx == 0f) Float.POSITIVE_INFINITY else insetW / abs(dx)
    val scaleY = if (dy == 0f) Float.POSITIVE_INFINITY else insetH / abs(dy)
    val s = min(scaleX, scaleY)
    out.set(camX + dx * s, camY + dy * s)
    return atan2(dy, dx)
}
