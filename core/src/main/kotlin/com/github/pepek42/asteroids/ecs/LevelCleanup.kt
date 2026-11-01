package com.github.pepek42.asteroids.ecs

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.bodyCmp
import ktx.ashley.allOf

private val bodyFamily = allOf(BodyComponent::class).get()

/** Destroys all Box2D bodies owned by entities and removes every entity. Must not be called while the world is locked. */
fun clearLevel(engine: Engine, world: World) {
    engine.getEntitiesFor(bodyFamily).toList().forEach { entity ->
        val body = entity.bodyCmp.body
        world.destroyBody(body)
        body.userData = null
    }
    engine.removeAllEntities()
}
