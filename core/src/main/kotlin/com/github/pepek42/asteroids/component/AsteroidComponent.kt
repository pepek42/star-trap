package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.utils.Pool.Poolable
import com.github.pepek42.asteroids.environment.AsteroidSize
import ktx.ashley.mapperFor
import ktx.ashley.propertyFor

class AsteroidComponent : Component, Poolable {
    var asteroidSize: AsteroidSize = AsteroidSize.LARGE
    /** Physics time left (s) in which collisions with this asteroid do no ram damage; see `FRAGMENT_RAM_IMMUNITY`. */
    var ramImmunity: Float = 0f

    override fun reset() {
        asteroidSize = AsteroidSize.LARGE
        ramImmunity = 0f
    }
}

val asteroidMapper = mapperFor<AsteroidComponent>()
val Entity.asteroidCmp by propertyFor(asteroidMapper)
