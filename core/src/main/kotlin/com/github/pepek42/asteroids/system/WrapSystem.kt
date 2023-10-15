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
        var x = body.position.x
        var y = body.position.y
        if (x < 0) {
            x += mapProvider.mapWidth
            moved = true
        }
        if (x > mapProvider.mapWidth) {
            x -= mapProvider.mapWidth
            moved = true
        }
        if (y < 0) {
            y += mapProvider.mapHeight
            moved = true
        }
        if (y > mapProvider.mapHeight) {
            y -= mapProvider.mapHeight
            moved = true
        }
        if (moved) {
            logger.debug {
                "Wrapping entity to $x x $y from ${body.position}"
            }
            // TODO How will it work with multiple objects? Maybe force field around borders is better idea
            body.setTransform(x, y, body.angle)
        }
    }

    companion object {
        private val logger = logger<WrapSystem>()
    }

}
