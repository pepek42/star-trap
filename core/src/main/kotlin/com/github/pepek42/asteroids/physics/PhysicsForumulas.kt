package com.github.pepek42.asteroids.physics

import com.badlogic.gdx.math.Vector2
import ktx.math.plus

// TODO limit to speed of light
fun combineSpeeds(speed1: Vector2, speed2: Vector2): Vector2 {
    return speed1 + speed2
}
