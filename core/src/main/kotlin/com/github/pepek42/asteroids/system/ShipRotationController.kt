package com.github.pepek42.asteroids.system

import com.badlogic.gdx.math.MathUtils
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import kotlin.math.sqrt

/** PD rotation controller. Evaluated once per physics step by [PhysicsSystem], applied as an angular impulse. */
object ShipRotationController {
    /** Proportional gain (1/s^2). */
    const val ROTATION_KP = 80f

    /** Derivative gain, 2*sqrt(kp) gives critical damping (1/s). */
    val ROTATION_KD = 2f * sqrt(ROTATION_KP)

    /** Max angular velocity (rad/s). Must stay below Box2D's cap of 0.5*PI rad per step. */
    const val MAX_ANGULAR_VELOCITY = MathUtils.PI2

    /** Max angular acceleration (rad/s^2), so max torque = inertia * this. */
    const val MAX_ANGULAR_ACCELERATION = 200f

    /**
     * Angular impulse for one step of length [dt]: I * a * dt where a = kp * error - kd * w, limited to
     * [MAX_ANGULAR_ACCELERATION] and such that the angular velocity after the step stays within
     * [MAX_ANGULAR_VELOCITY].
     */
    fun computeAngularImpulse(
        error: Float,
        angularVelocity: Float,
        inertia: Float,
        dt: Float = PHYSICS_UPDATE_INTERVAL,
        kp: Float = ROTATION_KP,
        kd: Float = ROTATION_KD,
        maxAngularAcceleration: Float = MAX_ANGULAR_ACCELERATION,
        maxAngularVelocity: Float = MAX_ANGULAR_VELOCITY,
    ): Float {
        var acceleration = kp * error - kd * angularVelocity
        acceleration = MathUtils.clamp(acceleration, -maxAngularAcceleration, maxAngularAcceleration)
        val nextVelocity = MathUtils.clamp(
            angularVelocity + acceleration * dt,
            -maxAngularVelocity,
            maxAngularVelocity
        )
        return inertia * (nextVelocity - angularVelocity)
    }
}
