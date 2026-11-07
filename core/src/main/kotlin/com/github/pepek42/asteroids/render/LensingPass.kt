package com.github.pepek42.asteroids.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.ScreenUtils
import ktx.log.logger
import kotlin.math.abs

/** Einstein radius of a hole as a multiple of its `r_s`: `R_E = 4 r_s` (research E18, thin lens). */
const val LENS_EINSTEIN_RADIUS_RS = 4f

/** Half-size of the lens quad around a hole, in units of `R_E`. */
const val LENS_QUAD_HALF = 3f

/** Fraction of the quad half-size where the deflection starts to fade to zero (FAKE taper, physics-lies row 19). */
const val LENS_FADE_START = 0.7f

/** Grey level the screen is cleared to (`PlayScreen`); the backdrop buffer starts from the same colour. */
const val SCREEN_CLEAR_GRAY = 0.1f

private const val QUAD_VERTICES = 4

/**
 * Where the backdrop point seen at offset ([rhoX], [rhoY]) from a hole's centre really lies, in the same units and relative to the
 * same centre: a thin lens `src = rho (1 - w R_E^2 / |rho|^2)` (research E18), which is exact for the top-down camera. [rE] is the
 * Einstein radius, [quadHalf] the half-size of the lens quad in world units (`LENS_QUAD_HALF * rE`). The weight
 * `w = 1 - smoothstep(LENS_FADE_START * quadHalf, quadHalf, max(|rho_x|, |rho_y|))` takes the deflection to zero at the quad edge,
 * so the lensed quad joins the unlensed backdrop without a seam (FAKE taper, outside the ring).
 * Returns false inside the shadow (`|rho| < BH_SHADOW_PER_RS * r_s`, `r_s = rE / LENS_EINSTEIN_RADIUS_RS`), which is drawn black;
 * [out] is then untouched. `lensing.frag` mirrors this.
 */
fun lensSourceOffset(rhoX: Float, rhoY: Float, rE: Float, quadHalf: Float, out: Vector2): Boolean {
    val r2 = rhoX * rhoX + rhoY * rhoY
    val shadow = BH_SHADOW_PER_RS * rE / LENS_EINSTEIN_RADIUS_RS
    if (r2 < shadow * shadow) return false
    val edge = maxOf(abs(rhoX), abs(rhoY))
    val w = 1f - smoothstep(LENS_FADE_START * quadHalf, quadHalf, edge)
    val k = 1f - w * rE * rE / r2
    out.set(rhoX * k, rhoY * k)
    return true
}

/**
 * Gravitational lensing of the backdrop (ST-088, research E18). The nebula and starfield are drawn into an off-screen
 * [FrameBuffer] the size of the back buffer ([beginCapture]/[endCapture]), [draw] copies it to the screen and then draws one quad
 * of half-size `LENS_QUAD_HALF * R_E` per black hole with `lensing.frag`, which samples the buffer at the lensed position. Only the
 * backdrop is lensed: sprites sit in the lens plane. The buffer is recreated when the back buffer changes size and freed in
 * [dispose]. When the shader or the buffer cannot be created [ready] is false and the caller draws the backdrop directly.
 */
