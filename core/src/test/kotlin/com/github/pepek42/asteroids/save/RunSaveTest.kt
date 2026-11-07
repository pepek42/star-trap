package com.github.pepek42.asteroids.save

import com.github.pepek42.asteroids.GameState
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

class RunSaveTest {
    private val rel = RelativitySettings()
    private val runSpecs = menuSettings(rel, ControlSettings(), DebugSettings(debugMode = false, panelVisible = false)).runSpecs

    @Test
    fun roundTripRestoresLevelAndRunPhysics() {
        val store = MemoryKeyValueStore()
        rel.c = 30f
        rel.relativityEnabled = false
        writeRunSave(store, 3, runSpecs)
        rel.c = 20f
        rel.relativityEnabled = true
        assertEquals(RunSave(3), readRunSave(store))
        applyRunSave(store, runSpecs)
        assertEquals(30f, rel.c)
        assertFalse(rel.relativityEnabled)
    }

    @Test
    fun applyingASaveDoesNotTouchNewGameDefaults() {
        val store = MemoryKeyValueStore()
        store.putFloat("speed_of_light", 25f)
        rel.c = 30f
        writeRunSave(store, 2, runSpecs)
        assertEquals(25f, store.getFloat("speed_of_light", 0f))
    }

    @Test
    fun noSaveReadsAsNull() = assertNull(readRunSave(MemoryKeyValueStore()))

    @Test
    fun deathClearsTheSave() {
        val store = MemoryKeyValueStore()
        writeRunSave(store, 2, runSpecs)
        clearRunSave(store)
        assertNull(readRunSave(store))
    }

    @Test
    fun scenariosNeverSave() {
        val store = MemoryKeyValueStore()
        val state = GameState().apply { startScenario(Scenarios.ALL.first()); nextLevel() }
        assertFalse(saveAfterLevel(store, state, runSpecs))
        assertNull(readRunSave(store))
    }

    @Test
    fun clearedNormalLevelSavesNextLevel() {
        val store = MemoryKeyValueStore()
        val state = GameState().apply { resetGame(); nextLevel() }
        assertTrue(saveAfterLevel(store, state, runSpecs))
        assertEquals(2, readRunSave(store)?.nextLevel)
    }

    @Test
    fun unknownVersionIsIgnored() {
        val store = MemoryKeyValueStore()
        writeRunSave(store, 2, runSpecs)
        store.putFloat(RUN_SAVE_PREFIX + "version", (RUN_SAVE_VERSION + 1).toFloat())
        assertNull(readRunSave(store))
    }
}
