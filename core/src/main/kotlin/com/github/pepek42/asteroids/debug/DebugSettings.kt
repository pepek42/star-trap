package com.github.pepek42.asteroids.debug

import com.github.pepek42.asteroids.render.CENTRE_POINTER_ENABLED
import com.github.pepek42.asteroids.render.GRID_ENABLED

/** Time-scale steps cycled by the debug panel. */
val TIME_SCALES = floatArrayOf(0.25f, 0.5f, 1f)

/** Runtime debug knobs. Only meaningful when [panelAllowed] is true. */
class DebugSettings(
    var panelAllowed: Boolean,
    var panelVisible: Boolean,
    var timeScale: Float = 1f,
    var showVectors: Boolean = false,
    var gridEnabled: Boolean = GRID_ENABLED,
    var centrePointerEnabled: Boolean = CENTRE_POINTER_ENABLED,
)
