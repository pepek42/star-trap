package com.github.pepek42.asteroids.input

import com.badlogic.gdx.Input
import com.badlogic.gdx.Input.Keys

enum class InputAction { AIM, FIRE, THRUST, ZOOM, TOGGLE_PILOT_VIEW, TOGGLE_TACTICAL_OVERLAY, BLOCK, QUIT_TO_MENU, DEBUG_PANEL, MOVE_UP, MOVE_DOWN, MOVE_LEFT, MOVE_RIGHT, SELECT_WEAPON_1, SELECT_WEAPON_2, SELECT_WEAPON_3 }

/** 0-based weapon slot of a select-weapon action, or -1 for any other action. */
fun InputAction.weaponSlot(): Int = when (this) {
    InputAction.SELECT_WEAPON_1 -> 0
    InputAction.SELECT_WEAPON_2 -> 1
    InputAction.SELECT_WEAPON_3 -> 2
    else -> -1
}

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
    private val TWIN = setOf(ControlScheme.TWIN_STICK)

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
        Binding(
            InputAction.THRUST, InputTrigger.MouseButton(Input.Buttons.RIGHT), "action_thrust",
            schemes = ControlScheme.entries.toSet() - ControlScheme.TWIN_STICK,
        ),
        Binding(InputAction.MOVE_UP, InputTrigger.Key(Keys.W), "action_move_up", schemes = TWIN),
        Binding(InputAction.MOVE_UP, InputTrigger.Key(Keys.UP), "action_move_up", schemes = TWIN),
        Binding(InputAction.MOVE_DOWN, InputTrigger.Key(Keys.S), "action_move_down", schemes = TWIN),
        Binding(InputAction.MOVE_DOWN, InputTrigger.Key(Keys.DOWN), "action_move_down", schemes = TWIN),
        Binding(InputAction.MOVE_LEFT, InputTrigger.Key(Keys.A), "action_move_left", schemes = TWIN),
        Binding(InputAction.MOVE_LEFT, InputTrigger.Key(Keys.LEFT), "action_move_left", schemes = TWIN),
        Binding(InputAction.MOVE_RIGHT, InputTrigger.Key(Keys.D), "action_move_right", schemes = TWIN),
        Binding(InputAction.MOVE_RIGHT, InputTrigger.Key(Keys.RIGHT), "action_move_right", schemes = TWIN),
        Binding(InputAction.ZOOM, InputTrigger.Wheel, "action_zoom"),
        Binding(InputAction.TOGGLE_PILOT_VIEW, InputTrigger.Key(Keys.V), "action_pilot_view"),
        Binding(InputAction.TOGGLE_TACTICAL_OVERLAY, InputTrigger.Key(Keys.T), "action_tactical_overlay"),
        Binding(InputAction.BLOCK, InputTrigger.Key(Keys.CONTROL_RIGHT), "action_block"),
        Binding(InputAction.QUIT_TO_MENU, InputTrigger.Key(Keys.ESCAPE), "action_quit_to_menu"),
        Binding(InputAction.SELECT_WEAPON_1, InputTrigger.Key(Keys.NUM_1), "action_weapon_1"),
        Binding(InputAction.SELECT_WEAPON_2, InputTrigger.Key(Keys.NUM_2), "action_weapon_2"),
        Binding(InputAction.SELECT_WEAPON_3, InputTrigger.Key(Keys.NUM_3), "action_weapon_3"),
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
