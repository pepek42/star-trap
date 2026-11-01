package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import ktx.ashley.mapperFor
import ktx.ashley.optionalPropertyFor
import ktx.ashley.propertyFor

class PlayerComponent : Component

val playerMapper = mapperFor<PlayerComponent>()
val Entity.playerCmp by propertyFor(playerMapper)
val Entity.playerCmpOptional by optionalPropertyFor(playerMapper)
