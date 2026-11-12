package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.utils.Pool
import com.github.pepek42.asteroids.weapon.LOCK_ON_AMMO_PER_LEVEL
import com.github.pepek42.asteroids.weapon.ROCKET_POD_SIZE
import com.github.pepek42.asteroids.weapon.TargetLockState
import com.github.pepek42.asteroids.weapon.WeaponType
import ktx.ashley.mapperFor
import ktx.ashley.optionalPropertyFor

/** Number of weapon slots, bound to the keys 1..3 (ST-163). */
const val WEAPON_SLOT_COUNT = 3

/**
 * The player's weapon slots (ST-163). The one [WeaponComponent] stays what `WeaponSystem` reads: [select] copies the
 * slot's type into it and swaps the cooldown, so every slot keeps its own cooldown. The laser still uses the shared
 * [WeaponComponent] parameters; the lock-on shot has its own rate and [lockOnAmmo] (ST-164).
 *
 * Web: no array fields. A component with an array-typed field lost its constructor from the TeaVM reflection
 * metadata ("missing no-arg constructor", 2026-11-07), so the three slots are plain scalar fields.
 */
class WeaponLoadoutComponent : Component, Pool.Poolable {
    var type0: WeaponType? = WeaponType.LASER
    var type1: WeaponType? = WeaponType.LOCK_ON
    var type2: WeaponType? = WeaponType.ROCKET
    var cooldown0 = 0f
    var cooldown1 = 0f
    var cooldown2 = 0f
    var activeSlot: Int = 0

    /** Lock-on shots left in slot 2 (ST-164). A new level spawns a new player entity, so [reset] refills it. */
    var lockOnAmmo: Int = LOCK_ON_AMMO_PER_LEVEL

    /** Proper seconds collected toward the next regenerated lock-on shot (`regenLockOnAmmo`). */
    var lockOnRegen: Float = 0f

    /**
     * Rocket pod (ST-181, slot 3), all on ship proper time: rockets left, seconds until the next may leave, and the reload
     * left while the pod is empty. Logic in `weapon/RocketPod.kt`. Scalar fields only, like the rest (web rule).
     */
    var rocketsInPod: Int = ROCKET_POD_SIZE
    var rocketSalvoCooldown: Float = 0f
    var rocketReloadLeft: Float = 0f

    /** Targeting-computer lock (ST-165); logic in `weapon/TargetLock.kt`. Uses the true target state. */
    var lockState: TargetLockState = TargetLockState.NONE

    /** Entity being locked or locked; checked against [lockTargetId] because entities are pooled. */
    var lockTarget: Entity? = null
    var lockTargetId: Int = -1

    /** Proper seconds the candidate has been held while LOCKING. */
    var lockProgress: Float = 0f

    override fun reset() {
        type0 = WeaponType.LASER
        type1 = WeaponType.LOCK_ON
        type2 = WeaponType.ROCKET
        cooldown0 = 0f
        cooldown1 = 0f
        cooldown2 = 0f
        activeSlot = 0
        lockOnAmmo = LOCK_ON_AMMO_PER_LEVEL
        lockOnRegen = 0f
        rocketsInPod = ROCKET_POD_SIZE
        rocketSalvoCooldown = 0f
        rocketReloadLeft = 0f
        lockState = TargetLockState.NONE
        lockTarget = null
        lockTargetId = -1
        lockProgress = 0f
    }
}

/** Weapon in [slot] (0-based), null = empty. */
fun WeaponLoadoutComponent.slot(slot: Int): WeaponType? = when (slot) {
    0 -> type0
    1 -> type1
    2 -> type2
    else -> null
}

/** Switches to [slot] (0-based); empty or invalid slots do nothing. Returns true when the active slot changed. */
fun WeaponLoadoutComponent.select(slot: Int, weapon: WeaponComponent): Boolean {
    if (slot !in 0 until WEAPON_SLOT_COUNT || slot == activeSlot) return false
    val type = slot(slot) ?: return false
    setCooldown(activeSlot, weapon.cooldown)
    activeSlot = slot
    weapon.weaponType = type
    weapon.cooldown = cooldown(slot)
    return true
}

/** Ticks the cooldowns of the inactive slots by [dt] (proper seconds). */
fun WeaponLoadoutComponent.tickInactive(dt: Float) {
    for (i in 0 until WEAPON_SLOT_COUNT) if (i != activeSlot) setCooldown(i, cooldown(i) - dt)
}

private fun WeaponLoadoutComponent.cooldown(slot: Int): Float = when (slot) {
    0 -> cooldown0
    1 -> cooldown1
    else -> cooldown2
}

private fun WeaponLoadoutComponent.setCooldown(slot: Int, value: Float) {
    when (slot) {
        0 -> cooldown0 = value
        1 -> cooldown1 = value
        else -> cooldown2 = value
    }
}


val weaponLoadoutMapper = mapperFor<WeaponLoadoutComponent>()
val Entity.weaponLoadoutCmpOptional by optionalPropertyFor(weaponLoadoutMapper)
