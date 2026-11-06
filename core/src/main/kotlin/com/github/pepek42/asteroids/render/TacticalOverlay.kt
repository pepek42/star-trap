package com.github.pepek42.asteroids.render

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.PolygonShape
import com.badlogic.gdx.physics.box2d.Shape
import com.github.pepek42.asteroids.physics.PHOTON_DAMAGE_D_MAX
import com.github.pepek42.asteroids.physics.PHOTON_DAMAGE_D_MIN
import com.github.pepek42.asteroids.physics.photonEnergyFactor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Vertices of the polyline that approximates a circle. */
const val TACTICAL_CIRCLE_SEGMENTS = 20

/** Smallest outline radius in screen pixels, so tiny shapes stay visible when zoomed out. */
const val TACTICAL_MIN_RADIUS_PX = 4f

/** Capacity needed by [outlineVertices]: at least Box2D `maxPolygonVertices` (8) and [TACTICAL_CIRCLE_SEGMENTS]. */
const val TACTICAL_OUTLINE_MAX_VERTS = 20

/** World units of velocity arrow at |v| = c. */
const val TACTICAL_ARROW_LENGTH_AT_C = 8f

/** Below this fraction of c no velocity arrow is drawn. */
const val TACTICAL_MIN_ARROW_BETA = 0.02f

const val TACTICAL_TIER_NONE = 0
const val TACTICAL_TIER_WIREFRAME = 1
const val TACTICAL_TIER_INTERCEPT = 2

/** Most intercept markers (tier 2) drawn at once. */
const val TACTICAL_LEAD_MAX = 6

/** Reach of the intercept markers in seconds of light travel (arena time): about 2 s. */
const val TACTICAL_LEAD_RANGE_SECONDS = 2f

/** Radius of an intercept marker ring in screen pixels. */
const val TACTICAL_LEAD_RING_PX = 6f

/** Radius of the auto-aim lock-on reticle circle in screen pixels (ST-142). */
const val LOCK_RETICLE_RADIUS_PX = 14f

/** Gap between the reticle circle and the inner end of each tick, in screen pixels. */
const val LOCK_RETICLE_TICK_GAP_PX = 4f

/** Length of each reticle tick in screen pixels. */
const val LOCK_RETICLE_TICK_LEN_PX = 8f

/** Intercept marker range in world units: [TACTICAL_LEAD_RANGE_SECONDS] of light travel. Pass the runtime `settings.c`. */
fun tacticalLeadRange(c: Float): Float = TACTICAL_LEAD_RANGE_SECONDS * c

/**
 * Inserts ([key], [id]) into the ascending, size-[n] prefix of [keys]/[ids] (capacity `keys.size`, also bounded by
 * `ids.size`), dropping the largest key when full. Returns the new size. Does not allocate.
 */
fun insertNearest(keys: FloatArray, ids: IntArray, n: Int, key: Float, id: Int): Int {
    val cap = minOf(keys.size, ids.size)
    var pos = n
    while (pos > 0 && keys[pos - 1] > key) pos--
    if (pos >= cap) return n
    val newN = minOf(n + 1, cap)
    var j = newN - 1
    while (j > pos) {
        keys[j] = keys[j - 1]
        ids[j] = ids[j - 1]
        j--
    }
    keys[pos] = key
    ids[pos] = id
    return newN
}

enum class TacticalOverlayMode(val flag: String) {
    OFF("off"),
    PILOT("pilot"),
    ALWAYS("always");

    companion object {
        /** Case-insensitive lookup by flag value; null when unknown. */
        fun fromFlag(s: String?): TacticalOverlayMode? = entries.firstOrNull { it.flag.equals(s, ignoreCase = true) }
    }
}

private val tmp = Vector2()

/**
 * World units per screen pixel of the main viewport. Callers pass `camera.viewportWidth`, `camera.zoom` and
 * `viewport.screenWidth`. Returns 0 when the screen width is not positive.
 */
fun worldPerPixel(viewportWorldWidth: Float, zoom: Float, screenWidthPx: Int): Float =
    if (screenWidthPx <= 0) 0f else viewportWorldWidth * zoom / screenWidthPx

