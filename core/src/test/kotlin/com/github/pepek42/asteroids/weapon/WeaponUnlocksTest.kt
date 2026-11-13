package com.github.pepek42.asteroids.weapon

import com.github.pepek42.asteroids.FIRST_LEVEL
import com.github.pepek42.asteroids.GameState
import com.github.pepek42.asteroids.QUICK_START_LEVEL
import com.github.pepek42.asteroids.save.clearRunSave
import com.github.pepek42.asteroids.save.readRunSave
import com.github.pepek42.asteroids.save.saveAfterLevel
import com.github.pepek42.asteroids.debug.DebugSettings
import com.github.pepek42.asteroids.input.ControlSettings
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.scenario.Scenarios
import com.github.pepek42.asteroids.settings.MemoryKeyValueStore
import com.github.pepek42.asteroids.settings.menuSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeaponUnlocksTest {
    private val L = WeaponType.LASER
    private val R = WeaponType.ROCKET
    private val K = WeaponType.LOCK_ON

    /** Plays level [level] to its end: the state is on that level when the cleared screen opens. */
    private fun GameState.clear(level: Int) { while (this.level < level) nextLevel() }

    @Test
    fun newRunHasOnlyTheLaserThenRocketsThenAll() {
        val s = GameState().apply { resetGame() }
        assertEquals(setOf(L), s.unlockedWeapons)
        s.clear(FIRST_PICK_LEVEL)
        assertTrue(s.weaponPickPending)
        assertNull(s.grantRemainingWeapon())
        assertTrue(s.pickWeapon(R))
        assertEquals(setOf(L, R), s.unlockedWeapons)
        assertFalse(s.weaponPickPending)
        s.clear(REMAINING_WEAPON_LEVEL)
        assertEquals(K, s.grantRemainingWeapon())
        assertEquals(setOf(L, R, K), s.unlockedWeapons)
        assertNull(s.grantRemainingWeapon())
    }

    @Test
    fun pickingLockOnGivesRocketsAfterLevelTwo() {
        val s = GameState().apply { resetGame() }
        s.clear(FIRST_PICK_LEVEL)
        assertTrue(s.pickWeapon(K))
        assertFalse(s.pickWeapon(R)) // one pick only
        s.clear(REMAINING_WEAPON_LEVEL)
        assertEquals(R, s.grantRemainingWeapon())
    }

    @Test
    fun laserIsNotAPickOption() {
        val s = GameState().apply { resetGame(); clear(FIRST_PICK_LEVEL) }
        assertFalse(s.pickWeapon(L))
        assertEquals(setOf(L), s.unlockedWeapons)
    }

    @Test
    fun quickStartAndScenariosHaveAllWeapons() {
        val s = GameState()
        s.resetGame(QUICK_START_LEVEL)
        assertEquals(ALL_WEAPONS, s.unlockedWeapons)
        s.resetGame(FIRST_LEVEL)
        assertEquals(setOf(L), s.unlockedWeapons)
        for (scenario in Scenarios.ALL) {
            s.startScenario(scenario)
            assertEquals(ALL_WEAPONS, s.unlockedWeapons, scenario.id)
            assertFalse(s.weaponPickPending)
        }
        s.resetGame() // leaving a scenario drops back to the laser
        assertEquals(setOf(L), s.unlockedWeapons)
    }

    @Test
    fun unlockedSetSurvivesSaveAndRestore() {
        val store = MemoryKeyValueStore()
        val rel = RelativitySettings()
        val specs = menuSettings(rel, ControlSettings(), DebugSettings(debugMode = false, panelVisible = false)).runSpecs
        val s = GameState().apply { resetGame(); clear(FIRST_PICK_LEVEL); pickWeapon(K) }
        assertTrue(saveAfterLevel(store, s, specs))
        val save = readRunSave(store)!!
        assertEquals(setOf(L, K), save.weapons)
        val restored = GameState().apply { resetGame(save.nextLevel); restoreUnlockedWeapons(save.weapons) }
        assertEquals(setOf(L, K), restored.unlockedWeapons)
        clearRunSave(store)
        assertNull(readRunSave(store))
    }

    @Test
    fun encodeDecodeRoundTripAndTolerance() {
        for (set in listOf(setOf(L), setOf(L, R), setOf(L, K), ALL_WEAPONS)) {
            assertEquals(set, decodeWeapons(encodeWeapons(set)))
        }
        assertEquals(setOf(L), decodeWeapons("garbage,"))
        assertEquals(setOf(L), decodeWeapons(""))
    }

    @Test
    fun balanceRunnerWeaponsByLevel() {
        assertEquals(setOf(L), weaponsAtLevel(1))
        assertEquals(setOf(L, R), weaponsAtLevel(2))
        assertEquals(ALL_WEAPONS, weaponsAtLevel(3))
    }
}
