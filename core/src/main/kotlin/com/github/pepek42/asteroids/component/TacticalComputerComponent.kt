package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.utils.Pool
import com.github.pepek42.asteroids.render.TACTICAL_TIER_NONE
import com.github.pepek42.asteroids.render.TACTICAL_TIER_WIREFRAME
import ktx.ashley.mapperFor
import ktx.ashley.optionalPropertyFor
import ktx.ashley.propertyFor

/** No shop yet, every ship has it; the shop will set tier. */
const val TACTICAL_COMPUTER_DEFAULT_TIER = TACTICAL_TIER_WIREFRAME

/** The installed tactical computer. [tier] decides what the overlay may show (`TACTICAL_TIER_*`). */
class TacticalComputerComponent : Component, Pool.Poolable {
    var tier = TACTICAL_TIER_NONE

    override fun reset() {
        tier = TACTICAL_TIER_NONE
    }
}

val tacticalComputerMapper = mapperFor<TacticalComputerComponent>()
val Entity.tacticalComputerCmp by propertyFor(tacticalComputerMapper)
val Entity.tacticalComputerCmpOptional by optionalPropertyFor(tacticalComputerMapper)
