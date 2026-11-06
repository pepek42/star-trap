package com.github.pepek42.asteroids.audio

import com.badlogic.gdx.physics.box2d.Body

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

    private val touching = arrayOfNulls<Body>(MAX_TOUCHING)
    var touchingCount = 0
        private set

    /** [other] started touching the player's hull (call once per contact; the same body may appear several times). */
    fun beginTouch(other: Body) {
        if (touchingCount >= MAX_TOUCHING) return
        touching[touchingCount++] = other
    }

    /** One contact with [body] ended; ignored when [body] is not tracked. */
    fun endTouch(body: Body) {
        for (i in 0 until touchingCount) {
            if (touching[i] === body) {
                touching[i] = touching[touchingCount - 1]
                touching[touchingCount - 1] = null
                touchingCount--
                return
            }
        }
    }

    fun touchingAt(i: Int): Body = touching[i]!!

    /** Forgets all touching contacts (level reset). */
    fun clearTouching() {
        for (i in 0 until touchingCount) touching[i] = null
        touchingCount = 0
    }

    companion object {
        const val MAX_TOUCHING = 8
    }
}
