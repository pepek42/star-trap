package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.ShipHandlingComponent
import com.github.pepek42.asteroids.component.shipHandlingCmpOptional
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.moveCmp
import com.github.pepek42.asteroids.component.relativisticCmpOptional
import com.github.pepek42.asteroids.physics.PhysicsStepListener
import com.github.pepek42.asteroids.physics.REVERSE_THRUST_FRACTION
import com.github.pepek42.asteroids.physics.brakeBoostFactor
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.gammaFromU
import com.github.pepek42.asteroids.physics.thrustAccelWorld
import com.github.pepek42.asteroids.physics.velocityFromU
import ktx.ashley.allOf
import ktx.math.times
import ktx.math.vec2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin

class MoveSystem(
    private val settings: RelativitySettings = RelativitySettings(),
) : EntitySystem(), PhysicsStepListener {
    private var entities: ImmutableArray<Entity>? = null
    private val velocity = Vector2()
    private val thrustAccel = Vector2()

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        entities = engine.getEntitiesFor(allOf(MoveComponent::class, BodyComponent::class).get())
    }

    override fun update(deltaTime: Float) {
        // Thrust is applied per physics step in beforeStep.
    }

    override fun beforeStep(dt: Float) {
        val list = entities ?: return
        for (entity in list) {
            val relativistic = entity.relativisticCmpOptional
            if (relativistic != null && settings.relativityEnabled) {
                addProperAcceleration(entity.moveCmp, entity.bodyCmp.body, relativistic, entity.shipHandlingCmpOptional)
            } else {
                applyMainThrusters(entity.moveCmp, entity.bodyCmp.body, entity.shipHandlingCmpOptional?.engineFactor ?: 1f)
            }
        }
    }

    override fun afterStep(dt: Float) {
    }

    /** Relativistic ships feel a constant proper acceleration. RelativisticDynamics integrates the sum into u. */
    private fun addProperAcceleration(
        moveComponent: MoveComponent, body: Body, rel: RelativisticComponent, handling: ShipHandlingComponent?,
    ) {
        val thrusters = moveComponent.thrusters
        if (thrusters == 0f) return
        val engineFactor = handling?.engineFactor ?: 1f
        val thr = if (thrusters > 0f) thrusters else thrusters * REVERSE_THRUST_FRACTION
        val angle = body.angle
        velocityFromU(rel.u.x, rel.u.y, settings.c, velocity)
        val gamma = gammaFromU(rel.u.x, rel.u.y, settings.c)
        val s = sign(thr)
        val boost = brakeBoostFactor(s * cos(angle), s * sin(angle), velocity.x, velocity.y, settings.brakeBoost)
        thrustAccelWorld(
            settings.engineProperAccel * engineFactor * thr * boost, cos(angle), sin(angle), velocity.x, velocity.y, gamma, thrustAccel,
        )
        rel.accel.add(thrustAccel)
    }

    private fun applyMainThrusters(
        moveComponent: MoveComponent,
        body: Body,
        engineFactor: Float,
    ) {
        if (moveComponent.thrusters > 0) {
            val angle = body.angle
            val mainThrusters = vec2(MAIN_ENGINE_THRUST, 0f).rotateRad(angle) * (moveComponent.thrusters * engineFactor)
            body.applyForceToCenter(mainThrusters, true)
        }
    }

    companion object {
        // TODO separate components
        const val MAIN_ENGINE_THRUST = 300f
    }
}
