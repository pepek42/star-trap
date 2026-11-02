package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.moveCmp
import com.github.pepek42.asteroids.component.rotationTimeFactor
import com.github.pepek42.asteroids.component.shipHandlingCmpOptional
import com.github.pepek42.asteroids.math.shortestAngleDiffRad
import com.github.pepek42.asteroids.physics.GameClock
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
    private val clock: GameClock? = null,
) : EntitySystem() {
    private val entities = engine.getEntitiesFor(allOf(BodyComponent::class, TransformComponent::class).get())
    private val rotatingEntities = engine.getEntitiesFor(allOf(MoveComponent::class, BodyComponent::class).get())
    private var accumulator = 0f

    init {
        logger.info { "Init finished" }
    }

    private val maxTimeToProcess = MAX_STEPS_PER_FRAME * PHYSICS_UPDATE_INTERVAL

    override fun update(deltaTime: Float) {
        val dt = clock?.worldDt ?: deltaTime
        accumulator += min(maxTimeToProcess, dt)
        // The epsilon absorbs float rounding so N whole intervals of time always yield N steps.
        while (accumulator >= PHYSICS_UPDATE_INTERVAL - STEP_EPSILON) {
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
            clock?.let { it.worldTime += PHYSICS_UPDATE_INTERVAL }
        }

        interpolateTransforms()
    }

    private fun applyRotation() {
        for (entity in rotatingEntities) {
            val targetAngle = entity.moveCmp.targetAngleRad ?: continue
            val body = entity.bodyCmp.body
            val error = shortestAngleDiffRad(body.angle, targetAngle)
            val handling = entity.shipHandlingCmpOptional
            // PD controller on a clock slowed by f: gains scale with f^2 (kp, max accel) and f (kd, max speed).
            val f = entity.rotationTimeFactor(clock)
            val kp = handling?.rotationKp ?: ShipRotationController.ROTATION_KP
            val kd = handling?.rotationKd ?: ShipRotationController.ROTATION_KD
            val maxAccel = handling?.maxAngularAcceleration ?: ShipRotationController.MAX_ANGULAR_ACCELERATION
            val maxSpeed = handling?.maxAngularVelocity ?: ShipRotationController.MAX_ANGULAR_VELOCITY
            val impulse = ShipRotationController.computeAngularImpulse(
                error = error,
                angularVelocity = body.angularVelocity,
                inertia = body.inertia,
                kp = kp * f * f,
                kd = kd * f,
                maxAngularAcceleration = maxAccel * f * f,
                maxAngularVelocity = maxSpeed * f,
            )
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
        val alpha = (accumulator / PHYSICS_UPDATE_INTERVAL).coerceAtLeast(0f)
        clock?.interpolationAlpha = alpha
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
        const val MAX_STEPS_PER_FRAME = 10
        const val MAX_LAG_STEPS = 5
        private const val STEP_EPSILON = 1e-6f
    }

}

