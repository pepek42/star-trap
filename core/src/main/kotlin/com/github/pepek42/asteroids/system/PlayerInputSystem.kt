package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.Vector3
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.event.PlayerInputListener
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.log.logger

class PlayerInputSystem(
    private val gameEventManager: GameEventManager,
) : IteratingSystem(allOf(PlayerComponent::class, MoveComponent::class).get()), PlayerInputListener {
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
        val moveComponent = entity[MoveComponent.mapper]!!
        moveComponent.thrusters = thrusters
        moveComponent.aimPointX = aimPointX
        moveComponent.aimPointY = aimPointY
    }

    override fun movement(thrusters: Float) {
        this.thrusters = thrusters
    }

    override fun aimPoint(point: Vector3) {
        aimPointX = point.x
        aimPointY = point.y
        logger.debug { "aimPoint $aimPointX - $aimPointY" }
    }

    override fun fire(start: Boolean) {
        // TODO
        logger.debug { "Fire $start" }
    }

    override fun block() {
        // TODO
        logger.debug { "Block" }
    }

    companion object {
        private val logger = logger<PlayerInputSystem>()
    }


}
