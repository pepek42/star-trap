package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import ktx.ashley.allOf
import ktx.ashley.get

class MoveSystem : IteratingSystem(allOf(MoveComponent::class, BodyComponent::class).get()) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        val moveComponent = entity[MoveComponent.mapper]!!
        val body = entity[BodyComponent.mapper]!!.body
        if (moveComponent.thrusters > 0) {
            body.applyForceToCenter(1000 * moveComponent.thrusters, 0f, true)
        }
    }
}
