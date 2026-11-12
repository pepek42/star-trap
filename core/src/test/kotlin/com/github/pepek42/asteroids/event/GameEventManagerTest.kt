package com.github.pepek42.asteroids.event

import com.badlogic.gdx.Input.Keys
import com.github.pepek42.asteroids.event.PlayerInputListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class GameEventManagerTest {
    @BeforeEach
    fun stubGdxApp() {
        // GameEventManager logs on init; give it a quiet Application when no earlier test installed one.
        if (Gdx.app == null) Gdx.app = mock<Application> { on { logLevel } doReturn Application.LOG_NONE }
    }

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
        assertFalse(manager.keyDown(Keys.Q))
    }

    @Test
    fun vTogglesPilotViewOnListeners() {
        val manager = GameEventManager()
        var calls = 0
        manager.addInputListener(object : PlayerInputListener {
            override fun togglePilotView() {
                calls++
            }
        })

        assertTrue(manager.keyDown(Keys.V))
        assertEquals(1, calls)
    }

    @Test
    fun tTogglesTacticalOverlayOnListenersUnlessIgnored() {
        val manager = GameEventManager()
        var calls = 0
        manager.addInputListener(object : PlayerInputListener {
            override fun toggleTacticalOverlay() {
                calls++
            }
        })

        assertTrue(manager.keyDown(Keys.T))
        assertEquals(1, calls)

        manager.ignorePlayerInputs()
        assertFalse(manager.keyDown(Keys.T))
        assertEquals(1, calls)
    }

    @Test
    fun moveKeysDispatchNormalisedAxis() {
        val manager = GameEventManager()
        manager.addInputListener(object : PlayerInputListener {
            override fun moveAxis(x: Float, y: Float) {
                lastX = x
                lastY = y
            }
        })
        assertTrue(manager.keyDown(Keys.W))
        assertTrue(manager.keyDown(Keys.D))
        assertEquals(0.7071f, lastX, 1e-4f)
        assertEquals(0.7071f, lastY, 1e-4f)
        assertTrue(manager.keyUp(Keys.W))
        assertEquals(1f, lastX, 1e-4f)
        assertEquals(0f, lastY, 1e-4f)
        manager.ignorePlayerInputs()
        assertEquals(0f, lastX, 1e-4f)
        assertEquals(0f, lastY, 1e-4f)
        assertFalse(manager.keyDown(Keys.W))
        assertFalse(manager.keyDown(Keys.Q))
    }

    private var lastX = -1f
    private var lastY = -1f

    @Test
    fun whileWorldPausedOnlyEscAndEnterReachListeners() {
        val manager = GameEventManager()
        var quits = 0
        var dismisses = 0
        var fires = 0
        var pilotToggles = 0
        manager.addInputListener(object : PlayerInputListener {
            override fun quitToMenu() { quits++ }
            override fun dismissBriefing() { dismisses++ }
            override fun fire(fire: Boolean) { if (fire) fires++ }
            override fun togglePilotView() { pilotToggles++ }
        })
        manager.setWorldPaused(true)

        assertFalse(manager.touchDown(0, 0, 0, com.badlogic.gdx.Input.Buttons.LEFT))
        assertFalse(manager.keyDown(Keys.V))
        assertTrue(manager.keyDown(Keys.ENTER))
        assertTrue(manager.keyDown(Keys.ESCAPE))
        assertEquals(0, fires)
        assertEquals(0, pilotToggles)
        assertEquals(1, dismisses)
        assertEquals(1, quits)

        manager.setWorldPaused(false)
        assertTrue(manager.keyDown(Keys.V))
        assertEquals(1, pilotToggles)
    }
}
