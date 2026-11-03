package com.github.pepek42.asteroids.input

import com.badlogic.gdx.Input
import com.badlogic.gdx.Input.Keys
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.event.PlayerInputListener
import com.github.pepek42.asteroids.i18nKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InputBindingsTest {
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
        assertEquals(InputBindings.DEFAULT.dropLast(1), noDebug)
        assertEquals(6, noDebug.size)
        val withDebug = InputBindings.DEFAULT.forMenu(ControlScheme.MOUSE_HULL, includeDebug = true)
        assertEquals(InputAction.DEBUG_PANEL, withDebug.last().action)
        assertEquals(7, withDebug.size)

        val twin = Binding(InputAction.AIM, InputTrigger.MouseMove, "x", schemes = setOf(ControlScheme.TWIN_STICK))
        assertFalse(listOf(twin).forMenu(ControlScheme.MOUSE_HULL, true).contains(twin))
    }

    @Test
    fun lookups() {
        assertEquals(InputAction.FIRE, InputBindings.DEFAULT.buttonAction(Input.Buttons.LEFT))
        assertEquals(InputAction.BLOCK, InputBindings.DEFAULT.keyAction(Keys.CONTROL_RIGHT))
        assertEquals(null, InputBindings.DEFAULT.keyAction(Keys.A))
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
    fun i18nKeysExistInBothFiles() {
        val needed = InputBindings.DEFAULT.map { it.labelKey } +
            listOf("input_lmb", "input_rmb", "input_mmb", "input_mouse", "input_wheel")
        listOf("messages.properties", "messages_pl.properties").forEach { file ->
            val keys = i18nKeys(file)
            assertEquals(emptyList<String>(), needed.filter { it !in keys }, "Missing in $file")
        }
    }

    @Test
    fun selectableSchemes() {
        assertEquals(listOf(ControlScheme.MOUSE_HULL), ControlScheme.selectable())
    }
}
