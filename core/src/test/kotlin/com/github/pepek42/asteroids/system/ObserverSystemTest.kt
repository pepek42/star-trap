package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.RelativitySettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** ST-195: `ObserverState.gravFactor` follows the player's `RelativisticComponent.gravFactor`, unsmoothed. */
class ObserverSystemTest {
    @Test
    fun gravFactorIsCopiedFromThePlayersRelativisticComponentWithoutSmoothing() {
        val state = ObserverState()
        val engine = Engine().apply { addSystem(ObserverSystem(state, RelativitySettings())) }
        val rel = RelativisticComponent().apply { gravFactor = 0.3f }
        val player = Entity().add(PlayerComponent()).add(TransformComponent()).add(BodyComponent()).add(rel)
        engine.addEntity(player)
        engine.update(0.016f)
        assertEquals(0.3f, state.gravFactor, 0f)
        rel.gravFactor = 0.9f
        engine.update(0.016f)
        assertEquals(0.9f, state.gravFactor, 0f)
    }
}
