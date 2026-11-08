package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor
import ktx.ashley.optionalPropertyFor
import ktx.ashley.propertyFor

/**
 * State of a homing lock-on shot (ST-164). It holds fields and [reset] only: the homing math lives in
 * `weapon/LockOnSteering.kt` and the per-step work in `LockOnSteeringSystem` (ST-163 note in `docs/tech/dev-workflow.md`).
 */
class LockOnComponent : Component, Pool.Poolable {
    /** Locked target, or null for a dumb shot that keeps its launch heading. */
    var target: Entity? = null

    /** `BaseInfoComponent.id` of [target] at launch, so a pooled entity reused for another object is not mistaken for it. */
    var targetId: Int = -1

    /** `BaseInfoComponent.id` of the shooter, for the [com.github.pepek42.asteroids.weapon.LOCK_ON_MAX_IN_FLIGHT] limit. */
    var shooterId: Int = -1

    /** Engine burn left on the lock-on shot's own clock (s). At 0 the engine is off and the lock-on shot coasts. */
    var burnLeft: Float = 0f

    /** Damage applied to the first body of another faction the lock-on shot touches. */
    var damage: Float = 0f

    override fun reset() {
        target = null
        targetId = -1
        shooterId = -1
        burnLeft = 0f
        damage = 0f
    }
}

val lockOnMapper = mapperFor<LockOnComponent>()
val Entity.lockOnCmp by propertyFor(lockOnMapper)
val Entity.lockOnCmpOptional by optionalPropertyFor(lockOnMapper)
