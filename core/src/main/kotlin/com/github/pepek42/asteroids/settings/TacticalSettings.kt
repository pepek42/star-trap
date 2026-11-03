package com.github.pepek42.asteroids.settings

import com.github.pepek42.asteroids.render.TacticalOverlayMode

/** Until the tactical overlay cards (ST-124, ST-126) settle the default. */
val TACTICAL_OVERLAY_DEFAULT_MODE = TacticalOverlayMode.ALWAYS

/** Tactical overlay preferences: [mode] and the in-game flip [inverted]. */
class TacticalSettings(
    var mode: TacticalOverlayMode = TACTICAL_OVERLAY_DEFAULT_MODE,
    var inverted: Boolean = false,
)
