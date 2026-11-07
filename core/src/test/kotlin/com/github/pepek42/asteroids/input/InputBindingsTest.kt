package com.github.pepek42.asteroids.input

import com.badlogic.gdx.Input
import com.badlogic.gdx.Input.Keys
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.event.PlayerInputListener
import com.github.pepek42.asteroids.i18nKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class InputBindingsTest {
    @BeforeEach
    fun stubGdxApp() {
        // GameEventManager logs on init; give it a quiet Application when no earlier test installed one.
        if (Gdx.app == null) Gdx.app = mock<Application> { on { logLevel } doReturn Application.LOG_NONE }
    }

    private val i18n: (String) -> String = { "<$it>" }

    @Test
    fun customTableDrivesDispatch() {
        val table = listOf(Binding(InputAction.TOGGLE_PILOT_VIEW, InputTrigger.Key(Keys.P), "action_pilot_view"))
        val manager = GameEventManager(table)
        var calls = 0
        manager.addInputListener(object : PlayerInputListener {
            override fun togglePilotView() {
                calls++
            }
        })
        assertTrue(manager.keyDown(Keys.P))
        assertFalse(manager.keyDown(Keys.V))
        assertEquals(1, calls)
    }

    @Test
    fun forMenuFiltersDebugAndSchemes() {
        val noDebug = InputBindings.DEFAULT.forMenu(ControlScheme.MOUSE_HULL, includeDebug = false)
        assertEquals(InputBindings.DEFAULT.filter { !it.debugOnly && ControlScheme.MOUSE_HULL in it.schemes }, noDebug)
        assertEquals(8, noDebug.size)
        val withDebug = InputBindings.DEFAULT.forMenu(ControlScheme.MOUSE_HULL, includeDebug = true)
        assertEquals(InputAction.DEBUG_PANEL, withDebug.last().action)
        assertEquals(9, withDebug.size)

        val twin = Binding(InputAction.AIM, InputTrigger.MouseMove, "x", schemes = setOf(ControlScheme.TWIN_STICK))
        assertFalse(listOf(twin).forMenu(ControlScheme.MOUSE_HULL, true).contains(twin))
    }

    @Test
    fun tKeyIsTacticalOverlayAndKeyCodesAreUnique() {
        assertEquals(InputAction.TOGGLE_TACTICAL_OVERLAY, InputBindings.DEFAULT.keyAction(Keys.T))
        val keyCodes = InputBindings.DEFAULT.mapNotNull { (it.trigger as? InputTrigger.Key)?.code }
        assertEquals(keyCodes.distinct(), keyCodes)
    }

    @Test
    fun lookups() {
        assertEquals(InputAction.FIRE, InputBindings.DEFAULT.buttonAction(Input.Buttons.LEFT))
        assertEquals(InputAction.BLOCK, InputBindings.DEFAULT.keyAction(Keys.CONTROL_RIGHT))
        assertEquals(null, InputBindings.DEFAULT.keyAction(Keys.Q))
    }

    @Test
    fun triggerLabels() {
        assertEquals("R-Ctrl", triggerLabel(InputTrigger.Key(Keys.CONTROL_RIGHT), i18n))
        assertEquals(i18n("input_lmb"), triggerLabel(InputTrigger.MouseButton(Input.Buttons.LEFT), i18n))
        assertEquals(i18n("input_rmb"), triggerLabel(InputTrigger.MouseButton(Input.Buttons.RIGHT), i18n))
        assertEquals(i18n("input_mmb"), triggerLabel(InputTrigger.MouseButton(Input.Buttons.MIDDLE), i18n))
        assertEquals(i18n("input_mouse"), triggerLabel(InputTrigger.MouseMove, i18n))
        assertEquals(i18n("input_wheel"), triggerLabel(InputTrigger.Wheel, i18n))
    }

    @Test
    fun i18nKeysExist() {
        val needed = InputBindings.DEFAULT.map { it.labelKey } +
            listOf("input_lmb", "input_rmb", "input_mmb", "input_mouse", "input_wheel")
        val keys = i18nKeys("messages.properties")
        assertEquals(emptyList<String>(), needed.filter { it !in keys }, "Missing in messages.properties")
    }

    @Test
    fun selectableSchemes() {
        assertEquals(listOf(ControlScheme.MOUSE_HULL, ControlScheme.TWIN_STICK, ControlScheme.AUTO_AIM), ControlScheme.selectable())
    }

    @Test
    fun autoAimHasItsOwnFireRow() {
        fun fireLabels(scheme: ControlScheme) =
            InputBindings.DEFAULT.forMenu(scheme, includeDebug = false).filter { it.action == InputAction.FIRE }.map { it.labelKey }
        assertEquals(listOf("action_fire_manual"), fireLabels(ControlScheme.AUTO_AIM))
        assertEquals(listOf("action_fire"), fireLabels(ControlScheme.MOUSE_HULL))
        assertEquals(listOf("action_fire"), fireLabels(ControlScheme.TWIN_STICK))
        // Dispatch ignores schemes, so LMB still resolves to FIRE.
        assertEquals(InputAction.FIRE, InputBindings.DEFAULT.buttonAction(Input.Buttons.LEFT))
    }
}
