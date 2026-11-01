package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.utils.Pool
import com.github.pepek42.asteroids.system.ShipRotationController
import ktx.ashley.mapperFor
import ktx.ashley.optionalPropertyFor
import ktx.ashley.propertyFor

/** Per-ship rotation tuning for the PD controller in [ShipRotationController]. Defaults match the player ship. */
class ShipHandlingComponent : Component, Pool.Poolable {
    /** Proportional gain (1/s^2). */
    var rotationKp = ShipRotationController.ROTATION_KP

    /** Derivative gain (1/s). */
    var rotationKd = ShipRotationController.ROTATION_KD

    /** Max angular acceleration (rad/s^2). */
    var maxAngularAcceleration = ShipRotationController.MAX_ANGULAR_ACCELERATION

    /** Max angular velocity (rad/s). Must stay below Box2D's cap of 0.5*PI rad per step. */
    var maxAngularVelocity = ShipRotationController.MAX_ANGULAR_VELOCITY

    override fun reset() {
        rotationKp = ShipRotationController.ROTATION_KP
        rotationKd = ShipRotationController.ROTATION_KD
        maxAngularAcceleration = ShipRotationController.MAX_ANGULAR_ACCELERATION
        maxAngularVelocity = ShipRotationController.MAX_ANGULAR_VELOCITY
    }
}

val shipHandlingMapper = mapperFor<ShipHandlingComponent>()
val Entity.shipHandlingCmp by propertyFor(shipHandlingMapper)
val Entity.shipHandlingCmpOptional by optionalPropertyFor(shipHandlingMapper)
