package com.github.pepek42.asteroids.provider

import com.github.pepek42.asteroids.event.GameEventManager
import ktx.math.vec2

/** Default arena width (u). Public so derived quantities (e.g. the light-history capacity) follow the map size. */
const val MAP_WIDTH = 200f

/** Default arena height (u). */
const val MAP_HEIGHT = 200f

class MapProvider(
    private val gameEventManager: GameEventManager,
    private val width: Float = MAP_WIDTH,
    private val height: Float = MAP_HEIGHT,
) {

    var mapWidth = 0f
        private set
    var mapHeight = 0f
        private set

    init {
        mapWidth = width
        mapHeight = height
    }

    fun playerSpawnLocation() = vec2(mapWidth / 2, mapHeight / 2)

    fun loadMap() {
        gameEventManager.newMap(mapWidth, mapHeight)
    }
}
