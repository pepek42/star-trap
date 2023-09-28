package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor

class PlayerComponent : Component, Pool.Poolable {
    override fun reset() = Unit

    companion object {
        val mapper = mapperFor<PlayerComponent>()
    }

}
