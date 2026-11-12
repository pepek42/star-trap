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

/** Circle segments of an intercept ring (the ShapeRenderer default draws a diamond at this size). */
const val TACTICAL_LEAD_RING_SEGMENTS = 16

/** Gap between a target's hull (bounding radius) and its target ring, in screen pixels. */
const val TARGET_RING_PAD_PX = 6f

/** Smallest target ring radius in screen pixels, so far-zoomed targets stay marked. */
const val TARGET_RING_MIN_RADIUS_PX = 10f

/** Circle segments of a target ring (the ShapeRenderer default is too coarse for small world radii). */
const val TARGET_RING_SEGMENTS = 40

/** Glow halo: rings this many screen pixels apart on both sides of the core ring. */
const val TARGET_RING_GLOW_STEP_PX = 1.5f

/** Alpha of the core ring, then of each glow layer outward/inward (additive blending, so layers add up to a soft glow). */
val TARGET_RING_LAYER_ALPHA = floatArrayOf(0.9f, 0.4f, 0.2f, 0.08f)

/** Period of the gentle glow pulse in real seconds. */
const val TARGET_RING_PULSE_PERIOD_S = 1.6f

/** Glow alpha swings by +-this fraction over a pulse; the core ring does not pulse. */
const val TARGET_RING_PULSE_DEPTH = 0.3f

/** Radius of the target ring around a target with hull [boundingRadius] (world units), [wpp] world units per pixel. */
fun targetRingRadius(boundingRadius: Float, wpp: Float): Float =
    maxOf(boundingRadius + TARGET_RING_PAD_PX * wpp, TARGET_RING_MIN_RADIUS_PX * wpp)

/** Alpha of glow layer [layer] (0 = core ring) at real time [timeS]: glow layers pulse gently, the core is steady. */
fun targetRingLayerAlpha(layer: Int, timeS: Float): Float {
    val base = TARGET_RING_LAYER_ALPHA[layer]
    if (layer == 0) return base
    val pulse = kotlin.math.sin(2f * kotlin.math.PI.toFloat() * timeS / TARGET_RING_PULSE_PERIOD_S)
    return base * (1f + TARGET_RING_PULSE_DEPTH * pulse)
}

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

/** Incoming-fire warning (ST-158): only photons arriving within this many seconds of arena time are marked. */
const val THREAT_HORIZON_LIGHT_SECONDS = 1.5f

/** A photon is a threat when its closest approach is under this many player bounding radii. */
const val THREAT_MISS_RADIUS_FACTOR = 2f

/** Half-size of the impact cross in screen pixels. */
const val THREAT_CROSS_PX = 6f

/** Alpha of the warning line and cross at the moment of impact (t* = horizon) and right now (t* = 0). */
const val THREAT_ALPHA_MIN = 0.25f
const val THREAT_ALPHA_MAX = 1f

/** Warning alpha: falls linearly from [THREAT_ALPHA_MAX] at t* = 0 to [THREAT_ALPHA_MIN] at the horizon. */
fun threatAlpha(tStar: Float): Float {
    val f = (tStar / THREAT_HORIZON_LIGHT_SECONDS).coerceIn(0f, 1f)
    return THREAT_ALPHA_MAX + (THREAT_ALPHA_MIN - THREAT_ALPHA_MAX) * f
}

/**
 * Closest approach of a photon (position [px],[py], velocity [pvx],[pvy]) to a ship ([sx],[sy], [svx],[svy]) under
 * constant velocities. Returns `t* = max(0, -(dr.dv)/|dv|^2)` (0 when the relative speed is 0) and writes the miss
 * vector `dr + dv t*` (photon minus ship) into [out].
 */
fun closestApproach(
    px: Float, py: Float, pvx: Float, pvy: Float,
    sx: Float, sy: Float, svx: Float, svy: Float, out: Vector2,
): Float {
    val drx = px - sx
    val dry = py - sy
    val dvx = pvx - svx
    val dvy = pvy - svy
    val dv2 = dvx * dvx + dvy * dvy
    val t = if (dv2 <= 0f) 0f else max(0f, -(drx * dvx + dry * dvy) / dv2)
    out.set(drx + dvx * t, dry + dvy * t)
    return t
}

/** Side length of the lock-on shot chevron marker in screen pixels (ST-177). */
const val LOCK_ON_MARKER_PX = 9f

/** Alpha of the faint line from a lock-on shot marker to its live target (ST-177). */
const val LOCK_ON_MARKER_LINE_ALPHA = 0.35f

/** Angle between the heading and each chevron wing, measured from the tip back toward the wing (rad, about 145 degrees). */
const val LOCK_ON_CHEVRON_WING_RAD = 2.5f

/**
 * Open chevron for a lock-on shot at ([x], [y]) pointing along [angleRad] (ST-177). Writes 6 floats into [out]:
 * tip (`sizeWorld` ahead of the centre), left wing, right wing. Draw it as the two lines tip-left and tip-right.
 * Wings are the centre plus `sizeWorld` at heading `angle +- LOCK_ON_CHEVRON_WING_RAD`, so they mirror about the heading.
 */
fun lockOnChevron(x: Float, y: Float, angleRad: Float, sizeWorld: Float, out: FloatArray): FloatArray {
    out[0] = x + cos(angleRad) * sizeWorld
    out[1] = y + sin(angleRad) * sizeWorld
    out[2] = x + cos(angleRad + LOCK_ON_CHEVRON_WING_RAD) * sizeWorld
    out[3] = y + sin(angleRad + LOCK_ON_CHEVRON_WING_RAD) * sizeWorld
    out[4] = x + cos(angleRad - LOCK_ON_CHEVRON_WING_RAD) * sizeWorld
    out[5] = y + sin(angleRad - LOCK_ON_CHEVRON_WING_RAD) * sizeWorld
    return out
}
