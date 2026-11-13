package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.audio.COMPUTER_VOICE_SECONDS
import com.github.pepek42.asteroids.audio.ComputerSoundKind
import com.github.pepek42.asteroids.audio.ComputerSoundQueue
import com.github.pepek42.asteroids.audio.SoundBank
import com.github.pepek42.asteroids.audio.VoiceLimiter
import com.github.pepek42.asteroids.audio.computerPan
import com.github.pepek42.asteroids.audio.computerPitch
import com.github.pepek42.asteroids.audio.computerVolume
import com.github.pepek42.asteroids.audio.lightHasArrived
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.dopplerFactor
import com.github.pepek42.asteroids.render.gravDoppler
import com.github.pepek42.asteroids.settings.SoundSettings
import kotlin.math.sqrt

/**
 * Presentation system (after [HullSoundSystem], ST-136): the ship's computer turns what it sees into sound (assist layer,
 * no sound travels through vacuum). Events wait in [ComputerSoundQueue] with their arena position and world time and play
 * once their light reaches the pilot's current position (`|x_pilot - x_event| <= c (t - t_event)`, [lightHasArrived]), never
 * earlier. Pitch is the Doppler factor of the source as seen by the pilot (true [ObserverState.betaRaw], only with
 * relativity on) times the gravitational `g_e / g_o` ([gravDoppler], ST-203), volume falls with distance ([computerVolume]), pan follows the bearing ([computerPan]). At most
 * `COMPUTER_MAX_VOICES` play at once; extra events are dropped. With [SoundSettings.computerSounds] off it queues and plays
 * nothing. Allocation-free per frame.
 */
class ComputerSoundSystem(
    bank: SoundBank,
    private val queue: ComputerSoundQueue,
    private val settings: SoundSettings,
    private val relativity: RelativitySettings,
    private val observer: ObserverState,
    private val clock: GameClock,
) : EntitySystem() {
    private val sounds: Array<Sound> = Array(ComputerSoundKind.entries.size) { bank[ComputerSoundKind.entries[it].soundId] }
    private val voices = VoiceLimiter()
    private val betaSrc = Vector2()

    /** Real (pilot) time in seconds since the system started; voice lifetimes run on it. */
    private var realTime = 0f

    /** While true (focus lost / paused) the system makes no sound and drops queued events. */
    var paused = false
        set(value) {
            if (value) silence()
            field = value
        }

    override fun update(deltaTime: Float) {
        realTime += deltaTime
        if (paused || !settings.computerSounds) {
            queue.clear()
            return
        }
        val now = clock.worldTime
        val c = relativity.c
        val px = observer.position.x
        val py = observer.position.y
        var i = queue.size - 1
        while (i >= 0) {
            if (lightHasArrived(px, py, queue.xAt(i), queue.yAt(i), queue.timeAt(i), now, c)) {
                play(i, px, py, c)
                queue.removeAt(i)
            }
            i--
        }
    }

    private fun play(i: Int, px: Float, py: Float, c: Float) {
        // Direction from source to observer, in the arena frame.
        val dx = px - queue.xAt(i)
        val dy = py - queue.yAt(i)
        val dist = sqrt(dx * dx + dy * dy)
        var doppler = 1f
        if (relativity.relativityEnabled && c > 0f && dist > MIN_DOPPLER_DISTANCE) {
            betaSrc.set(queue.vxAt(i) / c, queue.vyAt(i) / c)
            doppler = dopplerFactor(observer.betaRaw, betaSrc, dx / dist, dy / dist)
        }
        // Gravitational shift as in the colours (ST-195): g_e of the emitter at the event over g_o now (row 53).
        val pitch = computerPitch(gravDoppler(doppler, queue.gravFactorAt(i), observer.gravFactor))
        if (!voices.tryAcquire(realTime, COMPUTER_VOICE_SECONDS / pitch)) return
        val volume = computerVolume(dist, c) * settings.computerGain
        sounds[queue.kindAt(i).ordinal].play(volume, pitch, computerPan(-dx, -dy))
    }

    /** Stops everything and forgets pending events (pause, screen change, level start). */
    fun silence() {
        for (s in sounds) s.stop()
        voices.clear()
        queue.clear()
    }

    private companion object {
        /** Closer than this (u) the propagation direction is undefined and the Doppler factor is 1. */
        const val MIN_DOPPLER_DISTANCE = 1e-3f
    }
}
