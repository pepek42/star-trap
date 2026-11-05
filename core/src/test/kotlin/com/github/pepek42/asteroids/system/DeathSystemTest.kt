package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.AsteroidComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.provider.AsteroidProvider
import ktx.ashley.has
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
    fun deadPlayerReportsOnceAndIsNotRemoved() {
        engine.addSystem(DeathSystem(engine, provider, RandomXS128(1)) { deaths++ })
        val player = engine.createEntity().apply {
            add(PlayerComponent())
            add(HealthComponent(0f))
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
            add(HealthComponent(0f))
            add(BodyComponent().apply { body = body() })
        }
        engine.addEntity(asteroid)
        engine.update(0.016f)
        assertTrue(asteroid.has(removeMapper))
        verify(provider, times(2)).spawnAsteroid(any(), any(), any(), any())
        assertEquals(0, deaths)
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
