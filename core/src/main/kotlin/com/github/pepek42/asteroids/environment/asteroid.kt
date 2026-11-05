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

/**
 * Large asteroids spawned per level squared (on top of the one base asteroid). ST-101 balance pass: 2 left a careful
 * pilot no chance on levels 4 and 5 (level 5 had 51 large asteroids, 153 kills with the fragments, more than the guns can
 * do in 120 s). 1 gives 2, 5, 10, 17, 26 large asteroids on levels 1 to 5. See `docs/tech/balance.md`.
 */
const val ASTEROID_COUNT_PER_LEVEL_SQ = 1

/** Number of large asteroids spawned on [level]: `1 + ASTEROID_COUNT_PER_LEVEL_SQ · level²`. */
fun asteroidCount(level: Int): Int = 1 + ASTEROID_COUNT_PER_LEVEL_SQ * level * level

/**
 * Max spawn velocity component per axis as a fraction of c (0.1: 2 u/s per axis at c = 20, was 8/60 before ST-101). Multiply
 * by `settings.c`. The balance sweep showed the faster rocks cost a careful pilot too many missed shots: with 8/60 only
 * about 55 % cleared level 5 in 120 s, with 6/60 about 65 to 70 %, while the aggressive pilot's death rate stays in range.
 */
const val ASTEROID_SPAWN_BETA_MAX = 6f / 60f

/** Minimum spawn distance (u) from the map centre, where the player spawns. */
const val ASTEROID_SPAWN_MIN_RADIUS = 30f

/** Health of a [AsteroidSize.LARGE] asteroid; other sizes scale with rest mass relative to it. */
const val ASTEROID_LARGE_HEALTH = 150f

/** Rest mass of an asteroid of [size] (disc of [ASTEROID_DENSITY]), the same as Box2D computes for its circle fixture. */
fun asteroidRestMass(size: AsteroidSize): Float = ASTEROID_DENSITY * Math.PI.toFloat() * size.radius * size.radius

/** Asteroid health proportional to rest mass, [ASTEROID_LARGE_HEALTH] for a large one (small: about 30). */
fun asteroidHealth(size: AsteroidSize): Float =
    ASTEROID_LARGE_HEALTH * asteroidRestMass(size) / asteroidRestMass(AsteroidSize.LARGE)
