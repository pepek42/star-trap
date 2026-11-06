package com.github.pepek42.asteroids.audio

import kotlin.math.sqrt

/** Lowest pitch the computer plays (a strongly redshifted source, one octave down). */
const val COMPUTER_PITCH_MIN = 0.5f

/** Highest pitch the computer plays (a strongly blueshifted source, one octave up). */
const val COMPUTER_PITCH_MAX = 2f

/**
 * Falloff distance d0 as light travel time (s): the computer plays a source at `d0 = c * COMPUTER_FALLOFF_LIGHT_SECONDS`
 * at half volume. 1 light-second is 20 u at the default c, a bit under half a screen width.
 */
const val COMPUTER_FALLOFF_LIGHT_SECONDS = 1f

/** Maximum simultaneous computer-layer sound instances. */
const val COMPUTER_MAX_VOICES = 8

/** How long (s) one computer one-shot occupies a voice at pitch 1 (the longest `synth_` file is about 1.3 s). */
const val COMPUTER_VOICE_SECONDS = 1.5f

/**
 * True when the light of an event at ([ex], [ey]) that happened at world time [eventTime] has reached the pilot at
 * ([px], [py]) by world time [now]: `|x_pilot - x_event| <= c (now - t_event)`. Never true before the event.
 */
fun lightHasArrived(px: Float, py: Float, ex: Float, ey: Float, eventTime: Float, now: Float, c: Float): Boolean {
    val dt = now - eventTime
    if (dt < 0f || c <= 0f) return false
    val dx = (px - ex).toDouble()
    val dy = (py - ey).toDouble()
    val reach = c.toDouble() * dt
    return dx * dx + dy * dy <= reach * reach
}

/** Pitch for a source with Doppler factor [doppler] (> 1 approaching): the factor itself, clamped to the computer's range. */
fun computerPitch(doppler: Float): Float {
    if (!doppler.isFinite()) return 1f
    return doppler.coerceIn(COMPUTER_PITCH_MIN, COMPUTER_PITCH_MAX)
}

/**
 * Volume (0..1) at distance [dist]: `1 / (1 + dist / d0)` with `d0 = c * COMPUTER_FALLOFF_LIGHT_SECONDS`. A design choice of
 * the computer (it normalises loudness for readability), not acoustics.
 */
fun computerVolume(dist: Float, c: Float): Float {
    val d0 = c * COMPUTER_FALLOFF_LIGHT_SECONDS
    if (d0 <= 0f) return 1f
    return 1f / (1f + dist.coerceAtLeast(0f) / d0)
}

/**
 * Stereo pan (-1 left .. 1 right) of a source at offset ([dx], [dy]) from the pilot, from the bearing on the screen
 * (screen x is the arena x axis): the sine of the angle off straight ahead-or-behind, `dx / dist`. 0 when on top of the pilot.
 */
fun computerPan(dx: Float, dy: Float): Float {
    val dist = sqrt(dx * dx + dy * dy)
    if (dist < 1e-4f) return 0f
    return (dx / dist).coerceIn(-1f, 1f)
}

/** What the computer turns into sound. Enemy ships and their weapons come later. */
enum class ComputerSoundKind(val soundId: SoundId) {
    ASTEROID_BREAK_SMALL(SoundId.SYNTH_ASTEROID_BREAK_SMALL),
    ASTEROID_BREAK_MEDIUM(SoundId.SYNTH_ASTEROID_BREAK_MEDIUM),
    ASTEROID_BREAK_LARGE(SoundId.SYNTH_ASTEROID_BREAK_LARGE),
}

/**
 * Fixed set of voices (expiry times in the system's clock) that caps concurrent computer sounds. Allocation-free.
 */
class VoiceLimiter(private val maxVoices: Int = COMPUTER_MAX_VOICES) {
    private val endTimes = FloatArray(maxVoices)
    private var used = 0

    /** Takes a voice at [now] for [duration] seconds. False (nothing taken) when [maxVoices] are still busy. */
    fun tryAcquire(now: Float, duration: Float): Boolean {
        var i = 0
        while (i < used) {
            if (endTimes[i] <= now) {
                endTimes[i] = endTimes[--used]
            } else {
                i++
            }
        }
        if (used >= maxVoices) return false
        endTimes[used++] = now + duration
        return true
    }

    fun clear() {
        used = 0
    }
}
