package com.github.pepek42.asteroids.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Disposable
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.physics.BETA_MAX_MASSIVE
import com.github.pepek42.asteroids.physics.BH_RS
import com.github.pepek42.asteroids.physics.dopplerFactor
import ktx.log.logger
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Inner edge of the disk (the ISCO) as a multiple of `r_s`; the world radius is `DISK_INNER_PER_RS * rs` (`3 * BH_RS` = 3 u). */
const val DISK_INNER_PER_RS = 3f

/** Outer edge of the disk as a multiple of `r_s`; `10.2 * BH_RS` = 10.2 u (12 u before ST-175, 15 % smaller). */
const val DISK_OUTER_PER_RS = 10.2f

/** Inner edge of the default disk (a hole with `r_s` = [BH_RS]). */
const val DISK_INNER = DISK_INNER_PER_RS * BH_RS

/** Outer edge of the default disk. */
const val DISK_OUTER = DISK_OUTER_PER_RS * BH_RS

/** Particles per disk. 4 vertices each, so 12000 vertices: fits 16-bit indices. */
const val DISK_PARTICLES = 3000

/** Peak temperature of the disk (K), reached at `r = 49/36 * r_in`, before the gravitational redshift. */
const val DISK_T_PEAK = 6000f

/**
 * Half-size of a particle quad (world units) for a hole with `r_s` = [BH_RS]; scales with `r_s`. ST-175: 0.25 -> 0.2 with
 * [DISK_ALPHA] 1 -> 0.7; the smaller annulus packs the same 3000 particles tighter, so the mean brightness per area drops by
 * about `(0.2 / 0.25)^2 * 0.7 * (135 / 95)` = 0.64, i.e. roughly -35 %.
 */
const val DISK_PARTICLE_SIZE = 0.2f

/** Position of the temperature peak of a thin disk in units of the inner radius: `x = 49/36`. */
const val DISK_PEAK_X = 49f / 36f

/** Floats per vertex: `a_corner` (2), `a_r`, `a_phi0`, `a_size`, `a_temp`. */
const val DISK_VERTEX_FLOATS = 6

private const val DISK_ALPHA = 0.7f
private const val VERTEX_SHADER = "shaders/accretion_disk.vert"
private const val FRAGMENT_SHADER = "shaders/accretion_disk.frag"
private const val CORNERS_X = "-++-"
private const val CORNERS_Y = "--++"

/** True when the body at ([dx], [dy]) from the hole centre is inside the disk annulus `[3 rs, DISK_OUTER_PER_RS rs]` (ST-175). */
fun inDiskAnnulus(dx: Float, dy: Float, rs: Float): Boolean {
    val rIn = DISK_INNER_PER_RS * rs
    val rOut = DISK_OUTER_PER_RS * rs
    val d2 = dx * dx + dy * dy
    return d2 >= rIn * rIn && d2 <= rOut * rOut
}

/** Bodies the disk hurts and scatters particles around: ships (player and enemies) and asteroids; not projectiles, debris or the hole. */
fun diskAffects(type: EntityType): Boolean = type == EntityType.SHIP || type == EntityType.ASTEROID

/** `x^(-3/4) (1 - x^(-1/2))^(1/4)` of the Novikov-Thorne thin disk, without the scale `T*`. */
private fun diskShape(x: Float): Float = x.pow(-0.75f) * (1f - x.pow(-0.5f)).pow(0.25f)

/**
 * Temperature of a thin accretion disk at radius [r] (research E17): `T(r) = T* x^(-3/4) (1 - x^(-1/2))^(1/4)`, `x = r / rIn`,
 * with `T*` chosen so the maximum, at `x = 49/36`, equals [tPeak]. 0 at and inside [rIn].
 */
fun diskTemperature(r: Float, rIn: Float, tPeak: Float): Float {
    val x = r / rIn
    if (x <= 1f) return 0f
    return tPeak / diskShape(DISK_PEAK_X) * diskShape(x)
}

/** Redshift factor `g = sqrt(1 - 3 GM / (r c^2)) = sqrt(1 - 1.5 rs / r)` of a face-on circular orbit, transverse Doppler included. */
fun diskRedshift(r: Float, rs: Float): Float = sqrt((1f - 1.5f * rs / r).coerceAtLeast(0f))

/** Keplerian angular speed `sqrt(GM / r^3)` (rad/s). */
fun diskOmega(r: Float, gm: Float): Float = sqrt(gm / (r * r * r))

/** Radius with density proportional to `1 / r` between [rIn] and [rOut] for a uniform [u] in [0, 1). */
fun diskRadius(u: Float, rIn: Float, rOut: Float): Float = rIn * (rOut / rIn).pow(u)

/**
 * Vertex data of [count] particles for a hole with Schwarzschild radius [rs]: [DISK_VERTEX_FLOATS] floats per vertex, 4 vertices
 * per particle. Radii run over `[3 rs, 12 rs]` with density ~ 1/r, `phi0` is uniform, and `a_temp` is `T(r) * g(r)`.
 */
