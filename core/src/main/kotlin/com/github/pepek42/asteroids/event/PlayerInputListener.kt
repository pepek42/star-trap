package com.github.pepek42.asteroids.event

import com.badlogic.gdx.math.Vector3

interface PlayerInputListener {
    fun movement(thrusters: Float) = Unit

    fun aimPoint(point: Vector3) = Unit

    fun fire(start: Boolean) = Unit

    fun block() = Unit
}
