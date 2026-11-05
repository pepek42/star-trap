package com.github.pepek42.asteroids

class GameState {
    var level = 0
        private set

    /** Set when a run ended by player death; the main menu shows a message once and clears it. */
    var lastRunEnded = false

    fun nextLevel() {
        ++level
    }

    fun resetGame() {
        level = 0
    }
}
