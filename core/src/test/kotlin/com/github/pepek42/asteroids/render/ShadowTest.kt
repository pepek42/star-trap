package com.github.pepek42.asteroids.render

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.AsteroidComponent
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.GravitySourceComponent
import com.github.pepek42.asteroids.component.GravitySourceKind
import com.github.pepek42.asteroids.component.TransformComponent
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/** Shadows from planets and large asteroids (ST-098): the pure occlusion factor, the CPU occluder selection and the caster pool. */
class ShadowTest {
    private val eps = 1e-5f

    // Light 20 u to the right of the point at the origin, size 2: at t = 5 the penumbra half-width is w = 2 * 5 / 20 = 0.5.
    private val lx = 20f
    private val lightSize = 2f
    private val cr = 2f
    private val t = 5f
    private val w = lightSize * t / lx

    private fun point(cx: Float, cy: Float) = circleShadowFactor(0f, 0f, lx, 0f, lightSize, false, cx, cy, cr)

    @Test
    fun occluderBetweenPointAndLightShadowsFully() {
        assertTrue(point(t, 0f) < 0.05f)
    }

    @Test
    fun occluderOffsetSidewaysByRadiusPlusTwoPenumbraIsLit() {
        assertTrue(point(t, cr + 2f * w) > 0.95f)
        assertTrue(point(t, -(cr + 2f * w)) > 0.95f)
    }

    @Test
    fun occluderBehindThePointOrBeyondTheLightDoesNothing() {
        assertEquals(1f, point(-t, 0f), eps)
        assertEquals(1f, point(lx + 3f, 0f), eps)
    }

    @Test
    fun shadowIsContinuousAlongASidewaysSweep() {
        var prev = point(t, 0f)
        var y = 0f
        while (y < cr + 3f * w) {
            y += 0.01f
            val f = point(t, y)
            assertTrue(kotlin.math.abs(f - prev) < 0.1f, "jump ${f - prev} at y=$y")
            prev = f
        }
    }

    @Test
    fun directionalLightUsesTheStarAngularSizeAndAMinimumPenumbra() {
        // Light direction +x; at t = 10 the angular penumbra is 0.1 u, above MIN_PENUMBRA.
        val wDir = STAR_ANGULAR_SIZE * 10f
        assertTrue(circleShadowFactor(0f, 0f, 1f, 0f, 5f, true, 10f, 0f, cr) < 0.05f)
        assertTrue(circleShadowFactor(0f, 0f, 1f, 0f, 5f, true, 10f, cr + 2f * wDir, cr) > 0.95f)
        // The light size is ignored for a directional light; the direction need not be normalised.
        assertEquals(
            circleShadowFactor(0f, 0f, 1f, 0f, 5f, true, 10f, cr, cr),
            circleShadowFactor(0f, 0f, 7f, 0f, 99f, true, 10f, cr, cr), eps,
        )
        // Occluder behind a directional light's point: not between.
        assertEquals(1f, circleShadowFactor(0f, 0f, 1f, 0f, 5f, true, -10f, 0f, cr), eps)
    }

    @Test
    fun shadeDimsTheLightTermButNotTheAmbient() {
        val light = LightSource().apply { x = lx; y = 0f; size = lightSize; range = 1000f; intensity = 1000f }
        val lights = arrayOf(light)
        val lit = FloatArray(3)
        val shadowed = FloatArray(3)
        val occ = OccluderCircles().apply { add(t, 0f, cr) }
        shade(0f, 0f, 1f, 0f, 0f, lights, intArrayOf(0), 1, 0.35f, lit)
        shade(0f, 0f, 1f, 0f, 0f, lights, intArrayOf(0), 1, 0.35f, shadowed, occ)
        assertTrue(lit[0] > 0.35f + 0.05f)
        assertEquals(0.35f, shadowed[0], 0.05f)
    }

    private fun pool(vararg circles: FloatArray): OccluderPool = OccluderPool().apply {
        circles.forEachIndexed { i, c -> add(c[0], c[1], c[2], c.getOrElse(3) { 0f }, c.getOrElse(4) { 0f }, i) }
    }

    private fun lightAt(x: Float, y: Float) = arrayOf(LightSource().apply { this.x = x; this.y = y })

    @Test
    fun selectionSkipsTheSpritesOwnCircle() {
        val p = pool(floatArrayOf(5f, 0f, 2f), floatArrayOf(8f, 0.5f, 2f))
        val out = OccluderCircles()
        val n = selectOccluders(5f, 0f, 1f, lightAt(20f, 0f), intArrayOf(0), 1, p, 0f, selfId = 0, out = out)
        assertEquals(1, n)
        assertEquals(8f, out.x[0], eps)
    }

