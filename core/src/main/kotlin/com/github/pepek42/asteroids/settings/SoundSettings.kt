package com.github.pepek42.asteroids.settings

const val MASTER_VOLUME_DEFAULT = 0.8f
const val HULL_SOUND_VOLUME_DEFAULT = 0.8f

/** Player volume preferences (ST-135), both 0..1. Setters clamp. */
class SoundSettings(master: Float = MASTER_VOLUME_DEFAULT, hull: Float = HULL_SOUND_VOLUME_DEFAULT) {
    var masterVolume: Float = master.coerceIn(0f, 1f)
        set(value) { field = value.coerceIn(0f, 1f) }
    var hullSoundVolume: Float = hull.coerceIn(0f, 1f)
        set(value) { field = value.coerceIn(0f, 1f) }

    /** Gain for the hull layer: master times hull volume. */
    val hullGain: Float get() = masterVolume * hullSoundVolume
}
