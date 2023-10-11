package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.RemoveComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.bodyCmpOptional
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.component.transformMapper
import ktx.ashley.has
import ktx.ashley.oneOf

class RemoveSystem(
    private val world: World,
) : IteratingSystem(oneOf(RemoveComponent::class, TransformComponent::class).get()) {
    override fun processEntity(entity: Entity, deltaTime: Float) {

        if (entity.has(removeMapper)) {
            entity.bodyCmpOptional?.let { bodyComponent ->
                world.destroyBody(bodyComponent.body)
            }
            engine.removeEntity(entity)
        } else if (entity.has(transformMapper)) {
            // TODO remove out of bounds
        }
    }
}
