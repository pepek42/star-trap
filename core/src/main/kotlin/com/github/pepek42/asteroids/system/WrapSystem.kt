package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.WrapComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.provider.MapProvider
import ktx.ashley.allOf
import ktx.log.logger

class WrapSystem(
    private val mapProvider: MapProvider,
) : IteratingSystem(
    allOf(WrapComponent::class, BodyComponent::class).get()
) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        val body = entity.bodyCmp.body
        var moved = false
        var newX = 0f
        var newY = 0f
        val x = body.position.x
        val y = body.position.y
        if (x < 0) {
            newX = x + mapProvider.mapWidth
            moved = true
        }
        if (x > mapProvider.mapWidth) {
            newX = x - mapProvider.mapWidth
            moved = true
        }
        if (y < 0) {
            newY = y + mapProvider.mapHeight
            moved = true
        }
        if (y > mapProvider.mapWidth) {
            newY = y - mapProvider.mapHeight
            moved = true
        }
        if (moved) {
            logger.debug {
                "Wrapping entity to $newX x $newY from ${body.position}"
            }
            // TODO How will it work with multiple objects? Maybe force field around borders is better idea
            body.setTransform(newX, newY, body.angle)
        }
    }

    companion object {
        private val logger = logger<WrapSystem>()
    }

}
