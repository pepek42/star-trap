package com.github.pepek42.asteroids.render

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Lighting math (ST-095), the CPU reference for the lit-sprite shader (ST-097). All pure and allocation-free.
 * Lights live in the arena plane, raised by [LIGHT_HEIGHT] so flat sprites get a sensible N·L.
 */

/** Height (u) of point lights above the arena plane. */
const val LIGHT_HEIGHT = 2f

/** Ambient floor that keeps the unlit side readable (like ST-080's brightness floor). Used while a directional light (the star, ST-099) exists. */
const val LIGHT_AMBIENT = 0.35f

/**
 * Ambient while the light set has no directional light: stands in for general starlight until ST-099 adds the star, so
 * sprites away from a disk look as before and the disk only adds a warm glow on top. FAKE (physics-lies.md).
 */
const val LIGHT_AMBIENT_NO_STAR = 1f

/** Lights a single sprite takes into account; the shader has this many slots. */
const val MAX_LIGHTS_PER_SPRITE = 4

/** Accretion-disk light (FAKE: the disk annulus is one soft point light of size [DISK_LIGHT_SIZE] at the hole centre). */
const val DISK_LIGHT_INTENSITY = 8f
const val DISK_LIGHT_SIZE = DISK_OUTER
const val DISK_LIGHT_RANGE = 120f

/**
 * Colour temperature of the disk light. FAKE (physics-lies.md): the disk's own colour (about 0.85 of [DISK_T_PEAK]) is
 * near white, which on the blue-grey asteroid albedo barely shows (ST-146), so the light is a fixed warm orange instead.
 */
const val DISK_LIGHT_KELVIN = 3000f

/** One light. With [directional] true, ([x], [y]) is a unit direction toward a light at infinity (ST-099). */
class LightSource {
    var x = 0f
    var y = 0f
    var z = LIGHT_HEIGHT
    var r = 1f
    var g = 1f
    var b = 1f
    var intensity = 1f
    var size = 1f
    var range = 100f
    var directional = false
}

/** Fills [light]'s colour with the accretion-disk blackbody colour and sets its intensity, size and range. */
fun configureDiskLight(light: LightSource, rgbScratch: FloatArray) {
    BlackbodyLut.rgbInto(DISK_LIGHT_KELVIN, rgbScratch)
    light.r = rgbScratch[0]
    light.g = rgbScratch[1]
    light.b = rgbScratch[2]
    light.intensity = DISK_LIGHT_INTENSITY
    light.size = DISK_LIGHT_SIZE
    light.range = DISK_LIGHT_RANGE
    light.directional = false
}

/** Angular radius (rad) of the star as seen from the arena, the penumbra of a shadow from a directional light (ST-098; used by ST-099). */
const val STAR_ANGULAR_SIZE = 0.01f

/**
 * Smallest radius (u) of a body that casts a shadow (ST-098): planets and large asteroids. Small asteroids, ships and debris
 * don't cast, for performance (every cast circle costs a loop iteration per light in the fragment shader).
 */
const val OCCLUDER_MIN_RADIUS = 1.5f

/** Occluders a lit sprite takes into account; the shader has this many slots. */
const val MAX_OCCLUDERS = 6

/** Penumbra half-width floor (u): a point-like light still gets a soft edge, so a shadow never steps. */
const val MIN_PENUMBRA = 0.05f

