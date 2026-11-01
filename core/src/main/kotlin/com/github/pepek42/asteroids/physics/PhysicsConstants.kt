package com.github.pepek42.asteroids.physics

/**
 * World units per second. Must stay ≤ 0.75 × 2 u/step × 45 Hz ≈ 67 so Box2D's b2_maxTranslation never clamps (research §1).
 */
const val SPEED_OF_LIGHT = 60.0f

/**
 * Numerical safety bound, not a gameplay cap (owner decision ST-032). Runtime value lives in `RelativitySettings.gammaMaxMassive` (ST-056).
 * Enforced by clamping proper velocity (research §2).
 */
const val GAMMA_MAX_MASSIVE = 50f

/** Lorentz factor cap for slug projectiles. Photons travel at exactly [SPEED_OF_LIGHT] and carry no proper velocity. */
const val GAMMA_MAX_PROJECTILE = 50f

/** Speed cap as a fraction of c for massive bodies: sqrt(1 - 1/γmax²) ≈ 0.99499. */
val BETA_MAX_MASSIVE: Float = kotlin.math.sqrt(1f - 1f / (GAMMA_MAX_MASSIVE * GAMMA_MAX_MASSIVE))

/** Master switch for the relativistic integrator. When false, providers skip RelativisticComponent and ships use Newtonian thrust. */
const val RELATIVITY_ENABLED = true

/**
 * Engine proper acceleration (u/s²) at full thrust, felt by the pilot. With c = 60 and a = 25 a straight run
 * reaches 0.9c (γβ = 2.065) after t = (c/a)·γβ ≈ 5.0 s, covering d = (c²/a)(γ − 1) ≈ 186 u before the field.
 */
const val ENGINE_PROPER_ACCEL = 25f

/** Reverse thrusters are this fraction as strong as the main engine. */
const val REVERSE_THRUST_FRACTION = 0.5f

/** A post-step body velocity further than this (u/s) from the one we set means a contact changed it. */
const val COLLISION_EPS = 0.05f
