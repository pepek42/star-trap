package com.github.pepek42.asteroids.provider

import com.github.pepek42.asteroids.event.GameEventManager
import ktx.math.vec2

private const val MAP_WIDTH = 100f
private const val MAP_HEIGHT = 100f

class MapProvider(
    private val gameEventManager: GameEventManager,
) {

    var mapWidth = 0f
        private set
    var mapHeight = 0f
        private set

    init {
        mapWidth = MAP_WIDTH
        mapHeight = MAP_HEIGHT
    }

    fun playerSpawnLocation() = vec2(mapWidth / 2, mapHeight / 2)

    // TODO JSON map format
    fun loadMap() {
        gameEventManager.newMap(mapWidth, mapHeight)
    }
}
