package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.bodyMapper
import com.github.pepek42.asteroids.component.moveMapper
import ktx.ashley.allOf
import ktx.ashley.get

class MoveSystem : IteratingSystem(allOf(MoveComponent::class, BodyComponent::class).get()) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        val moveComponent = entity[moveMapper]!!
        val body = entity[bodyMapper]!!.body
        if (moveComponent.radiansToRotate != 0f) {
            val direction = if (moveComponent.radiansToRotate > 0) 1 else -1
            body.applyAngularImpulse(1000f * direction, true)
        }
        if (moveComponent.thrusters > 0) {
            body.applyForceToCenter(1000 * moveComponent.thrusters, 0f, true)
        }
    }
}
