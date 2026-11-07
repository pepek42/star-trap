package com.github.pepek42.asteroids

import com.github.pepek42.asteroids.scenario.Scenarios
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GameStateTest {
    @Test
    fun resetGameReturnsToStartLevel() {
        val state = GameState()
        val start = state.level
        state.nextLevel()
        state.nextLevel()
        state.resetGame()
        assertEquals(start, state.level)
    }

    @Test
    fun firstPlayedLevelIsTheChosenStartLevel() {
        for (start in START_LEVELS) {
            val state = GameState()
            state.resetGame(start)
            state.nextLevel() // PlayScreen.show() advances before spawning
            assertEquals(start, state.level)
        }
    }

    @Test
    fun startLevelMustBePositive() {
        assertThrows<IllegalArgumentException> { GameState().resetGame(0) }
    }

    @Test
    fun scenarioIsClearedByReset() {
        val state = GameState()
        state.startScenario(Scenarios.COMBAT)
        assertSame(Scenarios.COMBAT, state.scenario)
        state.resetGame(QUICK_START_LEVEL)
        assertNull(state.scenario)
    }

    @Test
    fun lastRunEndedIsSettable() {
        val state = GameState()
        state.lastRunEnded = true
        assertTrue(state.lastRunEnded)
    }
}

class QuickStartTest {
    @Test
    fun levelOneGrantsNothing() {
        val state = GameState()
        state.resetGame(FIRST_LEVEL)
        assertEquals(0, state.credits)
        assertTrue(!state.quickStart)
    }

    @Test
    fun quickStartGrantsLevelsOneToFourCreditsAndOpensShop() {
        val state = GameState()
        state.resetGame(QUICK_START_LEVEL)
        assertEquals(
            com.github.pepek42.asteroids.shop.typicalCreditsThroughLevel(QUICK_START_LEVEL - 1),
            state.credits,
        )
        assertTrue(state.credits > 0)
        assertTrue(state.quickStart)
        assertEquals(QUICK_START_LEVEL - 1, state.level)
    }

    @Test
    fun creditsMatchHandCount() {
        // Levels 1..4: 2+5+10+17 large rocks (9 credits each with fragments) + 1 + 2 enemies (20 each); assumes current balance.
        assertEquals(34 * 9 + 3 * 20, com.github.pepek42.asteroids.shop.typicalCreditsThroughLevel(4))
    }
}
