@file:JvmName("Lwjgl3Launcher")

package com.github.pepek42.asteroids.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.github.pepek42.asteroids.BASE_HEIGHT
import com.github.pepek42.asteroids.BASE_WIDTH
import com.github.pepek42.asteroids.Game
import com.github.pepek42.asteroids.TITLE
import com.github.pepek42.asteroids.debug.parseLaunchOptions

/** Launches the desktop (LWJGL3) application. */
fun main(args: Array<String>) {
    // This handles macOS support and helps on Windows.
    if (StartupHelper.startNewJvmIfRequired())
      return
    val options = parseLaunchOptions(args)
    val size = options.windowSize ?: (BASE_WIDTH to BASE_HEIGHT)
    Lwjgl3Application(Game(options), Lwjgl3ApplicationConfiguration().apply {
        setTitle(TITLE)
        setWindowedMode(size.first, size.second)
        setWindowIcon(*(arrayOf(128, 64, 32, 16).map { "libgdx$it.png" }.toTypedArray()))
    })
}
