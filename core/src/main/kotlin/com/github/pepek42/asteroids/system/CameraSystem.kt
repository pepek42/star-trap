package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.graphics.Camera
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import ktx.ashley.allOf
import ktx.ashley.get

class CameraSystem(
    private val camera: Camera,
) : IteratingSystem(allOf(PlayerComponent::class, BodyComponent::class).get()) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        val position = entity[BodyComponent.mapper]!!.body.position
        camera.position.set(
            position.x,
            position.y,
            camera.position.z
        )
        camera.update()
    }
}
