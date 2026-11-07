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
import com.github.pepek42.asteroids.ai.ENEMY_ENGINE_FRACTION
import com.github.pepek42.asteroids.component.EnemyComponent
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.RemoveComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.shipHandlingCmp
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.system.DeathSystem
import com.github.pepek42.asteroids.system.EndLevelSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.math.hypot

class EnemyShipProviderTest {
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = PooledEngine()
    private val settings = RelativitySettings()
    private val map = MapProvider(mock<GameEventManager>(), 200f, 200f)
    private val texture = mock<Texture> {
        on { width } doReturn 10
        on { height } doReturn 10
    }
    private val atlas = mock<TextureAtlas>().also { a ->
        whenever(a.createSprite(any<String>())).thenAnswer { Sprite(texture) }
    }
    private val provider = EnemyShipProvider(engine, world, atlas, settings)

    @AfterEach
    fun tearDown() = world.dispose()

    @Test
    fun enemyCountGrowsAndIsCapped() {
        assertEquals(0, enemyCount(1))
        assertEquals(1, enemyCount(2))
        assertEquals(ENEMY_MAX, enemyCount(10))
    }

    @Test
    fun spawnedEnemiesKeepMinimumDistanceFromThePlayer() {
        val pos = provider.spawn(10, RandomXS128(7L), map, FloatArray(0))
        assertEquals(ENEMY_MAX, pos.size / 2)
        val player = map.playerSpawnLocation()
        for (i in 0 until pos.size / 2) {
            assertTrue(hypot(pos[2 * i] - player.x, pos[2 * i + 1] - player.y) >= ENEMY_MIN_SPAWN_LIGHT_SECONDS * settings.c)
        }
        for (e in engine.entities) {
            assertEquals(ENEMY_ENGINE_FRACTION, e.shipHandlingCmp.engineFactor)
            assertNotNull(e.getComponent(EnemyComponent::class.java))
        }
    }

    @Test
    fun levelIsNotClearedWhileAnEnemyLivesAndDeadEnemyGetsRemoveComponent() {
        provider.spawn(2, RandomXS128(7L), map, FloatArray(0))
        var cleared = 0
        engine.addSystem(EndLevelSystem({ cleared++ }))
        engine.addSystem(DeathSystem(engine, mock<AsteroidProvider>(), RandomXS128(1)))
        engine.update(0.016f)
        assertEquals(0, cleared)

        val enemy = engine.entities.first()
        assertNotNull(enemy.bodyCmp.body)
        enemy.getComponent(HealthComponent::class.java).health = 0f
        engine.update(0.016f)
        assertNotNull(enemy.getComponent(RemoveComponent::class.java))
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
