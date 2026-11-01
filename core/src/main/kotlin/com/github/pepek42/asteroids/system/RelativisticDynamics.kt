package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Array
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.relativisticCmp
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import com.github.pepek42.asteroids.physics.BETA_MAX_MASSIVE
import com.github.pepek42.asteroids.physics.COLLISION_EPS
import com.github.pepek42.asteroids.physics.PhysicsStepListener
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import com.github.pepek42.asteroids.physics.clampSpeed
import com.github.pepek42.asteroids.physics.clampU
import com.github.pepek42.asteroids.physics.gammaFromU
import com.github.pepek42.asteroids.physics.uFromV
import com.github.pepek42.asteroids.physics.velocityFromU
import ktx.ashley.allOf
import ktx.log.logger

/**
 * Integrates the proper velocity u = γv of every [RelativisticComponent] body once per physics step (research §2, E1).
 *
 * It must be the LAST [PhysicsStepListener], so all contributors have already added to [RelativisticComponent.accel].
 * [beforeStep] advances u and writes v = u/γ into Box2D; Box2D then only handles collisions. [afterStep] detects a
 * contact-changed velocity and converts it back to u, then advances proper time.
 */
class RelativisticDynamics : EntitySystem(), PhysicsStepListener {
    private var entities: ImmutableArray<Entity> = ImmutableArray(Array())
    private val velocity = Vector2()
    private val bodyVelocity = Vector2()

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        entities = engine.getEntitiesFor(allOf(RelativisticComponent::class, BodyComponent::class).get())
    }

    override fun update(deltaTime: Float) {
        // Integration runs per physics step in beforeStep/afterStep.
    }

    override fun beforeStep(dt: Float) {
        for (i in 0 until entities.size()) {
            val entity = entities[i]
            val rel = entity.relativisticCmp
            if (!isFinite(rel.u) || !isFinite(rel.accel)) {
                defaultLoggingUtils.tryLogging { logger.error { "Non-finite u=${rel.u} accel=${rel.accel}, resetting" } }
                rel.u.setZero()
                rel.accel.setZero()
            }
            rel.u.mulAdd(rel.accel, dt)
            clampU(rel.u, rel.gammaMax, SPEED_OF_LIGHT)
            val gamma = gammaFromU(rel.u.x, rel.u.y, SPEED_OF_LIGHT)
            velocityFromU(rel.u.x, rel.u.y, SPEED_OF_LIGHT, velocity)
            entity.bodyCmp.body.setLinearVelocity(velocity)
            rel.vSet.set(velocity)
            rel.gUsed = gamma
        }
    }

    override fun afterStep(dt: Float) {
        for (i in 0 until entities.size()) {
            val entity = entities[i]
            val rel = entity.relativisticCmp
            // Box2D hands out a shared vector, so copy it before modifying.
            bodyVelocity.set(entity.bodyCmp.body.linearVelocity)
            if (bodyVelocity.dst(rel.vSet) > COLLISION_EPS) {
                clampSpeed(bodyVelocity, BETA_MAX_MASSIVE * SPEED_OF_LIGHT)
                uFromV(bodyVelocity.x, bodyVelocity.y, SPEED_OF_LIGHT, BETA_MAX_MASSIVE, rel.u)
            }
            rel.tau += dt / rel.gUsed
            rel.accel.setZero()
        }
    }

    private fun isFinite(v: Vector2) = !v.x.isNaN() && !v.x.isInfinite() && !v.y.isNaN() && !v.y.isInfinite()

    companion object {
        private val logger = logger<RelativisticDynamics>()
    }
}
