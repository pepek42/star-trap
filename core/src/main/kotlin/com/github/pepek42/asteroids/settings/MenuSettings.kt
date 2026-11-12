package com.github.pepek42.asteroids.settings

import com.github.pepek42.asteroids.debug.DEBUG_MODE_DEFAULT
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
import com.github.pepek42.asteroids.render.EINSTEIN_RING_ENABLED
import com.github.pepek42.asteroids.render.GRID_ENABLED
import com.github.pepek42.asteroids.render.TARGET_POINTERS_ENABLED
import com.github.pepek42.asteroids.render.TacticalOverlayMode

/** Key under which the settings format version is stored. */
const val SETTINGS_VERSION_KEY = "settings_version"
const val SETTINGS_VERSION = 1

const val PERSPECTIVE_OUTSIDE = "outside"
const val PERSPECTIVE_PILOT = "pilot"

/**
 * Tabs of the Settings page, in tab order (Dev stays last). The page builds its tabs from this enum, so a new
 * setting only names its category. The tab title is the i18n key [labelKey].
 */
enum class SettingCategory {
    CONTROLS, SHIP_COMPUTER, GRAPHICS, AUDIO, DEV;

    val labelKey: String get() = "settings_tab_${name.lowercase()}"

    companion object {
        /** The tab named [flag] (case-insensitive, e.g. `audio`, `ship_computer`), or null. */
        fun fromFlag(flag: String?): SettingCategory? = entries.firstOrNull { it.name.equals(flag, ignoreCase = true) }
    }
}

/**
 * One player-facing setting. The menu, persistence and "Reset defaults" all iterate a list of these.
 * [category] is the Settings tab; it is null only for run settings ([RUN_SETTING_KEYS]), which live on the New game page.
 */
sealed class SettingSpec(val key: String, val category: SettingCategory?, val enabled: () -> Boolean = { true }) {
    val labelKey: String get() = "setting_$key"
    val hintKey: String get() = "setting_${key}_hint"

    class Toggle(
        key: String,
        category: SettingCategory?,
        val default: Boolean,
        val get: () -> Boolean,
        val set: (Boolean) -> Unit,
        enabled: () -> Boolean = { true },
    ) : SettingSpec(key, category, enabled)

    class Slider(
        key: String,
        category: SettingCategory?,
        val min: Float,
        val max: Float,
        val step: Float,
        val decimals: Int,
        val default: Float,
        val get: () -> Float,
        val set: (Float) -> Unit,
        enabled: () -> Boolean = { true },
    ) : SettingSpec(key, category, enabled)

    class Choice(
        key: String,
        category: SettingCategory?,
        val options: List<String>,
        val optionLabelKeys: List<String>,
        val default: String,
        val get: () -> String,
        val set: (String) -> Unit,
        enabled: () -> Boolean = { true },
    ) : SettingSpec(key, category, enabled)
}

/**
 * Keys of the per-run physics settings. They are picked on the New game page before a run starts and stay fixed
 * during it; every other setting is global (Settings page) and applies to any run right away.
 */
val RUN_SETTING_KEYS = setOf("relativity", "speed_of_light", "time_warp")

/** Wrapper so DI can bind the list. [specs] holds every setting (persistence iterates it). */
class MenuSettings(val specs: List<SettingSpec>) {
    /** Settings chosen per run on the New game page. */
    val runSpecs: List<SettingSpec> = specs.filter { it.key in RUN_SETTING_KEYS }

    /** Global settings shown on the Settings page. */
    val globalSpecs: List<SettingSpec> = specs.filter { it.key !in RUN_SETTING_KEYS }

    /** The global settings of one Settings tab, in list order. */
    fun specsIn(category: SettingCategory): List<SettingSpec> = globalSpecs.filter { it.category == category }
}

