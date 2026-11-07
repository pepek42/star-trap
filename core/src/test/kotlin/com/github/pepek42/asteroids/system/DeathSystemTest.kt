package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.audio.ComputerSoundKind
import com.github.pepek42.asteroids.audio.ComputerSoundQueue
import com.github.pepek42.asteroids.component.AsteroidComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.EnemyComponent
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.provider.AsteroidProvider
import com.github.pepek42.asteroids.settings.SoundSettings
import ktx.ashley.has
import com.github.pepek42.asteroids.provider.PLAYER_START_HEALTH
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class DeathSystemTest {
    private val world = World(Vector2(0f, 0f), true)
    private val engine = PooledEngine()
    private val provider = mock<AsteroidProvider>()
    private var deaths = 0

    @AfterEach
    fun tearDown() = world.dispose()

    private fun body() = world.createBody(BodyDef().apply { type = BodyDef.BodyType.DynamicBody })

    @Test
    fun invulnerablePlayerIsHealedAndNeverReported() {
        engine.addSystem(DeathSystem(engine, provider, RandomXS128(1), invulnerablePlayer = true) { deaths++ })
        val player = engine.createEntity().apply {
            add(PlayerComponent())
            add(HealthComponent().apply { this.health = 0f })
        }
        engine.addEntity(player)
        repeat(3) { engine.update(0.016f) }
        assertEquals(0, deaths)
        assertEquals(PLAYER_START_HEALTH, player.getComponent(HealthComponent::class.java).health)
    }

    @Test
    fun deadPlayerReportsOnceAndIsNotRemoved() {
        engine.addSystem(DeathSystem(engine, provider, RandomXS128(1)) { deaths++ })
        val player = engine.createEntity().apply {
            add(PlayerComponent())
            add(HealthComponent().apply { this.health = 0f })
        }
        engine.addEntity(player)
        repeat(3) { engine.update(0.016f) }
        assertEquals(1, deaths)
        assertFalse(player.has(removeMapper))
    }

    @Test
    fun deadAsteroidSplitsAndIsRemoved() {
        engine.addSystem(DeathSystem(engine, provider, RandomXS128(1)) { deaths++ })
        val asteroid = engine.createEntity().apply {
            add(AsteroidComponent())
            add(HealthComponent().apply { this.health = 0f })
            add(BodyComponent().apply { body = body() })
        }
        engine.addEntity(asteroid)
        engine.update(0.016f)
        assertTrue(asteroid.has(removeMapper))
        verify(provider, times(2)).spawnAsteroid(any(), any(), any(), any(), any())
        assertEquals(0, deaths)
    }

    @Test
    fun deadEnemyAndAsteroidEarnCredits() {
        var credits = 0
        engine.addSystem(DeathSystem(engine, provider, RandomXS128(1), onCredits = { credits += it }))
        engine.addEntity(engine.createEntity().apply {
            add(EnemyComponent())
            add(HealthComponent().apply { this.health = 0f })
            add(BodyComponent().apply { body = body() })
        })
        engine.update(0.016f)
        assertEquals(com.github.pepek42.asteroids.shop.CREDITS_ENEMY_SHIP, credits)
    }

    @Test
    fun deadEnemyPushesOneComputerSoundAndIsRemoved() {
        val queue = ComputerSoundQueue(SoundSettings().apply { computerSounds = true })
        engine.addSystem(DeathSystem(engine, provider, RandomXS128(1), computerSounds = queue) { deaths++ })
        val enemy = engine.createEntity().apply {
            add(EnemyComponent())
            add(HealthComponent().apply { this.health = 0f })
            add(BodyComponent().apply { body = body() })
        }
        engine.addEntity(enemy)
        engine.update(0.016f)
        assertEquals(1, queue.size)
        assertEquals(ComputerSoundKind.ASTEROID_BREAK_LARGE, queue.kindAt(0))
        assertTrue(enemy.has(removeMapper))
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun init() {
            GdxNativesLoader.load()
            Box2D.init()
        }
    }
}
