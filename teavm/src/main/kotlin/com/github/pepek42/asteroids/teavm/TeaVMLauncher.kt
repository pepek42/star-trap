@file:JvmName("TeaVMLauncher")

package com.github.pepek42.asteroids.teavm

import com.github.xpenatan.gdx.backends.teavm.TeaApplicationConfiguration
import com.github.xpenatan.gdx.backends.teavm.TeaApplication
import com.github.pepek42.asteroids.AsteroidsCoop

/** Launches the TeaVM/HTML application. */
fun main() {
    val config = TeaApplicationConfiguration("canvas").apply {
        width = 640
        height = 480
    }
    TeaApplication(AsteroidsCoop(), config)
}
