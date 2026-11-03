package com.github.pepek42.asteroids.render

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.utils.Disposable
import com.github.pepek42.asteroids.physics.ObserverState
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

const val STAR_COUNT = 1500
const val STAR_SEED = 42L
const val STAR_MIN_KELVIN = 2500f
const val STAR_MAX_KELVIN = 12000f

/** Side of the repeating star tile in world units. Must exceed the largest visible area (96x54 at zoom 2). */
const val STAR_TILE_SIZE = 100f

/** Parallax factor per depth layer: the share of camera movement that shows up on screen. */
val STAR_LAYER_PARALLAX = floatArrayOf(0.15f, 0.4f, 0.75f)

/** On-screen star diameter in pixels per layer (far to near). */
val STAR_LAYER_PIXELS = floatArrayOf(1.5f, 2.2f, 3.2f)

/** Brightness multiplier per layer, so only the nearest, sparsest-looking layer is at full brightness (ST-100). */
val STAR_LAYER_BRIGHTNESS = floatArrayOf(0.25f, 0.4f, 1f)

private const val DOT_TEXTURE_SIZE = 8
private const val KELVIN_PER_TEMP_UNIT = 100f

/**
 * Star set stored as plain parallel arrays so later systems or shaders can read/modify it
 * (positions in [0, STAR_TILE_SIZE), temperature in kelvin) to apply Doppler shift and aberration.
 * [packedColor] is the blackbody colour scaled by brightness, as a packed float color for the batch.
 */
class StarData(val count: Int) {
    val x = FloatArray(count)
    val y = FloatArray(count)
    val layer = IntArray(count)
    val brightness = FloatArray(count)
    val kelvin = FloatArray(count)
    val packedColor = FloatArray(count)

    companion object {
        fun generate(seed: Long, count: Int = STAR_COUNT): StarData {
            val random = Random(seed)
            val data = StarData(count)
            for (i in 0 until count) {
                data.x[i] = random.nextFloat() * STAR_TILE_SIZE
                data.y[i] = random.nextFloat() * STAR_TILE_SIZE
                val l = random.nextInt(STAR_LAYER_PARALLAX.size)
                data.layer[i] = l
                // Mostly dim stars, few bright ones.
                val b = (0.35f + 0.65f * random.nextFloat().pow(2)) * STAR_LAYER_BRIGHTNESS[l]
                data.brightness[i] = b
                // Skewed towards cooler stars.
                val k = STAR_MIN_KELVIN + (STAR_MAX_KELVIN - STAR_MIN_KELVIN) * random.nextFloat().pow(1.5f)
                data.kelvin[i] = k
                val rgb = blackbodyRgb(k)
                data.packedColor[i] = Color.toFloatBits(rgb[0] * b, rgb[1] * b, rgb[2] * b, 1f)
            }
            return data
        }
    }
}

/** Approximate blackbody colour (components 0..1) for [kelvin] (clamped to 1000..40000). */
fun blackbodyRgb(kelvin: Float): FloatArray {
    val t = kelvin.coerceIn(1000f, 40000f) / KELVIN_PER_TEMP_UNIT
    val r = if (t <= 66f) 255.0 else 329.698727446 * (t - 60.0).pow(-0.1332047592)
    val g = if (t <= 66f) {
        99.4708025861 * ln(t.toDouble()) - 161.1195681661
    } else {
        288.1221695283 * (t - 60.0).pow(-0.0755148492)
    }
    val b = when {
        t >= 66f -> 255.0
        t <= 19f -> 0.0
        else -> 138.5177312231 * ln(t - 10.0) - 305.0447927307
    }
    return floatArrayOf(
        (r / 255.0).coerceIn(0.0, 1.0).toFloat(),
        (g / 255.0).coerceIn(0.0, 1.0).toFloat(),
        (b / 255.0).coerceIn(0.0, 1.0).toFloat(),
    )
}

/**
 * Screen-space offset (relative to the camera centre) of a star, wrapped into [-tile/2, tile/2).
 * [parallax] is the share of camera movement shown on screen.
 */
