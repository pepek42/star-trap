package com.github.pepek42.asteroids.render

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.IntMap
import com.badlogic.gdx.utils.ObjectMap
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.GravitySourceKind
import com.github.pepek42.asteroids.component.baseInfoMapper
import com.github.pepek42.asteroids.component.gravitySourceCmpOptional
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import ktx.log.logger

private const val VERTEX_SHADER = "shaders/lit_sprite.vert"
private const val FRAGMENT_SHADER = "shaders/lit_sprite.frag"

/** Suffix of the normal-map partner of an atlas region (ST-096): `asteroid_big` has `asteroid_big_n`. */
private const val NORMAL_SUFFIX = "_n"

/** [v] as a GLSL float literal (ES 1.00 has no int/float mixing, and some runtimes print 2f as "2"). */
private fun glslFloat(v: Float): String = v.toString().let { if (it.contains('.') || it.contains('E') || it.contains('e')) it else "$it.0" }

/** Bits of `regionY` in the integer key of a region (atlas pages are far smaller than 65536 px). */
private const val REGION_KEY_SHIFT = 16

/** Texture unit of the normal map (the colour texture is on unit 0, where the [SpriteBatch] binds it). */
private const val NORMAL_UNIT = 1

/** Floor of a blended normal's length before normalising (the GLSL uses the same). */
private const val MIN_NORMAL_LENGTH = 0.0001f

/** Normal source of a lit sprite: flat (0, 0, 1). */
const val NORMAL_MODE_FLAT = 0

/** Normal source of a lit sprite: the sprite's `_n` normal map (tangent space, y up). */
const val NORMAL_MODE_MAP = 1

/** Normal source of a lit sprite: an analytic sphere `(q.x, q.y, sqrt(1 - |q|²))` (planets). */
const val NORMAL_MODE_SPHERE = 2

/**
 * Share of the analytic sphere normal blended into the normal map of asteroids and debris (ST-148). The map is a 3 px bevel
 * around a flat +z interior, and the disk light is nearly horizontal ([LIGHT_HEIGHT]), so on the map alone N·L is about
 * the same all over an asteroid and only its rim shades. Mixing in a sphere normal gives the whole body a lit and a dark
 * side. FAKE (physics-lies.md): rocks are lumpy, not spheres; ships keep the plain map (0).
 */
const val ASTEROID_DOME_BLEND = 0.7f

/**
 * CPU reference of one fragment of the `lit_sprite` shader (ST-097); the GLSL mirrors it line by line. ([qx], [qy]) is the
 * texel in the sprite's local frame, −1..1 with y up; [halfW]/[halfH] the sprite half size (u); ([cx], [cy]) the sprite
 * centre and [rotRad] its angle in the arena frame. (nTexX, nTexY, nTexZ) is the unit tangent normal read from the map
 * (used in [NORMAL_MODE_MAP] only). Writes the light factor `ambient + Σ light` (rgb) over the first [n] lights of [idx]
 * into [out]; the caller multiplies the albedo. The normal is rotated into the arena frame and the lights are evaluated
 * at the texel's arena position, so the lit side follows the sprite's angle. [domeBlend] (0..1) mixes the analytic sphere
 * normal into the map normal in [NORMAL_MODE_MAP] (see [ASTEROID_DOME_BLEND]). Allocation-free.
 */
fun shadeSpriteTexel(
    qx: Float, qy: Float, halfW: Float, halfH: Float, cx: Float, cy: Float, rotRad: Float, mode: Int,
    nTexX: Float, nTexY: Float, nTexZ: Float,
    lights: Array<LightSource>, idx: IntArray, n: Int, ambient: Float, out: FloatArray, domeBlend: Float = 0f,
) {
    val cosR = cos(rotRad)
    val sinR = sin(rotRad)
    val nx: Float
    val ny: Float
    val nz: Float
    when (mode) {
        NORMAL_MODE_MAP -> {
            val sz = sqrt(max(0f, 1f - (qx * qx + qy * qy)))
            val mx = nTexX + (qx - nTexX) * domeBlend
            val my = nTexY + (qy - nTexY) * domeBlend
            val mz = nTexZ + (sz - nTexZ) * domeBlend
            val len = max(sqrt(mx * mx + my * my + mz * mz), MIN_NORMAL_LENGTH)
            nx = mx / len
            ny = my / len
            nz = mz / len
        }
        NORMAL_MODE_SPHERE -> {
            nx = qx
            ny = qy
            nz = sqrt(max(0f, 1f - (qx * qx + qy * qy)))
        }
        else -> {
            nx = 0f
            ny = 0f
            nz = 1f
        }
    }
    val lx = qx * halfW
    val ly = qy * halfH
    shade(
        cosR * nx - sinR * ny, sinR * nx + cosR * ny, nz,
        cx + cosR * lx - sinR * ly, cy + sinR * lx + cosR * ly,
        lights, idx, n, ambient, out,
    )
}

