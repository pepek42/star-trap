package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor

class PlayerComponent(
    var thrusters: Float,
    var aimPointX: Float,
    var aimPointY: Float,
) : Component, Pool.Poolable {
    override fun reset() {
        thrusters = 0f
        aimPointX = 0f
        aimPointY = 0f
    }

    companion object {
        val mapper = mapperFor<PlayerComponent>()
    }

}
