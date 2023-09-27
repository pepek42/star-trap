package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor

class MoveComponent(
    val movement: Vector2,
) : Component, Pool.Poolable {
    override fun reset() {
        movement.x = 0f
        movement.y = 0f
    }

    companion object {
        val mapper = mapperFor<MoveComponent>()
    }
}
