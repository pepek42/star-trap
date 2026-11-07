package com.github.pepek42.asteroids.debug

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.github.pepek42.asteroids.input.ControlScheme
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
    /** Initial control scheme (`--controls=mouse|twin|auto`); null means default. */
    val controls: ControlScheme? = null,
    /** Extra asteroids placed in a ring around the player at level spawn (`--near-asteroids=<n>`), for visual checks. */
    val nearAsteroids: Int = 0,
    /** Places the first black hole this many u from the player spawn (`--hole-near[=<u>]`, bare = [HOLE_NEAR_DISTANCE]), for visual checks. */
    val holeNear: Float? = null,
    /** The player cannot die (`--invulnerable`; web: `?invulnerable`), so long screenshot runs near a hole survive. Harness only. */
    val invulnerable: Boolean = false,
    /** Skips the main menu and starts a level at launch (`--play`; web: `?play`). */
    val play: Boolean = false,
    /** Forces the pilot view at launch (`--pilot-view`; web: `?pilot-view`); wins over the saved perspective setting. */
    val pilotView: Boolean = false,
    /**
     * Sets up a shadow test (`--shadow-demo`; web: `?shadow-demo`, ST-098): a black hole [HOLE_NEAR_DISTANCE] u right of the player
     * (as a bare `--hole-near`), a planet between them, an asteroid in the planet's shadow and a control asteroid at the same
     * distance from the hole outside it. Harness only.
     */
    val shadowDemo: Boolean = false,
    /** Starts with `RelativitySettings.starLightEnabled` off (`--no-star`), for A/B shots. Harness only. */
    val noStar: Boolean = false,
    /** Fixes the level's star azimuth in degrees (`--star-angle=<deg>`; web: `?star-angle=`), for A/B shots. Harness only. */
    val starAngleDeg: Float? = null,
    /** Starts with `RelativitySettings.shadowsEnabled` off (`--no-shadows`; web: `?no-shadows`), for A/B shots. Harness only. */
    val noShadows: Boolean = false,
)

/** Distance (u) from the player spawn to the first black hole under a bare `--hole-near`. */
const val HOLE_NEAR_DISTANCE = 25f

/**
 * Parses `--screenshot=<path>`, `--frames=<N>`, `--scene=play|menu`, `--thrust`, `--seed=<long>`, `--controls=mouse|twin|auto`, `--near-asteroids=<n>`, `--hole-near[=<u>]`, `--invulnerable`, `--debug-panel`, `--play`, `--pilot-view`, `--shadow-demo` and `--no-shadows`.
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
            "--controls" -> ControlScheme.fromFlag(value)?.let { options = options.copy(controls = it) }
            "--near-asteroids" -> value?.toIntOrNull()?.takeIf { it > 0 }?.let { options = options.copy(nearAsteroids = it) }
            "--hole-near" -> options = options.copy(holeNear = if (value == null) HOLE_NEAR_DISTANCE else value.toFloatOrNull()?.takeIf { it > 0f } ?: options.holeNear)
            "--invulnerable" -> options = options.copy(invulnerable = true)
            "--debug-panel" -> options = options.copy(debugPanel = true)
            "--play" -> options = options.copy(play = true)
            "--pilot-view" -> options = options.copy(pilotView = true)
            "--shadow-demo" -> options = options.copy(shadowDemo = true)
            "--no-shadows" -> options = options.copy(noShadows = true)
            "--no-star" -> options = options.copy(noStar = true)
            "--star-angle" -> value?.toFloatOrNull()?.let { options = options.copy(starAngleDeg = it) }
        }
    }
    return options
}

/**
 * Turns a web URL query (`?seed=42&play&controls=twin`) into command-line style flags for [parseLaunchOptions]:
 * each `key=value` becomes `--key=value`, a bare `key` becomes `--key`. `screenshot` is dropped because the
 * browser can't write files. Values are URL-decoded only for `%XX` and `+`; that's enough for the flags we have.
 */
fun queryToLaunchArgs(query: String): Array<String> =
    query.removePrefix("?").split('&')
        .filter { it.isNotBlank() }
        .map { part ->
            val key = part.substringBefore('=')
            if (part.contains('=')) "--$key=${decodeQueryValue(part.substringAfter('='))}" else "--$key"
        }
        .filterNot { it.substringBefore('=') == "--screenshot" }
        .toTypedArray()

private fun decodeQueryValue(v: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < v.length) {
        val c = v[i]
        when {
            c == '+' -> sb.append(' ')
            c == '%' && i + 2 < v.length ->
                v.substring(i + 1, i + 3).toIntOrNull(16)?.let { sb.append(it.toChar()); i += 2 } ?: sb.append(c)
            else -> sb.append(c)
        }
        i++
    }
    return sb.toString()
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
