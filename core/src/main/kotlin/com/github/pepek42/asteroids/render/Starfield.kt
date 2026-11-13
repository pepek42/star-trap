package com.github.pepek42.asteroids.render

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Disposable
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.apparentOffset
import com.github.pepek42.asteroids.VIEW_MIN_HEIGHT
import com.github.pepek42.asteroids.VIEW_MIN_WIDTH
import com.github.pepek42.asteroids.system.ZOOM_MAX
import kotlin.math.abs
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

/**
 * Side of the repeating star tile in world units. The wrapped offsets span [-tile/2, tile/2), so the tile alone covers a disc of
 * radius tile/2 (50); the neighbouring tiles are drawn where the view reaches past it ([starCopyAt], ST-201).
 */
const val STAR_TILE_SIZE = 100f

/**
 * Half-diagonal (units) of the largest minimum-aspect view: [VIEW_MIN_WIDTH] x [VIEW_MIN_HEIGHT] at [ZOOM_MAX] (about 55).
 * Wider windows extend the view; [Starfield.render] reads the real half-diagonal, this is the reference the tile must reach.
 */
val STAR_MAX_VIEW_HALF_DIAGONAL: Float =
    sqrt(VIEW_MIN_WIDTH * VIEW_MIN_WIDTH + VIEW_MIN_HEIGHT * VIEW_MIN_HEIGHT) * ZOOM_MAX / 2f

/**
 * Whether the copy of a star shifted by [shift] tiles (-1, 0 or 1) along one axis must be drawn: the star's wrapped [offset]
 * (in [-tile/2, tile/2)) puts the copy at `offset + shift * tile`, which is drawn when it lies within [reach] of the view centre.
 * The shift 0 copy is always drawn (the caller culls it). Pure.
 */
