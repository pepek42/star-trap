package com.github.pepek42.asteroids.provider

import com.badlogic.gdx.math.Vector2

private const val MAP_WIDTH = 500f
private const val MAP_HEIGHT = 500f

class MapProvider {

    var width = 0f
        private set
    var height = 0f
        private set

    // TODO JSON map format
    init {
        width = MAP_WIDTH
        height = MAP_HEIGHT
    }

    fun playerSpawnLocation() = Vector2(width / 2, height / 2)
}
