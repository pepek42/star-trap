package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.physics.box2d.Body
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.moveCmp
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import ktx.ashley.allOf
import ktx.log.logger
import ktx.math.times
import ktx.math.vec2

class MoveSystem : IteratingSystem(allOf(MoveComponent::class, BodyComponent::class).get()) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        val moveComponent = entity.moveCmp
        val bodyCmp = entity.bodyCmp

        if (!bodyCmp.moveForcesApplied) {
            applyMainThrusters(moveComponent, bodyCmp.body)
            applyTorque(moveComponent, bodyCmp.body)
            bodyCmp.moveForcesApplied = true
        }
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

        // TODO separate components
        private const val MAIN_ENGINE_THRUST = 100f
        private const val ADDITIONAL_ENGINES_TORQUE = 50f
        private const val MAX_ANGULAR_VELOCITY = MathUtils.PI
    }
}
