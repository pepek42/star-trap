package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.MassData
import com.badlogic.gdx.utils.Array
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.moveMapper
import com.github.pepek42.asteroids.component.relativisticCmp
import com.github.pepek42.asteroids.debug.LoggingUtils.Companion.defaultLoggingUtils
import com.github.pepek42.asteroids.physics.COLLISION_EPS_BETA
import com.github.pepek42.asteroids.physics.GAMMA_MASS_CAP
import com.github.pepek42.asteroids.physics.PhysicsStepListener
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.clampSpeed
import com.github.pepek42.asteroids.physics.clampU
import com.github.pepek42.asteroids.physics.gammaFromU
import com.github.pepek42.asteroids.physics.uFromV
import com.github.pepek42.asteroids.physics.velocityFromU
import ktx.ashley.allOf
import ktx.log.logger
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Integrates the proper velocity u = γv of every [RelativisticComponent] body once per physics step (research §2, E1).
 *
 * It must be the LAST [PhysicsStepListener], so all contributors have already added to [RelativisticComponent.accel].
 * [beforeStep] advances u and writes v = u/γ into Box2D; Box2D then only handles collisions. [afterStep] detects a
 * contact-changed velocity and converts it back to u, then advances proper time.
 *
 * With `RelativitySettings.gammaMassEnabled` the Box2D mass and inertia follow `min(γ, GAMMA_MASS_CAP)·m₀` (ST-047), so
 * contacts conserve `Σ γ m v`, and the readback is `u = γUsed·v` (momentum) instead of `u = γ(v)·v`. The readback
 * then writes `v = u/γ` back to Box2D, so a body is never left above c between steps.
 */
