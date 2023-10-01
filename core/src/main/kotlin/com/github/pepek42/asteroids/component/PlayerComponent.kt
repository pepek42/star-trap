package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import ktx.ashley.mapperFor

class PlayerComponent : Component {
    companion object {
        val mapper = mapperFor<PlayerComponent>()
    }

}
