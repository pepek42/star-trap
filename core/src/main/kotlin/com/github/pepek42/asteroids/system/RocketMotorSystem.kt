package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.RocketComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.relativisticCmp
import com.github.pepek42.asteroids.component.rocketCmp
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.physics.PhysicsStepListener
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.gammaFromU
import com.github.pepek42.asteroids.physics.thrustAccelWorld
import com.github.pepek42.asteroids.physics.velocityFromU
import com.github.pepek42.asteroids.weapon.ROCKET_ACCEL_FACTOR
import ktx.ashley.allOf

/**
 * Burns the motor of every pod rocket (ST-181) once per physics step: a constant proper acceleration
 * `ROCKET_ACCEL_FACTOR · settings.engineProperAccel` along the fixed launch heading while [RocketComponent.burnLeft] lasts,
 * then nothing (coast). A [PhysicsStepListener] before [RelativisticDynamics], which integrates the added acceleration, so
 * `u` never reaches c. The burn ticks on the rocket's own clock (`dt/γ`). No steering, no target: rockets are unguided.
 * No fuel or reaction mass is modelled (physics-lies row 49).
 */
class RocketMotorSystem(
    private val settings: RelativitySettings = RelativitySettings(),
    private val clock: GameClock? = null,
) : EntitySystem(), PhysicsStepListener {
    private var rockets: ImmutableArray<Entity>? = null

    // Scratch vectors: beforeStep runs inside world.step and must not allocate (web perf).
    private val velocity = Vector2()
    private val accel = Vector2()

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        rockets = engine.getEntitiesFor(
            allOf(RocketComponent::class, RelativisticComponent::class, BodyComponent::class).get(),
        )
    }

    override fun update(deltaTime: Float) {
        // Per-step work runs in beforeStep.
    }

    override fun beforeStep(dt: Float) {
        val list = rockets ?: return
        val c = settings.c
        val properAccel = ROCKET_ACCEL_FACTOR * settings.engineProperAccel
        for (i in 0 until list.size()) {
            val entity = list[i]
            val rocket = entity.rocketCmp
            if (rocket.burnLeft <= 0f) continue
            val rel = entity.relativisticCmp
            val gamma = gammaFromU(rel.u.x, rel.u.y, c)
            // The motor runs on the rocket's own clock (proper time): this step burns dt/γ. On the last step only the
            // remaining burnLeft is burnt, so the thrust is scaled by min(1, burnLeft·γ/dt) and the burn is exact.
            val fraction = minOf(1f, rocket.burnLeft * gamma / dt)
            if (settings.relativityEnabled) {
                velocityFromU(rel.u.x, rel.u.y, c, velocity)
                thrustAccelWorld(properAccel * fraction, rocket.headingX, rocket.headingY, velocity.x, velocity.y, gamma, accel)
                rel.accel.add(accel)
            } else {
                // Newtonian fallback, mirroring MoveSystem: a force of mass · a0 along the heading.
                val force = entity.bodyCmp.body.mass * properAccel * fraction
                entity.bodyCmp.body.applyForceToCenter(rocket.headingX * force, rocket.headingY * force, true)
            }
            rocket.burnLeft = (rocket.burnLeft - dt / gamma).coerceAtLeast(0f)
            // Arena time of burnout: the end of the current tick (worldTime advances after the substeps).
            if (rocket.burnLeft <= 0f) rocket.burnEndTime = (clock?.worldTime ?: 0f) + PHYSICS_UPDATE_INTERVAL
        }
    }

    override fun afterStep(dt: Float) = Unit
}
