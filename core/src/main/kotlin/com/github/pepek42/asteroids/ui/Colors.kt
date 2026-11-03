package com.github.pepek42.asteroids.ui

import com.badlogic.gdx.graphics.Color

/** Colour of the arena boundary (shared by the minimap frame and any future border rendering). */
val ARENA_BOUNDARY_COLOR: Color = Color(0.9f, 0.6f, 0.1f, 1f)

/** Colour of the player's marker on the minimap and the tactical overlay. */
val PLAYER_MARKER_COLOR: Color = Color(0.4f, 0.8f, 1f, 1f)

/** Alpha of tactical overlay outlines and arrows. */
const val TACTICAL_ALPHA = 0.65f

/** Alpha of the arena border drawn by the tactical overlay. */
const val TACTICAL_BORDER_ALPHA = 0.5f