/**
 * Draws sprites through the `lit_sprite` shader (ST-097): colour times `ambient + Σ lights` (see [shadeSpriteTexel]), on top
 * of the usual vertex colour (Doppler tint, searchlight). Call [prepare] before drawing a sprite's quad, `batch.flush()` after
 * it, and [release] before drawing anything unlit or ending the pass. One draw call per lit sprite is accepted because entity
 * counts are low (owner 2026-11-02); budget: about 150 lit sprites per frame.
 *
 * Which sprites are lit: ships, asteroids and debris whose atlas region has a `<name>_n` partner (normal mode 1, the map is
 * read through the two regions' UV mapping, bound on unit 1 whatever page it is on), and planets (mode 2, analytic sphere).
 * Lasers, thrusters and explosions have no partner and keep the default shader.
 *
 * **Pilot view: exact vs FAKE.**
 * - Exact (given the pilot view itself): which side of an object is lit is a set of events, so it is the same in every frame.
 *   The shader computes it in the arena frame from the sprite's retarded position and angle (the state ST-074 uses for the
 *   vertices) and the sprite is then drawn on the boosted vertices. So `u_center` and `u_rot` are always the arena-frame
 *   state (current interpolated in the world view, retarded in the pilot view), never the apparent one.
 * - FAKE 1: the emitter position. Exactly, the light hitting an object at its retarded time `t_ret` left the disk earlier
 *   still, at `t_ret − |p − e|/c`. We use the hole's position extrapolated to `t_ret` ([emitterAtRetardedTime]) and skip
 *   the extra emitter-to-object delay. Holes move slowly: the error is about `v_hole·|p − e|/c`, under 1 u for holes slower
 *   than 2 u/s at 30 u.
 * - FAKE 2: the light received by a moving object isn't Doppler-shifted or aberrated at the object (its colour and
 *   brightness are computed as if it were at rest in the arena). The pilot's own Doppler on the result still applies
 *   through the vertex colour.
 * - FAKE 3: lighting ignores length contraction (ST-044) of the lit sprite, and the disk is one soft point light (ST-095).
 *
 * Normal-map matching assumes un-trimmed, un-rotated atlas regions (the packer settings: no whitespace stripping, no rotation).
 * Normal map and colour share the atlas page in the shipped atlas, but the lookup stays per region, so a second page works.
 */
class LitSpriteRenderer(private val atlas: TextureAtlas) : Disposable {
    private val program: ShaderProgram

    /**
     * Normal-map partners by colour region, built once at construction (no string work per frame). `Atlas.createSprite` returns a
     * plain [Sprite] for unstripped regions, which no longer knows its region name, so the sprite is matched by its page texture
     * and pixel origin (`regionX`, `regionY`) instead.
     */
    private val partners = ObjectMap<Texture, IntMap<AtlasRegion>>()
    private val idx = IntArray(MAX_LIGHTS_PER_SPRITE)
    private val lightPos = FloatArray(MAX_LIGHTS_PER_SPRITE * 3)
    private val lightColor = FloatArray(MAX_LIGHTS_PER_SPRITE * 3)
    private val lightSize = FloatArray(MAX_LIGHTS_PER_SPRITE)
    private val lightRange = FloatArray(MAX_LIGHTS_PER_SPRITE)
    private val lightDirFlag = FloatArray(MAX_LIGHTS_PER_SPRITE)
    private var active = false

    init {
        ShaderProgram.pedantic = false
        // prependFragmentCode is global to ShaderProgram: set it only while compiling this shader.
        val previousPrepend = ShaderProgram.prependFragmentCode
        ShaderProgram.prependFragmentCode = "#define MAX_LIGHTS $MAX_LIGHTS_PER_SPRITE\n#define LIGHT_HEIGHT ${glslFloat(LIGHT_HEIGHT)}\n"
        try {
            program = ShaderProgram(Gdx.files.internal(VERTEX_SHADER), Gdx.files.internal(FRAGMENT_SHADER))
        } finally {
            ShaderProgram.prependFragmentCode = previousPrepend
        }
        if (!program.isCompiled) logger.error { "Lit sprite shader failed to compile, sprites are drawn unlit:\n${program.log}" }
        indexNormalPartners()
    }

