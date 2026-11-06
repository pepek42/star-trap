package com.github.pepek42.asteroids.render

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
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
const val DISK_LIGHT_INTENSITY = 5f
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

private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
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
 * (+∞ for a directional light, whose vector is `(x, y, LIGHT_HEIGHT/10)` normalised).
 */
fun lightDir(px: Float, py: Float, light: LightSource, out: Vector3): Float {
    if (light.directional) {
        out.set(light.x, light.y, LIGHT_HEIGHT / 10f).nor()
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

private val shadeDir = Vector3()

/**
 * `out(rgb) = ambient + Σ colour·intensity·falloff·wrapDiffuse(N·L)` over the first [n] indices in [idx]
 * (falloff 1 for directional lights). (nx, ny, nz) is the unit surface normal. Not thread-safe (shared scratch vector).
 */
fun shade(
    nx: Float, ny: Float, nz: Float, px: Float, py: Float,
    lights: Array<LightSource>, idx: IntArray, n: Int, ambient: Float, out: FloatArray,
) {
    out[0] = ambient
    out[1] = ambient
    out[2] = ambient
    for (i in 0 until n) {
        val light = lights[idx[i]]
        val d = lightDir(px, py, light, shadeDir)
        val f = if (light.directional) 1f else falloff(d, light.size, light.range)
        val nDotL = nx * shadeDir.x + ny * shadeDir.y + nz * shadeDir.z
        val k = light.intensity * f * wrapDiffuse(nDotL, lightWrap(light, d))
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
