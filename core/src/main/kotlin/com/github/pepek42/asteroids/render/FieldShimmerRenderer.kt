package com.github.pepek42.asteroids.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Disposable
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.ui.ARENA_BOUNDARY_COLOR
import ktx.log.logger
import kotlin.math.sin

const val SHIMMER_BASE_ALPHA = 0.08f

/** sin(2 * t) has period PI, so wrapping the phase keeps float precision in long sessions. */
private const val PULSE_PERIOD = kotlin.math.PI.toFloat()

private const val VERTEX_SHADER = "shaders/field_shimmer.vert"
private const val FRAGMENT_SHADER = "shaders/field_shimmer.frag"
private const val FLOATS_PER_IMPACT = 4
private const val FALLBACK_ALPHA_BASE = 0.06f
private const val FALLBACK_ALPHA_AMPLITUDE = 0.04f
private const val FALLBACK_PULSE_SPEED = 2f

/** Quads per band strip in the pilot view: each strip is cut along its length so the boost can bend it (ST-077). */
const val PILOT_BAND_SEGMENTS = 64

/** Strips of the field band (bottom, top, left, right). */
private const val BAND_STRIPS = 4

/** Floats per band quad in the SpriteBatch layout: 4 corners of (x, y, color, u, v). */
private const val BAND_QUAD_FLOATS = SPRITE_VERTEX_FLOATS

/** Floats [buildBandVertices] writes for [segments] quads per strip. */
fun bandVertexFloats(segments: Int): Int = BAND_STRIPS * segments * BAND_QUAD_FLOATS

private fun lerpExact(a: Float, b: Float, i: Int, n: Int): Float = if (i >= n) b else a + (b - a) * i / n

/**
 * Fills [out] (at least [bandVertexFloats] floats) with the field band as [BAND_STRIPS] non-overlapping strips of [segments] quads
 * each, in the SpriteBatch vertex layout (corner order bottom-left, top-left, top-right, bottom-right; `x, y, color, u, v`).
 * Strips: bottom and top (full width plus [outside] each side, [rippleDepth] + [outside] thick), then left and right (between them).
 * The texcoords carry the arena-frame position (the shader reads them as the world position, so the field, the ripples and the
 * band edges stay in the arena frame); the vertex position is the same point, boosted into the pilot frame when [aberration] is set
 * (`o + apparentOffset(p - o, β)` via [pilotCorner], o = ([ox], [oy]), β = ([betaX], [betaY])). The arena is static, so its
 * retarded position equals its true one and only the boost applies. Returns the floats written. Allocation-free.
 */
fun buildBandVertices(
    out: FloatArray, w: Float, h: Float, outside: Float, rippleDepth: Float, segments: Int, color: Float,
    aberration: Boolean, ox: Float, oy: Float, betaX: Float, betaY: Float, scratch: Vector2,
): Int {
    var n = 0
    val sideWidth = outside + rippleDepth
    for (strip in 0 until BAND_STRIPS) {
        val alongX = strip < 2
        // Strip rectangle: the long axis is x for bottom/top, y for left/right.
        val x0: Float
        val x1: Float
        val y0: Float
        val y1: Float
        when (strip) {
            0 -> { x0 = -outside; x1 = w + outside; y0 = -outside; y1 = -outside + sideWidth }
            1 -> { x0 = -outside; x1 = w + outside; y0 = h - rippleDepth; y1 = h - rippleDepth + sideWidth }
            2 -> { x0 = -outside; x1 = -outside + sideWidth; y0 = rippleDepth; y1 = h - rippleDepth }
            else -> { x0 = w - rippleDepth; x1 = w - rippleDepth + sideWidth; y0 = rippleDepth; y1 = h - rippleDepth }
        }
        for (i in 0 until segments) {
            val ax = if (alongX) lerpExact(x0, x1, i, segments) else x0
            val bx = if (alongX) lerpExact(x0, x1, i + 1, segments) else x1
            val ay = if (alongX) y0 else lerpExact(y0, y1, i, segments)
            val by = if (alongX) y1 else lerpExact(y0, y1, i + 1, segments)
            for (corner in 0 until 4) {
                val px = if (corner == 0 || corner == 1) ax else bx
                val py = if (corner == 0 || corner == 3) ay else by
                pilotCorner(px, py, ox, oy, betaX, betaY, aberration, scratch)
                out[n] = scratch.x
                out[n + 1] = scratch.y
                out[n + 2] = color
                out[n + 3] = px
                out[n + 4] = py
                n += SPRITE_VERTEX_STRIDE
            }
        }
    }
    return n
}

