package com.github.pepek42.asteroids.settings

import com.github.pepek42.asteroids.render.TacticalOverlayMode

/** Until the tactical overlay cards (ST-124, ST-126) settle the default. */
val TACTICAL_OVERLAY_DEFAULT_MODE = TacticalOverlayMode.ALWAYS

/** Default of [TacticalSettings.showLaserDamage] (ST-133). */
const val TACTICAL_LASER_DAMAGE_DEFAULT = true

/** Default of [TacticalSettings.showInterceptMarkers] (ST-137). */
const val TACTICAL_INTERCEPT_MARKERS_DEFAULT = true

/**
 * Tactical overlay preferences: [mode], the in-game flip [inverted], the laser damage multiplier label [showLaserDamage]
 * and the intercept markers [showInterceptMarkers] (tier 2 only).
 */
class TacticalSettings(
    var mode: TacticalOverlayMode = TACTICAL_OVERLAY_DEFAULT_MODE,
    var inverted: Boolean = false,
    var showLaserDamage: Boolean = TACTICAL_LASER_DAMAGE_DEFAULT,
    var showInterceptMarkers: Boolean = TACTICAL_INTERCEPT_MARKERS_DEFAULT,
)
