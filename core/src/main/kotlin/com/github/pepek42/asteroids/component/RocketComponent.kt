package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor
import ktx.ashley.optionalPropertyFor
import ktx.ashley.propertyFor

/**
 * State of an unguided pod rocket (ST-181). Fields and [reset] only; the motor is `RocketMotorSystem`, the pod and its
 * constants are `weapon/RocketConstants.kt` and `weapon/RocketPod.kt`. Scalar fields only (web reflection rule).
 */
class RocketComponent : Component, Pool.Poolable {
    /** Motor burn left on the rocket's own clock (s). At 0 the motor is off and the rocket coasts. */
    var burnLeft: Float = 0f

    /** Fixed launch heading as a unit vector in the arena frame; the motor thrusts along it for the whole burn. */
    var headingX: Float = 1f
    var headingY: Float = 0f

    /** Damage applied to the first body of another faction the rocket touches. */
    var damage: Float = 0f

    /** `BaseInfoComponent.id` of the shooter. */
    var shooterId: Int = -1

    override fun reset() {
        burnLeft = 0f
        headingX = 1f
        headingY = 0f
        damage = 0f
        shooterId = -1
    }
}

val rocketMapper = mapperFor<RocketComponent>()
val Entity.rocketCmp by propertyFor(rocketMapper)
val Entity.rocketCmpOptional by optionalPropertyFor(rocketMapper)
