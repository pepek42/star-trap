package com.github.pepek42.asteroids.settings

import com.github.pepek42.asteroids.debug.DebugSettings
import com.github.pepek42.asteroids.i18nKeys
import com.github.pepek42.asteroids.input.ControlScheme
import com.github.pepek42.asteroids.input.ControlSettings
import com.github.pepek42.asteroids.physics.C_MAX_BOX2D_SAFE
import com.github.pepek42.asteroids.physics.C_MIN
import com.github.pepek42.asteroids.physics.PILOT_VIEW_DEFAULT
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import com.github.pepek42.asteroids.render.TacticalOverlayMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuSettingsTest {
    private class Fixture {
        val rel = RelativitySettings()
        val dbg = DebugSettings(debugMode = false, panelVisible = false)
        val display = DisplaySettings()
        val applied = mutableListOf<Boolean>()
        val controls = ControlSettings()
        val sound = SoundSettings()
        val specs = menuSettings(rel, controls, dbg, display, true, applyFullscreen = { applied += it }, sound = sound).specs
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
    fun volumesRoundTripAndReset() {
        val a = Fixture()
        a.sound.masterVolume = 0.25f
        a.sound.hullSoundVolume = 0.5f
        val store = MemoryKeyValueStore()
        saveSettings(store, a.specs)
        val b = Fixture()
        loadSettings(store, b.specs)
        assertEquals(0.25f, b.sound.masterVolume)
        assertEquals(0.5f, b.sound.hullSoundVolume)
        resetSettings(b.specs)
        assertEquals(MASTER_VOLUME_DEFAULT, b.sound.masterVolume)
        assertEquals(HULL_SOUND_VOLUME_DEFAULT, b.sound.hullSoundVolume)
        b.sound.masterVolume = 3f
        assertEquals(1f, b.sound.masterVolume)
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
    fun allI18nKeysExist() {
        val keys = Fixture().specs.flatMap { spec ->
            listOf(spec.labelKey, spec.hintKey) + ((spec as? SettingSpec.Choice)?.optionLabelKeys ?: emptyList())
        }
        val missing = keys - i18nKeys("messages.properties")
        assertTrue(missing.isEmpty(), "Missing in messages.properties: $missing")
    }

    @Test
    fun fullscreenRoundTripAndApply() {
        val a = Fixture()
        (a.spec("fullscreen") as SettingSpec.Toggle).set(true)
        assertEquals(listOf(true), a.applied)
        val store = MemoryKeyValueStore()
        saveSettings(store, a.specs)

        val b = Fixture()
        loadSettings(store, b.specs)
        assertTrue(b.display.fullscreen)
    }

    @Test
    fun fullscreenSpecIsOptional() {
        val specs = menuSettings(
            RelativitySettings(), ControlSettings(), DebugSettings(debugMode = false, panelVisible = false),
            includeFullscreen = false,
        ).specs
        assertTrue(specs.none { it.key == "fullscreen" })
        assertTrue(specs.none { it.key == "language" })
    }

    @Test
    fun tacticalOverlayChoiceIsPersistedAndResetsInverted() {
        val tactical = TacticalSettings()
        val specs = menuSettings(
            RelativitySettings(), ControlSettings(), DebugSettings(debugMode = false, panelVisible = false),
            includeFullscreen = false, tactical = tactical,
        ).specs
        val choice = specs.first { it.key == "tactical_overlay" } as SettingSpec.Choice
        assertEquals(listOf("off", "pilot", "always"), choice.options)
        assertEquals(TACTICAL_OVERLAY_DEFAULT_MODE.flag, choice.default)

        tactical.inverted = true
        choice.set("off")
        assertEquals(TacticalOverlayMode.OFF, tactical.mode)
        assertFalse(tactical.inverted)

        val store = MemoryKeyValueStore()
        store.putString("tactical_overlay", "off")
        val fresh = TacticalSettings()
        loadSettings(store, menuSettings(
            RelativitySettings(), ControlSettings(), DebugSettings(debugMode = false, panelVisible = false),
            includeFullscreen = false, tactical = fresh,
        ).specs)
        assertEquals(TacticalOverlayMode.OFF, fresh.mode)

        resetSettings(specs)
        assertEquals(TACTICAL_OVERLAY_DEFAULT_MODE, tactical.mode)
    }

    @Test
    fun controlSchemeIsSelectableAndPersisted() {
        val a = Fixture()
        val choice = a.spec("aim_mode") as SettingSpec.Choice
        assertEquals(ControlScheme.selectable().map { it.flag }, choice.options)
        // Owner 2026-11-03 (ST-063): auto-aim is the default scheme.
        assertEquals(ControlScheme.AUTO_AIM, ControlSettings().scheme)
        assertEquals(ControlScheme.AUTO_AIM.flag, choice.default)
        assertEquals(ControlScheme.AUTO_AIM.flag, choice.get())
        choice.set(ControlScheme.MOUSE_HULL.flag)
        assertEquals(ControlScheme.MOUSE_HULL, a.controls.scheme)
        val store = MemoryKeyValueStore()
        saveSettings(store, a.specs)

        val b = Fixture()
        loadSettings(store, b.specs)
        assertEquals(ControlScheme.MOUSE_HULL, b.controls.scheme)

        assertTrue("twin" in choice.options)
        val c = Fixture()
        store.putString("aim_mode", ControlScheme.TWIN_STICK.flag)
        loadSettings(store, c.specs)
        assertEquals(ControlScheme.TWIN_STICK, c.controls.scheme)
        // Game.create applies --controls after loading, so the flag wins over the saved value.
        com.github.pepek42.asteroids.debug.parseLaunchOptions(arrayOf("--controls=mouse")).controls
            ?.let { c.controls.scheme = it }
        assertEquals(ControlScheme.MOUSE_HULL, c.controls.scheme)
    }

    @Test
    fun debugModeDefaultsOffAndRoundTrips() {
        val a = Fixture()
        val spec = a.spec("debug_mode") as SettingSpec.Toggle
        assertFalse(spec.default)
        assertFalse(spec.get())
        spec.set(true)
        val store = MemoryKeyValueStore()
        saveSettings(store, a.specs)
        val b = Fixture()
        loadSettings(store, b.specs)
        assertTrue(b.dbg.debugMode)
        assertTrue(b.dbg.panelAllowed)
    }

    @Test
    fun panelAllowedFollowsDebugModeOrForcePanel() {
        val d = DebugSettings()
        assertFalse(d.panelAllowed)
        d.debugMode = true
        assertTrue(d.panelAllowed)
        d.debugMode = false
        d.forcePanel = true
        assertTrue(d.panelAllowed)
    }

    @Test
    fun runSettingsAreThePhysicsChoicesAndTheRestIsGlobal() {
        val f = Fixture()
        val all = menuSettings(f.rel, f.controls, f.dbg, f.display, true, applyFullscreen = {}, sound = f.sound)
        assertEquals(RUN_SETTING_KEYS, all.runSpecs.map { it.key }.toSet())
        assertEquals(all.specs.size, all.runSpecs.size + all.globalSpecs.size)
        assertTrue(all.globalSpecs.none { it.key in RUN_SETTING_KEYS })
    }
}
