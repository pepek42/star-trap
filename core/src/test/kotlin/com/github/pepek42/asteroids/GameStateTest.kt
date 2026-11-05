package com.github.pepek42.asteroids

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
    fun lastRunEndedIsSettable() {
        val state = GameState()
        state.lastRunEnded = true
        assertTrue(state.lastRunEnded)
    }
}