fun menuSettings(
    relativity: RelativitySettings,
    controls: ControlSettings,
    debug: DebugSettings,
    display: DisplaySettings = DisplaySettings(),
    includeFullscreen: Boolean = true,
    applyFullscreen: (Boolean) -> Unit = {},
    tactical: TacticalSettings = TacticalSettings(),
    applyDebugMode: (Boolean) -> Unit = {},
    sound: SoundSettings = SoundSettings(),
): MenuSettings {
    val relativityOn = { relativity.relativityEnabled }
    return MenuSettings(
        listOfNotNull(
            ControlScheme.selectable().let { schemes ->
                SettingSpec.Choice(
                    "aim_mode", SettingCategory.CONTROLS,
                    schemes.map { it.flag },
                    schemes.map { "scheme_${it.flag}" },
                    ControlScheme.AUTO_AIM.flag,
                    { controls.scheme.flag },
                    { flag -> ControlScheme.fromFlag(flag)?.let { controls.scheme = it } },
                )
            },
            SettingSpec.Choice(
                "perspective", SettingCategory.GRAPHICS,
                listOf(PERSPECTIVE_OUTSIDE, PERSPECTIVE_PILOT),
                listOf("perspective_outside", "perspective_pilot"),
                if (PILOT_VIEW_DEFAULT) PERSPECTIVE_PILOT else PERSPECTIVE_OUTSIDE,
                { if (relativity.pilotView) PERSPECTIVE_PILOT else PERSPECTIVE_OUTSIDE },
                { relativity.pilotView = it == PERSPECTIVE_PILOT },
            ),
            SettingSpec.Choice(
                "tactical_overlay", SettingCategory.SHIP_COMPUTER,
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
                "tactical_laser_damage", SettingCategory.SHIP_COMPUTER, TACTICAL_LASER_DAMAGE_DEFAULT,
                { tactical.showLaserDamage }, { tactical.showLaserDamage = it }, relativityOn,
            ),
            SettingSpec.Toggle(
                "tactical_target_rings", SettingCategory.SHIP_COMPUTER, TACTICAL_TARGET_RINGS_DEFAULT,
                { tactical.showTargetRings }, { tactical.showTargetRings = it },
            ),
            SettingSpec.Toggle(
                "tactical_intercept_markers", SettingCategory.SHIP_COMPUTER, TACTICAL_INTERCEPT_MARKERS_DEFAULT,
                { tactical.showInterceptMarkers }, { tactical.showInterceptMarkers = it },
            ),
            SettingSpec.Toggle(
                "tactical_incoming_fire", SettingCategory.SHIP_COMPUTER, TACTICAL_INCOMING_FIRE_DEFAULT,
                { tactical.showIncomingFire }, { tactical.showIncomingFire = it },
            ),
            SettingSpec.Toggle(
                "tactical_lock_on_markers", SettingCategory.SHIP_COMPUTER, TACTICAL_LOCK_ON_MARKERS_DEFAULT,
                { tactical.showLockOnMarkers }, { tactical.showLockOnMarkers = it },
            ),
            SettingSpec.Toggle(
                "relativity", null, RELATIVITY_ENABLED,
                { relativity.relativityEnabled }, { relativity.relativityEnabled = it },
            ),
            SettingSpec.Slider(
                "speed_of_light", null, C_MIN, C_MAX_BOX2D_SAFE, 1f, 0, SPEED_OF_LIGHT,
                { relativity.c }, { relativity.c = it }, relativityOn,
            ),
            SettingSpec.Toggle(
                "time_warp", null, TIME_WARP_ENABLED,
                { relativity.timeWarpEnabled }, { relativity.timeWarpEnabled = it }, relativityOn,
            ),
            SettingSpec.Toggle(
                "target_pointers", SettingCategory.SHIP_COMPUTER, TARGET_POINTERS_ENABLED,
                { debug.targetPointersEnabled }, { debug.targetPointersEnabled = it },
            ),
            SettingSpec.Toggle(
                "einstein_ring", SettingCategory.SHIP_COMPUTER, EINSTEIN_RING_ENABLED,
                { debug.einsteinRingEnabled }, { debug.einsteinRingEnabled = it },
            ),
            SettingSpec.Toggle("grid", SettingCategory.SHIP_COMPUTER, GRID_ENABLED, { debug.gridEnabled }, { debug.gridEnabled = it }),
            SettingSpec.Slider(
                "master_volume", SettingCategory.AUDIO, 0f, 1f, 0.05f, 2, MASTER_VOLUME_DEFAULT,
                { sound.masterVolume }, { sound.masterVolume = it },
            ),
            SettingSpec.Slider(
                "hull_sound_volume", SettingCategory.AUDIO, 0f, 1f, 0.05f, 2, HULL_SOUND_VOLUME_DEFAULT,
                { sound.hullSoundVolume }, { sound.hullSoundVolume = it },
            ),
            SettingSpec.Toggle(
                "computer_sounds", SettingCategory.AUDIO, COMPUTER_SOUNDS_DEFAULT,
                { sound.computerSounds }, { sound.computerSounds = it },
            ),
            SettingSpec.Slider(
                "computer_sound_volume", SettingCategory.AUDIO, 0f, 1f, 0.05f, 2, COMPUTER_SOUND_VOLUME_DEFAULT,
                { sound.computerSoundVolume }, { sound.computerSoundVolume = it },
                { sound.computerSounds },
            ),
            if (includeFullscreen) {
                SettingSpec.Toggle(
                    "fullscreen", SettingCategory.GRAPHICS, false,
                    { display.fullscreen },
                    {
                        display.fullscreen = it
                        applyFullscreen(it)
                    },
                )
            } else null,
            SettingSpec.Toggle(
                "debug_mode", SettingCategory.DEV, DEBUG_MODE_DEFAULT,
                { debug.debugMode },
                {
                    debug.debugMode = it
                    applyDebugMode(it)
                },
            ),
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
