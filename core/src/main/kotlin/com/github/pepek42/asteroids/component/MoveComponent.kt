package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor

class MoveComponent(
    var thrusters: Float = 0f,
    var aimPointX: Float = 0f,
    var aimPointY: Float = 0f,
) : Component, Pool.Poolable {
    override fun reset() {
        thrusters = 0f
        aimPointX = 0f
        aimPointY = 0f
    }

    companion object {
        val mapper = mapperFor<MoveComponent>()
    }
}
