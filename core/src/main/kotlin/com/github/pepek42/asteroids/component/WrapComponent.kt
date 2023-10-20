package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor

class WrapComponent : Component, Pool.Poolable {
    override fun reset() = Unit
}

val wrapMapper = mapperFor<WrapComponent>()
