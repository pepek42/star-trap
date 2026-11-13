package com.github.pepek42.asteroids.debug

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LaunchOptionsTest {
    @Test
    fun noArgsGivesDefaults() {
        assertEquals(LaunchOptions(), parseLaunchOptions(emptyArray()))
    }

    @Test
    fun validArgsAreParsed() {
        val o = parseLaunchOptions(arrayOf("--screenshot=/tmp/a.png", "--frames=120", "--scene=menu", "--thrust"))
        assertEquals(LaunchOptions("/tmp/a.png", 120, ScreenshotScene.MENU, true), o)
    }

    @Test
    fun missingOrInvalidValuesKeepDefaults() {
        val o = parseLaunchOptions(arrayOf("--screenshot=", "--frames", "--scene=foo"))
        assertEquals(LaunchOptions(), o)
        assertEquals(DEFAULT_SCREENSHOT_FRAMES, parseLaunchOptions(arrayOf("--frames=abc")).frames)
    }

    @Test
    fun windowSizeIsParsed() {
        assertEquals(1920 to 1080, parseLaunchOptions(arrayOf("--window=1920x1080")).windowSize)
        for (bad in listOf("--window", "--window=1920", "--window=0x600", "--window=ax600"))
            assertEquals(null, parseLaunchOptions(arrayOf(bad)).windowSize, bad)
    }

    @Test
    fun seedIsParsed() {
        assertEquals(42L, parseLaunchOptions(arrayOf("--seed=42")).seed)
        assertEquals(null, parseLaunchOptions(arrayOf("--seed=abc")).seed)
        assertEquals(null, parseLaunchOptions(emptyArray()).seed)
    }

    @Test
    fun debugPanelFlagIsParsed() {
        assertEquals(true, parseLaunchOptions(arrayOf("--debug-panel")).debugPanel)
        assertEquals(false, parseLaunchOptions(emptyArray()).debugPanel)
    }

    @Test
    fun unknownFlagsAreIgnored() {
        val o = parseLaunchOptions(arrayOf("--bogus=1", "-x", "--frames=10"))
        assertEquals(LaunchOptions(frames = 10), o)
    }

    @Test
    fun parsesHoleNear() {
        assertEquals(HOLE_NEAR_DISTANCE, parseLaunchOptions(arrayOf("--hole-near")).holeNear)
        assertEquals(18f, parseLaunchOptions(arrayOf("--hole-near=18")).holeNear)
        assertEquals(null, parseLaunchOptions(arrayOf("--hole-near=x")).holeNear)
        assertEquals(null, parseLaunchOptions(arrayOf()).holeNear)
        assertEquals(true, parseLaunchOptions(arrayOf("--invulnerable")).invulnerable)
        assertEquals(false, parseLaunchOptions(arrayOf()).invulnerable)
        val web = parseLaunchOptions(queryToLaunchArgs("?play&hole-near=18&invulnerable"))
        assertEquals(18f, web.holeNear)
        assertEquals(true, web.invulnerable)
        assertEquals(true, parseLaunchOptions(arrayOf("--pilot-view")).pilotView)
        assertEquals(false, parseLaunchOptions(arrayOf()).pilotView)
        assertEquals(true, parseLaunchOptions(queryToLaunchArgs("?play&pilot-view")).pilotView)
        assertEquals(true, parseLaunchOptions(arrayOf("--shadow-demo")).shadowDemo)
        assertEquals(false, parseLaunchOptions(arrayOf()).shadowDemo)
        assertEquals(true, parseLaunchOptions(queryToLaunchArgs("?play&shadow-demo")).shadowDemo)
        assertEquals(true, parseLaunchOptions(arrayOf("--no-shadows")).noShadows)
        assertEquals(false, parseLaunchOptions(arrayOf()).noShadows)
        assertEquals(true, parseLaunchOptions(queryToLaunchArgs("?play&shadow-demo&no-shadows")).noShadows)
    }

    @Test
    fun parsesNearAsteroids() {
        assertEquals(4, parseLaunchOptions(arrayOf("--near-asteroids=4")).nearAsteroids)
        assertEquals(0, parseLaunchOptions(arrayOf("--near-asteroids=x")).nearAsteroids)
    }

    @Test
    fun parsesPlay() {
        assertEquals(true, parseLaunchOptions(arrayOf("--play")).play)
        assertEquals(false, LaunchOptions().play)
    }

    @Test
    fun webQueryBecomesFlags() {
        val o = parseLaunchOptions(queryToLaunchArgs("?seed=42&play&scene=menu&controls=twin&screenshot=/x.png"))
        assertEquals(42L, o.seed)
        assertEquals(true, o.play)
        assertEquals(ScreenshotScene.MENU, o.scene)
        assertEquals(null, o.screenshotPath)
    }

    @Test
    fun webQueryEdgeCases() {
        assertEquals(0, queryToLaunchArgs("").size)
        assertEquals(0, queryToLaunchArgs("?").size)
        assertEquals(listOf("--a=b c", "--d=%"), queryToLaunchArgs("a=b%20c&&d=%").toList())
    }
}
