package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.RemoveComponent
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.ashley.has

class RemoveSystem(
    private val world: World,
) : IteratingSystem(allOf(RemoveComponent::class).get()) {
    override fun processEntity(entity: Entity?, deltaTime: Float) {
        if (entity?.has(BodyComponent.mapper) == true) {
            entity[BodyComponent.mapper]?.let { bodyComponent ->
                world.destroyBody(bodyComponent.body)
            }
        }
        engine.removeEntity(entity)
    }
}
