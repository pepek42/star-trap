package com.github.pepek42.asteroids.physics

import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_TICK_RATE

/** Box2D `b2_maxTranslation`: the most a body may move in one step (u). Box2D clamps anything faster (research §1). */
const val BOX2D_MAX_TRANSLATION = 2f

/**
 * Highest c the runtime settings allow: 0.75 × [BOX2D_MAX_TRANSLATION] × [PHYSICS_TICK_RATE] (90 u/s at 60 Hz), so
 * Box2D's per-step translation clamp never bites. Changing the tick rate moves this ceiling automatically.
 */
const val C_MAX_BOX2D_SAFE = 0.75f * BOX2D_MAX_TRANSLATION * PHYSICS_TICK_RATE

/** World units per second. Owner decision 2026-11-02: c = 20, a third of the old 60, well under [C_MAX_BOX2D_SAFE]. */
const val SPEED_OF_LIGHT = 20.0f

/** Lowest runtime γ cap (numerical floor for the slider). */
const val GAMMA_MAX_MIN = 2f

/** Lowest c the runtime settings allow (ST-056). */
const val C_MIN = 10f

/**
 * Numerical safety bound, not a gameplay cap (owner decision ST-032). Runtime value lives in `RelativitySettings.gammaMaxMassive` (ST-056).
 * Enforced by clamping proper velocity (research §2).
 */
const val GAMMA_MAX_MASSIVE = 50f

/** Lorentz factor cap for slug projectiles. Photons travel at exactly [SPEED_OF_LIGHT] and carry no proper velocity. */
const val GAMMA_MAX_PROJECTILE = 50f

/** Speed cap as a fraction of c for massive bodies: sqrt(1 - 1/γmax²) ≈ 0.99499. */
val BETA_MAX_MASSIVE: Float = kotlin.math.sqrt(1f - 1f / (GAMMA_MAX_MASSIVE * GAMMA_MAX_MASSIVE))

/** Default for `RelativitySettings.relativityEnabled`, the runtime master switch. When false, ships use Newtonian thrust and bodies keep a synced `RelativisticComponent`. */
const val RELATIVITY_ENABLED = true

/**
 * Default for `RelativitySettings.gammaMassEnabled` (ST-047, research E7). When true a relativistic body's Box2D mass
 * (and inertia) is `γ·m₀`, so contact impulses conserve relativistic momentum `Σ γ m v` and a near-c ship hits like a
 * freight train.
 */
const val GAMMA_MASS_ENABLED = true

/**
 * Cap on the γ used for the Box2D mass, so the mass is at most 8× the rest mass (APPROX, `physics-lies.md` row 8). Its
 * own solver-stability limit: mass ratios much above ~10:1 make Box2D contacts mushy. It is independent of the γ safety
 * bound [GAMMA_MAX_MASSIVE]; the kinematics stay exact above it because the collision readback uses the true γ.
 */
const val GAMMA_MASS_CAP = 8f

/** Target time (arena time) for a straight run from rest to 0.9c under the default engine. */
const val ENGINE_TIME_TO_09C = 5f

/** γβ at β = 0.9: 0.9 / sqrt(1 − 0.81) = 2.0647. The rapidity-like quantity u/c reached at 0.9c. */
const val GAMMA_BETA_09C = 2.0647f

/** Default engine proper acceleration as a fraction of c (about 0.41): a/c = γβ(0.9) / [ENGINE_TIME_TO_09C]. */
const val ENGINE_ACCEL_PER_C = GAMMA_BETA_09C / ENGINE_TIME_TO_09C

/**
 * Engine proper acceleration (u/s²) at full thrust, felt by the pilot: a = c·γβ(0.9) / [ENGINE_TIME_TO_09C],
 * about 0.413·c (8.26 at c = 20). A straight run reaches 0.9c after arena time t = (c/a)·γβ = 5 s (pilot proper
 * time τ = (c/a)·asinh(γβ) ≈ 3.6 s), covering d = (c²/a)(γ − 1) ≈ 62 u at c = 20 before the field.
 */
const val ENGINE_PROPER_ACCEL = SPEED_OF_LIGHT * ENGINE_ACCEL_PER_C

/** Runtime engine-acceleration clamp as fractions of c: the default is 0.41c, so it is never clamped. */
const val ENGINE_ACCEL_PER_C_MIN = 0.1f
const val ENGINE_ACCEL_PER_C_MAX = 2f

/** The same clamp in u/s² at the default c. */
const val ENGINE_ACCEL_MIN = ENGINE_ACCEL_PER_C_MIN * SPEED_OF_LIGHT
const val ENGINE_ACCEL_MAX = ENGINE_ACCEL_PER_C_MAX * SPEED_OF_LIGHT

/** Reverse thrusters are this fraction as strong as the main engine. */
const val REVERSE_THRUST_FRACTION = 0.5f

/**
 * Braking boost k (owner decision 2026-11-02): engine proper acceleration is a0·(1 + k·max(0, −cos θ)), θ the angle
 * between thrust and velocity. Burning straight against the motion gives 1 + k, i.e. 3× at k = 2.
 */
const val BRAKE_BOOST_K = 2f

/**
 * Impact damage per unit of kinetic energy measured in units of the reduced rest energy μc² (ST-048): damage =
 * (γrel − 1)·μ·this, c-independent. Calibrated against a large asteroid (r 2.25, density 10: m = 159, health 150)
 * and the ship (1.33 u box, density 20: m = 35.6): μ = 29, so a γrel = 3 ram does 2·29·3 = 175 and kills the asteroid
 * in one hit, γrel = 2.3 (about 0.9c) does 112 (two hits), and the threshold edge (γrel − 1 = 0.02) does under 2.
 * Two large asteroids (μ = 80) at the fastest spawn speeds (about 0.4c relative, γrel − 1 = 0.09) lose about 20.
 */
const val DAMAGE_PER_REST_ENERGY = 3f

/** Collisions with `γrel − 1` under this do no damage (about 0.2c relative speed). */
const val RAM_GAMMA_THRESHOLD = 0.02f

/** Share of the ram damage the player ship takes. 0 until there is a player-death flow. */
const val PLAYER_RAM_DAMAGE_FACTOR = 0f

/**
 * A post-step body velocity further than this fraction of c from the one we set means a contact changed it.
 * Use as `COLLISION_EPS_BETA * settings.c` (u/s). 0.05 u/s at the old c = 60.
 */
const val COLLISION_EPS_BETA = 0.05f / 60f

/**
 * Game clock = pilot proper time (owner decision 2026-11-02, research E21). Past TIME_WARP_MAX the world advances
 * slower than the pilot's γ demands: declared FAKE.
 */
const val TIME_WARP_ENABLED = true

/** Default fast-forward cap for the pilot clock (about 10×). */
const val TIME_WARP_MAX = 10f

/** Upper clamp for the runtime time-warp setting; the step cap is sized from this. */
const val TIME_WARP_MAX_LIMIT = 12f

/**
 * Default for `RelativitySettings.pilotView` (research E8 + E9). When true the main view shows what the pilot sees (light
 * delay and aberration), so sprites drift away from their Box2D bodies by roughly β·distance. When false the camera shows
 * the true arena positions and sprites match the bodies. Off by default since 2026-11-03: the owner found the pilot view
 * unplayable because sprites and hitboxes disagree. V toggles it at runtime.
 */
const val PILOT_VIEW_DEFAULT = false