class RelativisticDynamics(
    private val settings: RelativitySettings = RelativitySettings(),
) : EntitySystem(), PhysicsStepListener {
    private var entities: ImmutableArray<Entity> = ImmutableArray(Array())
    private val velocity = Vector2()
    private val bodyVelocity = Vector2()
    private val massData = MassData()

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        entities = engine.getEntitiesFor(allOf(RelativisticComponent::class, BodyComponent::class).get())
    }

    override fun update(deltaTime: Float) {
        // Integration runs per physics step in beforeStep/afterStep.
    }

    override fun beforeStep(dt: Float) {
        val c = settings.c
        for (i in 0 until entities.size()) {
            val entity = entities[i]
            val rel = entity.relativisticCmp
            if (!settings.relativityEnabled) {
                // Undo the spin dilation, afterStep resets gUsed to 1.
                if (!isRotationDriven(entity)) entity.bodyCmp.body.angularVelocity *= rel.gUsed
                rel.gUsed = 1f
                rel.accel.setZero()
                setMassGamma(entity.bodyCmp.body, rel, 1f)
                continue
            }
            if (!isFinite(rel.u) || !isFinite(rel.accel)) {
                defaultLoggingUtils.tryLogging { logger.error { "Non-finite u=${rel.u} accel=${rel.accel}, resetting" } }
                rel.u.setZero()
                rel.accel.setZero()
            }
            rel.u.mulAdd(rel.accel, dt)
            clampU(rel.u, min(rel.gammaMax, settings.gammaMaxMassive), c)
            val gamma = gammaFromU(rel.u.x, rel.u.y, c)
            setMassGamma(entity.bodyCmp.body, rel, if (settings.gammaMassEnabled) min(gamma, GAMMA_MASS_CAP) else 1f)
            velocityFromU(rel.u.x, rel.u.y, c, velocity)
            entity.bodyCmp.body.setLinearVelocity(velocity)
            rel.vSet.set(velocity)
            // Free spin (asteroids) runs on proper time: rescale by the change of 1/γ, which survives collisions.
            if (!isRotationDriven(entity)) {
                val body = entity.bodyCmp.body
                body.angularVelocity = body.angularVelocity * rel.gUsed / gamma
            }
            rel.gUsed = gamma
        }
    }

    /**
     * Sets the Box2D mass and inertia to [target]·rest values, when [target] differs from [RelativisticComponent.massGamma]
     * by more than [MASS_GAMMA_HYSTERESIS] (relative), so it does not call `setMassData` every step. Reuses one [MassData].
     */
    private fun setMassGamma(body: Body, rel: RelativisticComponent, target: Float) {
        if (rel.restMass <= 0f) {
            // Not captured by a provider: the mass has never been changed, so the body still has its rest mass.
            if (target == 1f) return
            rel.captureRestMass(body)
        }
        if (abs(target - rel.massGamma) / rel.massGamma <= MASS_GAMMA_HYSTERESIS) return
        massData.mass = rel.restMass * target
        massData.I = rel.restInertia * target
        massData.center.set(body.localCenter)
        body.massData = massData
        rel.massGamma = target
    }

    /** True when the PD controller owns this body's rotation (non-null `targetAngleRad`). */
    private fun isRotationDriven(entity: Entity) = moveMapper.get(entity)?.targetAngleRad != null

    override fun afterStep(dt: Float) {
        val c = settings.c
        for (i in 0 until entities.size()) {
            val entity = entities[i]
            val rel = entity.relativisticCmp
            // Box2D hands out a shared vector, so copy it before modifying.
            bodyVelocity.set(entity.bodyCmp.body.linearVelocity)
            if (!settings.relativityEnabled) {
                // Keep u in sync with the Newtonian velocity so switching relativity back on is seamless.
                val betaMaxOff = settings.betaMaxMassive
                clampSpeed(bodyVelocity, betaMaxOff * c)
                uFromV(bodyVelocity.x, bodyVelocity.y, c, betaMaxOff, rel.u)
                rel.gUsed = 1f
                rel.tau += dt
                rel.lastAccel.set(rel.accel)
                rel.accel.setZero()
                continue
            }
            // Velocity changes under COLLISION_EPS_BETA * c are dropped on purpose: they are float noise from Box2D's own
            // solver, and feeding them back would make u drift every step. Larger changes are contact or backstop
            // impulses and are converted back into u.
            if (bodyVelocity.dst(rel.vSet) > COLLISION_EPS_BETA * c) {
                val gammaMax = min(rel.gammaMax, settings.gammaMaxMassive)
                val betaMax = sqrt(1f - 1f / (gammaMax * gammaMax))
                clampSpeed(bodyVelocity, betaMax * c)
                if (settings.gammaMassEnabled) {
                    // Box2D conserved Σ M v with M = massGamma·m₀, so v·γUsed is the proper momentum per rest mass.
                    rel.u.set(bodyVelocity).scl(rel.gUsed)
                    clampU(rel.u, gammaMax, c)
                } else {
                    uFromV(bodyVelocity.x, bodyVelocity.y, c, betaMax, rel.u)
                }
                // The Newtonian kick of a heavy body can push a light one past c. u is the truth, so write the
                // physical v = u/γ back now: nothing that reads the Box2D velocity before the next beforeStep
                // (contact sweep, rendering, other systems) may see a speed above c.
                velocityFromU(rel.u.x, rel.u.y, c, velocity)
                entity.bodyCmp.body.setLinearVelocity(velocity)
                rel.vSet.set(velocity)
            }
            rel.tau += dt * rel.gravFactor / rel.gUsed
            rel.lastAccel.set(rel.accel)
            rel.accel.setZero()
        }
    }

    private fun isFinite(v: Vector2) = !v.x.isNaN() && !v.x.isInfinite() && !v.y.isNaN() && !v.y.isInfinite()

    companion object {
        private val logger = logger<RelativisticDynamics>()

        /** Relative change of the mass γ below which the Box2D mass is left alone (ST-047). */
        const val MASS_GAMMA_HYSTERESIS = 0.01f
    }
}
