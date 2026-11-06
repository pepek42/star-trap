package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.audio.Sound
import com.github.pepek42.asteroids.audio.ENGINE_SMOOTHING_TIME
import com.github.pepek42.asteroids.audio.ENGINE_STOP_VOLUME
import com.github.pepek42.asteroids.audio.HullSoundKind
import com.github.pepek42.asteroids.audio.HullSoundQueue
import com.github.pepek42.asteroids.audio.SoundBank
import com.github.pepek42.asteroids.audio.SoundId
import com.github.pepek42.asteroids.audio.engineVolume
import com.github.pepek42.asteroids.audio.enginePitch
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.moveCmp
import com.github.pepek42.asteroids.physics.smoothToward
import com.github.pepek42.asteroids.settings.SoundSettings
import ktx.ashley.allOf

/**
 * Presentation system (after render): the sounds the ship itself makes (ST-135, sim layer). The engine loop follows the
 * player's [MoveComponent.thrusters]; laser, hit and scrape sounds come from [HullSoundQueue]. Pitch is never
 * Doppler- or time-warp-shifted: the sound lives in the ship's own frame. Allocation-free per frame.
 *
 * TODO alarms (`alarm_proximity`, `alarm_hull_damage`): there is no proximity or low-hull state yet, see docs/tech/sound.md.
 */
class HullSoundSystem(
    private val bank: SoundBank,
    private val queue: HullSoundQueue,
    private val settings: SoundSettings,
) : EntitySystem() {
    private var players: ImmutableArray<Entity>? = null
    private var loopId = -1L
    private var volume = 0f
    private var pitch = enginePitch(0f)

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        players = engine.getEntitiesFor(allOf(PlayerComponent::class, MoveComponent::class).get())
    }

    override fun update(deltaTime: Float) {
        val gain = settings.hullGain
        val player = players?.let { if (it.size() > 0) it[0] else null }
        val thrusters = player?.moveCmp?.thrusters ?: 0f
        val dt = deltaTime
        volume = smoothToward(volume, engineVolume(thrusters, gain), dt, ENGINE_SMOOTHING_TIME)
        pitch = smoothToward(pitch, enginePitch(thrusters), dt, ENGINE_SMOOTHING_TIME)
        val engine = bank[SoundId.ENGINE_LOOP]
        if (volume < ENGINE_STOP_VOLUME) {
            stopLoop(engine)
        } else {
            if (loopId == -1L) loopId = engine.loop(volume, pitch, 0f)
            else {
                engine.setVolume(loopId, volume)
                engine.setPitch(loopId, pitch)
            }
        }
        for (i in 0 until queue.size) {
            val v = queue.volumeAt(i) * gain
            when (queue.kindAt(i)) {
                HullSoundKind.LASER_FIRE -> bank[SoundId.LASER_FIRE].play(v)
                HullSoundKind.HULL_HIT -> bank[SoundId.HULL_HIT].play(v)
                HullSoundKind.HULL_SCRAPE -> bank[SoundId.HULL_SCRAPE].play(v)
            }
        }
        queue.clear()
    }

    private fun stopLoop(sound: Sound) {
        if (loopId != -1L) {
            sound.stop(loopId)
            loopId = -1L
        }
    }

    /** Stops everything (pause, screen change). */
    fun silence() {
        for (id in SoundId.entries) bank[id].stop()
        loopId = -1L
        volume = 0f
        pitch = enginePitch(0f)
        queue.clear()
    }
}