    @Test
    fun selectionKeepsAtMostSixAndIgnoresCirclesOffTheSegment() {
        val circles = Array(10) { floatArrayOf(3f + it, 0f, 2f) } + floatArrayOf(10f, 40f, 2f) + floatArrayOf(-8f, 0f, 2f)
        val p = pool(*circles)
        val out = OccluderCircles()
        val n = selectOccluders(0f, 0f, 1f, lightAt(20f, 0f), intArrayOf(0), 1, p, 0f, selfId = -1, out = out)
        assertEquals(MAX_OCCLUDERS, n)
        for (j in 0 until n) assertTrue(out.y[j] == 0f && out.x[j] >= 3f)
        // Without the crowd, the far-off and the behind circles are not selected.
        val few = pool(floatArrayOf(10f, 40f, 2f), floatArrayOf(-8f, 0f, 2f))
        assertEquals(0, selectOccluders(0f, 0f, 1f, lightAt(20f, 0f), intArrayOf(0), 1, few, 0f, -1, out))
    }

    @Test
    fun selectionMovesOccludersBackByTheLag() {
        // At lag 0 the circle (y = 12, vy = 6) is off the segment.
        val p = pool(floatArrayOf(10f, 12f, 2f, 0f, 6f))
        val out = OccluderCircles()
        assertEquals(0, selectOccluders(0f, 0f, 1f, lightAt(20f, 0f), intArrayOf(0), 1, p, 0f, -1, out))
        // 2 s earlier it was at y = 12 - 6 * 2 = 0: on the segment.
        assertEquals(1, selectOccluders(0f, 0f, 1f, lightAt(20f, 0f), intArrayOf(0), 1, p, 2f, -1, out))
        assertEquals(0f, out.y[0], eps)
    }

    @Test
    fun selectionSeesADirectionalRayButNotBehind() {
        val dir = arrayOf(LightSource().apply { x = 1f; y = 0f; directional = true })
        val out = OccluderCircles()
        assertEquals(1, selectOccluders(0f, 0f, 1f, dir, intArrayOf(0), 1, pool(floatArrayOf(500f, 0f, 2f)), 0f, -1, out))
        assertEquals(0, selectOccluders(0f, 0f, 1f, dir, intArrayOf(0), 1, pool(floatArrayOf(-500f, 0f, 2f)), 0f, -1, out))
    }

    @Test
    fun poolGrowsBeyondItsInitialCapacity() {
        val p = OccluderPool()
        for (i in 0 until 100) p.add(i.toFloat(), 0f, 2f, 0f, 0f, i)
        assertEquals(100, p.count)
        assertEquals(99f, p.x[99], eps)
        assertEquals(99, p.id[99])
    }

    companion object {
        private lateinit var world: World

        @JvmStatic
        @BeforeAll
        fun init() {
            GdxNativesLoader.load()
            Box2D.init()
            world = World(Vector2.Zero, true)
        }

        @JvmStatic
        @AfterAll
        fun done() {
            world.dispose()
        }
    }

    private fun bodyEntity(x: Float, y: Float, radius: Float): Entity {
        val body = world.createBody(BodyDef().apply { type = BodyDef.BodyType.DynamicBody })
        body.createFixture(CircleShape().apply { this.radius = radius }, 1f)
        return Entity().apply {
            add(BodyComponent().also { it.body = body })
            add(TransformComponent().also { it.interpolatedPosition.set(x, y) })
            add(BaseInfoComponent())
        }
    }

    private fun immutable(vararg e: Entity): ImmutableArray<Entity> = ImmutableArray(Array<Entity>().also { a -> e.forEach(a::add) })

    @Test
    fun rebuildTakesPlanetsAndLargeAsteroidsOnly() {
        val planet = bodyEntity(10f, 10f, 2.5f).apply { add(GravitySourceComponent().also { it.kind = GravitySourceKind.PLANET }) }
        val hole = bodyEntity(30f, 30f, 3f).apply { add(GravitySourceComponent().also { it.kind = GravitySourceKind.BLACK_HOLE }) }
        val big = bodyEntity(1f, 2f, 2.25f).apply { add(AsteroidComponent()) }
        val small = bodyEntity(5f, 6f, 1f).apply { add(AsteroidComponent()) }
        val pool = OccluderPool()
        pool.rebuild(immutable(planet, hole), immutable(big, small))
        assertEquals(2, pool.count)
        assertEquals(10f, pool.x[0], eps)
        assertEquals(2.5f, pool.r[0], eps)
        assertEquals(1f, pool.x[1], eps)
        assertEquals(2.25f, pool.r[1], eps)
        assertTrue(pool.id[0] != pool.id[1])
    }
}
