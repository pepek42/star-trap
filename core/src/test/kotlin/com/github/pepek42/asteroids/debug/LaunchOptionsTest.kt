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
        assertEquals(true, parseLaunchOptions(arrayOf("--hole-near")).holeNear)
        assertEquals(false, parseLaunchOptions(arrayOf()).holeNear)
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
