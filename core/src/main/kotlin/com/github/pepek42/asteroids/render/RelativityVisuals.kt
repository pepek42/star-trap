package com.github.pepek42.asteroids.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.component.EntityType
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow

/** Visual length contraction strength: 1 is exact L0/γ (research E5). */
const val CONTRACTION_STRENGTH = 1f

/** A sprite never shrinks below this fraction of its rest length along v̂ (readability clamp, there is no γ cap). */
const val CONTRACTION_MIN_SCALE = 0.1f

/** Contraction factor k for [gamma]: `min(CONTRACTION_STRENGTH·(1 − 1/γ), 1 − CONTRACTION_MIN_SCALE)`. */
fun contractionK(gamma: Float): Float =
    min(CONTRACTION_STRENGTH * (1f - 1f / gamma), 1f - CONTRACTION_MIN_SCALE)

/** Writes `o' = o − k·(o·v̂)·v̂` into [out]. v̂ = (vhx, vhy) must be a unit vector. */
fun contractOffset(ox: Float, oy: Float, vhx: Float, vhy: Float, k: Float, out: Vector2): Vector2 {
    val dot = ox * vhx + oy * vhy
    return out.set(ox - k * dot * vhx, oy - k * dot * vhy)
}

/** Largest tidal stretch factor `s` along `r̂` (ST-153); the perpendicular axis shrinks by `1/s`. */
const val TIDAL_STRETCH_MAX = 1.5f

/** Visual stretch per unit of `tidalRatio` (ST-153): `s = min(TIDAL_STRETCH_MAX, 1 + TIDAL_VISUAL_K * ratio)`. FAKE scale. */
const val TIDAL_VISUAL_K = 0.25f

/** Below this stretch factor the sprite is drawn unstretched (no extra work far from holes). */
const val TIDAL_VISUAL_MIN_SCALE = 1.01f

/** Stretch factor `s` for a `tidalRatio` ([ratio] 0 gives 1). */
fun tidalStretch(ratio: Float): Float = min(TIDAL_STRETCH_MAX, 1f + TIDAL_VISUAL_K * ratio)

/**
 * Writes the offset (ox, oy) with its component along the unit vector r̂ = (rhx, rhy) scaled by [s] and the perpendicular
 * component by `1/s` into [out]: area-preserving (determinant 1). Identity at `s = 1`.
 */
fun tidalOffset(ox: Float, oy: Float, rhx: Float, rhy: Float, s: Float, out: Vector2): Vector2 {
    val along = ox * rhx + oy * rhy
    val inv = 1f / s
    return out.set(
        (ox - along * rhx) * inv + s * along * rhx,
        (oy - along * rhy) * inv + s * along * rhy,
    )
}

/** Doppler colour strength: 1 is the exact factor, smaller values exaggerate less (research E4). */
const val DOPPLER_COLOR_STRENGTH = 1f

/** Lower clamp of the effective Doppler factor (stars never shift redder than a tenth of their frequency; FAKE safety clamp, physics-lies row 26). */
const val DOPPLER_D_MIN = 0.1f

/** Upper clamp of the effective Doppler factor. */
const val DOPPLER_D_MAX = 10f

/** Searchlight brightness B = D^exponent; 1 is the mild MVP version of research E10. */
const val BRIGHTNESS_EXPONENT_MVP = 1f

/**
 * Searchlight exponent of the pilot view for extended sprites (ST-194, research E10, 2D column): the invariant is
 * `I_ν/ν³`, so surface brightness goes as D³.
 */
const val BRIGHTNESS_EXPONENT_SURFACE = 3f

/**
 * Searchlight exponent of the pilot view for point sources (stars, disk particles): a point drawn at a fixed pixel size
 * carries flux `∝ D³·dθ` and `dθ' = dθ/D`, so D². The aberration mapping already crowds the points by another factor D,
 * so the sky's total still goes as D³.
 */
const val BRIGHTNESS_EXPONENT_POINT = 2f

/** Things behind never dim below this fraction of their rest brightness (FAKE safety floor, physics-lies row 21). */
const val B_MIN = 0.1f

/** Things ahead never grow beyond this multiple of their rest brightness or size (FAKE safety clamp). */
const val B_MAX = 10f