/** GLSL `smoothstep`: 0 below [e0], 1 above [e1], cubic between. */
fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = MathUtils.clamp((x - e0) / (e1 - e0), 0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Inverse square softened at the emitter [size], faded to 0 between 0.75·[range] and [range] so lights can be culled. */
fun falloff(d: Float, size: Float, range: Float): Float {
    val q = d / size
    return (1f / (1f + q * q)) * (1f - smoothstep(0.75f * range, range, d))
}

/** Wrapped Lambert term: `wrap = 0` is plain Lambert, `wrap = 1` lights the whole sphere (an extended source seen from close up). */
fun wrapDiffuse(nDotL: Float, wrap: Float): Float = max(0f, (nDotL + wrap) / (1f + wrap))

/** Wrap for a light of [size] seen from in-plane distance [d]: `clamp(size/d, 0, 1)`; 0 for directional lights. */
fun lightWrap(light: LightSource, d: Float): Float =
    if (light.directional) 0f else if (d <= 0f) 1f else MathUtils.clamp(light.size / d, 0f, 1f)

/**
 * Writes the unit vector from point ([px], [py], 0) toward [light] into [out] and returns the in-plane distance
 * (+∞ for a directional light, whose vector is `(x, y, z)` normalised: a unit azimuth and `z = tan(elevation)`, see [StarLight]).
 */
fun lightDir(px: Float, py: Float, light: LightSource, out: Vector3): Float {
    if (light.directional) {
        out.set(light.x, light.y, light.z).nor()
        return Float.POSITIVE_INFINITY
    }
    val dx = light.x - px
    val dy = light.y - py
    out.set(dx, dy, light.z).nor()
    return sqrt(dx * dx + dy * dy)
}

/** Strength of [light] at the point, used to rank lights: intensity·falloff (just intensity for directional). */
fun lightStrengthAt(px: Float, py: Float, light: LightSource): Float {
    if (light.directional) return light.intensity
    val dx = light.x - px
    val dy = light.y - py
    return light.intensity * falloff(sqrt(dx * dx + dy * dy), light.size, light.range)
}

/**
 * Light that gets through a circular occluder (ST-098), 1 = lit, 0 = fully shadowed, at the point ([px], [py]) for a light at
 * ([lx], [ly]) of diameter-like [lightSize] (point light) or in direction ([lx], [ly]) ([directional], a light at infinity).
 * The occluder is the circle ([cx], [cy], [cr]). It only counts when it lies between the point and the light along the line.
 * The penumbra half-width at the occluder follows from similar triangles, `lightSize·t/|l − p|` (point), or
 * [STAR_ANGULAR_SIZE]`·t` (directional), at least [MIN_PENUMBRA]; the result is `smoothstep(cr − w, cr + w, dPerp)` with
 * `dPerp` the distance of the circle centre from the line. Pure; the GLSL `circleShadow` mirrors it.
 */
fun circleShadowFactor(
    px: Float, py: Float, lx: Float, ly: Float, lightSize: Float, directional: Boolean, cx: Float, cy: Float, cr: Float,
): Float {
    val dx: Float
    val dy: Float
    val dist: Float
    if (directional) {
        val len = sqrt(lx * lx + ly * ly)
        if (len <= 0f) return 1f
        dx = lx / len
        dy = ly / len
        dist = Float.POSITIVE_INFINITY
    } else {
        val ex = lx - px
        val ey = ly - py
        dist = sqrt(ex * ex + ey * ey)
        if (dist <= 0f) return 1f
        dx = ex / dist
        dy = ey / dist
    }
    val ocx = cx - px
    val ocy = cy - py
    val t = ocx * dx + ocy * dy
    if (t <= 0f || t >= dist) return 1f
    val dPerp = abs(ocx * dy - ocy * dx)
    val w = max(if (directional) STAR_ANGULAR_SIZE * t else lightSize * t / dist, MIN_PENUMBRA)
    return smoothstep(cr - w, cr + w, dPerp)
}

/** A short list of circles (x, y, r) in the arena frame: the occluders chosen for one sprite. */
class OccluderCircles(capacity: Int = MAX_OCCLUDERS) {
    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    val r = FloatArray(capacity)
    var count = 0

    fun add(cx: Float, cy: Float, cr: Float) {
        x[count] = cx
        y[count] = cy
        r[count] = cr
        count++
    }
}

/**
 * The shadow casters of one frame (ST-098): planets and asteroids of at least [OCCLUDER_MIN_RADIUS], with the data needed to
 * move them back to a sprite's retarded time (linear back-extrapolation by velocity, like the emitters of [LightSources];
 * physics-lies row 44). Grows its arrays when needed, allocation-free in steady state.
 */
class OccluderPool {
    var x = FloatArray(INITIAL_CAPACITY)
        private set
    var y = FloatArray(INITIAL_CAPACITY)
        private set
    var r = FloatArray(INITIAL_CAPACITY)
        private set
    var vx = FloatArray(INITIAL_CAPACITY)
        private set
    var vy = FloatArray(INITIAL_CAPACITY)
        private set
    var id = IntArray(INITIAL_CAPACITY)
        private set
    var count = 0

    fun clear() {
        count = 0
    }

    fun add(cx: Float, cy: Float, cr: Float, cvx: Float, cvy: Float, entityId: Int) {
        if (count == x.size) {
            val n = count * 2
            x = x.copyOf(n)
            y = y.copyOf(n)
            r = r.copyOf(n)
            vx = vx.copyOf(n)
            vy = vy.copyOf(n)
            id = id.copyOf(n)
        }
        x[count] = cx
        y[count] = cy
        r[count] = cr
        vx[count] = cvx
        vy[count] = cvy
        id[count] = entityId
        count++
    }

    private companion object {
        const val INITIAL_CAPACITY = 32
    }
}

/**
 * Squared distance from the circle centre ([cx], [cy]) to the segment from ([ax], [ay]) toward the light: the segment ends at
 * the light ([lx], [ly]) for a point light, and is the ray from the point along the unit-ish direction ([lx], [ly]) for a
 * [directional] one. Pure.
 */
fun distSqToLightSegment(
    ax: Float, ay: Float, lx: Float, ly: Float, directional: Boolean, cx: Float, cy: Float,
): Float {
    val dx: Float
    val dy: Float
    val len: Float
    if (directional) {
        val l = sqrt(lx * lx + ly * ly)
        if (l <= 0f) return (cx - ax) * (cx - ax) + (cy - ay) * (cy - ay)
        dx = lx / l
        dy = ly / l
        len = Float.POSITIVE_INFINITY
    } else {
        val ex = lx - ax
        val ey = ly - ay
        len = sqrt(ex * ex + ey * ey)
        if (len <= 0f) return (cx - ax) * (cx - ax) + (cy - ay) * (cy - ay)
        dx = ex / len
        dy = ey / len
    }
    val t = ((cx - ax) * dx + (cy - ay) * dy).coerceIn(0f, len)
    val qx = ax + dx * t - cx
    val qy = ay + dy * t - cy
    return qx * qx + qy * qy
}

/** Squared distance from ([px], [py]) to the centre of circle [i] of [out]. */
private fun occluderDistSq(out: OccluderCircles, i: Int, px: Float, py: Float): Float {
    val dx = out.x[i] - px
    val dy = out.y[i] - py
    return dx * dx + dy * dy
}

/**
 * Adds the circle to [out] keeping [out] sorted by distance to ([px], [py]), nearest first. When [out] is full the farthest
 * circle is dropped if the new one is nearer. Allocation-free.
 */
private fun insertNearestOccluder(out: OccluderCircles, px: Float, py: Float, cx: Float, cy: Float, cr: Float) {
    val d2 = (cx - px) * (cx - px) + (cy - py) * (cy - py)
    if (out.count == out.x.size) {
        if (d2 >= occluderDistSq(out, out.count - 1, px, py)) return
        out.count--
    }
    var j = out.count++
    while (j > 0 && occluderDistSq(out, j - 1, px, py) > d2) {
        out.x[j] = out.x[j - 1]
        out.y[j] = out.y[j - 1]
        out.r[j] = out.r[j - 1]
        j--
    }
    out.x[j] = cx
    out.y[j] = cy
    out.r[j] = cr
}

/**
 * Fills [out] with up to [MAX_OCCLUDERS] circles of [pool] that can shadow the sprite at ([px], [py]) of radius [spriteRadius]:
 * those whose circle comes within `cr + spriteRadius` of the segment from the sprite to one of the first [n] lights of [idx]
 * (the lights picked for the sprite), skipping the entry with [selfId] (a body never shadows itself; N·L does that).
 * The occluders are moved back by [lag] seconds along their velocity (0 in the world view). When more qualify, the nearest
 * to the sprite are kept (ST-149), sorted nearest first, whatever their pool order.
 * Returns how many were written. Allocation-free.
 */
fun selectOccluders(
    px: Float, py: Float, spriteRadius: Float, lights: Array<LightSource>, idx: IntArray, n: Int,
    pool: OccluderPool, lag: Float, selfId: Int, out: OccluderCircles,
): Int {
    out.count = 0
    for (j in 0 until pool.count) {
        if (pool.id[j] == selfId) continue
        val cx = pool.x[j] - pool.vx[j] * lag
        val cy = pool.y[j] - pool.vy[j] * lag
        val reach = pool.r[j] + spriteRadius
        var near = false
        for (i in 0 until n) {
            val l = lights[idx[i]]
            if (distSqToLightSegment(px, py, l.x, l.y, l.directional, cx, cy) <= reach * reach) {
                near = true
                break
            }
        }
        if (!near) continue
        insertNearestOccluder(out, px, py, cx, cy, pool.r[j])
    }
    return out.count
}

private val shadeDir = Vector3()

/**
 * `out(rgb) = ambient + Σ colour·intensity·falloff·wrapDiffuse(N·L)` over the first [n] indices in [idx]
 * (falloff 1 for directional lights). (nx, ny, nz) is the unit surface normal. Each light's term is multiplied by the product
 * of [circleShadowFactor] over [occluders] (ST-098; null = none). Not thread-safe (shared scratch vector).
 */
fun shade(
    nx: Float, ny: Float, nz: Float, px: Float, py: Float,
    lights: Array<LightSource>, idx: IntArray, n: Int, ambient: Float, out: FloatArray,
    occluders: OccluderCircles? = null,
) {
    out[0] = ambient
    out[1] = ambient
    out[2] = ambient
    for (i in 0 until n) {
        val light = lights[idx[i]]
        val d = lightDir(px, py, light, shadeDir)
        val f = if (light.directional) 1f else falloff(d, light.size, light.range)
        val nDotL = nx * shadeDir.x + ny * shadeDir.y + nz * shadeDir.z
        var shadow = 1f
        if (occluders != null) {
            for (j in 0 until occluders.count) {
                shadow *= circleShadowFactor(
                    px, py, light.x, light.y, light.size, light.directional, occluders.x[j], occluders.y[j], occluders.r[j],
                )
            }
        }
        val k = light.intensity * f * wrapDiffuse(nDotL, lightWrap(light, d)) * shadow
        out[0] += light.r * k
        out[1] += light.g * k
        out[2] += light.b * k
    }
}

/**
 * Writes into [outIdx] the indices of up to [max] of the first [count] lights with the largest strength at the
 * point, strongest first, skipping lights with strength 0. Returns how many were written. Insertion into
 * [outIdx] itself; no allocation (strengths are recomputed on compare, fine for a handful of slots).
 */
fun pickLights(px: Float, py: Float, lights: Array<LightSource>, count: Int, max: Int, outIdx: IntArray): Int {
    var n = 0
    for (i in 0 until count) {
        val s = lightStrengthAt(px, py, lights[i])
        if (s <= 0f) continue
        if (n == max && s <= lightStrengthAt(px, py, lights[outIdx[n - 1]])) continue
        var j = if (n < max) n++ else n - 1
        while (j > 0 && lightStrengthAt(px, py, lights[outIdx[j - 1]]) < s) {
            outIdx[j] = outIdx[j - 1]
            j--
        }
        outIdx[j] = i
    }
    return n
}
