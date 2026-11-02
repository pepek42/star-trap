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

/** Max spawn velocity component per axis as a fraction of c (the old 8 u/s at c = 60). Multiply by `settings.c`. */
const val ASTEROID_SPAWN_BETA_MAX = 8f / 60f

/** Minimum spawn distance (u) from the map centre, where the player spawns. */
const val ASTEROID_SPAWN_MIN_RADIUS = 30f
