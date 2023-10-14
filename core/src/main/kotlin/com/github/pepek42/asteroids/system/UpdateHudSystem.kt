package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.ui.Hud
import ktx.ashley.allOf

class UpdateHudSystem(
    private val hud: Hud,
) : IteratingSystem(allOf(PlayerComponent::class, TransformComponent::class, BodyComponent::class).get()) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        val body = entity.bodyCmp.body
        val transformCmp = entity.transformCmp
        hud.updatePosition(transformCmp.interpolatedPosition)
        hud.updateVelocity(body.linearVelocity)
    }

}
