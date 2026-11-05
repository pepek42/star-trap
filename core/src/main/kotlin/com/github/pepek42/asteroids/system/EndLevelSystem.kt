package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.utils.ImmutableArray
import com.github.pepek42.asteroids.component.AsteroidComponent
import ktx.ashley.oneOf

/** Calls [onLevelCleared] every update while no asteroid is left. */
class EndLevelSystem(
    private val onLevelCleared: () -> Unit,
    private val family: Family = oneOf(AsteroidComponent::class).get(),
) : EntitySystem() {
    private var entities: ImmutableArray<Entity>? = null

    override fun update(deltaTime: Float) {
        if (entities != null && entities!!.size() == 0) {
            onLevelCleared()
        }
    }

    override fun addedToEngine(engine: Engine) {
        entities = engine.getEntitiesFor(family)
    }

    override fun removedFromEngine(engine: Engine?) {
        entities = null
    }
}
