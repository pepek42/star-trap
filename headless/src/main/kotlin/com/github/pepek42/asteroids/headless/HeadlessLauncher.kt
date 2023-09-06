@file:JvmName("HeadlessLauncher")

package com.github.pepek42.asteroids.headless

import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.github.pepek42.asteroids.AsteroidsCoop

/** Launches the headless application. Can be converted into a server application or a scripting utility. */
fun main() {
    HeadlessApplication(AsteroidsCoop(), HeadlessApplicationConfiguration().apply {
        // When this value is negative, AsteroidsCoop#render() is never called:
        updatesPerSecond = -1
    })
}
