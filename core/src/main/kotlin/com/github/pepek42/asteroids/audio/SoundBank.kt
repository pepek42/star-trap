package com.github.pepek42.asteroids.audio

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.audio.Sound

/** Generated sound files (`assets/sounds`, see docs/tech/sound.md). */
enum class SoundId(val path: String) {
    ENGINE_LOOP("sounds/engine_loop.wav"),
    LASER_FIRE("sounds/laser_fire.wav"),
    HULL_HIT("sounds/hull_hit.wav"),
    HULL_SCRAPE("sounds/hull_scrape.wav"),
    ALARM_PROXIMITY("sounds/alarm_proximity.wav"),
    ALARM_HULL_DAMAGE("sounds/alarm_hull_damage.wav"),
    SYNTH_ASTEROID_BREAK_SMALL("sounds/synth_asteroid_break_small.wav"),
    SYNTH_ASTEROID_BREAK_MEDIUM("sounds/synth_asteroid_break_medium.wav"),
    SYNTH_ASTEROID_BREAK_LARGE("sounds/synth_asteroid_break_large.wav"),
    SYNTH_UI_LOCK("sounds/synth_ui_lock.wav"),
}

/** DI singleton holding the libGDX [Sound]s the [AssetManager] loaded. Create after `finishLoading()`. */
class SoundBank(private val assets: AssetManager) {
    operator fun get(id: SoundId): Sound = assets.get(id.path, Sound::class.java)

    companion object {
        /** Queues every sound on [assets]; call before `finishLoading()`. */
        fun queueLoading(assets: AssetManager) {
            for (id in SoundId.entries) assets.load(id.path, Sound::class.java)
        }
    }
}
