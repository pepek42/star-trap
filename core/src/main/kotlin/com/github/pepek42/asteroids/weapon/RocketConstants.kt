package com.github.pepek42.asteroids.weapon

import com.github.pepek42.asteroids.physics.ENGINE_PROPER_ACCEL
import com.github.pepek42.asteroids.provider.ENEMY_HEALTH
import kotlin.math.tanh

/**
 * Rockets in a full pod (ST-181, owner Q2). A pod empties in `(ROCKET_POD_SIZE - 1) · ROCKET_SALVO_INTERVAL` = 0.6 s of
 * held fire and then reloads as one block.
 */
const val ROCKET_POD_SIZE = 5

/** Ship proper seconds between two rockets of a salvo (owner Q2): five rockets leave in 0.6 s. */
const val ROCKET_SALVO_INTERVAL = 0.15f

/**
 * Ship proper seconds from the last rocket leaving until the whole pod is full again (owner Q2). Rockets are free (no
 * ammo limit beyond this reload), so the reload is the only brake: a pod delivers `ROCKET_POD_SIZE / (reload + salvo)`
 * rockets per second on average, about 1.1/s.
 */
const val ROCKET_RELOAD_SECONDS = 4f

/**
 * Rocket motor proper acceleration as a multiple of the ship's [ENGINE_PROPER_ACCEL] (owner Q3). The rocket's actual
 * acceleration is this times the runtime `settings.engineProperAccel`, so it follows c and the engine slider; with the
 * default engine it is about 0.83·c per second. The proper-velocity integration keeps `v` below c.
 */
const val ROCKET_ACCEL_FACTOR = 2f

/**
 * Motor burn time on the rocket's own clock (s, owner Q3). With the default engine a rocket launched at rest gains
 * `ROCKET_ACCEL_FACTOR · ENGINE_PROPER_ACCEL · burn / c` = 1.24 of rapidity, so v = c · tanh(1.24) = 0.85c at burnout
 * ([rocketBurnoutBeta]). The card's 0.78 is the same 1.24 read as `u/c` without the burn's time dilation; the burn runs
 * on proper time (`burnLeft` ticks by `dt/γ`), so the exact value is higher.
 */
const val ROCKET_BURN_SECONDS = 1.5f

/** One direct hit takes half an enemy ship: two rockets kill it, a large asteroid (150 HP) takes three (owner Q4). */
const val ROCKET_DAMAGE = ENEMY_HEALTH / 2f

/** Health of a rocket so one laser hit shoots it down (owner Q5), the same rule as the lock-on shot. */
const val ROCKET_HEALTH = LOCK_ON_HEALTH

/** Long side of the rocket's Box2D box (u): the lock-on shot's size, a third of the player's hull. */
const val ROCKET_LENGTH = LOCK_ON_LENGTH

/** Short side of the rocket's Box2D box (u). */
const val ROCKET_WIDTH = LOCK_ON_WIDTH

/** Density of the rocket body (kg/u²); the tiny box keeps its mass negligible, like the lock-on shot. */
const val ROCKET_DENSITY = LOCK_ON_DENSITY

/**
 * Length of the drawn rocket sprite along its heading (u): the 32 px `player_rocket` image, which is the hull in the
 * middle and room for the motor plume behind it. It is wider than the body, like the lasers' and lock-on sprites.
 */
const val ROCKET_SPRITE_LENGTH = 1.6f

/**
 * Burnout speed over c of a rocket launched at rest in the heading direction: the motor adds a constant proper
 * acceleration `a0 = ROCKET_ACCEL_FACTOR · engineProperAccel` for [ROCKET_BURN_SECONDS] of rocket proper time, so the
 * rapidity is `a0 · burn / c` and `β = tanh(rapidity)`. A launch from a moving ship along its motion adds the ship's
 * rapidity first (`atanh β_ship + a0 · burn / c`).
 */
fun rocketBurnoutBeta(c: Float, engineProperAccel: Float): Float {
    val rapidity = ROCKET_ACCEL_FACTOR * engineProperAccel * ROCKET_BURN_SECONDS / c
    return tanh(rapidity)
}
