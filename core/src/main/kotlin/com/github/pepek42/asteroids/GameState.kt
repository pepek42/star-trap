package com.github.pepek42.asteroids

import com.github.pepek42.asteroids.scenario.Scenario

/** First level of a normal new game. */
const val FIRST_LEVEL = 1

/** "Quick start" level offered by the New game page (later: with a prebuilt ship from the shop). */
const val QUICK_START_LEVEL = 5

/** Levels a new game can start at, in menu order. */
val START_LEVELS = listOf(FIRST_LEVEL, QUICK_START_LEVEL)

class GameState {
    /** Level being played (PlayScreen.show advances it before spawning, so it is one below the next level between levels). */
    var level = 0
        private set

    /** The scenario being played, or null for a normal run (the level sequence). */
    var scenario: Scenario? = null
        private set

    /** Set when a run ended by player death; the main menu shows a message once and clears it. */
    var lastRunEnded = false

    fun nextLevel() {
        ++level
    }

    /** Starts a normal run whose first level is [startLevel]. */
    fun resetGame(startLevel: Int = FIRST_LEVEL) {
        require(startLevel >= FIRST_LEVEL) { "startLevel must be >= $FIRST_LEVEL, was $startLevel" }
        scenario = null
        level = startLevel - 1
    }

    /** Starts (or restarts) [scenario]; it is a single level. */
    fun startScenario(scenario: Scenario) {
        this.scenario = scenario
        level = FIRST_LEVEL - 1
    }
}
