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

    /** Arena time (`GameClock.worldTime`) the rocket was launched (s). */
    var launchTime: Float = 0f

    /** Arena time the motor burned out (s): set when [burnLeft] reaches 0, `+∞` while it burns. Drives the pilot-view plume. */
    var burnEndTime: Float = Float.POSITIVE_INFINITY

    override fun reset() {
        launchTime = 0f
        burnEndTime = Float.POSITIVE_INFINITY
        burnLeft = 0f
        headingX = 1f
        headingY = 0f
        damage = 0f
        shooterId = -1
    }
}

/**
 * True when a rocket's plume shows in light emitted at [emitTime] (arena time): from [launchTime] up to [burnEndTime]
 * (`+∞` while burning). The pilot view passes `now - sourceLag`, the outside view `now` (ST-197; ST-187 reuses it).
 */
fun plumeVisible(emitTime: Float, launchTime: Float, burnEndTime: Float): Boolean =
    emitTime >= launchTime && emitTime < burnEndTime

val rocketMapper = mapperFor<RocketComponent>()
val Entity.rocketCmp by propertyFor(rocketMapper)
val Entity.rocketCmpOptional by optionalPropertyFor(rocketMapper)