/**
 * Writes x,y pairs of a closed polyline (last vertex not repeated) for [shape] on a body at ([cx], [cy]) rotated
 * by [angleRad]; returns the vertex count. Edge and chain shapes return 0. Never allocates.
 */
fun outlineVertices(shape: Shape, cx: Float, cy: Float, angleRad: Float, minRadius: Float, out: FloatArray): Int {
    val cosA = cos(angleRad)
    val sinA = sin(angleRad)
    when (shape) {
        is CircleShape -> {
            val p = shape.position
            val ccx = cx + p.x * cosA - p.y * sinA
            val ccy = cy + p.x * sinA + p.y * cosA
            return writeCircle(ccx, ccy, angleRad, max(shape.radius, minRadius), out)
        }
        is PolygonShape -> {
            val n = shape.vertexCount
            var maxDist2 = 0f
            for (i in 0 until n) {
                shape.getVertex(i, tmp)
                maxDist2 = max(maxDist2, tmp.x * tmp.x + tmp.y * tmp.y)
                out[2 * i] = cx + tmp.x * cosA - tmp.y * sinA
                out[2 * i + 1] = cy + tmp.x * sinA + tmp.y * cosA
            }
            if (sqrt(maxDist2) < minRadius) return writeCircle(cx, cy, angleRad, minRadius, out)
            return n
        }
        else -> return 0
    }
}

private fun writeCircle(x: Float, y: Float, angleRad: Float, radius: Float, out: FloatArray): Int {
    for (i in 0 until TACTICAL_CIRCLE_SEGMENTS) {
        val a = angleRad + 2f * PI.toFloat() * i / TACTICAL_CIRCLE_SEGMENTS
        out[2 * i] = x + radius * cos(a)
        out[2 * i + 1] = y + radius * sin(a)
    }
    return TACTICAL_CIRCLE_SEGMENTS
}

/**
 * World units of arrow per unit of speed. c-relative like `velocityVectorScale`: pass the runtime
 * `settings.c`, not `SPEED_OF_LIGHT`.
 */
fun tacticalArrowScale(c: Float): Float = TACTICAL_ARROW_LENGTH_AT_C / c

/**
 * Whether the tactical overlay is drawn. Never without the computer ([tier] below wireframe); otherwise the mode's
 * choice flipped by the in-game key ([inverted]).
 */
fun tacticalOverlayVisible(mode: TacticalOverlayMode, pilotView: Boolean, tier: Int, inverted: Boolean): Boolean {
    if (tier < TACTICAL_TIER_WIREFRAME) return false
    val byMode = mode == TacticalOverlayMode.ALWAYS || (mode == TacticalOverlayMode.PILOT && pilotView)
    return byMode xor inverted
}

/** Arrow head length in screen pixels. */
const val TACTICAL_ARROW_HEAD_PX = 8f

/** Text colour of the laser damage label at or above x1 (ST-133). */
const val LASER_DAMAGE_LABEL_MIN_FACTOR = 1f

/**
 * Laser damage multiplier the ship computer shows for the aimed target (ST-133): [photonEnergyFactor] of a shot from a
 * shooter with velocity over c ([bsx], [bsy]) at a target with ([btx], [bty]) along ([nx], [ny]), clamped like
 * `applyProjectileHit` to [PHOTON_DAMAGE_D_MIN]..[PHOTON_DAMAGE_D_MAX]. Null when the setting or relativity is off.
 */
fun displayedPhotonFactor(
    enabled: Boolean, relativityOn: Boolean,
    bsx: Float, bsy: Float, btx: Float, bty: Float, nx: Float, ny: Float,
): Float? {
    if (!enabled || !relativityOn) return null
    return photonEnergyFactor(bsx, bsy, btx, bty, nx, ny).coerceIn(PHOTON_DAMAGE_D_MIN, PHOTON_DAMAGE_D_MAX)
}

/** Replaces the contents of [out] with `×1.8` (one decimal, no allocation, no `String.format`). */
fun formatDamageFactor(factor: Float, out: StringBuilder) {
    out.setLength(0)
    val tenths = (factor * 10f + 0.5f).toInt()
    out.append('\u00d7').append(tenths / 10).append('.').append(tenths % 10)
}
