package com.github.pepek42.asteroids.debug

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import ktx.log.logger

const val DEFAULT_SCREENSHOT_FRAMES = 180

enum class ScreenshotScene { PLAY, MENU }

/** Launch options for automated visual checks. [screenshotPath] == null means normal game start. */
data class LaunchOptions(
    val screenshotPath: String? = null,
    val frames: Int = DEFAULT_SCREENSHOT_FRAMES,
    val scene: ScreenshotScene = ScreenshotScene.PLAY,
    val thrust: Boolean = false,
    /** Seed for all gameplay randomness; null means seed from time. */
    val seed: Long? = null,
    /** Shows the debug side panel at launch (`--debug-panel`). */
    val debugPanel: Boolean = false,
)

/**
 * Parses `--screenshot=<path>`, `--frames=<N>`, `--scene=play|menu`, `--thrust`, `--seed=<long>` and `--debug-panel`.
 * Unknown flags and flags with a missing or invalid value are ignored (defaults are kept).
 */
fun parseLaunchOptions(args: Array<String>): LaunchOptions {
    var options = LaunchOptions()
    for (arg in args) {
        val key = arg.substringBefore('=')
        val value = if (arg.contains('=')) arg.substringAfter('=') else null
        when (key) {
            "--screenshot" -> if (!value.isNullOrBlank()) options = options.copy(screenshotPath = value)
            "--frames" -> value?.toIntOrNull()?.takeIf { it > 0 }?.let { options = options.copy(frames = it) }
            "--scene" -> ScreenshotScene.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?.let { options = options.copy(scene = it) }
            "--seed" -> value?.toLongOrNull()?.let { options = options.copy(seed = it) }
            "--thrust" -> options = options.copy(thrust = true)
            "--debug-panel" -> options = options.copy(debugPanel = true)
        }
    }
    return options
}

/** Counts rendered frames and, after the last one, saves the framebuffer to a PNG and exits. */
/** Fixed frame delta used for screenshot runs so that the simulation is deterministic. */
const val SCREENSHOT_FRAME_DELTA = 1f / 60f

class ScreenshotHarness(private val options: LaunchOptions) {
    private var frame = 0

    /** Call at the end of the render, before the buffer swap. */
    fun afterRender() {
        val path = options.screenshotPath ?: return
        if (++frame < options.frames) return
        if (frame == options.frames) {
            try {
                capture(path)
            } catch (e: Exception) {
                logger.error(e) { "Failed to save screenshot to $path" }
            } finally {
                Gdx.app.exit()
            }
        }
    }

    private fun capture(path: String) {
        val pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        try {
            FileHandle(path).parent().mkdirs()
            PixmapIO.writePNG(FileHandle(path), pixmap, 6, true)
            logger.info { "Screenshot saved to $path" }
        } finally {
            pixmap.dispose()
        }
    }

    companion object {
        private val logger = logger<ScreenshotHarness>()
    }
}
