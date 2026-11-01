package com.github.pepek42.asteroids.input

enum class ControlScheme(val flag: String) {
    MOUSE_HULL("mouse"),
    TWIN_STICK("twin"),
    AUTO_AIM("auto");

    companion object {
        /** Case-insensitive lookup by command-line flag value; null when unknown. */
        fun fromFlag(s: String?): ControlScheme? = entries.firstOrNull { it.flag.equals(s, ignoreCase = true) }
    }
}