class LensingPass : Disposable {
    private val program: ShaderProgram
    private val quad: Mesh = Mesh(
        true, QUAD_VERTICES, 0, VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_corner"),
    ).also { it.setVertices(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)) }
    private var buffer: FrameBuffer? = null
    private var failed = false
    private val screenProjection = Matrix4()

    init {
        ShaderProgram.pedantic = false
        program = ShaderProgram(Gdx.files.internal(VERTEX_SHADER), Gdx.files.internal(FRAGMENT_SHADER))
        if (!program.isCompiled) {
            logger.error { "Lensing shader failed to compile, the backdrop is not lensed:\n${program.log}" }
        }
    }

    /** False when the shader did not compile or the frame buffer could not be created; the backdrop is then drawn directly. */
    val ready: Boolean get() = program.isCompiled && !failed

    /**
     * Binds the (re)created back-buffer sized frame buffer and clears it; draw the backdrop, then call [endCapture]. Returns false
     * (nothing bound) when the buffer cannot be made, so the caller draws the backdrop directly.
     */
    fun beginCapture(): Boolean {
        val w = Gdx.graphics.backBufferWidth
        val h = Gdx.graphics.backBufferHeight
        var fb = buffer
        if (fb == null || fb.width != w || fb.height != h) {
            fb?.dispose()
            buffer = null
            fb = try {
                FrameBuffer(Pixmap.Format.RGBA8888, w, h, false).also {
                    it.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                    it.colorBufferTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
                }
            } catch (e: RuntimeException) {
                logger.error(e) { "Lensing frame buffer failed, the backdrop is not lensed" }
                failed = true
                return false
            }
            buffer = fb
        }
        fb.begin()
        ScreenUtils.clear(SCREEN_CLEAR_GRAY, SCREEN_CLEAR_GRAY, SCREEN_CLEAR_GRAY, 1f)
        return true
    }

    fun endCapture() {
        buffer?.end()
    }

    /**
     * Copies the captured backdrop to the screen, then draws the lens quad of every hole in [centres] (`count` pairs x, y, plus the
     * matching `rs` in [radii]) over it. [camera] maps the world to the screen; [batch] must not be between begin and end.
     */
    fun draw(camera: Camera, batch: Batch, centres: FloatArray, radii: FloatArray, count: Int) {
        val fb = buffer ?: return
        val tex = fb.colorBufferTexture
        Gdx.gl.glDisable(GL20.GL_BLEND)
        screenProjection.idt()
        batch.projectionMatrix = screenProjection
        val oldColor = batch.packedColor
        batch.setColor(1f, 1f, 1f, 1f)
        batch.begin()
        batch.disableBlending()
        // The buffer's texture row 0 is the bottom of the screen, so the unflipped (u, v)..(u2, v2) range is upright.
        batch.draw(tex, -1f, -1f, 2f, 2f, 0f, 0f, 1f, 1f)
        batch.end()
        batch.enableBlending()
        batch.packedColor = oldColor
        if (count > 0) drawLenses(camera, tex, centres, radii, count)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }

    private fun drawLenses(camera: Camera, tex: Texture, centres: FloatArray, radii: FloatArray, count: Int) {
        val m = camera.combined.`val`
        program.bind()
        program.setUniformMatrix("u_projTrans", camera.combined)
        tex.bind(0)
        program.setUniformi("u_tex", 0)
        // texture coordinate = (clip * 0.5 + 0.5); the orthographic matrix is affine, so it is linear in the world offset
        program.setUniformf("u_uvAxisX", m[Matrix4.M00] * HALF, m[Matrix4.M10] * HALF)
        program.setUniformf("u_uvAxisY", m[Matrix4.M01] * HALF, m[Matrix4.M11] * HALF)
        for (i in 0 until count) {
            val cx = centres[2 * i]
            val cy = centres[2 * i + 1]
            val rs = radii[i]
            val rE = LENS_EINSTEIN_RADIUS_RS * rs
            val clipX = m[Matrix4.M00] * cx + m[Matrix4.M01] * cy + m[Matrix4.M03]
            val clipY = m[Matrix4.M10] * cx + m[Matrix4.M11] * cy + m[Matrix4.M13]
            program.setUniformf("u_center", cx, cy)
            program.setUniformf("u_uvCenter", clipX * HALF + HALF, clipY * HALF + HALF)
            program.setUniformf("u_rE", rE)
            program.setUniformf("u_shadow", BH_SHADOW_PER_RS * rs)
            val quadHalf = LENS_QUAD_HALF * rE
            program.setUniformf("u_quadHalf", quadHalf)
            program.setUniformf("u_fadeInner", LENS_FADE_START * quadHalf)
            program.setUniformf("u_fadeOuter", quadHalf)
            quad.render(program, GL20.GL_TRIANGLE_STRIP)
        }
    }

    override fun dispose() {
        buffer?.dispose()
        buffer = null
        program.dispose()
        quad.dispose()
    }

    private companion object {
        const val VERTEX_SHADER = "shaders/lensing.vert"
        const val FRAGMENT_SHADER = "shaders/lensing.frag"
        const val HALF = 0.5f
        val logger = logger<LensingPass>()
    }
}