/**
 * Draws the boundary field band (honeycomb + impact ripples) as four edge strips with the shimmer shader.
 * Must be called inside an active [SpriteBatch] begin/end. Falls back to a pulsing tint when the shader
 * does not compile.
 */
class FieldShimmerRenderer(
    private val impacts: ShimmerImpacts,
    private val mapProvider: MapProvider,
    private val settings: RelativitySettings = RelativitySettings(),
) : Disposable {
    private val texture: Texture = createWhiteTexture()
    /** Strips reach this deep inside so ripples can fade out between skin and 2 * skin. */
    private val rippleDepth: Float get() = 2 * settings.fieldParams.skin
    private val program: ShaderProgram
    private val bandVertices = FloatArray(bandVertexFloats(PILOT_BAND_SEGMENTS))
    private val scratch = Vector2()

    init {
        ShaderProgram.pedantic = false
        program = ShaderProgram(Gdx.files.internal(VERTEX_SHADER), Gdx.files.internal(FRAGMENT_SHADER))
        if (!program.isCompiled) {
            logger.error { "Field shimmer shader failed to compile, using fallback:\n${program.log}" }
        }
    }

    /**
     * Draws the band. With [aberration] (pilot view) the strips are boosted into the pilot frame about the pilot at ([ox], [oy])
     * with velocity fraction ([betaX], [betaY]); the shader still works in the arena frame (see [buildBandVertices]).
     */
    fun render(batch: SpriteBatch, aberration: Boolean = false, ox: Float = 0f, oy: Float = 0f, betaX: Float = 0f, betaY: Float = 0f) {
        val savedColor = batch.packedColor
        val savedShader = batch.shader
        val savedSrc = batch.blendSrcFunc
        val savedDst = batch.blendDstFunc
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        if (program.isCompiled) {
            batch.shader = program
            batch.setColor(1f, 1f, 1f, 1f)
            bindUniforms()
        } else {
            val alpha = FALLBACK_ALPHA_BASE + FALLBACK_ALPHA_AMPLITUDE * sin(FALLBACK_PULSE_SPEED * impacts.time)
            batch.setColor(ARENA_BOUNDARY_COLOR.r, ARENA_BOUNDARY_COLOR.g, ARENA_BOUNDARY_COLOR.b, alpha)
        }
        drawStrips(batch, aberration, ox, oy, betaX, betaY)
        batch.shader = savedShader
        batch.setBlendFunction(savedSrc, savedDst)
        batch.packedColor = savedColor
    }

    private fun bindUniforms() {
        program.setUniformf("u_mapSize", mapProvider.mapWidth, mapProvider.mapHeight)
        program.setUniformf("u_skin", settings.fieldParams.skin)
        program.setUniformf("u_time", impacts.time)
        program.setUniformf("u_pulsePhase", impacts.time % PULSE_PERIOD)
        program.setUniformf("u_baseColor", ARENA_BOUNDARY_COLOR.r, ARENA_BOUNDARY_COLOR.g, ARENA_BOUNDARY_COLOR.b)
        program.setUniformf("u_baseAlpha", SHIMMER_BASE_ALPHA)
        program.setUniformf("u_c", settings.c)
        program.setUniform4fv("u_impacts", impacts.data, 0, impacts.capacity * FLOATS_PER_IMPACT)
        program.setUniformi("u_impactCount", impacts.count)
    }

    /**
     * Four non-overlapping strips (so additive blending does not double up in corners), each cut into [PILOT_BAND_SEGMENTS] quads.
     * Without the boost the output is the same band as four plain rectangles.
     */
    private fun drawStrips(batch: SpriteBatch, aberration: Boolean, ox: Float, oy: Float, betaX: Float, betaY: Float) {
        val count = buildBandVertices(
            bandVertices, mapProvider.mapWidth, mapProvider.mapHeight, settings.fieldParams.skin / 2, rippleDepth,
            PILOT_BAND_SEGMENTS, batch.packedColor, aberration, ox, oy, betaX, betaY, scratch,
        )
        batch.draw(texture, bandVertices, 0, count)
    }

    override fun dispose() {
        program.dispose()
        texture.dispose()
    }

    private fun createWhiteTexture(): Texture {
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(1f, 1f, 1f, 1f)
        pixmap.fill()
        val tex = Texture(pixmap)
        pixmap.dispose()
        return tex
    }

    companion object {
        private val logger = logger<FieldShimmerRenderer>()
    }
}
