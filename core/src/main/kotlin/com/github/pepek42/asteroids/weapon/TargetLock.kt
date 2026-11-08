package com.github.pepek42.asteroids.weapon

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.MathUtils
import com.github.pepek42.asteroids.component.WeaponLoadoutComponent

/** Targeting-computer lock for slot 2 (ST-165). */
enum class TargetLockState { NONE, LOCKING, LOCKED }

/** Half-angle (rad) of the cone around the aim direction in which a new lock can start: ±15°. */
const val TARGET_LOCK_CONE_RAD = 15f * MathUtils.degreesToRadians

/** Half-angle (rad) a LOCKED target may drift to before the lock drops: ±30°, wider than [TARGET_LOCK_CONE_RAD] so it doesn't flicker. */
const val TARGET_LOCK_KEEP_CONE_RAD = 30f * MathUtils.degreesToRadians

/** Lock range as light travel time (s); the range in world units is `TARGET_LOCK_RANGE_LIGHT_SECONDS * c` (60 u at c = 20). */
const val TARGET_LOCK_RANGE_LIGHT_SECONDS = 3f

/** Ship proper time (s) the same candidate must stay in the cone before LOCKING turns into LOCKED. */
const val TARGET_LOCK_TIME_S = 1f

/** Minimum time (s) between two "no lock" clicks while the trigger is held without a lock. */
const val TARGET_LOCK_NO_LOCK_CLICK_INTERVAL_S = 0.4f

/** The LOCKING reticle starts this many times the target ring radius and closes in to the ring over [TARGET_LOCK_TIME_S]. */
const val TARGET_LOCK_RETICLE_START_FACTOR = 3f

/**
 * Off-axis angle (rad, 0..π) of the target at offset ([dx], [dy]) from the shooter, seen along the unit aim direction
 * ([dirX], [dirY]); NaN when the target is farther than [range] or sits on the shooter. Pure and allocation-free.
 */
fun lockOffAxis(dx: Float, dy: Float, dirX: Float, dirY: Float, range: Float): Float {
    val d2 = dx * dx + dy * dy
    if (d2 > range * range || d2 == 0f) return Float.NaN
    val cosA = ((dx * dirX + dy * dirY) / kotlin.math.sqrt(d2)).coerceIn(-1f, 1f)
    return kotlin.math.acos(cosA)
}

/**
 * Advances the lock by [dt] (ship proper seconds). [candidate] is the target nearest the aim inside [TARGET_LOCK_CONE_RAD]
 * and range (null = none), [candidateId] its `BaseInfoComponent.id`; [lockedKept] says the current lock target is still
 * alive, in range and inside [TARGET_LOCK_KEEP_CONE_RAD]. Returns true on the call that reaches LOCKED (play the tone).
 */
fun WeaponLoadoutComponent.advanceLock(candidate: Entity?, candidateId: Int, lockedKept: Boolean, dt: Float): Boolean {
    if (lockState == TargetLockState.LOCKED) {
        if (lockedKept) return false
        clearLock()
    }
    if (candidate == null) {
        clearLock()
        return false
    }
    if (lockState == TargetLockState.NONE || candidate !== lockTarget || candidateId != lockTargetId) {
        lockState = TargetLockState.LOCKING
        lockTarget = candidate
        lockTargetId = candidateId
        lockProgress = 0f
        return false
    }
    lockProgress += dt
    if (lockProgress < TARGET_LOCK_TIME_S) return false
    lockProgress = TARGET_LOCK_TIME_S
    lockState = TargetLockState.LOCKED
    return true
}

/** Drops any lock or lock in progress. */
fun WeaponLoadoutComponent.clearLock() {
    lockState = TargetLockState.NONE
    lockTarget = null
    lockTargetId = -1
    lockProgress = 0f
}

/** The locked target when a lock-on shot may fire, else null: fire is refused while not LOCKED. */
fun WeaponLoadoutComponent.lockedTarget(): Entity? =
    if (lockState == TargetLockState.LOCKED) lockTarget else null

/** LOCKING progress 0..1. */
fun WeaponLoadoutComponent.lockFraction(): Float = (lockProgress / TARGET_LOCK_TIME_S).coerceIn(0f, 1f)

/**
 * Radius of the LOCKING reticle: from [TARGET_LOCK_RETICLE_START_FACTOR] × [ringRadius] at [fraction] 0 down to
 * [ringRadius] at 1.
 */
fun lockReticleRadius(ringRadius: Float, fraction: Float): Float =
    ringRadius * (TARGET_LOCK_RETICLE_START_FACTOR + (1f - TARGET_LOCK_RETICLE_START_FACTOR) * fraction.coerceIn(0f, 1f))

/**
 * Slow ammo regeneration (owner 2026-11-08): one lock-on shot per [LOCK_ON_AMMO_REGEN_SECONDS] of ship proper time, up to
 * [LOCK_ON_AMMO_PER_LEVEL]. The timer only runs while the magazine is not full.
 */
fun WeaponLoadoutComponent.regenLockOnAmmo(dt: Float) {
    if (lockOnAmmo >= LOCK_ON_AMMO_PER_LEVEL) {
        lockOnRegen = 0f
        return
    }
    lockOnRegen += dt
    while (lockOnRegen >= LOCK_ON_AMMO_REGEN_SECONDS && lockOnAmmo < LOCK_ON_AMMO_PER_LEVEL) {
        lockOnRegen -= LOCK_ON_AMMO_REGEN_SECONDS
        lockOnAmmo++
    }
    if (lockOnAmmo >= LOCK_ON_AMMO_PER_LEVEL) lockOnRegen = 0f
}
