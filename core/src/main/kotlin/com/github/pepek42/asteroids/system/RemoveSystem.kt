package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.RemoveComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.bodyCmpOptional
import com.github.pepek42.asteroids.component.baseInfoMapper
import com.github.pepek42.asteroids.component.bodyMapper
import com.github.pepek42.asteroids.component.playerCmpOptional
import com.github.pepek42.asteroids.component.removeMapper
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
        if (entity.has(bodyMapper) && !entity.has(removeMapper)) {
            val body = entity.bodyCmp.body
            if (isOutOfBounds(body.position)) {
                if (entity.playerCmpOptional != null) {
                    val x = body.position.x.coerceIn(PLAYER_CLAMP_MARGIN, mapProvider.mapWidth - PLAYER_CLAMP_MARGIN)
                    val y = body.position.y.coerceIn(PLAYER_CLAMP_MARGIN, mapProvider.mapHeight - PLAYER_CLAMP_MARGIN)
                    body.setTransform(x, y, body.angle)
                    body.setLinearVelocity(0f, 0f)
                    defaultLoggingUtils.tryLogging { logger.info { "WARN: Player out of bounds, clamped to ($x, $y)" } }
                } else {
                    entity.add(RemoveComponent())
                    if (baseInfoMapper.get(entity)?.entityType == EntityType.PROJECTILE) {
                        defaultLoggingUtils.tryLogging { logger.debug { "Projectile left the arena, removing ${entity.baseInfoCmp}" } }
                    } else {
                        defaultLoggingUtils.tryLogging { logger.info { "WARN: Out of bounds, removing ${entity.baseInfoCmp}" } }
                    }
                }
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
        position.x < -mapProvider.mapWidth * REMOVE_BUFFER_FRACTION
            || position.x > mapProvider.mapWidth * (1 + REMOVE_BUFFER_FRACTION)
            || position.y < -mapProvider.mapHeight * REMOVE_BUFFER_FRACTION
            || position.y > mapProvider.mapHeight * (1 + REMOVE_BUFFER_FRACTION)

    companion object {
        private val logger = logger<RemoveSystem>()
        private const val PLAYER_CLAMP_MARGIN = 1f
        private const val REMOVE_BUFFER_FRACTION = 0.20f
    }
}
