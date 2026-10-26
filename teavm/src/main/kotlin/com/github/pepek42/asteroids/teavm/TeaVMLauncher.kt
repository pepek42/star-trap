@file:JvmName("TeaVMLauncher")

package com.github.pepek42.asteroids.teavm

import com.github.pepek42.asteroids.BASE_HEIGHT
import com.github.pepek42.asteroids.BASE_WIDTH
import com.github.pepek42.asteroids.Game
import com.github.xpenatan.gdx.teavm.backends.web.WebApplication
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration
import com.github.xpenatan.gdx.teavm.backends.web.WebAssetPreloadListener

/** Launches the TeaVM/HTML application. */
fun main() {
    val config = WebApplicationConfiguration("canvas").apply {
        width = BASE_WIDTH
        height = BASE_HEIGHT
        // needed for freetype support
        preloadListener = WebAssetPreloadListener { assetLoader -> assetLoader.loadScript("freetype.js") }
    }
    WebApplication(Game(), config)
}
