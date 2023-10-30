package com.github.pepek42.asteroids.environment

enum class AsteroidSize(
    val radius: Float,
    val nextSize: AsteroidSize?,
    val textureRegionName: String,
) {
    SMALL(0.45f, null, "asteroid_big"),
    MEDIUM(1f, SMALL, "asteroid_mid"),
    LARGE(2.25f, MEDIUM, "asteroid_small"),
}

const val ASTEROID_DENSITY = 10f
