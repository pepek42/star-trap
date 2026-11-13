package com.github.pepek42.asteroids.weapon

/**
 * Weapon unlock progression (ST-183). A run starts with the laser; after [FIRST_PICK_LEVEL] the player picks one of
 * [FIRST_PICK_OPTIONS]; after [REMAINING_WEAPON_LEVEL] the other one is given. A quick start and scenarios have all.
 * Pure functions so they run without a GL context.
 */

/** Fixed slot order (keys 1..3): slot i holds `WEAPON_SLOT_ORDER[i]` when unlocked, else it is empty. */
val WEAPON_SLOT_ORDER: List<WeaponType> = listOf(WeaponType.LASER, WeaponType.ROCKET, WeaponType.LOCK_ON)

/** Every weapon. */
val ALL_WEAPONS: Set<WeaponType> = WEAPON_SLOT_ORDER.toSet()

/** What a new run starts with. */
val STARTING_WEAPONS: Set<WeaponType> = setOf(WeaponType.LASER)

/** The level whose clear offers the pick. */
const val FIRST_PICK_LEVEL = 1

/** The level whose clear gives the weapon that was not picked. */
const val REMAINING_WEAPON_LEVEL = 2

/** Weapons offered by the pick, in button order. */
val FIRST_PICK_OPTIONS: List<WeaponType> = listOf(WeaponType.ROCKET, WeaponType.LOCK_ON)

/** Slot index (0-based) of [type] in the fixed order. */
fun weaponSlotOf(type: WeaponType): Int = WEAPON_SLOT_ORDER.indexOf(type)

/** The weapon [unlocked] misses once the pick is made (a set of exactly 2), else null. */
fun remainingWeapon(unlocked: Set<WeaponType>): WeaponType? =
    if (unlocked.size == ALL_WEAPONS.size - 1) FIRST_PICK_OPTIONS.firstOrNull { it !in unlocked } else null

/**
 * What a player on [level] of a normal run would have (the balance runner, which has no pick): the laser, rockets
 * once the first pick is behind them and everything after [REMAINING_WEAPON_LEVEL].
 */
fun weaponsAtLevel(level: Int): Set<WeaponType> = when {
    level > REMAINING_WEAPON_LEVEL -> ALL_WEAPONS
    level > FIRST_PICK_LEVEL -> setOf(WeaponType.LASER, FIRST_PICK_OPTIONS.first())
    else -> STARTING_WEAPONS
}

/** Save format: enum names joined by commas. */
fun encodeWeapons(unlocked: Set<WeaponType>): String =
    WEAPON_SLOT_ORDER.filter { it in unlocked }.joinToString(",") { it.name }

/** Inverse of [encodeWeapons]; unknown names are skipped and the laser is always included. */
fun decodeWeapons(text: String): Set<WeaponType> {
    val out = mutableSetOf(WeaponType.LASER)
    for (part in text.split(',')) WEAPON_SLOT_ORDER.firstOrNull { it.name == part.trim() }?.let { out.add(it) }
    return out
}
