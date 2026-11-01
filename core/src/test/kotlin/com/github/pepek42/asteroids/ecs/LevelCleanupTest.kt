package com.github.pepek42.asteroids.ecs

import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.BodyComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class LevelCleanupTest {
    @Test
    fun clearLevelRemovesBodiesAndEntities() {
        GdxNativesLoader.load()
        Box2D.init()
        Gdx.app = mock<Application>()
        val world = World(com.badlogic.gdx.math.Vector2(0f, 0f), true)
        val engine = PooledEngine()
        repeat(3) {
            val body = world.createBody(BodyDef().apply { type = BodyDef.BodyType.DynamicBody })
            val entity = engine.createEntity()
            entity.add(engine.createComponent(BodyComponent::class.java).also { it.body = body })
            body.userData = entity
            engine.addEntity(entity)
        }
        assertEquals(3, world.bodyCount)

        clearLevel(engine, world)

        assertEquals(0, world.bodyCount)
        assertEquals(0, engine.entities.size())
        world.dispose()
    }
}
