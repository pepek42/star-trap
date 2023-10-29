package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor
import ktx.ashley.propertyFor

data class HealthComponent(
    var health: Float = 0f
) : Component, Pool.Poolable {

    override fun reset() {
        health = 0f
    }
}

val healthMapper = mapperFor<HealthComponent>()
val Entity.healthCmp by propertyFor(healthMapper)
