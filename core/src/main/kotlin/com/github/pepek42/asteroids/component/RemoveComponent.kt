package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool

class RemoveComponent : Component, Pool.Poolable {
    override fun reset() = Unit
}
