package com.github.pepek42.asteroids.audio

/** What the ship's own hull heard this frame. */
enum class HullSoundKind { LASER_FIRE, HULL_HIT, HULL_SCRAPE }

/**
 * Fixed-size, allocation-free event queue filled by `ContactSystem` and `WeaponSystem` (inside the physics step) and
 * drained once per frame by `HullSoundSystem`. Holds no audio, so the headless balance runner can use it or leave it null.
 * When full, new events are dropped.
 */
class HullSoundQueue(private val capacity: Int = 16) {
    private val kinds = arrayOfNulls<HullSoundKind>(capacity)
    private val volumes = FloatArray(capacity)
    var size = 0
        private set

    fun push(kind: HullSoundKind, volume: Float = 1f) {
        if (size >= capacity) return
        kinds[size] = kind
        volumes[size] = volume
        size++
    }

    fun kindAt(i: Int): HullSoundKind = kinds[i]!!
    fun volumeAt(i: Int): Float = volumes[i]
    fun clear() {
        size = 0
    }
}