fun wrappedOffset(starPos: Float, cameraPos: Float, parallax: Float, tile: Float = STAR_TILE_SIZE): Float {
    val d = starPos - cameraPos * parallax + tile / 2
    return d - floor(d / tile) * tile - tile / 2
}

/**
 * Draws [StarData] as additive soft dots. Stars keep a constant size in logical (HiDPI-independent) pixels regardless of camera zoom;
 * their world anchoring is parallax-scrolled, so zoom only changes how many fit on screen.
 */
class Starfield(val data: StarData = StarData.generate(STAR_SEED)) : Disposable {
    private val dot: Texture = createDotTexture()
    private val rgb = FloatArray(3)

    /**
     * Draws the stars. While the visual speed is below [DOPPLER_MIN_BETA] the cached [StarData.packedColor] is used; otherwise each
     * star's colour and size follow its Doppler factor (blue and larger ahead, red and dimmer behind). Does not allocate.
     */
    fun render(batch: Batch, camera: Camera, zoom: Float, logicalWidthPx: Int, observer: ObserverState) {
        val bx = observer.betaVisual.x
        val by = observer.betaVisual.y
        val gamma = observer.gammaVisual
        val doppler = observer.betaVisual.len() >= DOPPLER_MIN_BETA
        val px = observer.position.x
        val py = observer.position.y
        val halfW = camera.viewportWidth * zoom / 2f
        val halfH = camera.viewportHeight * zoom / 2f
        val worldPerPx = camera.viewportWidth * zoom / logicalWidthPx.coerceAtLeast(1)
        val cx = camera.position.x
        val cy = camera.position.y
        val oldColor = batch.packedColor
        val oldSrc = batch.blendSrcFunc
        val oldDst = batch.blendDstFunc
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        for (i in 0 until data.count) {
            val l = data.layer[i]
            val p = STAR_LAYER_PARALLAX[l]
            val size = STAR_LAYER_PIXELS[l] * worldPerPx
            val dx = wrappedOffset(data.x[i], cx, p)
            val dy = wrappedOffset(data.y[i], cy, p)
            if (dx < -halfW - size || dx > halfW + size || dy < -halfH - size || dy > halfH + size) continue
            if (!doppler) {
                batch.packedColor = data.packedColor[i]
                batch.draw(dot, cx + dx - size / 2, cy + dy - size / 2, size, size)
                continue
            }
            // Direction from the player to where the star is drawn.
            val ox = cx + dx - px
            val oy = cy + dy - py
            val dist = sqrt(ox * ox + oy * oy)
            val d = if (dist < 1e-6f) 1f else effectiveDoppler(starDoppler(bx, by, gamma, ox / dist, oy / dist))
            BlackbodyLut.rgbInto(data.kelvin[i] * d, rgb)
            val b = d.pow(BRIGHTNESS_EXPONENT_MVP).coerceIn(B_MIN, B_MAX)
            val scale = data.brightness[i] * min(b, 1f)
            batch.packedColor = Color.toFloatBits(rgb[0] * scale, rgb[1] * scale, rgb[2] * scale, 1f)
            val s = size * sqrt(max(b, 1f))
            batch.draw(dot, cx + dx - s / 2, cy + dy - s / 2, s, s)
        }
        batch.setBlendFunction(oldSrc, oldDst)
        batch.packedColor = oldColor
    }

    override fun dispose() = dot.dispose()

    private fun createDotTexture(): Texture {
        val pixmap = Pixmap(DOT_TEXTURE_SIZE, DOT_TEXTURE_SIZE, Pixmap.Format.RGBA8888)
        val c = (DOT_TEXTURE_SIZE - 1) / 2f
        for (px in 0 until DOT_TEXTURE_SIZE) {
            for (py in 0 until DOT_TEXTURE_SIZE) {
                val dist = sqrt((px - c) * (px - c) + (py - c) * (py - c)) / (DOT_TEXTURE_SIZE / 2f)
                val a = (1.6f * (1f - dist)).coerceIn(0f, 1f)
                pixmap.setColor(1f, 1f, 1f, a)
                pixmap.drawPixel(px, py)
            }
        }
        val texture = Texture(pixmap)
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        pixmap.dispose()
        return texture
    }
}
