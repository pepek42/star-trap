package com.github.pepek42.asteroids.render

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.GravitySourceComponent
import com.github.pepek42.asteroids.component.GravitySourceKind
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.physics.BH_RS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Lit sprites (ST-097): the CPU reference of the shader, the pilot-view emitter extrapolation and the light list. */
class LitSpriteTest {
    private val eps = 1e-5f

    companion object {
        @JvmStatic
        @org.junit.jupiter.api.BeforeAll
        fun initBox2D() {
            Box2D.init()
        }
    }

    private fun diskLightAtOrigin() = LightSource().apply {
        configureDiskLight(this, FloatArray(3))
        x = 0f
        y = 0f
    }

    private fun lightAt(lights: kotlin.Array<LightSource>, qx: Float, qy: Float, cx: Float, cy: Float, rot: Float, mode: Int,
        nx: Float = 0f, ny: Float = 0f, nz: Float = 1f): FloatArray {
        val out = FloatArray(3)
        shadeSpriteTexel(
            qx, qy, 1f, 1f, cx, cy, rot, mode, nx, ny, nz, lights, intArrayOf(0), lights.size, LIGHT_AMBIENT, out,
        )
        return out
    }

    @Test
    fun sphereTexelFacingTheHoleIsBrighterThanTheFarSide() {
        val lights = arrayOf(diskLightAtOrigin())
        val near = lightAt(lights, -1f, 0f, 10f, 0f, 0f, NORMAL_MODE_SPHERE)
        val far = lightAt(lights, 1f, 0f, 10f, 0f, 0f, NORMAL_MODE_SPHERE)
        assertTrue(near[0] > far[0], "near=${near[0]} far=${far[0]}")
        // The far side normal points away from the light: N.L < 0, so only the wrap term lights it.
        val d = 11f
        val wrap = (DISK_LIGHT_SIZE / d).coerceIn(0f, 1f)
        val f = falloff(d, DISK_LIGHT_SIZE, DISK_LIGHT_RANGE)
        val len = kotlin.math.sqrt(d * d + LIGHT_HEIGHT * LIGHT_HEIGHT)
        val nDotL = -d / len
        val expected = LIGHT_AMBIENT + lights[0].r * DISK_LIGHT_INTENSITY * f * wrapDiffuse(nDotL, wrap)
        assertEquals(expected, far[0], eps)
        assertTrue(far[0] >= LIGHT_AMBIENT)
    }

    @Test
    fun normalMapTexelOnARotatedSpriteIsLitFromTheRotatedSide() {
        // Tangent normal +x on a sprite turned by 90 degrees points to +y in the arena.
        fun light(x: Float, y: Float) = LightSource().apply { this.x = x; this.y = y; directional = true }
        val fromPlusY = arrayOf(light(0f, 1f))
        val fromPlusX = arrayOf(light(1f, 0f))
        val rot = Math.PI.toFloat() / 2f
        val litY = lightAt(fromPlusY, 0f, 0f, 0f, 0f, rot, NORMAL_MODE_MAP, 1f, 0f, 0f)
        val litX = lightAt(fromPlusX, 0f, 0f, 0f, 0f, rot, NORMAL_MODE_MAP, 1f, 0f, 0f)
        assertTrue(litY[0] > LIGHT_AMBIENT + 0.5f, "lit from +y: ${litY[0]}")
        assertEquals(LIGHT_AMBIENT, litX[0], eps)
    }

    @Test
    fun flatModeIgnoresTheTexelNormal() {
        val lights = arrayOf(diskLightAtOrigin())
        val a = lightAt(lights, 0.5f, 0.5f, 10f, 0f, 0f, NORMAL_MODE_FLAT, 1f, 0f, 0f)
        val b = lightAt(lights, 0.5f, 0.5f, 10f, 0f, 0f, NORMAL_MODE_FLAT, 0f, 1f, 0f)
        assertEquals(a[0], b[0], eps)
    }

    @Test
    fun noLightsGivesTheAmbientFloor() {
        val out = FloatArray(3)
        shadeSpriteTexel(0f, 0f, 1f, 1f, 5f, 5f, 0f, NORMAL_MODE_SPHERE, 0f, 0f, 1f, emptyArray(), IntArray(0), 0, LIGHT_AMBIENT, out)
        assertEquals(LIGHT_AMBIENT, out[0], eps)
        assertEquals(LIGHT_AMBIENT, out[2], eps)
    }

    @Test
    fun emitterIsExtrapolatedBackAlongTheHoleVelocity() {
        // A hole at x = 10 moving at 2 u/s, the object's light left it 0.5 s ago: the emitter was 1 u behind.
        assertEquals(9f, emitterAtRetardedTime(10f, 2f, 5f, 4.5f), eps)
        assertEquals(10f, emitterAtRetardedTime(10f, 2f, 5f, 5f), eps)
    }

    private fun hole(x: Float, y: Float, kind: GravitySourceKind, rs: Float): Entity = Entity().apply {
        add(GravitySourceComponent().also { it.kind = kind; it.rs = rs })
        add(TransformComponent().also { it.interpolatedPosition.set(x, y) })
    }

    private fun immutable(vararg e: Entity): ImmutableArray<Entity> = ImmutableArray(Array<Entity>().also { a -> e.forEach(a::add) })

    @Test
    fun onlyBlackHolesBecomeLightsAndMergedHolesScale() {
        val sources = LightSources()
        sources.rebuild(immutable(hole(1f, 2f, GravitySourceKind.BLACK_HOLE, 2f * BH_RS), hole(9f, 9f, GravitySourceKind.PLANET, BH_RS)))
        assertEquals(1, sources.count)
        val l = sources.lights[0]
        assertEquals(1f, l.x, eps)
        assertEquals(2f, l.y, eps)
        assertEquals(2f * DISK_LIGHT_SIZE, l.size, eps)
        assertEquals(2f * DISK_LIGHT_RANGE, l.range, eps)
        assertEquals(DISK_LIGHT_INTENSITY, l.intensity, eps)
    }

    @Test
    fun ambientIsFullWithoutAStarAndLowerWithOne() {
        val sources = LightSources()
        sources.rebuild(immutable(hole(1f, 2f, GravitySourceKind.BLACK_HOLE, BH_RS)))
        assertEquals(LIGHT_AMBIENT_NO_STAR, sources.ambient, eps)
    }

    @Test
    fun applyLagMovesEmittersBackAlongTheirVelocityAndRestores() {
        val world = World(Vector2.Zero, true)
        val e = hole(10f, 0f, GravitySourceKind.BLACK_HOLE, BH_RS)
        val body = world.createBody(BodyDef().apply { type = BodyDef.BodyType.DynamicBody })
        body.setLinearVelocity(2f, 0f)
        e.add(BodyComponent().also { it.body = body })
        val sources = LightSources()
        sources.rebuild(immutable(e))
        sources.applyLag(0.5f)
        assertEquals(9f, sources.lights[0].x, eps)
        sources.applyLag(0f)
        assertEquals(10f, sources.lights[0].x, eps)
        world.destroyBody(body)
        world.dispose()
    }
}
