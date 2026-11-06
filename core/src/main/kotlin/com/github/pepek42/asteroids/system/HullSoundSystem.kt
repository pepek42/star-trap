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
import com.github.pepek42.asteroids.audio.scrapeVolume
import com.github.pepek42.asteroids.audio.tangentialSpeed
import com.github.pepek42.asteroids.component.bodyCmpOptional
import com.github.pepek42.asteroids.physics.RelativitySettings
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
    private val relativity: RelativitySettings = RelativitySettings(),
) : EntitySystem() {
    private var players: ImmutableArray<Entity>? = null
    private var loopId = -1L
    private var volume = 0f
    private var pitch = enginePitch(0f)
    private var scrapeId = -1L

    // Cached handles: no per-frame AssetManager lookup by string.
    private val engineSound = bank[SoundId.ENGINE_LOOP]
    private val laserSound = bank[SoundId.LASER_FIRE]
    private val hitSound = bank[SoundId.HULL_HIT]
    private val scrapeSound = bank[SoundId.HULL_SCRAPE]

    /** While true (focus lost / paused) the system makes no sound and drops queued events. */
    var paused = false
        set(value) {
            if (value) silence()
            field = value
        }

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        players = engine.getEntitiesFor(allOf(PlayerComponent::class, MoveComponent::class).get())
    }

    override fun update(deltaTime: Float) {
        if (paused) {
            queue.clear()
            return
        }
        val gain = settings.hullGain
        val player = players?.let { if (it.size() > 0) it[0] else null }
        val thrusters = player?.moveCmp?.thrusters ?: 0f
        val dt = deltaTime
        volume = smoothToward(volume, engineVolume(thrusters, gain), dt, ENGINE_SMOOTHING_TIME)
        pitch = smoothToward(pitch, enginePitch(thrusters), dt, ENGINE_SMOOTHING_TIME)
        val engine = engineSound
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
                HullSoundKind.LASER_FIRE -> laserSound.play(v)
                HullSoundKind.HULL_HIT -> hitSound.play(v)
                HullSoundKind.HULL_SCRAPE -> scrapeSound.play(v)
            }
        }
        queue.clear()
        updateScrape(player, gain)
    }

    /** Keeps one scrape loop playing while the player touches something with a tangential speed above the threshold. */
    private fun updateScrape(player: Entity?, gain: Float) {
        val playerBody = player?.bodyCmpOptional?.body
        var best = 0f
        if (playerBody != null) {
            val pv = playerBody.linearVelocity
            val pp = playerBody.position
            for (i in 0 until queue.touchingCount) {
                val other = queue.touchingAt(i)
                val ov = other.linearVelocity
                val op = other.position
                best = maxOf(
                    best,
                    tangentialSpeed(pv.x - ov.x, pv.y - ov.y, pp.x - op.x, pp.y - op.y),
                )
            }
        }
        val v = scrapeVolume(best, relativity.c) * gain
        if (queue.touchingCount == 0 || v <= 0f) {
            if (scrapeId != -1L) {
                scrapeSound.stop(scrapeId)
                scrapeId = -1L
            }
        } else if (scrapeId == -1L) {
            scrapeId = scrapeSound.loop(v)
        } else {
            scrapeSound.setVolume(scrapeId, v)
        }
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
        scrapeId = -1L
        queue.clearTouching()
        volume = 0f
        pitch = enginePitch(0f)
        queue.clear()
    }
}
