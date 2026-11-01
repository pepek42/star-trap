package com.github.pepek42.asteroids.physics

/**
 * World units per second. Must stay ≤ 0.75 × 2 u/step × 45 Hz ≈ 67 so Box2D's b2_maxTranslation never clamps (research §1).
 */
const val SPEED_OF_LIGHT = 60.0f
