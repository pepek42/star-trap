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

class HudSystem(
    private val hud: Hud,
) : IteratingSystem(allOf(PlayerComponent::class, TransformComponent::class, BodyComponent::class).get()) {

    override fun update(deltaTime: Float) {
        super.update(deltaTime)
        hud.updateAndRender(deltaTime)
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val body = entity.bodyCmp.body
        val transformCmp = entity.transformCmp
        hud.updatePosition(transformCmp.interpolatedPosition)
        hud.updateVelocity(body.linearVelocity)
    }

}
