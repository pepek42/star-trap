package com.github.pepek42.asteroids.environment

enum class AsteroidSize(
    val radius: Float,
    val nextSize: AsteroidSize?,
    val textureRegionName: String,
) {
    SMALL(1f, null, "asteroid_big"),
    LARGE(2.25f, SMALL, "asteroid_big"),
}

const val ASTEROID_DENSITY = 10f