/**
 * The searchlight exponent: [BRIGHTNESS_EXPONENT_SURFACE] / [BRIGHTNESS_EXPONENT_POINT] in pilot view, the mild
 * [BRIGHTNESS_EXPONENT_MVP] in the Outside view (its colours are the pilot's painted on a distant camera, row 52).
 */
fun brightnessExponent(pilotView: Boolean, point: Boolean): Float =
    if (!pilotView) BRIGHTNESS_EXPONENT_MVP else if (point) BRIGHTNESS_EXPONENT_POINT else BRIGHTNESS_EXPONENT_SURFACE

/**
 * Searchlight brightness `B = clamp(D^k, B_MIN, B_MAX)` of an object at Doppler factor [d]; k is [brightnessExponent]
 * for [pilotView] and [point]. The single place the exponents and clamps live.
 */
fun beamBrightness(d: Float, pilotView: Boolean, point: Boolean = false): Float =
    d.pow(brightnessExponent(pilotView, point)).coerceIn(B_MIN, B_MAX)

/** Below this |β| the starfield uses its cached colours (no Doppler pass). */
const val DOPPLER_MIN_BETA = 1e-4f

/** `D^DOPPLER_COLOR_STRENGTH` clamped to [[DOPPLER_D_MIN], [DOPPLER_D_MAX]]. */
fun effectiveDoppler(d: Float): Float =
    exp(DOPPLER_COLOR_STRENGTH * ln(d.coerceAtLeast(Float.MIN_VALUE))).coerceIn(DOPPLER_D_MIN, DOPPLER_D_MAX)

/**
 * Total frequency factor with gravitational shift (ST-195, research E15): `D_tot = D · g_e / g_o`, where [d] is the kinematic
 * Doppler factor, [ge] the emitter's clock factor (`RelativisticComponent.gravFactor`; 1 for stars, which sit at infinity)
 * and [go] the observer's. Light climbing out of a well (`ge < 1`) is redshifted, a pilot deep in a well (`go < 1`) sees
 * everything blueshifted (`1 / go`). A non-positive factor is treated as 1.
 */
fun gravDoppler(d: Float, ge: Float, go: Float): Float = if (ge > 0f && go > 0f) d * ge / go else d

/** True when `ge / go` differs from 1 enough that the colours need the Doppler pass even for sources and pilot at rest. */
fun gravShiftActive(ge: Float, go: Float): Boolean = ge > 0f && go > 0f && abs(ge / go - 1f) >= DOPPLER_MIN_BETA

/**
 * Doppler factor of a static star seen by an observer with velocity β = ([betaX], [betaY]) and Lorentz factor [gamma]:
 * `D = γ(1 + β·ŝ)`. (ŝx, ŝy) is the unit vector from the observer to the star, so flying toward a star blueshifts it
 * (D > 1). This is `dopplerFactor` with a source at rest and n̂ = -ŝ.
 */
fun starDoppler(betaX: Float, betaY: Float, gamma: Float, sx: Float, sy: Float): Float =
    gamma * (1f + betaX * sx + betaY * sy)

/** Reference temperature (K) of an untinted sprite: a Doppler factor of 1 maps to white (research E4 sprites, FAKE white point). */
const val T_REF = 6500f

/** Most sprites the additive glow pass can queue per frame; beyond it the extra sprites skip the glow. */
const val ADDITIVE_QUEUE_MAX_SPRITES = 256

/**
 * Sprite tint for Doppler factor [d]: `lut(T_REF·d) / lut(T_REF)` per channel, each clamped to [0, 1]. Written to `out[0..2]`.
 * Allocation-free.
 */
fun spriteTint(d: Float, lut: BlackbodyLut, out: FloatArray) {
    lut.rgbInto(T_REF, out)
    val r0 = out[0]
    val g0 = out[1]
    val b0 = out[2]
    lut.rgbInto(T_REF * d, out)
    out[0] = if (r0 > 0f) (out[0] / r0).coerceIn(0f, 1f) else 1f
    out[1] = if (g0 > 0f) (out[1] / g0).coerceIn(0f, 1f) else 1f
    out[2] = if (b0 > 0f) (out[2] / b0).coerceIn(0f, 1f) else 1f
}

/** Glow strength of the additive copy: 1 would make `D − 1` full alpha; 0.5 keeps the glow readable rather than blinding (ST-121). */
const val DOPPLER_GLOW_STRENGTH = 0.5f

