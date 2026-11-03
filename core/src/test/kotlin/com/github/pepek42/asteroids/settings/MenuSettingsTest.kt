package com.github.pepek42.asteroids.settings

import com.github.pepek42.asteroids.debug.DebugSettings
import com.github.pepek42.asteroids.i18nKeys
import com.github.pepek42.asteroids.input.ControlSettings
import com.github.pepek42.asteroids.physics.C_MAX_BOX2D_SAFE
import com.github.pepek42.asteroids.physics.C_MIN
import com.github.pepek42.asteroids.physics.PILOT_VIEW_DEFAULT
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuSettingsTest {
    private class Fixture {
        val rel = RelativitySettings()
        val dbg = DebugSettings(panelAllowed = false, panelVisible = false)
        val specs = menuSettings(rel, ControlSettings(), dbg).specs
        fun spec(key: String) = specs.first { it.key == key }
    }

    @Test
    fun saveAndLoadRoundTrip() {
        val a = Fixture()
        a.rel.pilotView = !a.rel.pilotView
        a.rel.relativityEnabled = !a.rel.relativityEnabled
        a.rel.c = C_MIN + 3f
        a.rel.timeWarpEnabled = !a.rel.timeWarpEnabled
        a.dbg.targetPointersEnabled = !a.dbg.targetPointersEnabled
        a.dbg.gridEnabled = !a.dbg.gridEnabled
        val store = MemoryKeyValueStore()
        saveSettings(store, a.specs)

        val b = Fixture()
        loadSettings(store, b.specs)
        assertEquals(a.rel.pilotView, b.rel.pilotView)
        assertEquals(a.rel.relativityEnabled, b.rel.relativityEnabled)
        assertEquals(a.rel.c, b.rel.c)
        assertEquals(a.rel.timeWarpEnabled, b.rel.timeWarpEnabled)
        assertEquals(a.dbg.targetPointersEnabled, b.dbg.targetPointersEnabled)
        assertEquals(a.dbg.gridEnabled, b.dbg.gridEnabled)
        assertEquals(1f, store.getFloat(SETTINGS_VERSION_KEY, 0f))
    }

    @Test
    fun storedCIsClampedAndEmptyStoreChangesNothing() {
        val f = Fixture()
        val store = MemoryKeyValueStore()
        store.putFloat("speed_of_light", 1000f)
        loadSettings(store, f.specs)
        assertEquals(C_MAX_BOX2D_SAFE, f.rel.c)

        val g = Fixture()
        loadSettings(MemoryKeyValueStore(), g.specs)
        assertEquals(SPEED_OF_LIGHT, g.rel.c)
        assertEquals(PILOT_VIEW_DEFAULT, g.rel.pilotView)
    }

    @Test
    fun resetRestoresDefaults() {
        val f = Fixture()
        f.rel.pilotView = true
        f.rel.c = C_MIN
        f.rel.relativityEnabled = false
        f.dbg.gridEnabled = !f.dbg.gridEnabled
        resetSettings(f.specs)
        assertEquals(PILOT_VIEW_DEFAULT, f.rel.pilotView)
        assertEquals(if (PILOT_VIEW_DEFAULT) "pilot" else "outside", (f.spec("perspective") as SettingSpec.Choice).get())
        assertEquals(SPEED_OF_LIGHT, f.rel.c)
        f.specs.forEach { spec ->
            when (spec) {
                is SettingSpec.Toggle -> assertEquals(spec.default, spec.get(), spec.key)
                is SettingSpec.Slider -> assertEquals(spec.default, spec.get(), spec.key)
                is SettingSpec.Choice -> assertEquals(spec.default, spec.get(), spec.key)
            }
        }
    }

    @Test
    fun relativityDependentSettingsDisableWithRelativity() {
        val f = Fixture()
        f.rel.relativityEnabled = true
        assertTrue(f.spec("speed_of_light").enabled())
        assertTrue(f.spec("time_warp").enabled())
        f.rel.relativityEnabled = false
        assertFalse(f.spec("speed_of_light").enabled())
        assertFalse(f.spec("time_warp").enabled())
        assertTrue(f.spec("grid").enabled())
    }

    @Test
    fun allI18nKeysExistInBothFiles() {
        val keys = Fixture().specs.flatMap { spec ->
            listOf(spec.labelKey, spec.hintKey) + ((spec as? SettingSpec.Choice)?.optionLabelKeys ?: emptyList())
        }
        listOf("messages.properties", "messages_pl.properties").forEach { file ->
            val missing = keys - i18nKeys(file)
            assertTrue(missing.isEmpty(), "Missing in $file: $missing")
        }
    }
}
