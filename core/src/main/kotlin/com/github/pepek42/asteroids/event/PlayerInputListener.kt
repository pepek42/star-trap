package com.github.pepek42.asteroids.event

interface PlayerInputListener {
    fun movement(thrusters: Float) = Unit

    fun screenAimPoint(screenX: Int, screenY: Int) = Unit

    fun fire(fire: Boolean) = Unit

    fun block() = Unit

    fun zoom(zoomDelta: Float) = Unit
}
