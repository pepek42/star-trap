package com.github.pepek42.asteroids.settings

const val MASTER_VOLUME_DEFAULT = 0.8f
const val HULL_SOUND_VOLUME_DEFAULT = 0.8f
const val COMPUTER_SOUND_VOLUME_DEFAULT = 0.8f

/** Default of [SoundSettings.computerSounds] (ST-136): the computer sonifies what it sees. */
const val COMPUTER_SOUNDS_DEFAULT = true

/**
 * Player sound preferences, volumes 0..1 (setters clamp). [computerSounds] (ST-136) switches the computer layer: off is the
 * realistic silent vacuum.
 */
class SoundSettings(
    master: Float = MASTER_VOLUME_DEFAULT,
    hull: Float = HULL_SOUND_VOLUME_DEFAULT,
    computer: Float = COMPUTER_SOUND_VOLUME_DEFAULT,
    var computerSounds: Boolean = COMPUTER_SOUNDS_DEFAULT,
) {
    var masterVolume: Float = master.coerceIn(0f, 1f)
        set(value) { field = value.coerceIn(0f, 1f) }
    var hullSoundVolume: Float = hull.coerceIn(0f, 1f)
        set(value) { field = value.coerceIn(0f, 1f) }

    var computerSoundVolume: Float = computer.coerceIn(0f, 1f)
        set(value) { field = value.coerceIn(0f, 1f) }

    /** Gain for the hull layer: master times hull volume. */
    val hullGain: Float get() = masterVolume * hullSoundVolume

    /** Gain for the computer layer: master times computer volume. */
    val computerGain: Float get() = masterVolume * computerSoundVolume
}
