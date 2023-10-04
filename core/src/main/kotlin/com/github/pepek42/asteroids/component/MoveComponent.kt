package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor

class MoveComponent(
    var thrusters: Float = 0f,
    var radiansToRotate: Float = 0f,
) : Component, Pool.Poolable {
    override fun reset() {
        thrusters = 0f
        radiansToRotate = 0f
    }
}

val moveMapper = mapperFor<MoveComponent>()
