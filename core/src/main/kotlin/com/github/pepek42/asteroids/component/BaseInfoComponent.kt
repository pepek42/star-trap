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
    /** A guided lock-on shot body (ST-164): a real projectile with an engine, distinct from the sensor-based lasers. */
    LOCK_ON,
    /** An unguided pod rocket (ST-181): a real body with a fixed-heading motor and one health point. */
    ROCKET,
    BLACK_HOLE,
    PLANET,
    DEBRIS,
    UNKNOWN,
}

/**
 * Every Entity should have it
 */
class BaseInfoComponent : Component, Pool.Poolable {
    var id: Int = ++globalCounter
    var faction: FactionEnum = FactionEnum.NEUTRAL
    var entityType: EntityType = EntityType.UNKNOWN

    override fun reset() {
        id = ++globalCounter
        faction = FactionEnum.NEUTRAL
        entityType = EntityType.UNKNOWN
    }

    companion object {
        // TODO Not nice solution
        @Volatile
        private var globalCounter = 0
    }
}

val baseInfoMapper = mapperFor<BaseInfoComponent>()
val Entity.baseInfoCmp by propertyFor(baseInfoMapper)
