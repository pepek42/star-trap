package com.github.pepek42.asteroids.provider

import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.AsteroidComponent
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.GravitySourceComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.environment.AsteroidSize
import com.github.pepek42.asteroids.event.GameEventManager
import ktx.ashley.allOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.math.hypot

class BlackHoleProviderTest {
    private val mapW = 200f
    private val mapH = 200f
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = PooledEngine()
    private val map = MapProvider(mock<GameEventManager>(), mapW, mapH)
    private val holes = BlackHoleProvider(engine, world, map)

    @AfterEach
    fun tearDown() = world.dispose()

    private fun valid(x: Float, y: Float, others: FloatArray = FloatArray(0)) =
        validSourcePosition(x, y, BH_WALL_MARGIN, others, others.size / 2, BH_MIN_SEPARATION, mapW, mapH)

    @Test
    fun validSourcePositionRejectsNearWallCentreAndOtherHole() {
        val other = floatArrayOf(40f, 40f)
        assertFalse(valid(20f, 150f), "20 u from the wall")
        assertFalse(valid(mapW / 2f + 30f, mapH / 2f), "30 u from the centre")
        assertFalse(valid(90f, 40f, other), "50 u from another hole")
        assertTrue(valid(150f, 150f, other))
    }

    @Test
    fun level3SpawnsTwoValidHolesAtRest() {
        val positions = holes.spawn(3, RandomXS128(42L))

        assertEquals(4, positions.size)
        val entities = engine.getEntitiesFor(allOf(GravitySourceComponent::class).get())
        assertEquals(2, entities.size())
        for (i in 0 until 2) {
            val others = FloatArray(2).also { if (i == 1) { it[0] = positions[0]; it[1] = positions[1] } }
            assertTrue(valid(positions[2 * i], positions[2 * i + 1], others, if (i == 1) 1 else 0))
        }
        for (e in entities) {
            assertEquals(EntityType.BLACK_HOLE, e.getComponent(BaseInfoComponent::class.java).entityType)
            assertEquals(0f, e.bodyCmp.body.linearVelocity.len())
        }
    }

    private fun valid(x: Float, y: Float, others: FloatArray, n: Int) =
        validSourcePosition(x, y, BH_WALL_MARGIN, others, n, BH_MIN_SEPARATION, mapW, mapH)

    @Test
    fun levelOneSpawnsOneHole() {
        assertEquals(2, holes.spawn(2, RandomXS128(1L)).size)
    }

    @Test
    fun noAsteroidSpawnsWithinClearanceOfAHole() {
        val texture = mock<Texture> {
            on { width } doReturn 10
            on { height } doReturn 10
        }
        val sprite = Sprite(texture)
        val atlas = mock<TextureAtlas> {
            on { createSprite(AsteroidSize.LARGE.textureRegionName) } doReturn sprite
        }
        val random = RandomXS128(42L)
        val positions = holes.spawn(4, random)
        AsteroidProvider(atlas, engine, world, random).spawnAsteroids(4, map)

        val asteroids = engine.getEntitiesFor(allOf(AsteroidComponent::class).get())
        assertTrue(asteroids.size() > 0)
        for (a in asteroids) {
            val p = a.bodyCmp.body.position
            for (h in 0 until positions.size / 2) {
                val d = hypot(p.x - positions[2 * h], p.y - positions[2 * h + 1])
                assertTrue(d >= 16f, "asteroid $d u from a hole")
            }
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun init() {
            GdxNativesLoader.load()
            Box2D.init()
            Gdx.app = mock<Application> { on { logLevel } doReturn Application.LOG_NONE }
        }
    }
}
