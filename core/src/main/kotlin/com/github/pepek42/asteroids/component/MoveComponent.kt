package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor

class MoveComponent(
    /** From -1 (opposite direction thruster) to 1 (max main thrusters) */
    var thrusters: Float = 0f,
    /**
     * Between -1 and 1. -1 means rotate half a circle clock wise, 1 half a circle counter-clock wise
     */
    var rotationNormalised: Float = 0f,
) : Component, Pool.Poolable {
    override fun reset() {
        thrusters = 0f
        rotationNormalised = 0f
    }
}

val moveMapper = mapperFor<MoveComponent>()
