@file:JvmName("TeaVMLauncher")

package com.github.pepek42.asteroids.teavm

import com.github.xpenatan.gdx.backends.teavm.TeaApplicationConfiguration
import com.github.xpenatan.gdx.backends.teavm.TeaApplication
import com.github.pepek42.asteroids.AsteroidsCoop
import com.github.pepek42.asteroids.BASE_HEIGHT
import com.github.pepek42.asteroids.BASE_WIDTH

/** Launches the TeaVM/HTML application. */
fun main() {
    val config = TeaApplicationConfiguration("canvas").apply {
        width = BASE_WIDTH
        height = BASE_HEIGHT
    }
    TeaApplication(AsteroidsCoop(), config)
}
