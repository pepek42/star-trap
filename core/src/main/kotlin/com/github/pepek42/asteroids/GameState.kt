package com.github.pepek42.asteroids

class GameState {
    var level = 0
        private set

    fun nextLevel() {
        ++level
    }

    fun resetGame() {
        level = 0
    }
}
