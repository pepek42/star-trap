package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import ktx.ashley.mapperFor
import ktx.ashley.optionalPropertyFor
import ktx.ashley.propertyFor

/** Marker for enemy ships (ST-155). Must be destroyed to clear the level. */
class EnemyComponent : Component

val enemyMapper = mapperFor<EnemyComponent>()
val Entity.enemyCmp by propertyFor(enemyMapper)
val Entity.enemyCmpOptional by optionalPropertyFor(enemyMapper)
