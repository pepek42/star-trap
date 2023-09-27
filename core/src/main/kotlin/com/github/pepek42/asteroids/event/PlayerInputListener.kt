package com.github.pepek42.asteroids.event

interface PlayerInputListener {
    fun movement(thrusters: Float) = Unit

    fun aimPoint(x: Float, y: Float) = Unit

    fun fire(start: Boolean) = Unit

    fun block() = Unit
}
