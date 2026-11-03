package com.github.pepek42.asteroids.settings

import com.github.pepek42.asteroids.debug.DebugSettings
import com.github.pepek42.asteroids.input.ControlScheme
import com.github.pepek42.asteroids.input.ControlSettings
import com.github.pepek42.asteroids.physics.C_MAX_BOX2D_SAFE
import com.github.pepek42.asteroids.physics.C_MIN
import com.github.pepek42.asteroids.physics.PILOT_VIEW_DEFAULT
import com.github.pepek42.asteroids.physics.RELATIVITY_ENABLED
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import com.github.pepek42.asteroids.physics.TIME_WARP_ENABLED
import com.github.pepek42.asteroids.render.GRID_ENABLED
import com.github.pepek42.asteroids.render.TARGET_POINTERS_ENABLED
import com.github.pepek42.asteroids.render.TacticalOverlayMode

/** Key under which the settings format version is stored. */
const val SETTINGS_VERSION_KEY = "settings_version"
const val SETTINGS_VERSION = 1

const val PERSPECTIVE_OUTSIDE = "outside"
const val PERSPECTIVE_PILOT = "pilot"

/** One player-facing setting. The menu, persistence and "Reset defaults" all iterate a list of these. */
sealed class SettingSpec(val key: String, val enabled: () -> Boolean = { true }) {
    val labelKey: String get() = "setting_$key"
    val hintKey: String get() = "setting_${key}_hint"

    class Toggle(
        key: String,
        val default: Boolean,
        val get: () -> Boolean,
        val set: (Boolean) -> Unit,
        enabled: () -> Boolean = { true },
    ) : SettingSpec(key, enabled)

    class Slider(
        key: String,
        val min: Float,
        val max: Float,
        val step: Float,
        val decimals: Int,
        val default: Float,
        val get: () -> Float,
        val set: (Float) -> Unit,
        enabled: () -> Boolean = { true },
    ) : SettingSpec(key, enabled)

    class Choice(
        key: String,
        val options: List<String>,
        val optionLabelKeys: List<String>,
        val default: String,
        val get: () -> String,
        val set: (String) -> Unit,
        enabled: () -> Boolean = { true },
    ) : SettingSpec(key, enabled)
}

/** Wrapper so DI can bind the list. */
class MenuSettings(val specs: List<SettingSpec>)

fun menuSettings(
    relativity: RelativitySettings,
    controls: ControlSettings,
    debug: DebugSettings,
    display: DisplaySettings = DisplaySettings(),
    includeFullscreen: Boolean = true,
    applyFullscreen: (Boolean) -> Unit = {},
    tactical: TacticalSettings = TacticalSettings(),
): MenuSettings {
    val relativityOn = { relativity.relativityEnabled }
    return MenuSettings(
        listOfNotNull(
            ControlScheme.selectable().let { schemes ->
                SettingSpec.Choice(
                    "aim_mode",
                    schemes.map { it.flag },
                    schemes.map { "scheme_${it.flag}" },
                    ControlScheme.AUTO_AIM.flag,
                    { controls.scheme.flag },
                    { flag -> ControlScheme.fromFlag(flag)?.let { controls.scheme = it } },
                )
            },
            SettingSpec.Choice(
                "perspective",
                listOf(PERSPECTIVE_OUTSIDE, PERSPECTIVE_PILOT),
                listOf("perspective_outside", "perspective_pilot"),
                if (PILOT_VIEW_DEFAULT) PERSPECTIVE_PILOT else PERSPECTIVE_OUTSIDE,
                { if (relativity.pilotView) PERSPECTIVE_PILOT else PERSPECTIVE_OUTSIDE },
                { relativity.pilotView = it == PERSPECTIVE_PILOT },
            ),
            SettingSpec.Choice(
                "tactical_overlay",
                TacticalOverlayMode.entries.map { it.flag },
                TacticalOverlayMode.entries.map { "tactical_overlay_${it.flag}" },
                TACTICAL_OVERLAY_DEFAULT_MODE.flag,
                { tactical.mode.flag },
                { f ->
                    TacticalOverlayMode.fromFlag(f)?.let {
                        tactical.mode = it
                        tactical.inverted = false
                    }
                },
            ),
            SettingSpec.Toggle(
                "relativity", RELATIVITY_ENABLED,
                { relativity.relativityEnabled }, { relativity.relativityEnabled = it },
            ),
            SettingSpec.Slider(
                "speed_of_light", C_MIN, C_MAX_BOX2D_SAFE, 1f, 0, SPEED_OF_LIGHT,
                { relativity.c }, { relativity.c = it }, relativityOn,
            ),
            SettingSpec.Toggle(
                "time_warp", TIME_WARP_ENABLED,
                { relativity.timeWarpEnabled }, { relativity.timeWarpEnabled = it }, relativityOn,
            ),
            SettingSpec.Toggle(
                "target_pointers", TARGET_POINTERS_ENABLED,
                { debug.targetPointersEnabled }, { debug.targetPointersEnabled = it },
            ),
            SettingSpec.Toggle("grid", GRID_ENABLED, { debug.gridEnabled }, { debug.gridEnabled = it }),
            if (includeFullscreen) {
                SettingSpec.Toggle(
                    "fullscreen", false,
                    { display.fullscreen },
                    {
                        display.fullscreen = it
                        applyFullscreen(it)
                    },
                )
            } else null,
        )
    )
}

/** Applies every stored value; keys missing from [store] are left alone. Setters clamp. */
fun loadSettings(store: KeyValueStore, specs: List<SettingSpec>) {
    for (spec in specs) {
        if (!store.contains(spec.key)) continue
        when (spec) {
            is SettingSpec.Toggle -> spec.set(store.getBoolean(spec.key, spec.default))
            is SettingSpec.Slider -> spec.set(store.getFloat(spec.key, spec.default))
            is SettingSpec.Choice -> {
                val value = store.getString(spec.key, spec.default)
                if (value in spec.options) spec.set(value)
            }
        }
    }
}

fun saveSettings(store: KeyValueStore, specs: List<SettingSpec>) {
    for (spec in specs) {
        when (spec) {
            is SettingSpec.Toggle -> store.putBoolean(spec.key, spec.get())
            is SettingSpec.Slider -> store.putFloat(spec.key, spec.get())
            is SettingSpec.Choice -> store.putString(spec.key, spec.get())
        }
    }
    store.putFloat(SETTINGS_VERSION_KEY, SETTINGS_VERSION.toFloat())
    store.flush()
}

fun resetSettings(specs: List<SettingSpec>) {
    for (spec in specs) {
        when (spec) {
            is SettingSpec.Toggle -> spec.set(spec.default)
            is SettingSpec.Slider -> spec.set(spec.default)
            is SettingSpec.Choice -> spec.set(spec.default)
        }
    }
}
