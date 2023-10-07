package com.github.pepek42.asteroids.event

fun interface MapEventListener {
    fun onNewMap(mapWidth: Float, mapHeight: Float)
}
