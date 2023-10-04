package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.WrapComponent
import com.github.pepek42.asteroids.component.transformMapper
import com.github.pepek42.asteroids.provider.MapProvider
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.log.logger

class WrapSystem(
    private val mapProvider: MapProvider,
) : IteratingSystem(
    allOf(TransformComponent::class, WrapComponent::class).get()
) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        val transformComponent = entity[transformMapper]!!
        val newPosition = Vector2.Zero
        if (transformComponent.interpolatedPosition.x < 0) {
            newPosition.x = transformComponent.interpolatedPosition.x + mapProvider.width
        }
        if (transformComponent.interpolatedPosition.x > mapProvider.width) {
            newPosition.x = transformComponent.interpolatedPosition.x - mapProvider.width
        }
        if (transformComponent.interpolatedPosition.y < 0) {
            newPosition.y = transformComponent.interpolatedPosition.y + mapProvider.height
        }
        if (transformComponent.interpolatedPosition.y > mapProvider.width) {
            newPosition.y = transformComponent.interpolatedPosition.y - mapProvider.height
        }
        if (newPosition != Vector2.Zero) {
            // TODO wrap - most likely destroy current body and rebuild?
            logger.debug { "Wrapping entity to $newPosition from ${transformComponent.interpolatedPosition}" }
        }
    }

    companion object {
        private val logger = logger<WrapSystem>()
    }

}
