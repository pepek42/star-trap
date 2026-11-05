package com.github.pepek42.asteroids.input

enum class ControlScheme(val flag: String, val implemented: Boolean) {
    MOUSE_HULL("mouse", true),
    TWIN_STICK("twin", true),
    AUTO_AIM("auto", true);

    companion object {
        /** Schemes the menu offers. */
        fun selectable(): List<ControlScheme> = entries.filter { it.implemented }

        /** Case-insensitive lookup by command-line flag value; null when unknown. */
        fun fromFlag(s: String?): ControlScheme? = entries.firstOrNull { it.flag.equals(s, ignoreCase = true) }
    }
}
