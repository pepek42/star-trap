package com.github.pepek42.asteroids.environment

enum class AsteroidSize(
    val radius: Float,
    val nextSize: AsteroidSize?,
    val textureRegionName: String,
) {
    SMALL(1f, null, "asteroid_small"),
    LARGE(2.25f, SMALL, "asteroid_big"),
}

const val ASTEROID_DENSITY = 10f

/** Asteroids spawned per level squared (on top of the one base asteroid). */
const val ASTEROID_COUNT_PER_LEVEL_SQ = 2

/** Max spawn velocity component (u/s) per axis. */
const val ASTEROID_SPAWN_SPEED_MAX = 8f

/** Minimum spawn distance (u) from the map centre, where the player spawns. */
const val ASTEROID_SPAWN_MIN_RADIUS = 30f