fun starCopyAt(offset: Float, shift: Int, reach: Float, tile: Float = STAR_TILE_SIZE): Boolean =
    shift == 0 || (offset * shift < 0f && abs(offset + shift * tile) <= reach)

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
 * Pilot-view aberration of a star on the celestial shell (ST-077, research E9). ([dx], [dy]) is the star's offset from the pilot in
 * the arena frame (its source direction, which ST-043's Doppler `D = γ(1 + β·ŝ)` keeps using); the star is drawn at the same
 * radius `|d|` but at the aberrated angle, the direction of `apparentOffset(d, β)` (the Lorentz boost of the light-reception
 * event), so the sky crowds toward β. Identity at β = 0. Writes the pilot-frame offset to [out]. Pure and allocation-free.
 */
fun aberrateStarOffset(dx: Float, dy: Float, betaX: Float, betaY: Float, out: Vector2): Vector2 {
    val r = sqrt(dx * dx + dy * dy)
    apparentOffset(dx, dy, betaX, betaY, out)
    val len = sqrt(out.x * out.x + out.y * out.y)
    if (r < 1e-9f || len < 1e-9f) return out.set(dx, dy)
    val k = r / len
    return out.set(out.x * k, out.y * k)
}

/**
 * Draws [StarData] as additive soft dots. Stars keep a constant size in logical (HiDPI-independent) pixels regardless of camera zoom;
 * their world anchoring is parallax-scrolled, so zoom only changes how many fit on screen.
 */
class Starfield(val data: StarData = StarData.generate(STAR_SEED)) : Disposable {
    private val dot: Texture = createDotTexture()
    private val rgb = FloatArray(3)
    private val aberrated = Vector2()

    /**
     * Draws the stars. While the visual speed is below [DOPPLER_MIN_BETA] the cached [StarData.packedColor] is used; otherwise each
     * star's colour and size follow its Doppler factor (blue and larger ahead, red and dimmer behind). With [aberration] (pilot
     * view) the stars are a shell around the pilot at ([pilotX], [pilotY]) and are drawn at their aberrated direction
     * ([aberrateStarOffset] with [betaX], [betaY]); colour and size still use the arena-frame source direction. [pilotBeam] (default: [aberration]) selects the pilot-view searchlight exponent. Stars are
     * culled by distance to the pilot (the view half-diagonal) before the aberration. Does not allocate.
     */
    fun render(
        batch: Batch, camera: Camera, zoom: Float, logicalWidthPx: Int, observer: ObserverState,
        aberration: Boolean = false, pilotX: Float = 0f, pilotY: Float = 0f, betaX: Float = 0f, betaY: Float = 0f,
        pilotBeam: Boolean = aberration,
    ) {
        val bx = observer.betaVisual.x
        val by = observer.betaVisual.y
        val gamma = observer.gammaVisual
        val doppler = observer.betaVisual.len() >= DOPPLER_MIN_BETA
        val px = observer.position.x
        val py = observer.position.y
        val halfW = camera.viewportWidth * zoom / 2f
        val halfH = camera.viewportHeight * zoom / 2f
        val reachR = sqrt(halfW * halfW + halfH * halfH)
        val worldPerPx = camera.viewportWidth * zoom / logicalWidthPx.coerceAtLeast(1)
        val cx = camera.position.x
        val cy = camera.position.y
        frame.set(batch, cx, cy, halfW, halfH, doppler, aberration, pilotX, pilotY, betaX, betaY, px, py, bx, by, gamma)
        frame.pilotBeam = pilotBeam
        val oldColor = batch.packedColor
        val oldSrc = batch.blendSrcFunc
        val oldDst = batch.blendDstFunc
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        for (i in 0 until data.count) {
            val l = data.layer[i]
            val p = STAR_LAYER_PARALLAX[l]
            val size = STAR_LAYER_PIXELS[l] * worldPerPx
            // Drawn offset from the camera in the arena frame (the source direction), plus the neighbouring tile copies that
            // the view reaches (ST-201): the tile alone covers only radius tile/2.
            val ax = wrappedOffset(data.x[i], cx, p)
            val ay = wrappedOffset(data.y[i], cy, p)
            val reachX = (if (aberration) reachR else halfW) + size
            val reachY = (if (aberration) reachR else halfH) + size
            for (sx in -1..1) {
                if (!starCopyAt(ax, sx, reachX)) continue
                for (sy in -1..1) {
                    if (!starCopyAt(ay, sy, reachY)) continue
                    drawStar(i, ax + sx * STAR_TILE_SIZE, ay + sy * STAR_TILE_SIZE, size)
                }
            }
        }
        batch.setBlendFunction(oldSrc, oldDst)
        batch.packedColor = oldColor
    }

    /** Per-frame values [drawStar] needs; a field holder instead of a long parameter list (no allocation per star). */
    private class Frame {
        lateinit var batch: Batch
        var cx = 0f; var cy = 0f; var halfW = 0f; var halfH = 0f
        var doppler = false; var aberration = false; var pilotBeam = false
        var pilotX = 0f; var pilotY = 0f; var betaX = 0f; var betaY = 0f
        var px = 0f; var py = 0f; var bx = 0f; var by = 0f; var gamma = 1f

        fun set(
            batch: Batch, cx: Float, cy: Float, halfW: Float, halfH: Float, doppler: Boolean, aberration: Boolean,
            pilotX: Float, pilotY: Float, betaX: Float, betaY: Float, px: Float, py: Float, bx: Float, by: Float, gamma: Float,
        ) {
            this.batch = batch; this.cx = cx; this.cy = cy; this.halfW = halfW; this.halfH = halfH
            this.doppler = doppler; this.aberration = aberration
            this.pilotX = pilotX; this.pilotY = pilotY; this.betaX = betaX; this.betaY = betaY
            this.px = px; this.py = py; this.bx = bx; this.by = by; this.gamma = gamma
        }
    }

    private val frame = Frame()

    /**
     * Draws star [i] at the arena-frame offset ([adx], [ady]) from the camera (a tile copy included). With the aberration the star
     * is a shell around the pilot: cull by radius (an aberrated star can land anywhere on its circle), then rotate it.
     */
    private fun drawStar(i: Int, adx: Float, ady: Float, size: Float) {
        val f = frame
        val batch = f.batch
        var dx = adx
        var dy = ady
        if (f.aberration) {
            val rx = f.cx + adx - f.pilotX
            val ry = f.cy + ady - f.pilotY
            val reach = sqrt(f.halfW * f.halfW + f.halfH * f.halfH) + size
            if (rx * rx + ry * ry > reach * reach) return
            aberrateStarOffset(rx, ry, f.betaX, f.betaY, aberrated)
            dx = f.pilotX + aberrated.x - f.cx
            dy = f.pilotY + aberrated.y - f.cy
        }
        if (dx < -f.halfW - size || dx > f.halfW + size || dy < -f.halfH - size || dy > f.halfH + size) return
        if (!f.doppler) {
            batch.packedColor = data.packedColor[i]
            batch.draw(dot, f.cx + dx - size / 2, f.cy + dy - size / 2, size, size)
            return
        }
        // Arena-frame direction from the player to the star (the source direction), not the aberrated one.
        val ox = f.cx + adx - f.px
        val oy = f.cy + ady - f.py
        val dist = sqrt(ox * ox + oy * oy)
        val d = if (dist < 1e-6f) 1f else effectiveDoppler(starDoppler(f.bx, f.by, f.gamma, ox / dist, oy / dist))
        BlackbodyLut.rgbInto(data.kelvin[i] * d, rgb)
        val b = beamBrightness(d, f.pilotBeam)
        val scale = data.brightness[i] * min(b, 1f)
        batch.packedColor = Color.toFloatBits(rgb[0] * scale, rgb[1] * scale, rgb[2] * scale, 1f)
        val s = size * sqrt(max(b, 1f))
        batch.draw(dot, f.cx + dx - s / 2, f.cy + dy - s / 2, s, s)
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
