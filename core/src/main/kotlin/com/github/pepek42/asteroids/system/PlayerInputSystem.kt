package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.moveCmp
import com.github.pepek42.asteroids.component.weaponCmpOptional
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.event.PlayerInputListener
import ktx.ashley.allOf
import ktx.log.logger
import ktx.math.minus
import ktx.math.vec2
import ktx.math.vec3

class PlayerInputSystem(
    private val gameEventManager: GameEventManager,
    private val camera: Camera,
) : IteratingSystem(allOf(PlayerComponent::class, MoveComponent::class, BodyComponent::class).get()),
    PlayerInputListener {
    private var thrusters = 0f
    private var screenAimPoint = Vector2(0f, 0f)
    private var attack = false

    override fun addedToEngine(engine: Engine?) {
        super.addedToEngine(engine)
        gameEventManager.addInputListener(this)
    }

    override fun removedFromEngine(engine: Engine?) {
        gameEventManager.removeInputListener(this)
        super.removedFromEngine(engine)
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val moveCmp = entity.moveCmp
        val body = entity.bodyCmp.body
        moveCmp.thrusters = thrusters

        handleAngle(body, moveCmp)

        entity.weaponCmpOptional?.let { it.doAttack = attack }
    }

    private fun handleAngle(
        body: Body,
        moveCmp: MoveComponent
    ) {
        val targetAngleRad = getTargetAngleRad(body)
        val currentAngleRads = body.angle + MathUtils.PI
        val angleDiff = targetAngleRad - currentAngleRads
        moveCmp.rotationRequired = (angleDiff - MathUtils.PI) % MathUtils.PI2 + MathUtils.PI
        defaultLoggingUtils.tryLogging {
            logger.debug {
                """
                    target angle deg        -> ${targetAngleRad * MathUtils.radiansToDegrees}
                    from angle deg          -> ${currentAngleRads * MathUtils.radiansToDegrees}
                    rotation required deg   -> ${moveCmp.rotationRequired * MathUtils.radiansToDegrees}
                """.trimIndent()
            }
        }
    }

    private fun getTargetAngleRad(body: Body): Float {
        val gameAimPoint = screenAimPoint
            .run { camera.unproject(vec3(x, y)) }
            .run { vec2(x, y) }
        val aimVector = gameAimPoint.minus(body.position)
        return aimVector.angleRad() + MathUtils.PI

    }

    override fun movement(thrusters: Float) {
        this.thrusters = thrusters
    }

    override fun screenAimPoint(screenX: Int, screenY: Int) {
        screenAimPoint.set(screenX.toFloat(), screenY.toFloat())
        defaultLoggingUtils.tryLogging { logger.debug { "screenAimPoint -> $screenAimPoint" } }
    }

    override fun fire(fire: Boolean) {
        this.attack = fire
    }

    override fun block() {
        // TODO
        logger.debug { "Block" }
    }

    companion object {
        private val logger = logger<PlayerInputSystem>()
    }


}
