package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.physics.box2d.Body
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.bodyMapper
import com.github.pepek42.asteroids.component.moveMapper
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.log.logger
import ktx.math.times
import ktx.math.vec2

class MoveSystem : IteratingSystem(allOf(MoveComponent::class, BodyComponent::class).get()) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        val moveComponent = entity[moveMapper]!!
        val body = entity[bodyMapper]!!.body

        applyMainThrusters(moveComponent, body)
        applyTorque(moveComponent, body)
    }

    private fun applyMainThrusters(
        moveComponent: MoveComponent,
        body: Body
    ) {
        if (moveComponent.thrusters > 0) {
            val angle = body.angle
            val mainThrusters = vec2(MAIN_ENGINE_THRUST, 0f).rotateRad(angle) * moveComponent.thrusters
            body.applyForceToCenter(mainThrusters, true)
        }
    }

    private fun applyTorque(
        moveComponent: MoveComponent,
        body: Body
    ) {
        val angularVelocity = body.angularVelocity
        val normalisedAngularVelocity = angularVelocity / MAX_ANGULAR_VELOCITY
        val torqueNormalised = moveComponent.rotationNormalised - normalisedAngularVelocity
        defaultLoggingUtils.tryLogging {
            logger.debug {
                """
                    angularVelocity: $angularVelocity
                    normalisedAngularVelocity: $normalisedAngularVelocity
                    rotationNormalised: ${moveComponent.rotationNormalised}
                    torqueNormalised: $torqueNormalised
                """.trimIndent()
            }
        }
        body.applyTorque(torqueNormalised * ADDITIONAL_ENGINES_TORQUE, true)
    }

    companion object {
        private val logger = logger<MoveSystem>()
        private const val MAIN_ENGINE_THRUST = 10f
        private const val ADDITIONAL_ENGINES_TORQUE = 5f
        private const val MAX_ANGULAR_VELOCITY = MathUtils.HALF_PI
    }
}
