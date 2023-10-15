@file:JvmName("TeaVMLauncher")

package com.github.pepek42.asteroids.teavm

import com.github.pepek42.asteroids.BASE_HEIGHT
import com.github.pepek42.asteroids.BASE_WIDTH
import com.github.pepek42.asteroids.Game
import com.github.xpenatan.gdx.backends.teavm.TeaApplication
import com.github.xpenatan.gdx.backends.teavm.TeaApplicationConfiguration

/** Launches the TeaVM/HTML application. */
fun main() {
    val config = TeaApplicationConfiguration("canvas").apply {
        width = BASE_WIDTH
        height = BASE_HEIGHT
    }
    TeaApplication(Game(), config)
}