    /** False when the shader did not compile; then [prepare] always returns false. */
    val ready: Boolean get() = program.isCompiled

    /**
     * Makes the next quad of [sprite] lit, if [entity] can be: picks the strongest lights of [lights] at the sprite's arena
     * position ([ax], [ay]) with angle [angleRad] (arena frame, see the class doc), moving the emitters back by [lag] seconds
     * (`now − t_ret`, 0 in the world view), then binds the shader and uniforms. Returns false (and the caller draws unlit
     * after [release]) for entities without normals. Allocation-free after the first call per region.
     */
    fun prepare(
        batch: SpriteBatch, entity: Entity, sprite: Sprite, lights: LightSources,
        ax: Float, ay: Float, angleRad: Float, lag: Float,
    ): Boolean {
        if (!program.isCompiled) return false
        val mode: Int
        var normals: Texture = sprite.texture
        var nr: AtlasRegion? = null
        if (entity.gravitySourceCmpOptional?.kind == GravitySourceKind.PLANET) {
            mode = NORMAL_MODE_SPHERE
        } else {
            nr = normalPartner(sprite) ?: return false
            mode = NORMAL_MODE_MAP
            normals = nr.texture
        }
        lights.applyLag(lag)
        val n = pickLights(ax, ay, lights.lights, lights.count, MAX_LIGHTS_PER_SPRITE, idx)
        for (i in 0 until n) {
            val l = lights.lights[idx[i]]
            lightPos[i * 3] = l.x
            lightPos[i * 3 + 1] = l.y
            lightPos[i * 3 + 2] = l.z
            val k = l.intensity
            lightColor[i * 3] = l.r * k
            lightColor[i * 3 + 1] = l.g * k
            lightColor[i * 3 + 2] = l.b * k
            lightSize[i] = l.size
            lightRange[i] = l.range
            lightDirFlag[i] = if (l.directional) 1f else 0f
        }
        batch.shader = program // flushes what was queued with the default shader, then binds
        program.bind()
        normals.bind(NORMAL_UNIT)
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
        program.setUniformi("u_normals", NORMAL_UNIT)
        program.setUniformf("u_colorRegion", sprite.u, sprite.v, sprite.u2, sprite.v2)
        if (nr != null) program.setUniformf("u_normalRegion", nr.u, nr.v, nr.u2, nr.v2)
        program.setUniformi("u_normalMode", mode)
        val type = baseInfoMapper.get(entity)?.entityType
        program.setUniformf("u_domeBlend", if (type == EntityType.ASTEROID || type == EntityType.DEBRIS) ASTEROID_DOME_BLEND else 0f)
        program.setUniformf("u_center", ax, ay)
        program.setUniformf("u_rot", cos(angleRad), sin(angleRad))
        program.setUniformf("u_halfSize", sprite.width / 2f, sprite.height / 2f)
        program.setUniformf("u_ambient", lights.ambient)
        program.setUniformi("u_lightCount", n)
        if (n > 0) {
            program.setUniform3fv("u_lightPos", lightPos, 0, n * 3)
            program.setUniform3fv("u_lightColor", lightColor, 0, n * 3)
            program.setUniform1fv("u_lightSize", lightSize, 0, n)
            program.setUniform1fv("u_lightRange", lightRange, 0, n)
            program.setUniform1fv("u_lightDir", lightDirFlag, 0, n)
        }
        active = true
        return true
    }

    /** Back to the default shader if a lit sprite was drawn last. Call `batch.flush()` first when quads are pending. */
    fun release(batch: SpriteBatch) {
        if (!active) return
        batch.shader = null
        active = false
    }

    private fun indexNormalPartners() {
        for (region in atlas.regions) {
            val partner = atlas.findRegion(region.name + NORMAL_SUFFIX) ?: continue
            partners.get(region.texture) ?: IntMap<AtlasRegion>().also { partners.put(region.texture, it) }
            partners.get(region.texture).put(regionKey(region), partner)
        }
    }

    private fun regionKey(region: TextureRegion): Int = (region.regionX shl REGION_KEY_SHIFT) or region.regionY

    /** The `_n` region of the sprite's atlas region; null for sprites with no partner (lasers, thrusters, planets). */
    private fun normalPartner(sprite: Sprite): AtlasRegion? = partners.get(sprite.texture)?.get(regionKey(sprite))

    override fun dispose() {
        program.dispose()
    }

    private companion object {
        val logger = logger<LitSpriteRenderer>()
    }
}
