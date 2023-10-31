package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.RemoveComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.bodyCmpOptional
import com.github.pepek42.asteroids.component.bodyMapper
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.component.wrapMapper
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import com.github.pepek42.asteroids.provider.MapProvider
import ktx.ashley.has
import ktx.ashley.oneOf
import ktx.log.logger

class RemoveSystem(
    private val world: World,
    private val mapProvider: MapProvider,
) : IteratingSystem(oneOf(RemoveComponent::class, TransformComponent::class).get()) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        if (entity.has(bodyMapper) && !entity.has(wrapMapper) && !entity.has(removeMapper)) {
            if (isOutOfBounds(entity.bodyCmp.body.position)) {
                entity.add(RemoveComponent())
                defaultLoggingUtils.tryLogging { logger.debug { "Adding remove component to ${entity.baseInfoCmp}" } }
            }
        }
        if (entity.has(removeMapper)) {
            entity.bodyCmpOptional?.let { bodyComponent ->
                world.destroyBody(bodyComponent.body)
                bodyComponent.body.userData = null
            }
            engine.removeEntity(entity)
            defaultLoggingUtils.tryLogging { logger.debug { "${entity.baseInfoCmp} removed" } }
        }
    }

    private fun isOutOfBounds(position: Vector2) =
        position.x < -mapProvider.mapWidth * REMOVE_BUFFER_MULTIPLAYER
            || position.x > mapProvider.mapWidth * (1 + REMOVE_BUFFER_MULTIPLAYER)
            || position.y < -mapProvider.mapHeight * REMOVE_BUFFER_MULTIPLAYER
            || position.y > mapProvider.mapHeight * (1 + REMOVE_BUFFER_MULTIPLAYER)

    companion object {
        private val logger = logger<RemoveSystem>()
        private const val REMOVE_BUFFER_MULTIPLAYER = 0.05f
    }
}
