package com.github.pepek42.asteroids

import com.github.pepek42.asteroids.scenario.Scenario
import com.github.pepek42.asteroids.weapon.ALL_WEAPONS
import com.github.pepek42.asteroids.weapon.FIRST_PICK_LEVEL
import com.github.pepek42.asteroids.weapon.FIRST_PICK_OPTIONS
import com.github.pepek42.asteroids.weapon.REMAINING_WEAPON_LEVEL
import com.github.pepek42.asteroids.weapon.STARTING_WEAPONS
import com.github.pepek42.asteroids.weapon.WeaponType
import com.github.pepek42.asteroids.weapon.remainingWeapon

/** First level of a normal new game. */
const val FIRST_LEVEL = 1

/** "Quick start" level offered by the New game page. */
const val QUICK_START_LEVEL = 5

/** Levels a new game can start at, in menu order. */
val START_LEVELS = listOf(FIRST_LEVEL, QUICK_START_LEVEL)

class GameState {
    /** Level being played (PlayScreen.show advances it before spawning, so it is one below the next level between levels). */
    var level = 0
        private set

    /** The scenario being played, or null for a normal run (the level sequence). */
    var scenario: Scenario? = null
        private set

    /** Set when a run ended by player death; the main menu shows a message once and clears it. */
    var lastRunEnded = false

    private val unlocked = mutableSetOf<WeaponType>().apply { addAll(STARTING_WEAPONS) }

    /** Weapons the player has (ST-183): the laser at the start of a run, all in scenarios and from a quick start. */
    val unlockedWeapons: Set<WeaponType> get() = unlocked

    /** True while a normal run is on its cleared level 1 and the player has not picked a weapon yet. */
    val weaponPickPending: Boolean
        get() = scenario == null && level == FIRST_PICK_LEVEL && unlocked.size == STARTING_WEAPONS.size

    /** Unlocks [weapon] when the pick is pending and [weapon] is one of the options; returns whether it did. */
    fun pickWeapon(weapon: WeaponType): Boolean {
        if (!weaponPickPending || weapon !in FIRST_PICK_OPTIONS) return false
        unlocked.add(weapon)
        return true
    }

    /**
     * After [REMAINING_WEAPON_LEVEL] of a normal run gives the weapon that was not picked and returns it; otherwise
     * null (also when called again, since then all weapons are unlocked).
     */
    fun grantRemainingWeapon(): WeaponType? {
        if (scenario != null || level < REMAINING_WEAPON_LEVEL) return null
        val weapon = remainingWeapon(unlocked) ?: return null
        unlocked.add(weapon)
        return weapon
    }

    /** Replaces the unlocked set (a restored run save); the laser is always kept. */
    fun restoreUnlockedWeapons(weapons: Set<WeaponType>) {
        unlocked.clear()
        unlocked.addAll(STARTING_WEAPONS)
        unlocked.addAll(weapons)
    }

    private fun resetWeapons(all: Boolean) = restoreUnlockedWeapons(if (all) ALL_WEAPONS else STARTING_WEAPONS)

    fun nextLevel() {
        ++level
    }

    /** Starts a normal run whose first level is [startLevel]. */
    fun resetGame(startLevel: Int = FIRST_LEVEL) {
        require(startLevel >= FIRST_LEVEL) { "startLevel must be >= $FIRST_LEVEL, was $startLevel" }
        scenario = null
        level = startLevel - 1
        resetWeapons(startLevel >= QUICK_START_LEVEL)
    }

    /** Starts (or restarts) [scenario]; it is a single level. */
    fun startScenario(scenario: Scenario) {
        this.scenario = scenario
        resetWeapons(true)
        level = FIRST_LEVEL - 1
    }
}
