package com.github.pepek42.asteroids

import com.github.pepek42.asteroids.scenario.Scenario
import com.github.pepek42.asteroids.shop.RunUpgrades
import com.github.pepek42.asteroids.shop.ShopVisit
import com.github.pepek42.asteroids.shop.typicalCreditsThroughLevel

/** First level of a normal new game. */
const val FIRST_LEVEL = 1

/** "Quick start" level offered by the New game page (starts with the credits levels 1 to 4 typically pay, spent in the shop first). */
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

    /** Credits of the current run (earned per kill, spent in the shop). Always 0 in scenarios. */
    var credits = 0

    /** Seed of the run (the launch seed), the base of the shop's own RNG. */
    var runSeed = 0L

    /** Shop visit restored from a save, consumed by the cleared screen; null when the next level is next. */
    var pendingShop: ShopVisit? = null

    /** True from a quick start (first level above [FIRST_LEVEL]) until the cleared screen shows its shop once. */
    var quickStart = false

    /** Upgrades bought in the current run. */
    val upgrades = RunUpgrades()

    /** Adds [amount] credits to a normal run; scenarios earn nothing. */
    fun addCredits(amount: Int) {
        if (scenario == null) credits += amount
    }

    fun nextLevel() {
        ++level
    }

    /** Starts a normal run whose first level is [startLevel]. */
    fun resetGame(startLevel: Int = FIRST_LEVEL) {
        require(startLevel >= FIRST_LEVEL) { "startLevel must be >= $FIRST_LEVEL, was $startLevel" }
        scenario = null
        level = startLevel - 1
        // A start above level 1 grants what the skipped levels would have paid; the shop opens before the level.
        credits = typicalCreditsThroughLevel(startLevel - 1)
        quickStart = startLevel > FIRST_LEVEL
        pendingShop = null
        upgrades.clear()
    }

    /** Starts (or restarts) [scenario]; it is a single level. */
    fun startScenario(scenario: Scenario) {
        this.scenario = scenario
        quickStart = false
        credits = 0
        pendingShop = null
        upgrades.clear()
        level = FIRST_LEVEL - 1
    }
}
