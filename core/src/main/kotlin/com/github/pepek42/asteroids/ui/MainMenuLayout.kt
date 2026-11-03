package com.github.pepek42.asteroids.ui

import com.github.pepek42.asteroids.input.Binding
import com.github.pepek42.asteroids.input.ControlScheme
import com.github.pepek42.asteroids.input.forMenu
import com.github.pepek42.asteroids.input.triggerLabel

/** Rows (trigger label, action label) for the controls list, in bindings-table order. */
fun controlsRows(
    bindings: List<Binding>,
    scheme: ControlScheme,
    includeDebug: Boolean,
    i18n: (String) -> String,
): List<Pair<String, String>> =
    bindings.forMenu(scheme, includeDebug).map { triggerLabel(it.trigger, i18n) to i18n(it.labelKey) }

/** [v] with exactly [decimals] decimals, always with a dot (locale independent). */
fun formatSettingValue(v: Float, decimals: Int): String = appendFixed(StringBuilder(), v, decimals).toString()
