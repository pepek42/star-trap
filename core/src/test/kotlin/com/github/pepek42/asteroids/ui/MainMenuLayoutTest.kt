package com.github.pepek42.asteroids.ui

import com.github.pepek42.asteroids.i18nKeys
import com.github.pepek42.asteroids.input.ControlScheme
import com.github.pepek42.asteroids.input.InputBindings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainMenuLayoutTest {
    private val i18n = { key: String -> "i18n($key)" }

    @Test
    fun controlsRowsInTableOrder() {
        val rows = controlsRows(InputBindings.DEFAULT, ControlScheme.MOUSE_HULL, false, i18n)
        assertEquals(6, rows.size)
        assertEquals("R-Ctrl" to i18n("action_block"), rows[5])
        assertEquals(i18n("input_lmb") to i18n("action_fire"), rows[1])
    }

    @Test
    fun debugRowLast() {
        val rows = controlsRows(InputBindings.DEFAULT, ControlScheme.MOUSE_HULL, true, i18n)
        assertEquals("F1" to i18n("action_debug_panel"), rows.last())
    }

    @Test
    fun formatSettingValueFixedDecimals() {
        assertEquals("20", formatSettingValue(20f, 0))
        assertEquals("0.25", formatSettingValue(0.25f, 2))
    }

    @Test
    fun menuKeysExistInBothFiles() {
        val keys = listOf("menu_settings", "menu_controls", "menu_controls_debug", "menu_reset_defaults")
        for (file in listOf("messages.properties", "messages_pl.properties")) {
            assertTrue(i18nKeys(file).containsAll(keys), "$file missing menu keys")
        }
    }
}
