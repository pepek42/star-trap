package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.physics.box2d.Body
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.moveCmp
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
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
            rotate(moveComponent, bodyCmp.body)
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

    private fun rotate(moveComponent: MoveComponent, body: Body) {
        val speedRequired = moveComponent.rotationRequired / PHYSICS_UPDATE_INTERVAL
        body.angularVelocity = MathUtils.clamp(speedRequired, -MAX_ROTATION_SPEED, MAX_ROTATION_SPEED)
    }

    // TODO https://physics.stackexchange.com/questions/317615/determining-the-torque-needed-to-rotate-a-spacecraft-to-a-given-rotation-quatern
//    private fun applyTorque(
//        moveComponent: MoveComponent,
//        body: Body
//    ) {
//        val angularVelocity = body.angularVelocity
//        val normalisedAngularVelocity = angularVelocity / MAX_ANGULAR_VELOCITY
//        val torqueNormalised = moveComponent.rotationNormalised - normalisedAngularVelocity
//        defaultLoggingUtils.tryLogging {
//            logger.debug {
//                """
//                    angularVelocity: $angularVelocity
//                    normalisedAngularVelocity: $normalisedAngularVelocity
//                    rotationNormalised: ${moveComponent.rotationNormalised}
//                    torqueNormalised: $torqueNormalised
//                """.trimIndent()
//            }
//        }
//        body.applyTorque(torqueNormalised * ADDITIONAL_ENGINES_TORQUE, true)
//    }

    companion object {
        private val logger = logger<MoveSystem>()

        // TODO separate components
        private const val MAIN_ENGINE_THRUST = 300f
        private const val ADDITIONAL_ENGINES_TORQUE = 100f
        private const val MAX_ROTATION_SPEED = MathUtils.HALF_PI
        private const val MAX_ANGULAR_VELOCITY = MathUtils.PI2
    }
}
