package com.github.pepek42.asteroids.environment

enum class AsteroidSize(
    val radius: Float,
    val textureRegionName: String,
) {
    SMALL(0.5f, "asteroid_big"),
    MEDIUM(1f, "asteroid_mid"),
    LARGE(1.3f, "asteroid_small"),
}

const val ASTEROID_DENSITY = 10f
