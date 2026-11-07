package com.github.pepek42.asteroids.provider

import com.github.pepek42.asteroids.event.GameEventManager
import ktx.math.vec2

/** Default arena width (u). Public so derived quantities (e.g. the light-history capacity) follow the map size. */
const val MAP_WIDTH = 200f

/** Default arena height (u). */
const val MAP_HEIGHT = 200f

class MapProvider(
    private val gameEventManager: GameEventManager,
    width: Float = MAP_WIDTH,
    height: Float = MAP_HEIGHT,
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

    /** Broadcasts the current map; with a size, the arena changes to it first (scenarios use smaller arenas). */
    fun loadMap(width: Float = mapWidth, height: Float = mapHeight) {
        require(width > 0f && height > 0f) { "map size must be positive, was $width x $height" }
        mapWidth = width
        mapHeight = height
        gameEventManager.newMap(mapWidth, mapHeight)
    }
}
