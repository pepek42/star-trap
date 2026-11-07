package com.github.pepek42.asteroids.settings

import com.github.pepek42.asteroids.render.TacticalOverlayMode

/** Until the tactical overlay cards (ST-124, ST-126) settle the default. */
val TACTICAL_OVERLAY_DEFAULT_MODE = TacticalOverlayMode.ALWAYS

/** Default of [TacticalSettings.showLaserDamage] (ST-133). */
const val TACTICAL_LASER_DAMAGE_DEFAULT = true

/** Default of [TacticalSettings.showInterceptMarkers] (ST-137). */
const val TACTICAL_INTERCEPT_MARKERS_DEFAULT = true

/** Default of [TacticalSettings.showTargetRings]: glowing red rings on every level target (owner 2026-11-07). */
const val TACTICAL_TARGET_RINGS_DEFAULT = true

/** Default of [TacticalSettings.showIncomingFire] (ST-158). */
const val TACTICAL_INCOMING_FIRE_DEFAULT = true

/**
 * Tactical overlay preferences: [mode], the in-game flip [inverted], the laser damage multiplier label [showLaserDamage]
 * the intercept markers [showInterceptMarkers] (tier 2 only)
 * the target rings [showTargetRings] and the incoming fire warning [showIncomingFire].
 */
class TacticalSettings(
    var mode: TacticalOverlayMode = TACTICAL_OVERLAY_DEFAULT_MODE,
    var inverted: Boolean = false,
    var showLaserDamage: Boolean = TACTICAL_LASER_DAMAGE_DEFAULT,
    var showInterceptMarkers: Boolean = TACTICAL_INTERCEPT_MARKERS_DEFAULT,
    var showTargetRings: Boolean = TACTICAL_TARGET_RINGS_DEFAULT,
    var showIncomingFire: Boolean = TACTICAL_INCOMING_FIRE_DEFAULT,
)
