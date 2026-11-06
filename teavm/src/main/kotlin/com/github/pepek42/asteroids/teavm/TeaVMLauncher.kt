@file:JvmName("TeaVMLauncher")

package com.github.pepek42.asteroids.teavm

import com.github.pepek42.asteroids.Game
import com.github.pepek42.asteroids.debug.parseLaunchOptions
import com.github.pepek42.asteroids.debug.queryToLaunchArgs
import com.github.xpenatan.gdx.teavm.backends.web.WebApplication
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration
import com.github.xpenatan.gdx.teavm.backends.web.WebAssetPreloadListener
import org.teavm.jso.browser.Window

/**
 * Launches the TeaVM/HTML application. The canvas fills the browser window, and the URL query is the web
 * equivalent of the desktop command line (see [queryToLaunchArgs]), e.g. `index.html?seed=42&play`.
 */
fun main() {
    val config = WebApplicationConfiguration("canvas").apply {
        // 0 x 0 = auto-size: the canvas follows the browser window.
        width = 0
        height = 0
        // needed for freetype support
        preloadListener = WebAssetPreloadListener { assetLoader -> assetLoader.loadScript("freetype.js") }
    }
    val options = parseLaunchOptions(queryToLaunchArgs(Window.current().location.search))
    WebApplication(Game(options), config)
}
