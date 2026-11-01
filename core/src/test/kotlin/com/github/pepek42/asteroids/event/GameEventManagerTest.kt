package com.github.pepek42.asteroids.event

import com.badlogic.gdx.Input.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GameEventManagerTest {
    @Test
    fun f1CallsDebugListenerOnce() {
        val manager = GameEventManager()
        var calls = 0
        manager.addDebugListener { calls++ }

        assertTrue(manager.keyDown(Keys.F1))
        assertEquals(1, calls)
    }

    @Test
    fun f1StillCallsDebugListenerWhenPlayerInputIsIgnored() {
        val manager = GameEventManager()
        var calls = 0
        manager.addDebugListener { calls++ }
        manager.ignorePlayerInputs()

        manager.keyDown(Keys.F1)
        assertEquals(1, calls)
    }

    @Test
    fun unhandledKeyReturnsFalse() {
        val manager = GameEventManager()
        assertFalse(manager.keyDown(Keys.A))
    }
}
