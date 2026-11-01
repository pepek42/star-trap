package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.LifetimeComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.provider.MapProvider
import ktx.ashley.has
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class LifetimeAndRemoveSystemTest {

    @Test
    fun lifetimeShouldAddRemoveWhenExpired() {
        val engine = PooledEngine()
        engine.addSystem(LifetimeSystem())
        val entity = engine.createEntity().apply {
            add(LifetimeComponent(remaining = 0.5f, total = 0.5f))
        }
        engine.addEntity(entity)

        engine.update(0.3f)
        assertFalse(entity.has(removeMapper))
        engine.update(0.3f)
        assertTrue(entity.has(removeMapper))
    }

    @Test
    fun playerOutOfBoundsShouldBeClampedNotRemoved() {
        val pair = setup()
        val engine = pair.first
        val world = pair.second
        val player = entityAt(engine, world, 130f, 50f, isPlayer = true)
        player.body.setLinearVelocity(5f, 0f)

        engine.update(0.016f)

        assertFalse(player.entity.has(removeMapper))
        assertEquals(99f, player.body.position.x, 0.001f)
        assertEquals(50f, player.body.position.y, 0.001f)
        assertEquals(0f, player.body.linearVelocity.len(), 0.001f)
    }

    @Test
    fun nonPlayerFarOutsideShouldBeRemoved() {
        val pair = setup()
        val engine = pair.first
        val world = pair.second
        val other = entityAt(engine, world, 130f, 50f, isPlayer = false)
        engine.update(0.016f)
        // tagged and removed by RemoveSystem in the same update
        assertFalse(engine.entities.contains(other.entity, true))
        assertEquals(0, world.bodyCount)
    }

    @Test
    fun bodyInsideMarginShouldBeLeftAlone() {
        val pair = setup()
        val engine = pair.first
        val world = pair.second
        val other = entityAt(engine, world, 110f, 50f, isPlayer = false)
        engine.update(0.016f)
        assertFalse(other.entity.has(removeMapper))
        assertEquals(110f, other.body.position.x, 0.001f)
    }

    private class Spawned(val entity: com.badlogic.ashley.core.Entity, val body: com.badlogic.gdx.physics.box2d.Body)

    private fun setup(): Pair<PooledEngine, World> {
        val world = World(Vector2(0f, 0f), true)
        val engine = PooledEngine()
        engine.addSystem(RemoveSystem(world, MapProvider(mock(), 100f, 100f)))
        return engine to world
    }

    private fun entityAt(engine: PooledEngine, world: World, x: Float, y: Float, isPlayer: Boolean): Spawned {
        val body = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(x, y)
        })
        val entity = engine.createEntity()
        entity.add(BodyComponent().apply { this.body = body })
        entity.add(TransformComponent())
        if (isPlayer) entity.add(PlayerComponent())
        body.userData = entity
        engine.addEntity(entity)
        return Spawned(entity, body)
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
