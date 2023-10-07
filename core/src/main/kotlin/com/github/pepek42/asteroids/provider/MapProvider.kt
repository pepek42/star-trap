package com.github.pepek42.asteroids.provider

import com.github.pepek42.asteroids.event.GameEventManager
import ktx.math.vec2

private const val MAP_WIDTH = 100f
private const val MAP_HEIGHT = 100f

class MapProvider(
    private val gameEventManager: GameEventManager,
) {

    var width = 0f
        private set
    var height = 0f
        private set

    init {
        width = MAP_WIDTH
        height = MAP_HEIGHT
    }

    // TODO vec2(width / 2, height / 2)
    fun playerSpawnLocation() = vec2(0f, 0f) // vec2(width / 2, height / 2)

    // TODO JSON map format
    fun loadMap() {
        gameEventManager.newMap(width, height)
    }
}
