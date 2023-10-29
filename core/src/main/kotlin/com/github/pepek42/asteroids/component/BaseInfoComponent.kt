package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.utils.Pool
import com.github.pepek42.asteroids.faction.FactionEnum
import ktx.ashley.mapperFor
import ktx.ashley.propertyFor

enum class EntityType {
    SHIP,
    ASTEROID,
    PROJECTILE,
    UNKNOWN,
}

/**
 * Every Entity should have it
 */
data class BaseInfoComponent(
    var id: Int = ++globalCounter,
    var faction: FactionEnum = FactionEnum.NEUTRAL,
    var entityType: EntityType = EntityType.UNKNOWN,
) : Component, Pool.Poolable {
    override fun reset() {
        id = ++globalCounter
        faction = FactionEnum.NEUTRAL
    }

    companion object {
        // TODO Not nice solution
        @Volatile
        private var globalCounter = 0
    }
}

val baseInfoMapper = mapperFor<BaseInfoComponent>()
val Entity.baseInfoCmp by propertyFor(baseInfoMapper)
