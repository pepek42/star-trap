package com.github.pepek42.asteroids.audio

import kotlin.math.abs
import kotlin.math.sqrt

/** Engine loop pitch at idle thrust (pitch 1 is the file's own pitch). */
const val ENGINE_PITCH_MIN = 0.8f

/** Engine loop pitch at full thrust. */
const val ENGINE_PITCH_MAX = 1.25f

/** Smoothing time constant (s) of the engine loop volume and pitch, in real (pilot) time. */
const val ENGINE_SMOOTHING_TIME = 0.12f

/** Smoothed loop volume below this stops the loop instance. */
const val ENGINE_STOP_VOLUME = 0.005f

/** Relative speed (u/s) as a fraction of c above which a contact counts as a full-volume hit. */
const val HIT_FULL_VOLUME_C_FRACTION = 0.25f

/** Contacts slower than this fraction of [HIT_FULL_VOLUME_C_FRACTION]'s speed are a scrape, faster ones a hit. */
const val SCRAPE_SPEED_FRACTION = 0.2f

/** Volume of a hull hit or scrape never goes below this (heard, but quiet). */
const val IMPACT_MIN_VOLUME = 0.15f

/** Engine loop volume for [thrusters] (-1..1): 0 at no thrust, [maxVolume] at full thrust either way, monotonic in |thrusters|. */
fun engineVolume(thrusters: Float, maxVolume: Float): Float = abs(thrusters).coerceIn(0f, 1f) * maxVolume

/** Engine loop pitch for [thrusters]: [ENGINE_PITCH_MIN] at none, [ENGINE_PITCH_MAX] at full, monotonic in |thrusters|. Not Doppler-shifted. */
fun enginePitch(thrusters: Float): Float =
    ENGINE_PITCH_MIN + (ENGINE_PITCH_MAX - ENGINE_PITCH_MIN) * abs(thrusters).coerceIn(0f, 1f)

/** Volume (0..1) of a hull impact at [relativeSpeed], full at [HIT_FULL_VOLUME_C_FRACTION] of [c]. */
fun impactVolume(relativeSpeed: Float, c: Float): Float {
    val full = HIT_FULL_VOLUME_C_FRACTION * c
    if (full <= 0f) return IMPACT_MIN_VOLUME
    return (relativeSpeed / full).coerceIn(IMPACT_MIN_VOLUME, 1f)
}

/** True when a contact at [relativeSpeed] is a slow rub (scrape) rather than a hit. */
fun isScrape(relativeSpeed: Float, c: Float): Boolean = relativeSpeed < SCRAPE_SPEED_FRACTION * HIT_FULL_VOLUME_C_FRACTION * c

/** Tangential speed (as a fraction of c) below which a touching contact makes no scrape sound. */
const val SCRAPE_MIN_C_FRACTION = 0.01f

/** Tangential speed (as a fraction of c) at which the scrape loop is at full volume. */
const val SCRAPE_FULL_C_FRACTION = 0.1f

/**
 * Speed of the relative velocity ([relVx], [relVy]) along the contact tangent, given the (not necessarily unit)
 * contact normal ([nx], [ny]). A zero normal counts the whole relative speed as tangential.
 */
fun tangentialSpeed(relVx: Float, relVy: Float, nx: Float, ny: Float): Float {
    val nLen = sqrt(nx * nx + ny * ny)
    if (nLen <= 1e-6f) return sqrt(relVx * relVx + relVy * relVy)
    val along = (relVx * nx + relVy * ny) / nLen
    val total2 = relVx * relVx + relVy * relVy
    return sqrt(maxOf(0f, total2 - along * along))
}

/** Scrape loop volume (0..1) for [tangentialSpeed]: 0 below [SCRAPE_MIN_C_FRACTION] of [c], then rising (min [IMPACT_MIN_VOLUME]) to 1 at [SCRAPE_FULL_C_FRACTION]. */
fun scrapeVolume(tangentialSpeed: Float, c: Float): Float {
    if (c <= 0f || tangentialSpeed < SCRAPE_MIN_C_FRACTION * c) return 0f
    return (tangentialSpeed / (SCRAPE_FULL_C_FRACTION * c)).coerceIn(IMPACT_MIN_VOLUME, 1f)
}
