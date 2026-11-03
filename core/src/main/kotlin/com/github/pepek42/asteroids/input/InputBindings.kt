package com.github.pepek42.asteroids.input

import com.badlogic.gdx.Input
import com.badlogic.gdx.Input.Keys

enum class InputAction { AIM, FIRE, THRUST, ZOOM, TOGGLE_PILOT_VIEW, BLOCK, DEBUG_PANEL }

sealed interface InputTrigger {
    data class Key(val code: Int) : InputTrigger
    data class MouseButton(val button: Int) : InputTrigger
    data object MouseMove : InputTrigger
    data object Wheel : InputTrigger
}

/** One row of the bindings table. [schemes] is a menu filter only; dispatch ignores it. */
data class Binding(
    val action: InputAction,
    val trigger: InputTrigger,
    val labelKey: String,
    val debugOnly: Boolean = false,
    val schemes: Set<ControlScheme> = ControlScheme.entries.toSet(),
)

/** Single source of truth for input bindings: read by `GameEventManager` and listed by the menu. */
object InputBindings {
    val DEFAULT: List<Binding> = listOf(
        Binding(InputAction.AIM, InputTrigger.MouseMove, "action_aim"),
        Binding(
            InputAction.FIRE, InputTrigger.MouseButton(Input.Buttons.LEFT), "action_fire",
            schemes = ControlScheme.entries.toSet() - ControlScheme.AUTO_AIM,
        ),
        Binding(
            InputAction.FIRE, InputTrigger.MouseButton(Input.Buttons.LEFT), "action_fire_manual",
            schemes = setOf(ControlScheme.AUTO_AIM),
        ),
        Binding(InputAction.THRUST, InputTrigger.MouseButton(Input.Buttons.RIGHT), "action_thrust"),
        Binding(InputAction.ZOOM, InputTrigger.Wheel, "action_zoom"),
        Binding(InputAction.TOGGLE_PILOT_VIEW, InputTrigger.Key(Keys.V), "action_pilot_view"),
        Binding(InputAction.BLOCK, InputTrigger.Key(Keys.CONTROL_RIGHT), "action_block"),
        Binding(InputAction.DEBUG_PANEL, InputTrigger.Key(Keys.F1), "action_debug_panel", debugOnly = true),
    )
}

fun List<Binding>.keyAction(code: Int): InputAction? =
    firstOrNull { (it.trigger as? InputTrigger.Key)?.code == code }?.action

fun List<Binding>.buttonAction(button: Int): InputAction? =
    firstOrNull { (it.trigger as? InputTrigger.MouseButton)?.button == button }?.action

fun List<Binding>.forMenu(scheme: ControlScheme, includeDebug: Boolean): List<Binding> =
    filter { scheme in it.schemes && (includeDebug || !it.debugOnly) }

fun triggerLabel(t: InputTrigger, i18n: (String) -> String): String = when (t) {
    is InputTrigger.Key -> Keys.toString(t.code)
    is InputTrigger.MouseButton -> when (t.button) {
        Input.Buttons.LEFT -> i18n("input_lmb")
        Input.Buttons.RIGHT -> i18n("input_rmb")
        Input.Buttons.MIDDLE -> i18n("input_mmb")
        else -> "Mouse ${t.button}"
    }
    InputTrigger.MouseMove -> i18n("input_mouse")
    InputTrigger.Wheel -> i18n("input_wheel")
}