fun buildDiskVertices(seed: Long, rs: Float, count: Int = DISK_PARTICLES): FloatArray {
    val rnd = RandomXS128(seed)
    val rIn = DISK_INNER_PER_RS * rs
    val rOut = DISK_OUTER_PER_RS * rs
    val size = DISK_PARTICLE_SIZE * rs / BH_RS
    val out = FloatArray(count * 4 * DISK_VERTEX_FLOATS)
    var k = 0
    for (i in 0 until count) {
        val r = diskRadius(rnd.nextFloat(), rIn, rOut)
        val phi0 = rnd.nextFloat() * (2f * Math.PI.toFloat())
        val temp = diskTemperature(r, rIn, DISK_T_PEAK) * diskRedshift(r, rs)
        for (c in 0 until 4) {
            out[k++] = if (CORNERS_X[c] == '+') 1f else -1f
            out[k++] = if (CORNERS_Y[c] == '+') 1f else -1f
            out[k++] = r
            out[k++] = phi0
            out[k++] = size
            out[k++] = temp
        }
    }
    return out
}

/** Fixed-point iterations of the per-particle light cone in pilot view; each one shrinks the error by about `v/c` (< 0.5 at the ISCO). */
const val DISK_RETARD_ITERATIONS = 6

/**
 * Pilot view of one disk particle (ST-089), the reference the vertex shader `accretion_disk.vert` mirrors by hand.
 *
 * The hole centre ([cx], [cy]) is already retarded (its light left [tau] seconds ago, at which time the particle's phase is
 * [phi]); [holeVx], [holeVy] is the hole's arena velocity then. The particle sits at `C + r (cos φ', sin φ')` on its circular
 * orbit. With [delay], its own emission is `s` seconds before the centre's, solved from `|p(φ − Ω s) − o| = c (τ + s)` by
 * [DISK_RETARD_ITERATIONS] fixed-point steps on the true circle (the tangent-line formula fails here: Ω·s reaches over a radian
 * near the ISCO). The particle velocity is `v_hole + Ω r t̂` (Galilean sum, error O(v_hole v_orb / c²)). Writes the apparent
 * position (boosted by `apparentOffset` when [aberration]) into [out] and returns the Doppler factor `D` toward the observer at
 * ([ox], [oy]) moving with ([betaX], [betaY]) (fractions of c).
 */
fun diskParticleApparent(
    r: Float, phi: Float, gm: Float, c: Float,
    ox: Float, oy: Float, betaX: Float, betaY: Float,
    cx: Float, cy: Float, tau: Float, holeVx: Float, holeVy: Float,
    delay: Boolean, aberration: Boolean, out: Vector2,
): Float {
    val omega = diskOmega(r, gm)
    var ph = phi
    if (delay) {
        var s = 0f
        repeat(DISK_RETARD_ITERATIONS) {
            val a = phi - omega * s
            val dx = cx + r * cos(a) - ox
            val dy = cy + r * sin(a) - oy
            s = sqrt(dx * dx + dy * dy) / c - tau
        }
        ph = phi - omega * s
    }
    val px = cx + r * cos(ph)
    val py = cy + r * sin(ph)
    val vOrb = omega * r
    val bsx = (holeVx - vOrb * sin(ph)) / c
    val bsy = (holeVy + vOrb * cos(ph)) / c
    var nx = ox - px
    var ny = oy - py
    val len = sqrt(nx * nx + ny * ny)
    val d = if (len > 0f) {
        nx /= len
        ny /= len
        dopplerFactor(scratchObs.set(betaX, betaY), scratchSrc.set(bsx, bsy), nx, ny)
    } else {
        1f
    }
    pilotCorner(px, py, ox, oy, betaX, betaY, aberration, out)
    return d
}

private val scratchObs = Vector2()
private val scratchSrc = Vector2()

/** Searchlight brightness of a disk particle in pilot view at Doppler factor [d]: `beamBrightness` with the point exponent (D²). */
fun diskBeamBrightness(d: Float): Float = beamBrightness(d, true, true)

/** Two triangles per particle quad: corners 0-1-2 and 2-3-0. */
fun buildDiskIndices(count: Int = DISK_PARTICLES): ShortArray {
    val idx = ShortArray(count * 6)
    for (i in 0 until count) {
        val v = i * 4
        val o = i * 6
        idx[o] = v.toShort()
        idx[o + 1] = (v + 1).toShort()
        idx[o + 2] = (v + 2).toShort()
        idx[o + 3] = (v + 2).toShort()
        idx[o + 4] = (v + 3).toShort()
        idx[o + 5] = v.toShort()
    }
    return idx
}

/** The static particle mesh of one hole (ST-087). Built once for a given [rs]; a merge builds a new one. Needs a GL context. */
class AccretionDisk(val seed: Long, val rs: Float) : Disposable {
    val mesh: Mesh = Mesh(
        true, DISK_PARTICLES * 4, DISK_PARTICLES * 6,
        VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_corner"),
        VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_r"),
        VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_phi0"),
        VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_size"),
        VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_temp"),
    ).also {
        it.setVertices(buildDiskVertices(seed, rs))
        it.setIndices(buildDiskIndices())
    }

    override fun dispose() = mesh.dispose()
}

