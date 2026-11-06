package com.github.pepek42.asteroids.audio

import kotlin.math.abs

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
