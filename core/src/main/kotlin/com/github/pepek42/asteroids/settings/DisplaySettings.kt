package com.github.pepek42.asteroids.settings

import java.util.Locale

/** Language codes offered in the menu: "" follows the system locale. */
val LANGUAGE_OPTIONS = listOf("", "en", "pl")

/** Display preferences. [language] is read once at startup, [fullscreen] applies at once. */
class DisplaySettings(var language: String = "", var fullscreen: Boolean = false)

/** The default locale for a blank [code], else the locale with that language tag. */
fun localeFor(code: String): Locale = if (code.isBlank()) Locale.getDefault() else Locale.forLanguageTag(code)
