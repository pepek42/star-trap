package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor
import ktx.math.vec2

class TransformComponent(
    val position: Vector2 = vec2(0f, 0f),
    val prevPosition: Vector2 = vec2(0f, 0f),
    val interpolatedPosition: Vector2 = vec2(0f, 0f),
    var rotationDeg: Float = 0f,
    var prevRotationDeg: Float = 0f,
    var interpolatedRotationDeg: Float = 0f,
) : Component, Pool.Poolable {



    override fun reset() {
        position.set(0f, 0f)
        prevPosition.set(position)
        interpolatedPosition.set(position)
        rotationDeg = 0f
        prevRotationDeg = 0f
        interpolatedRotationDeg = 0f
    }
}

val transformMapper = mapperFor<TransformComponent>()
