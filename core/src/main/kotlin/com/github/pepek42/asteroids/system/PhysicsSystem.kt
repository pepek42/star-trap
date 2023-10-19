package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import ktx.ashley.allOf
import ktx.log.logger
import kotlin.math.min


class PhysicsSystem(
    private val world: World,
    engine: Engine,
) : EntitySystem() {
    private val entities = engine.getEntitiesFor(allOf(BodyComponent::class, TransformComponent::class).get())
    private var accumulator = 0f

    init {
        logger.info { "Init finished" }
    }

    private val maxTimeToProcess = 5 * PHYSICS_UPDATE_INTERVAL

    override fun update(deltaTime: Float) {
        accumulator += min(maxTimeToProcess, deltaTime)
        var runSimulation = false
        while (accumulator >= PHYSICS_UPDATE_INTERVAL) {
            preProcess()
            world.step(PHYSICS_UPDATE_INTERVAL, 6, 2)
            accumulator -= PHYSICS_UPDATE_INTERVAL
            runSimulation = true
        }
        if (runSimulation) {
            world.clearForces()
        }

        interpolateTransforms()
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
                moveForcesApplied = false
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
        private const val PHYSICS_UPDATE_INTERVAL = 1f / 45
    }

}

