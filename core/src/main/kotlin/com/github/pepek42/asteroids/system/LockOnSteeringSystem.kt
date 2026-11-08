package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.LockOnComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.baseInfoMapper
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.bodyCmpOptional
import com.github.pepek42.asteroids.component.lockOnCmp
import com.github.pepek42.asteroids.component.relativisticCmp
import com.github.pepek42.asteroids.component.relativisticCmpOptional
import com.github.pepek42.asteroids.physics.PhysicsStepListener
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.gammaFromU
import com.github.pepek42.asteroids.physics.thrustAccelWorld
import com.github.pepek42.asteroids.physics.velocityFromU
import com.github.pepek42.asteroids.weapon.LOCK_ON_ACCEL_FACTOR
import com.github.pepek42.asteroids.weapon.LOCK_ON_TURN_RATE
import com.github.pepek42.asteroids.weapon.lockOnLeadDirection
import com.github.pepek42.asteroids.weapon.turnHeadingTowards
import ktx.ashley.allOf
import kotlin.math.cos
import kotlin.math.sin

/**
 * Flies every lock-on shot (ST-164) once per physics step: turns its heading toward the lead point at no more than
 * [LOCK_ON_TURN_RATE], burns its engine along the guidance direction while [LockOnComponent.burnLeft] lasts, and then
 * lets it coast. Runs as a [PhysicsStepListener] before [RelativisticDynamics], which integrates the added acceleration.
 *
 * The homing itself is the assist layer: it reads the target's true arena state (physics-lies row 34), like the fire
 * computer. The engine thrust uses the guidance direction rather than the hull heading, so the turn-limited sprite is a
 * visual readout and the lock-on shot can still hit a target that started off-axis. Thrust is a proper acceleration through
 * [thrustAccelWorld], so `u` still never reaches c.
 */
class LockOnSteeringSystem(
    private val settings: RelativitySettings = RelativitySettings(),
) : EntitySystem(), PhysicsStepListener {
    private var lockOnShots: ImmutableArray<Entity>? = null

    // Scratch vectors: beforeStep runs inside world.step and must not allocate (web perf).
    private val desired = Vector2()
    private val heading = Vector2()
    private val velocity = Vector2()
    private val accel = Vector2()

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        lockOnShots = engine.getEntitiesFor(
            allOf(LockOnComponent::class, RelativisticComponent::class, BodyComponent::class).get(),
        )
    }

    override fun update(deltaTime: Float) {
        // Per-step work runs in beforeStep.
    }

    override fun beforeStep(dt: Float) {
        val list = lockOnShots ?: return
        val c = settings.c
        val properAccel = LOCK_ON_ACCEL_FACTOR * settings.engineProperAccel
        for (i in 0 until list.size()) {
            val entity = list[i]
            val shot = entity.lockOnCmp
            val rel = entity.relativisticCmp
            val body = entity.bodyCmp.body

            var hasGuidance = false
            val target = validTarget(shot)
            if (target != null) {
                val targetBody = target.bodyCmpOptional?.body
                if (targetBody != null) {
                    val targetV = target.relativisticCmpOptional?.vSet ?: targetBody.linearVelocity
                    lockOnLeadDirection(
                        targetBody.position.x - body.position.x,
                        targetBody.position.y - body.position.y,
                        targetV.x, targetV.y, body.linearVelocity.len(), desired,
                    )
                    val hx = cos(body.angle)
                    val hy = sin(body.angle)
                    turnHeadingTowards(hx, hy, desired.x, desired.y, LOCK_ON_TURN_RATE * dt, heading)
                    if (heading.dst2(hx, hy) > HEADING_EPSILON_SQ) {
                        body.setTransform(body.position.x, body.position.y, MathUtils.atan2(heading.y, heading.x))
                    }
                    hasGuidance = true
                }
            }

            if (shot.burnLeft <= 0f) continue
            val gamma = gammaFromU(rel.u.x, rel.u.y, c)
            if (settings.relativityEnabled) {
                velocityFromU(rel.u.x, rel.u.y, c, velocity)
                if (hasGuidance) {
                    thrustAccelWorld(properAccel, desired.x, desired.y, velocity.x, velocity.y, gamma, accel)
                } else {
                    thrustAccelWorld(properAccel, cos(body.angle), sin(body.angle), velocity.x, velocity.y, gamma, accel)
                }
                rel.accel.add(accel)
            } else {
                // Newtonian fallback, mirroring MoveSystem: a force of mass · a0 along the guidance direction.
                val nx = if (hasGuidance) desired.x else cos(body.angle)
                val ny = if (hasGuidance) desired.y else sin(body.angle)
                val force = body.mass * properAccel
                body.applyForceToCenter(nx * force, ny * force, true)
            }
            // The engine runs on the lock-on shot's own clock (proper time): dt/γ. It can never burn past zero.
            shot.burnLeft = (shot.burnLeft - dt / gamma).coerceAtLeast(0f)
        }
    }

    override fun afterStep(dt: Float) = Unit

    /**
     * The target while it is still the same entity: [LockOnComponent.targetId] guards against a pooled entity being
     * reused, and the body's `userData` identity guards against a target whose body was already destroyed.
     */
    private fun validTarget(shot: LockOnComponent): Entity? {
        val target = shot.target ?: return null
        val info = baseInfoMapper.get(target) ?: return null
        if (info.id != shot.targetId) return null
        val body = target.bodyCmpOptional?.body ?: return null
        return if (body.userData === target) target else null
    }

    private companion object {
        /** Squared distance between old and new unit heading below which the body is not rotated. */
        const val HEADING_EPSILON_SQ = 1e-8f
    }
}
