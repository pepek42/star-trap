package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.physics.box2d.Body
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.bodyMapper
import com.github.pepek42.asteroids.component.moveMapper
import com.github.pepek42.asteroids.debug.PlayScreenLoggingUtils.tryLogging
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.event.PlayerInputListener
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.log.logger
import ktx.math.minus

class PlayerInputSystem(
    private val gameEventManager: GameEventManager,
) : IteratingSystem(allOf(PlayerComponent::class, MoveComponent::class, BodyComponent::class).get()),
    PlayerInputListener {
    private var thrusters = 0f
    private var aimPoint = Vector2(0f, 0f)

    override fun addedToEngine(engine: Engine?) {
        super.addedToEngine(engine)
        gameEventManager.addInputListener(this)
    }

    override fun removedFromEngine(engine: Engine?) {
        gameEventManager.removeInputListener(this)
        super.removedFromEngine(engine)
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val moveCmp = entity[moveMapper]!!
        val body = entity[bodyMapper]!!.body
        moveCmp.thrusters = thrusters

        handleAngle(body, moveCmp)
    }

    private inline fun handleAngle(
        body: Body,
        moveCmp: MoveComponent
    ) {
        val targetAngle = aimPoint.minus(body.position).angleRad()
        val currentAngle = body.angle
        val angleDiff = targetAngle - currentAngle
        moveCmp.rotationNormalised = ((angleDiff - MathUtils.PI) % MathUtils.PI2 + MathUtils.PI) / MathUtils.PI
        tryLogging {
            logger.debug {
                """

                    target angle         -> ${targetAngle * MathUtils.radiansToDegrees}
                    from angle           -> ${currentAngle * MathUtils.radiansToDegrees}
                    rotation normalised  -> ${moveCmp.rotationNormalised}
                """.trimIndent()
            }
        }
    }

    override fun movement(thrusters: Float) {
        this.thrusters = thrusters
    }

    override fun aimPoint(point: Vector3) {
        aimPoint.set(point.x, point.y)
        tryLogging { logger.debug { "aimPoint -> $aimPoint" } }
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