/**
 * Shader, blackbody LUT texture and draw call shared by every [AccretionDisk]. The disk turns on the GPU:
 * `phi = phi0 + sqrt(gm / r^3) * time`. The vertex shader uses `highp` (mandatory in WebGL vertex shaders) so the phase keeps
 * its precision for tens of minutes of `worldTime`; the phase of each particle has its own angular speed, so it cannot be wrapped
 * on the CPU with one period. Falls back to drawing nothing when the shader does not compile.
 */
class AccretionDiskShader : Disposable {
    private val program: ShaderProgram
    private val lut: Texture = createLutTexture()
    private val logMin = ln(BLACKBODY_LUT_MIN_K)

    init {
        ShaderProgram.pedantic = false
        program = ShaderProgram(Gdx.files.internal(VERTEX_SHADER), Gdx.files.internal(FRAGMENT_SHADER))
        if (!program.isCompiled) logger.error { "Accretion disk shader failed to compile, disk is not drawn:\n${program.log}" }
    }

    val ready: Boolean get() = program.isCompiled

    /**
     * Binds the program and the per-frame uniforms (additive blending); call [draw] for each hole, then [end]. [disturbers] is
     * [DiskDisturbers.uniforms] (x, y, reach, strength per slot), uploaded without allocating.
     */
    fun begin(camera: Camera, time: Float, disturbers: FloatArray) {
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        program.bind()
        program.setUniformMatrix("u_projTrans", camera.combined)
        program.setUniformf("u_time", time)
        program.setUniformf("u_alpha", DISK_ALPHA)
        val span = ln(BLACKBODY_LUT_MAX_K) - logMin
        program.setUniformf(
            "u_lutMap", logMin, 1f / span, (BLACKBODY_LUT_SIZE - 1f) / BLACKBODY_LUT_SIZE, 0.5f / BLACKBODY_LUT_SIZE,
        )
        lut.bind(0)
        program.setUniformi("u_lut", 0)
        for (i in 0 until DISK_DISTURBERS) {
            val o = i * DISK_DISTURBER_FLOATS
            program.setUniformf(DISTURB_UNIFORMS[i], disturbers[o], disturbers[o + 1], disturbers[o + 2], disturbers[o + 3])
        }
    }

    /** World view: the disk at its true centre, no light delay, aberration or Doppler. */
    fun draw(disk: AccretionDisk, centerX: Float, centerY: Float, gm: Float) {
        program.setUniformf("u_pilot", 0f)
        drawMesh(disk, centerX, centerY, gm)
    }

    /**
     * Pilot view (ST-089): [time] is the centre's emission time (the shader turns the disk to it), [tau] its light age,
     * ([holeVx], [holeVy]) the hole's arena velocity then; see [diskParticleApparent].
     */
    fun drawPilot(
        disk: AccretionDisk, centerX: Float, centerY: Float, gm: Float, time: Float, tau: Float, holeVx: Float, holeVy: Float,
        ox: Float, oy: Float, betaX: Float, betaY: Float, c: Float, delay: Boolean, aberration: Boolean,
    ) {
        program.setUniformf("u_pilot", 1f)
        program.setUniformf("u_time", time)
        program.setUniformf("u_tau", tau)
        program.setUniformf("u_holeVel", holeVx, holeVy)
        program.setUniformf("u_observer", ox, oy)
        program.setUniformf("u_beta", betaX, betaY)
        program.setUniformf("u_c", c)
        program.setUniformf("u_delay", if (delay) 1f else 0f)
        program.setUniformf("u_aberration", if (aberration) 1f else 0f)
        program.setUniformf("u_beam", BRIGHTNESS_EXPONENT_POINT, B_MIN, B_MAX)
        program.setUniformf("u_betaMax", BETA_MAX_MASSIVE)
        drawMesh(disk, centerX, centerY, gm)
    }

    private fun drawMesh(disk: AccretionDisk, centerX: Float, centerY: Float, gm: Float) {
        program.setUniformf("u_center", centerX, centerY)
        program.setUniformf("u_gm", gm)
        program.setUniformf("u_rs", disk.rs)
        disk.mesh.render(program, GL20.GL_TRIANGLES)
    }

    /** Restores the usual alpha blending that the other renderers expect. */
    fun end() {
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun dispose() {
        program.dispose()
        lut.dispose()
    }

    private fun createLutTexture(): Texture {
        val pixmap = Pixmap(BLACKBODY_LUT_SIZE, 1, Pixmap.Format.RGBA8888)
        val rgb = FloatArray(3)
        for (i in 0 until BLACKBODY_LUT_SIZE) {
            BlackbodyLut.rgbInto(BlackbodyLut.kelvinAt(i), rgb)
            pixmap.setColor(rgb[0], rgb[1], rgb[2], 1f)
            pixmap.drawPixel(i, 0)
        }
        val tex = Texture(pixmap)
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        tex.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
        pixmap.dispose()
        return tex
    }

    private companion object {
        val logger = logger<AccretionDiskShader>()
        val DISTURB_UNIFORMS = Array(DISK_DISTURBERS) { "u_disturb$it" }
    }
}
