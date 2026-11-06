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
import com.github.pepek42.asteroids.component.GravitySourceKind
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.environment.AsteroidSize
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.physics.RelativitySettings
import ktx.ashley.allOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.math.hypot

class PlanetProviderTest {
    private val mapW = 200f
    private val mapH = 200f
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = PooledEngine()
    private val map = MapProvider(mock<GameEventManager>(), mapW, mapH)
    private val texture = mock<Texture> {
        on { width } doReturn 10
        on { height } doReturn 10
    }
    private val settings = RelativitySettings()
    private val holes = BlackHoleProvider(engine, world, map)
    private val planets = PlanetProvider(engine, world, map, settings) { Sprite(texture) }

    @AfterEach
    fun tearDown() = world.dispose()

    @Test
    fun planetCountGrowsAtLevelFour() {
        assertEquals(1, planetCount(1))
        assertEquals(1, planetCount(3))
        assertEquals(2, planetCount(4))
    }

    @Test
    fun surfaceGravityIsHalfTheEngineAcceleration() {
        assertEquals(0.5f * settings.engineProperAccel, planetGm(settings) / (PLANET_RADIUS * PLANET_RADIUS), 1e-4f)
        settings.c = settings.c / 2f
        assertEquals(0.5f * settings.engineProperAccel, planetGm(settings) / (PLANET_RADIUS * PLANET_RADIUS), 1e-4f)
    }

    @Test
    fun levelFourSpawnsTwoSeparatedRestingPlanetsAndAsteroidsKeepClear() {
        val random = RandomXS128(42L)
        val holePos = holes.spawn(4, random)
        val planetPos = planets.spawn(4, random, holePos, holePos.size / 2)
        val sprite = Sprite(texture)
        val atlas = mock<TextureAtlas> {
            on { createSprite(AsteroidSize.LARGE.textureRegionName) } doReturn sprite
        }
        AsteroidProvider(atlas, engine, world, random).spawnAsteroids(4, map)

        assertEquals(4, planetPos.size)
        val entities = engine.getEntitiesFor(allOf(GravitySourceComponent::class).get())
        val planetEntities = entities.filter { it.getComponent(GravitySourceComponent::class.java).kind == GravitySourceKind.PLANET }
        assertEquals(2, planetEntities.size)
        for (i in 0 until 2) {
            val x = planetPos[2 * i]
            val y = planetPos[2 * i + 1]
            assertTrue(hypot(x - mapW / 2f, y - mapH / 2f) >= PLANET_MIN_SEPARATION)
            for (h in 0 until holePos.size / 2) assertTrue(hypot(x - holePos[2 * h], y - holePos[2 * h + 1]) >= PLANET_MIN_SEPARATION)
        }
        assertTrue(hypot(planetPos[0] - planetPos[2], planetPos[1] - planetPos[3]) >= PLANET_MIN_SEPARATION)
        for (e in planetEntities) {
            assertEquals(EntityType.PLANET, e.getComponent(BaseInfoComponent::class.java).entityType)
            assertEquals(0f, e.bodyCmp.body.linearVelocity.len())
        }
        val asteroids = engine.getEntitiesFor(allOf(AsteroidComponent::class).get())
        assertTrue(asteroids.size() > 0)
        for (a in asteroids) {
            val p = a.bodyCmp.body.position
            for (i in 0 until 2) {
                assertTrue(hypot(p.x - planetPos[2 * i], p.y - planetPos[2 * i + 1]) >= 11f)
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
