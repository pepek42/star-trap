package com.github.pepek42.asteroids.system

import com.badlogic.gdx.audio.Sound
import com.github.pepek42.asteroids.audio.COMPUTER_MAX_VOICES
import com.github.pepek42.asteroids.audio.COMPUTER_PITCH_MAX
import com.github.pepek42.asteroids.audio.COMPUTER_PITCH_MIN
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
import com.github.pepek42.asteroids.settings.SoundSettings
import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ComputerSoundSystemTest {
    private val relativity = RelativitySettings()
    private val c = relativity.c
    private val settings = SoundSettings()
    private val sound: Sound = mock()
    private val bank: SoundBank = mock<SoundBank>().also { whenever(it[any()]).thenReturn(sound) }
    private val queue = ComputerSoundQueue(settings)
    private val observer = ObserverState()
    private val clock = GameClock()
    private val system = ComputerSoundSystem(bank, queue, settings, relativity, observer, clock)

    private fun tick(worldTime: Float) {
        clock.worldTime = worldTime
        system.update(0.016f)
    }

    @Test
    fun eventPlaysAfterLightTravelTimeNotBefore() {
        val d = 10f
        queue.push(ComputerSoundKind.ASTEROID_BREAK_SMALL, d, 0f, 0f, 0f, 0f)
        tick(d / c * 0.99f)
        verify(sound, never()).play(any(), any(), any())
        assertEquals(1, queue.size)
        tick(d / c * 1.01f)
        verify(sound, times(1)).play(any(), any(), any())
        assertEquals(0, queue.size)
    }

    @Test
    fun lightArrivalPureRule() {
        assertFalse(lightHasArrived(0f, 0f, 5f, 0f, 1f, 0.5f, c))
        assertFalse(lightHasArrived(0f, 0f, 5f, 0f, 0f, 4f / c, c))
        assertTrue(lightHasArrived(0f, 0f, 5f, 0f, 0f, 5f / c + 0.001f, c))
    }

    @Test
    fun approachingSourcePlaysHigherAndRecedingLower() {
        // Source at +x moving toward the pilot at the origin (-x), and then away.
        val toward = Vector2(-0.3f, 0f)
        val away = Vector2(0.3f, 0f)
        val nx = -1f // source -> observer
        val dTo = dopplerFactor(observer.betaRaw, toward, nx, 0f)
        val dAway = dopplerFactor(observer.betaRaw, away, nx, 0f)
        assertTrue(computerPitch(dTo) > 1f)
        assertTrue(computerPitch(dAway) < 1f)

        queue.push(ComputerSoundKind.ASTEROID_BREAK_LARGE, 10f, 0f, -0.3f * c, 0f, 0f)
        tick(10f)
        val pitch = argumentCaptor<Float>()
        verify(sound).play(any(), pitch.capture(), any())
        assertTrue(pitch.firstValue > 1f)
    }

    @Test
    fun pitchIncludesGravitationalShift() {
        observer.gravFactor = 1f
        queue.push(ComputerSoundKind.ASTEROID_BREAK_LARGE, 10f, 0f, 0f, 0f, 0f, 0.8f)
        tick(10f)
        val pitch = argumentCaptor<Float>()
        verify(sound).play(any(), pitch.capture(), any())
        assertEquals(computerPitch(0.8f), pitch.firstValue, 1e-4f)
    }

    @Test
    fun pitchIsClamped() {
        assertEquals(COMPUTER_PITCH_MAX, computerPitch(50f))
        assertEquals(COMPUTER_PITCH_MIN, computerPitch(0.01f))
        assertEquals(1f, computerPitch(Float.NaN))
    }

    @Test
    fun toggleOffQueuesAndPlaysNothing() {
        settings.computerSounds = false
        queue.push(ComputerSoundKind.ASTEROID_BREAK_SMALL, 1f, 0f, 0f, 0f, 0f)
        assertEquals(0, queue.size)
        tick(100f)
        verify(sound, never()).play(any(), any(), any())
    }

    @Test
    fun toggleOffDropsPendingEvents() {
        queue.push(ComputerSoundKind.ASTEROID_BREAK_SMALL, 100f, 0f, 0f, 0f, 0f)
        settings.computerSounds = false
        tick(100f)
        assertEquals(0, queue.size)
        verify(sound, never()).play(any(), any(), any())
    }

    @Test
    fun volumeFallsWithDistanceAndPanFollowsBearing() {
        assertEquals(1f, computerVolume(0f, c))
        assertTrue(computerVolume(10f, c) > computerVolume(40f, c))
        assertEquals(0.5f, computerVolume(c, c), 1e-4f)
        assertTrue(computerPan(5f, 0f) > 0.9f)
        assertTrue(computerPan(-5f, 0f) < -0.9f)
        assertEquals(0f, computerPan(0f, 0f))
    }

    @Test
    fun voiceCapDropsExtraEvents() {
        val limiter = VoiceLimiter()
        repeat(COMPUTER_MAX_VOICES) { assertTrue(limiter.tryAcquire(0f, 1f)) }
        assertFalse(limiter.tryAcquire(0.5f, 1f))
        assertTrue(limiter.tryAcquire(1.5f, 1f))
    }

    @Test
    fun systemRespectsVoiceCap() {
        repeat(COMPUTER_MAX_VOICES + 3) { queue.push(ComputerSoundKind.ASTEROID_BREAK_SMALL, 1f, 0f, 0f, 0f, 0f) }
        tick(10f)
        verify(sound, times(COMPUTER_MAX_VOICES)).play(any(), any(), any())
    }
}
