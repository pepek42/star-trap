package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.event.PlayerInputListener
import ktx.ashley.allOf
import ktx.ashley.get

class PlayerInputSystem(
    private val gameEventManager: GameEventManager,
) : IteratingSystem(allOf(PlayerComponent::class).get()), PlayerInputListener {
    private var thrusters = 0f
    private var aimPointX = 0f
    private var aimPointY = 0f

    override fun addedToEngine(engine: Engine?) {
        super.addedToEngine(engine)
        gameEventManager.addInputListener(this)
    }

    override fun removedFromEngine(engine: Engine?) {
        gameEventManager.removeInputListener(this)
        super.removedFromEngine(engine)
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val playerComponent = entity[PlayerComponent.mapper]!!
        // TODO pass to move component
        playerComponent.thrusters = thrusters
        playerComponent.aimPointX = aimPointX
        playerComponent.aimPointY = aimPointY
    }

    override fun movement(thrusters: Float) {
        this.thrusters = thrusters
    }

    override fun aimPoint(x: Float, y: Float) {
        aimPointX = x
        aimPointY = y
    }

    override fun fire(start: Boolean) {
        // TODO
    }

    override fun block() {
        // TODO
    }


}
