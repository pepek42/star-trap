package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.utils.Pool.Poolable
import com.github.pepek42.asteroids.environment.AsteroidSize
import ktx.ashley.mapperFor
import ktx.ashley.propertyFor

data class AsteroidComponent(
    var asteroidSize: AsteroidSize = AsteroidSize.LARGE,
) : Component, Poolable {
    override fun reset() {
        asteroidSize = AsteroidSize.LARGE
    }
}

val asteroidMapper = mapperFor<AsteroidComponent>()
val Entity.asteroidCmp by propertyFor(asteroidMapper)
