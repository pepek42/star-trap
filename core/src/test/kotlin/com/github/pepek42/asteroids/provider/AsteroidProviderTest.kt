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
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.environment.ASTEROID_SPAWN_MIN_RADIUS
import com.github.pepek42.asteroids.environment.ASTEROID_SPAWN_BETA_MAX
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import com.github.pepek42.asteroids.environment.AsteroidSize
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.physics.FIELD_SKIN
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.math.hypot
import kotlin.math.min

class AsteroidProviderTest {

    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = PooledEngine()
    private val texture = mock<Texture> {
        on { width } doReturn 10
        on { height } doReturn 10
    }
    private val asteroidSprite = Sprite(texture)
    private val textureAtlas = mock<TextureAtlas> {
        on { createSprite(AsteroidSize.LARGE.textureRegionName) } doReturn asteroidSprite
    }
    private val random = RandomXS128(42L)
    private val provider = AsteroidProvider(textureAtlas, engine, world, random)

    @AfterEach
    fun tearDown() = world.dispose()

    @Test
    fun level3SpawnsNineteenAsteroidsInsideDiscAroundCentre() {
        // given
        val map = MapProvider(mock<GameEventManager>(), 200f, 200f)
        val centerX = 100f
        val centerY = 100f
        val maxRadius = min(centerX, centerY) - FIELD_SKIN - AsteroidSize.LARGE.radius - 1f

        // when
        provider.spawnAsteroids(3, map)

        // then
        val asteroids = engine.entities.filter { it.getComponent(AsteroidComponent::class.java) != null }
        assertEquals(19, asteroids.size)
        val speedMax = ASTEROID_SPAWN_BETA_MAX * SPEED_OF_LIGHT
        for (asteroid in asteroids) {
            val body = asteroid.getComponent(BodyComponent::class.java).body
            val distance = hypot(body.position.x - centerX, body.position.y - centerY)
            assertTrue(distance >= ASTEROID_SPAWN_MIN_RADIUS - 1e-3f, "distance $distance below min")
            assertTrue(distance <= maxRadius + 1e-3f, "distance $distance above max $maxRadius")
            assertTrue(body.linearVelocity.x in -speedMax..speedMax)
            assertTrue(body.linearVelocity.y in -speedMax..speedMax)
        }
    }

    @Test
    fun nearAsteroidsSpawnInRingAroundPlayer() {
        val around = Vector2(100f, 100f)
        provider.spawnNearAsteroids(4, around)
        val asteroids = engine.entities.filter { it.getComponent(AsteroidComponent::class.java) != null }
        assertEquals(4, asteroids.size)
        for (a in asteroids) {
            val p = a.getComponent(BodyComponent::class.java).body.position
            val d = hypot(p.x - around.x, p.y - around.y)
            assertTrue(d >= AsteroidProvider.NEAR_ASTEROID_MIN_DIST - 1e-3f && d <= AsteroidProvider.NEAR_ASTEROID_MAX_DIST + 1e-3f)
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
