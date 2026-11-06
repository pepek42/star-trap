package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.utils.Pool
import com.github.pepek42.asteroids.physics.GAMMA_MAX_MASSIVE
import com.github.pepek42.asteroids.physics.GameClock
import ktx.ashley.mapperFor
import ktx.ashley.optionalPropertyFor
import ktx.ashley.propertyFor

/**
 * State of a massive body integrated by [com.github.pepek42.asteroids.system.RelativisticDynamics]. The body's Box2D
 * velocity is derived from [u] every step: v = u / γ.
 */
class RelativisticComponent : Component, Pool.Poolable {
    /** Proper velocity γv in world coordinates (u/s). */
    val u = Vector2()

    /** Proper acceleration (u/s²) added by step listeners during `beforeStep`. Cleared after every step. */
    val accel = Vector2()

    /** [accel] of the last finished step, kept for the debug vector overlay. Zero when relativity is off. */
    val lastAccel = Vector2()

    /** Proper time elapsed along this body's worldline (s). */
    var tau = 0f

    /** γ used for the last step, kept for the proper time update in `afterStep`. */
    var gUsed = 1f

    /**
     * Gravitational clock rate `prod sqrt(1 - rs/r)` over all gravity sources (ST-086, research E15), set by `GravitySystem`
     * every step. 1 without gravity. The product over several sources is an APPROXIMATION (the potentials add, the
     * exact factor would be `sqrt(1 - sum rs/r)`).
     */
    var gravFactor = 1f

    /** Velocity written into Box2D before the last step, to detect contact-induced changes. */
    val vSet = Vector2()

    /** Lorentz factor cap enforced by clamping [u]. */
    var gammaMax = GAMMA_MAX_MASSIVE

    /** Rest mass of the Box2D body (kg), captured by the providers; 0 until captured (the dynamics then captures it). */
    var restMass = 0f

    /** Rest rotational inertia of the Box2D body (about its centre of mass); see [restMass]. */
    var restInertia = 0f

    /** The γ currently baked into the Box2D mass and inertia (ST-047): 1 = rest mass, at most `GAMMA_MASS_CAP`. */
    var massGamma = 1f

    /** Stores the body's current mass and inertia as the rest values. Call once, while the body still has its rest mass. */
    fun captureRestMass(body: Body) {
        restMass = body.mass
        restInertia = body.inertia
        massGamma = 1f
    }

    override fun reset() {
        u.setZero()
        accel.setZero()
        lastAccel.setZero()
        tau = 0f
        gUsed = 1f
        gravFactor = 1f
        vSet.setZero()
        gammaMax = GAMMA_MAX_MASSIVE
        restMass = 0f
        restInertia = 0f
        massGamma = 1f
    }
}

val relativisticMapper = mapperFor<RelativisticComponent>()
val Entity.relativisticCmp by propertyFor(relativisticMapper)
val Entity.relativisticCmpOptional by optionalPropertyFor(relativisticMapper)

/**
 * Time that passed on this entity's own clock during a frame of world time (ST-040, research E2).
 *
 * The player keeps the pilot's clock, so it gets the real frame time (exact while the warp is uncapped, FAKE past the
 * cap: the pilot's clock never slows). Other relativistic bodies run on proper time `worldDt · gravFactor / γ` (ST-086). Everything else
 * runs on world time. Without a [clock] all three fall back to [frameDt].
 */
fun Entity.properDelta(clock: GameClock?, frameDt: Float): Float {
    val realDt = clock?.realDt ?: frameDt
    val worldDt = clock?.worldDt ?: frameDt
    if (playerCmpOptional != null) return realDt
    val rel = relativisticCmpOptional ?: return worldDt
    return worldDt * rel.gravFactor / rel.gUsed
}

/**
 * Factor `f` by which the rotation controller's clock is slowed relative to world time: `1 / warp` for the player (a
 * turn takes the same real time at any warp), `gravFactor / γ` for other relativistic bodies, 1 otherwise or with no [clock].
 */
fun Entity.rotationTimeFactor(clock: GameClock?): Float {
    if (clock == null) return 1f
    if (playerCmpOptional != null) return 1f / clock.warp
    val rel = relativisticCmpOptional ?: return 1f
    return rel.gravFactor / rel.gUsed
}
