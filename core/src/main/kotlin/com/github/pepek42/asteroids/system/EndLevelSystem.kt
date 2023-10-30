package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.utils.ImmutableArray
import com.github.pepek42.asteroids.Game
import com.github.pepek42.asteroids.GameState
import com.github.pepek42.asteroids.component.AsteroidComponent
import ktx.ashley.oneOf
import ktx.log.logger

class EndLevelSystem(
    private val game: Game,
    // TODO Add enemy ships
    private val family: Family = oneOf(AsteroidComponent::class).get(),
) : EntitySystem() {
    private val gameState = game.get<GameState>()
    private var entities: ImmutableArray<Entity>? = null

    override fun update(deltaTime: Float) {
        if (entities != null && entities!!.size() == 0) {
            logger.info { "Level ${gameState.level} finished" }
            game.finishLevel()
        }
    }

    override fun addedToEngine(engine: Engine) {
        entities = engine.getEntitiesFor(family)
    }

    override fun removedFromEngine(engine: Engine?) {
        entities = null
    }

    companion object {
        private val logger = logger<EndLevelSystem>()
    }
}
