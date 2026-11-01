package com.github.pepek42.asteroids.physics

/**
 * World units per second. Must stay ≤ 0.75 × 2 u/step × 45 Hz ≈ 67 so Box2D's b2_maxTranslation never clamps (research §1).
 */
const val SPEED_OF_LIGHT = 60.0f

/** Lorentz factor cap for massive bodies (ships, asteroids). Enforced by clamping proper velocity (research §2). */
const val GAMMA_MAX_MASSIVE = 10f

/** Lorentz factor cap for slug projectiles. Photons travel at exactly [SPEED_OF_LIGHT] and carry no proper velocity. */
const val GAMMA_MAX_PROJECTILE = 20f

/** Speed cap as a fraction of c for massive bodies: sqrt(1 - 1/γmax²) ≈ 0.99499. */
val BETA_MAX_MASSIVE: Float = kotlin.math.sqrt(1f - 1f / (GAMMA_MAX_MASSIVE * GAMMA_MAX_MASSIVE))
