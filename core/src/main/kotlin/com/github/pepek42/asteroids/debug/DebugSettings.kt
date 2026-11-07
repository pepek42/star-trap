package com.github.pepek42.asteroids.debug

import com.github.pepek42.asteroids.render.EINSTEIN_RING_ENABLED
import com.github.pepek42.asteroids.render.TARGET_POINTERS_ENABLED
import com.github.pepek42.asteroids.render.GRID_ENABLED

/** Time-scale steps cycled by the debug panel. */
val TIME_SCALES = floatArrayOf(0.25f, 0.5f, 1f)

/** Default of the "Debug mode" menu setting: no debug overlays or debug logs. */
const val DEBUG_MODE_DEFAULT = false

/**
 * Runtime debug knobs. The panel knobs are only meaningful when [panelAllowed] is true.
 * [debugMode] is the player-facing "Debug mode" setting; [forcePanel] is the `--debug-panel` command-line flag.
 */
class DebugSettings(
    var debugMode: Boolean = DEBUG_MODE_DEFAULT,
    var panelVisible: Boolean = false,
    var timeScale: Float = 1f,
    var showVectors: Boolean = false,
    var gridEnabled: Boolean = GRID_ENABLED,
    var einsteinRingEnabled: Boolean = EINSTEIN_RING_ENABLED,
    var targetPointersEnabled: Boolean = TARGET_POINTERS_ENABLED,
    var forcePanel: Boolean = false,
) {
    /** The debug panel exists when debug mode is on or the command line forces it. */
    val panelAllowed: Boolean get() = debugMode || forcePanel
}
