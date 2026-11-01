package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Pool
import com.github.pepek42.asteroids.physics.GAMMA_MAX_MASSIVE
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

    /** Proper time elapsed along this body's worldline (s). */
    var tau = 0f

    /** γ used for the last step, kept for the proper time update in `afterStep`. */
    var gUsed = 1f

    /** Velocity written into Box2D before the last step, to detect contact-induced changes. */
    val vSet = Vector2()

    /** Lorentz factor cap enforced by clamping [u]. */
    var gammaMax = GAMMA_MAX_MASSIVE

    override fun reset() {
        u.setZero()
        accel.setZero()
        tau = 0f
        gUsed = 1f
        vSet.setZero()
        gammaMax = GAMMA_MAX_MASSIVE
    }
}

val relativisticMapper = mapperFor<RelativisticComponent>()
val Entity.relativisticCmp by propertyFor(relativisticMapper)
val Entity.relativisticCmpOptional by optionalPropertyFor(relativisticMapper)
