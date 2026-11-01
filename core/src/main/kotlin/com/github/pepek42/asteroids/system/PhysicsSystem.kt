package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.moveCmp
import com.github.pepek42.asteroids.component.shipHandlingCmpOptional
import com.github.pepek42.asteroids.math.shortestAngleDiffRad
import com.github.pepek42.asteroids.physics.PhysicsStepListener
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import ktx.ashley.allOf
import ktx.log.logger
import kotlin.math.min


class PhysicsSystem(
    private val world: World,
    engine: Engine,
    private val stepListeners: List<PhysicsStepListener> = emptyList(),
) : EntitySystem() {
    private val entities = engine.getEntitiesFor(allOf(BodyComponent::class, TransformComponent::class).get())
    private val rotatingEntities = engine.getEntitiesFor(allOf(MoveComponent::class, BodyComponent::class).get())
    private var accumulator = 0f

    init {
        logger.info { "Init finished" }
    }

    private val maxTimeToProcess = 5 * PHYSICS_UPDATE_INTERVAL

    override fun update(deltaTime: Float) {
        accumulator += min(maxTimeToProcess, deltaTime)
        while (accumulator >= PHYSICS_UPDATE_INTERVAL) {
            preProcess()
            val subDt = PHYSICS_UPDATE_INTERVAL / PHYSICS_SUBSTEPS
            repeat(PHYSICS_SUBSTEPS) {
                stepListeners.forEach { it.beforeStep(subDt) }
                applyRotation()
                world.step(subDt, 6, 2)
                stepListeners.forEach { it.afterStep(subDt) }
                world.clearForces()
            }
            accumulator -= PHYSICS_UPDATE_INTERVAL
        }

        interpolateTransforms()
    }

    private fun applyRotation() {
        for (entity in rotatingEntities) {
            val targetAngle = entity.moveCmp.targetAngleRad ?: continue
            val body = entity.bodyCmp.body
            val error = shortestAngleDiffRad(body.angle, targetAngle)
            val handling = entity.shipHandlingCmpOptional
            val impulse = if (handling == null) {
                ShipRotationController.computeAngularImpulse(
                    error = error,
                    angularVelocity = body.angularVelocity,
                    inertia = body.inertia,
                )
            } else {
                ShipRotationController.computeAngularImpulse(
                    error = error,
                    angularVelocity = body.angularVelocity,
                    inertia = body.inertia,
                    kp = handling.rotationKp,
                    kd = handling.rotationKd,
                    maxAngularAcceleration = handling.maxAngularAcceleration,
                    maxAngularVelocity = handling.maxAngularVelocity,
                )
            }
            body.applyAngularImpulse(impulse, true)
        }
    }

    private fun preProcess() {
        entities.forEach { entity ->
            entity.bodyCmp.run {
                val transformCmp: TransformComponent = entity.transformCmp
                defaultLoggingUtils.tryLogging {
                    logger.debug {
                        """
                        position: ${body.position}
                        angle: ${body.angle}
                        linearVelocity: ${body.linearVelocity}
                        angularVelocity: ${body.angularVelocity}
                    """.trimIndent()
                    }
                }
                transformCmp.prevPosition.set(
                    body.position.x,
                    body.position.y,
                )
                transformCmp.prevRotationDeg = body.angle * MathUtils.radiansToDegrees
            }
        }
    }

    private fun interpolateTransforms() {
        val alpha = accumulator / PHYSICS_UPDATE_INTERVAL
        for (entity in entities) {
            val body = entity.bodyCmp.body
            val transform = entity.transformCmp

            val rotationDeg = body.angle * MathUtils.radiansToDegrees

            transform.interpolatedPosition.x = MathUtils.lerp(transform.prevPosition.x, body.position.x, alpha)
            transform.interpolatedPosition.y = MathUtils.lerp(transform.prevPosition.y, body.position.y, alpha)
            transform.interpolatedRotationDeg = MathUtils.lerp(transform.prevRotationDeg, rotationDeg, alpha)
        }
    }

    companion object {
        private val logger = logger<PhysicsSystem>()
        const val PHYSICS_UPDATE_INTERVAL = 1f / 45
        const val PHYSICS_SUBSTEPS = 1
    }

}

