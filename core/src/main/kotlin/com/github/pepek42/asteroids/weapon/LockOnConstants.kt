package com.github.pepek42.asteroids.weapon

import com.github.pepek42.asteroids.physics.ENGINE_PROPER_ACCEL
import com.github.pepek42.asteroids.provider.ENEMY_HEALTH

/**
 * Engine proper acceleration of the lock-on shot as a multiple of the ship's [ENGINE_PROPER_ACCEL] (ST-164). The lock-on shot
 * carries a small engine of its own; three times the ship's acceleration gives it a short, punchy burn while the
 * proper-velocity integration still caps it below c (`RelativisticDynamics`).
 */
const val LOCK_ON_ACCEL_FACTOR = 3f

/**
 * Lock-on shot engine proper acceleration (u/s²): [LOCK_ON_ACCEL_FACTOR] × [ENGINE_PROPER_ACCEL], about 1.24·c per second
 * at the default engine. Deriving it from [ENGINE_PROPER_ACCEL] makes it follow the runtime `settings.engineProperAccel`
 * when c or the engine slider changes.
 */
const val LOCK_ON_PROPER_ACCEL = LOCK_ON_ACCEL_FACTOR * ENGINE_PROPER_ACCEL

/**
 * Engine burn time on the lock-on shot's own clock (s). With the default engine a burn of [LOCK_ON_BURN_SECONDS] adds
 * `LOCK_ON_PROPER_ACCEL · t ≈ 0.99·c` of proper velocity, i.e. v ≈ 0.70c (γ ≈ 1.4) at burnout. Short enough that the
 * lock-on shot visibly arcs instead of crossing the arena in one frame, long enough to correct the launch heading.
 */
const val LOCK_ON_BURN_SECONDS = 0.8f

/**
 * Maximum heading turn rate (rad/s). A 180° turn takes π / [LOCK_ON_TURN_RATE] ≈ 0.79 s, about one burn, so the sprite
 * points at the target before the engine cuts out while the turn still reads as a curve.
 */
const val LOCK_ON_TURN_RATE = 4f

/**
 * Arena lifetime (s) of a lock-on shot: enough for the burn plus a long coast at the default c, but shorter than the
 * arena crossing so a missed shot cannot chase a target forever. `LifetimeSystem` fades the sprite near the end.
 */
const val LOCK_ON_LIFETIME = 4f

/** One hit destroys an enemy ship: [LOCK_ON_DAMAGE] = [ENEMY_HEALTH] (ST-164). */
const val LOCK_ON_DAMAGE = ENEMY_HEALTH

/**
 * Lock-on shots carried per level, also the magazine size for the slow regeneration. `WeaponLoadoutComponent.reset()`
 * puts this many in slot 2, so every level refills it.
 */
const val LOCK_ON_AMMO_PER_LEVEL = 6

/**
 * Ship proper seconds to regenerate one lock-on shot (owner 2026-11-08: "replenish slowly"). An empty magazine refills
 * in `LOCK_ON_AMMO_PER_LEVEL × 10 s` = 1 min, so the laser stays the main weapon.
 */
const val LOCK_ON_AMMO_REGEN_SECONDS = 10f

/** Most lock-on shots one shooter may have in flight at once; further launches wait for a hit or the lifetime to run out. */
const val LOCK_ON_MAX_IN_FLIGHT = 4

/**
 * Lock-on shot launch cooldown (shots per second). The lock-on shot has its own rate instead of sharing the laser's
 * `WeaponComponent.shotsPerSecond` (ST-164; ST-163 left the slot parameters shared).
 */
const val LOCK_ON_SHOTS_PER_SECOND = 1f

/** Long side of the lock-on shot's Box2D box (u), a third of the player ship's 1.33 u hull. */
const val LOCK_ON_LENGTH = 0.5f

/** Short side of the lock-on shot's Box2D box (u). */
const val LOCK_ON_WIDTH = 0.16f

/** Density of the lock-on shot body (kg/u²); the tiny box keeps the mass negligible next to a ship or an asteroid. */
const val LOCK_ON_DENSITY = 1f

/** Health of a lock-on shot so one laser hit pops it; laser base damage is 50, so 1 is enough and survives no hit. */
const val LOCK_ON_HEALTH = 1f

/** Length of the drawn lock-on shot sprite along its heading (u). The sprite is bigger than the body, like the lasers'. */
const val LOCK_ON_SPRITE_LENGTH = 1f
