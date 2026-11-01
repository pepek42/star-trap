package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor
import ktx.ashley.propertyFor

class MoveComponent(
    /** From -1 (opposite direction thruster) to 1 (max main thrusters). */
    var thrusters: Float = 0f,
    /** World angle (rad) the ship should face, or null when no rotation is requested. */
    var targetAngleRad: Float? = null,
) : Component, Pool.Poolable {
    override fun reset() {
        thrusters = 0f
        targetAngleRad = null
    }
}

val moveMapper = mapperFor<MoveComponent>()
val Entity.moveCmp by propertyFor(moveMapper)