/**
 * Alpha of the additive glow copy of a brightened sprite: `clamp((B − 1)·DOPPLER_GLOW_STRENGTH, 0, 1)` from the
 * searchlight brightness [b] (`beamBrightness`; equals D in the Outside view), 0 when it is not brightened.
 */
fun additiveGlowAlpha(b: Float): Float = ((b - 1f) * DOPPLER_GLOW_STRENGTH).coerceIn(0f, 1f)

/**
 * Whether an entity of [type] may get the additive glow copy. Projectiles fly near c and would always glow, so they keep
 * only the colour tint (ST-121).
 */
fun glowsAdditively(type: EntityType): Boolean = type != EntityType.PROJECTILE

/** Writes the packed colour `(r, g, b, a)` into the four corner colour slots of the sprite quad [v] (20 floats). */
fun setSpriteVertexColor(v: FloatArray, r: Float, g: Float, b: Float, a: Float) {
    val packed = Color.toFloatBits(r, g, b, a)
    var i = SPRITE_COLOR_OFFSET
    while (i < SPRITE_VERTEX_FLOATS) {
        v[i] = packed
        i += SPRITE_VERTEX_STRIDE
    }
}

/** Index of the packed colour inside a sprite corner `(x, y, color, u, v)`. */
const val SPRITE_COLOR_OFFSET = 2

/**
 * Tints the sprite quad [v] for the effective Doppler factor [d] (base colour [r], [g], [b], [a] is the sprite's own, usually white).
 * Dims redshifted sprites through alpha (`min(B, 1)`). Returns the alpha of the additive glow copy, 0 if none is needed.
 */
fun tintSpriteVertices(
    v: FloatArray, d: Float, r: Float, g: Float, b: Float, a: Float, lut: BlackbodyLut, rgb: FloatArray,
    pilotView: Boolean = false,
): Float {
    spriteTint(d, lut, rgb)
    val beam = beamBrightness(d, pilotView, false)
    setSpriteVertexColor(v, rgb[0] * r, rgb[1] * g, rgb[2] * b, min(beam, 1f) * a)
    return additiveGlowAlpha(beam)
}

/**
 * Preallocated list of sprite quads drawn additively after all normal sprites (the glow of blueshifted ones). Holds at most
 * [capacity] quads; [add] returns false when full, so the caller skips the glow instead of allocating.
 */
class AdditiveSpriteQueue(val capacity: Int = ADDITIVE_QUEUE_MAX_SPRITES) {
    private val vertices = FloatArray(capacity * SPRITE_VERTEX_FLOATS)
    private val textures = arrayOfNulls<Texture>(capacity)

    private val tmpColor = Color()

    var size = 0
        private set

    fun clear() {
        size = 0
        java.util.Arrays.fill(textures, null)
    }

    /** Copies the quad [v] with its colour alpha replaced by [alpha] (rgb kept). Returns false when the queue is full. */
    fun add(texture: Texture, v: FloatArray, alpha: Float): Boolean {
        if (size >= capacity) return false
        val base = size * SPRITE_VERTEX_FLOATS
        System.arraycopy(v, 0, vertices, base, SPRITE_VERTEX_FLOATS)
        Color.abgr8888ToColor(tmpColor, v[SPRITE_COLOR_OFFSET])
        val packed = Color.toFloatBits(tmpColor.r, tmpColor.g, tmpColor.b, alpha)
        var i = base + SPRITE_COLOR_OFFSET
        while (i < base + SPRITE_VERTEX_FLOATS) {
            vertices[i] = packed
            i += SPRITE_VERTEX_STRIDE
        }
        textures[size] = texture
        size++
        return true
    }

    /** Draws every queued quad into [batch], in insertion order. */
    fun draw(batch: Batch) {
        for (i in 0 until size) batch.draw(textures[i]!!, vertices, i * SPRITE_VERTEX_FLOATS, SPRITE_VERTEX_FLOATS)
    }

    fun textureAt(i: Int): Texture? = textures[i]

    /** Colour alpha of corner 0 of queued quad [i]. */
    fun alphaAt(i: Int): Float {
        Color.abgr8888ToColor(tmpColor, vertices[i * SPRITE_VERTEX_FLOATS + SPRITE_COLOR_OFFSET])
        return tmpColor.a
    }
}
