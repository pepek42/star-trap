package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.utils.Pool
import ktx.ashley.mapperFor
import ktx.ashley.optionalPropertyFor
import ktx.ashley.propertyFor

enum class LifetimeClock { WORLD, PROPER }

class LifetimeComponent : Component, Pool.Poolable {
    var remaining: Float = 0f
    var total: Float = 0f
    var clock: LifetimeClock = LifetimeClock.WORLD

    override fun reset() {
        remaining = 0f
        total = 0f
        clock = LifetimeClock.WORLD
    }
}

val lifetimeMapper = mapperFor<LifetimeComponent>()
val Entity.lifetimeCmp by propertyFor(lifetimeMapper)
val Entity.lifetimeCmpOptional by optionalPropertyFor(lifetimeMapper)
