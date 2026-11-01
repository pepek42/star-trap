package com.github.pepek42.asteroids.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import com.github.pepek42.asteroids.physics.FIELD_SKIN
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.ui.ARENA_BOUNDARY_COLOR
import ktx.log.logger
import kotlin.math.sin

const val SHIMMER_BASE_ALPHA = 0.08f

private const val VERTEX_SHADER = "shaders/field_shimmer.vert"
private const val FRAGMENT_SHADER = "shaders/field_shimmer.frag"
private const val FLOATS_PER_IMPACT = 4
private const val FALLBACK_ALPHA_BASE = 0.06f
private const val FALLBACK_ALPHA_AMPLITUDE = 0.04f
private const val FALLBACK_PULSE_SPEED = 2f

/**
 * Draws the boundary field band (honeycomb + impact ripples) as four edge strips with the shimmer shader.
 * Must be called inside an active [SpriteBatch] begin/end. Falls back to a pulsing tint when the shader
 * does not compile.
 */
class FieldShimmerRenderer(
    private val impacts: ShimmerImpacts,
    private val mapProvider: MapProvider,
) : Disposable {
    private val texture: Texture = createWhiteTexture()
    private val program: ShaderProgram

    init {
        ShaderProgram.pedantic = false
        program = ShaderProgram(Gdx.files.internal(VERTEX_SHADER), Gdx.files.internal(FRAGMENT_SHADER))
        if (!program.isCompiled) {
            logger.error { "Field shimmer shader failed to compile, using fallback:\n${program.log}" }
        }
    }

    fun render(batch: SpriteBatch) {
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
        drawStrips(batch)
        batch.shader = savedShader
        batch.setBlendFunction(savedSrc, savedDst)
        batch.packedColor = savedColor
    }

    private fun bindUniforms() {
        program.setUniformf("u_mapSize", mapProvider.mapWidth, mapProvider.mapHeight)
        program.setUniformf("u_skin", FIELD_SKIN)
        program.setUniformf("u_time", impacts.time)
        program.setUniformf("u_baseColor", ARENA_BOUNDARY_COLOR.r, ARENA_BOUNDARY_COLOR.g, ARENA_BOUNDARY_COLOR.b)
        program.setUniformf("u_baseAlpha", SHIMMER_BASE_ALPHA)
        program.setUniformf("u_c", SPEED_OF_LIGHT)
        program.setUniform4fv("u_impacts", impacts.data, 0, impacts.capacity * FLOATS_PER_IMPACT)
        program.setUniformi("u_impactCount", impacts.count)
    }

    /** Four non-overlapping strips (so additive blending does not double up in corners). */
    private fun drawStrips(batch: SpriteBatch) {
        val w = mapProvider.mapWidth
        val h = mapProvider.mapHeight
        val outside = FIELD_SKIN / 2
        val fullWidth = w + 2 * outside
        val innerHeight = h - 2 * FIELD_SKIN
        val sideWidth = outside + FIELD_SKIN
        batch.draw(texture, -outside, -outside, fullWidth, sideWidth)
        batch.draw(texture, -outside, h - FIELD_SKIN, fullWidth, sideWidth)
        batch.draw(texture, -outside, FIELD_SKIN, sideWidth, innerHeight)
        batch.draw(texture, w - FIELD_SKIN, FIELD_SKIN, sideWidth, innerHeight)
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
