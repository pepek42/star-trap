package com.github.pepek42.asteroids.render

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.utils.Disposable
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Number of generated planet looks. */
const val PLANET_VARIANTS = 4

/** Side of a generated planet texture, in texels. */
const val PLANET_TEXTURE_SIZE = 96

private const val MIN_BANDS = 3
private const val EXTRA_BANDS = 3 // 3..5 bands
private const val BAND_WOBBLE = 0.35f
private const val NOISE_FREQUENCY = 6f
private const val SHADE_RANGE = 0.12f
private const val RIM_SHADE = 0.55f

/** The rim is one texel wide: one texel in the unit-disc coordinate (radius 1 spans half the texture). */
private const val RIM_WIDTH = 2f / PLANET_TEXTURE_SIZE

private const val RGBA_ALPHA_OPAQUE = 0xFF
private const val HASH_MULT_A = -0x61c8864680b583ebL
private const val HASH_MULT_B = -0x40a7b892e31b1a47L
private const val HASH_SHIFT_A = 30
private const val HASH_SHIFT_B = 27
private const val HASH_SHIFT_C = 31
private const val HASH_BITS = 24
private const val HASH_RANGE = (1 shl HASH_BITS).toFloat()

/** Four colours per variant (0xRRGGBB), picked by band. */
private val PLANET_PALETTES = arrayOf(
    intArrayOf(0xC2956B, 0x9A6B47, 0xE0C9A0, 0x7A5238),
    intArrayOf(0x6F8FB8, 0x4A6A94, 0xA9C2DD, 0x35507A),
    intArrayOf(0x8FB070, 0x5F8048, 0xC8D49A, 0x3E5E34),
    intArrayOf(0xB86F6F, 0x8A4A4A, 0xD9A59A, 0x5E3333),
)

/** Deterministic hash of ([lattice], [seed]) in 0..1 (splitmix64 finaliser). */
private fun hash01(lattice: Int, seed: Long): Float {
    var z = seed + lattice * HASH_MULT_A
    z = (z xor (z ushr HASH_SHIFT_A)) * HASH_MULT_A
    z = (z xor (z ushr HASH_SHIFT_B)) * HASH_MULT_B
    z = z xor (z ushr HASH_SHIFT_C)
    return (z ushr (Long.SIZE_BITS - HASH_BITS)).toInt() / HASH_RANGE
}

/** 1D value noise in 0..1 with smoothstep interpolation between hashed lattice points. */
private fun valueNoise(x: Float, seed: Long): Float {
    val i = floor(x).toInt()
    val f = x - i
    val s = f * f * (3f - 2f * f)
    val a = hash01(i, seed)
    val b = hash01(i + 1, seed)
    return a + (b - a) * s
}

/**
 * Colour of a planet texel (RGBA8888) at texture coordinates ([u], [v]) in 0..1 (v = 0 at the top). Pure.
 * Transparent outside the unit disc inscribed in the square; inside, 3 to 5 horizontal bands whose edges wobble with a 1D value
 * noise in `v`, a palette per [variant], a small per-texel shade from a second noise, and a darker rim one texel wide.
 */
fun planetTexel(variant: Int, seed: Long, u: Float, v: Float): Int {
    val dx = 2f * u - 1f
    val dy = 2f * v - 1f
    val r = hypot(dx, dy)
    if (r > 1f) return 0
    val palette = PLANET_PALETTES[Math.floorMod(variant, PLANET_VARIANTS)]
    val bands = MIN_BANDS + (hash01(0, seed) * EXTRA_BANDS).toInt().coerceAtMost(EXTRA_BANDS - 1)
    val offset = (hash01(1, seed) * palette.size).toInt()
    val w = v * bands + BAND_WOBBLE * (valueNoise(v * NOISE_FREQUENCY, seed) * 2f - 1f)
    val band = floor(w).toInt().coerceIn(0, bands - 1)
    val rgb = palette[(band + offset) % palette.size]
    var shade = 1f + SHADE_RANGE * (valueNoise(v * NOISE_FREQUENCY * 2f + 17f, seed + 1) * 2f - 1f)
    if (r > 1f - RIM_WIDTH) shade *= RIM_SHADE
    val red = (((rgb shr 16) and 0xFF) * shade).roundToInt().coerceIn(0, 0xFF)
    val green = (((rgb shr 8) and 0xFF) * shade).roundToInt().coerceIn(0, 0xFF)
    val blue = ((rgb and 0xFF) * shade).roundToInt().coerceIn(0, 0xFF)
    return (red shl 24) or (green shl 16) or (blue shl 8) or RGBA_ALPHA_OPAQUE
}

/** Generates the [PLANET_VARIANTS] planet textures once from [random] (ST-093); needs a GL context. Nearest filtering, like the atlas. */
class PlanetTextures(random: RandomXS128) : Disposable {
    private val textures = Array(PLANET_VARIANTS) { variant -> build(variant, random.nextLong()) }

    private fun build(variant: Int, seed: Long): Texture {
        val pixmap = Pixmap(PLANET_TEXTURE_SIZE, PLANET_TEXTURE_SIZE, Pixmap.Format.RGBA8888)
        pixmap.blending = Pixmap.Blending.None
        for (y in 0 until PLANET_TEXTURE_SIZE) {
            for (x in 0 until PLANET_TEXTURE_SIZE) {
                val u = (x + 0.5f) / PLANET_TEXTURE_SIZE
                val v = (y + 0.5f) / PLANET_TEXTURE_SIZE
                pixmap.drawPixel(x, y, planetTexel(variant, seed, u, v))
            }
        }
        val texture = Texture(pixmap)
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
        pixmap.dispose()
        return texture
    }

    fun createSprite(variant: Int): Sprite = Sprite(textures[Math.floorMod(variant, PLANET_VARIANTS)])

    override fun dispose() {
        textures.forEach { it.dispose() }
    }
}
