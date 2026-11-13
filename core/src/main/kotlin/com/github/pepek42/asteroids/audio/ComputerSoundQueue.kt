package com.github.pepek42.asteroids.audio

import com.github.pepek42.asteroids.settings.SoundSettings

/**
 * Fixed-size, allocation-free list of events the ship's computer has yet to "see" (ST-136). Systems that cause an event
 * (`DeathSystem` for asteroid break-ups) [push] its arena position, velocity, world time and gravitational clock factor; `ComputerSoundSystem` plays it
 * when the event's light reaches the pilot and then [removeAt]s it. Holds no audio, so the headless balance runner leaves it
 * null. Nothing is queued while [SoundSettings.computerSounds] is off. When full, new events are dropped.
 */
class ComputerSoundQueue(private val settings: SoundSettings, private val capacity: Int = 32) {
    private val kinds = arrayOfNulls<ComputerSoundKind>(capacity)
    private val xs = FloatArray(capacity)
    private val ys = FloatArray(capacity)
    private val vxs = FloatArray(capacity)
    private val vys = FloatArray(capacity)
    private val times = FloatArray(capacity)
    private val gravs = FloatArray(capacity)
    var size = 0
        private set

    /** Queues an event at arena position ([x], [y]) with arena velocity ([vx], [vy]) at world time [time]. */
    fun push(kind: ComputerSoundKind, x: Float, y: Float, vx: Float, vy: Float, time: Float, gravFactor: Float = 1f) {
        if (!settings.computerSounds || size >= capacity) return
        kinds[size] = kind
        xs[size] = x
        ys[size] = y
        vxs[size] = vx
        vys[size] = vy
        times[size] = time
        gravs[size] = gravFactor
        size++
    }

    fun kindAt(i: Int): ComputerSoundKind = kinds[i]!!
    fun xAt(i: Int): Float = xs[i]
    fun yAt(i: Int): Float = ys[i]
    fun vxAt(i: Int): Float = vxs[i]
    fun vyAt(i: Int): Float = vys[i]
    fun timeAt(i: Int): Float = times[i]

    /** The emitter's gravitational clock factor at the event (`g_e`, 1 without gravity). */
    fun gravFactorAt(i: Int): Float = gravs[i]

    /** Removes event [i] by moving the last one into its slot (order is not kept). */
    fun removeAt(i: Int) {
        val last = size - 1
        kinds[i] = kinds[last]
        xs[i] = xs[last]
        ys[i] = ys[last]
        vxs[i] = vxs[last]
        vys[i] = vys[last]
        times[i] = times[last]
        gravs[i] = gravs[last]
        kinds[last] = null
        size = last
    }

    fun clear() {
        for (i in 0 until size) kinds[i] = null
        size = 0
    }
}
