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

    private inline fun handleAngle(
        body: Body,
        moveCmp: MoveComponent
    ) {
        val targetAngle = screenAimPoint
            .run { camera.unproject(vec3(x, y)) } // screen to game coordinates
            .run { vec2(x, y) } // aim point in game coordinates
            .minus(body.position) // vector from player position to aim point
            .angleRad() // angle between x-axis and the aim vector
        val currentAngle = body.angle
        val angleDiff = targetAngle - currentAngle
        moveCmp.rotationNormalised = ((angleDiff - MathUtils.PI) % MathUtils.PI2 + MathUtils.PI) / MathUtils.PI
        defaultLoggingUtils.tryLogging {
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
