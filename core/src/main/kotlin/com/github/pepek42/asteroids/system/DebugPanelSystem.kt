package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.ashley.core.Entity
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.relativisticCmpOptional
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.ui.DebugPanel
import ktx.ashley.allOf

/** Time between readout refreshes of the debug panel (10 Hz). */
const val DEBUG_PANEL_REFRESH_SECONDS = 0.1f

/** Refreshes the debug panel readouts at 10 Hz while the panel is visible. Registered just before HudSystem. */
class DebugPanelSystem(
    private val panel: DebugPanel,
    private val observerState: ObserverState,
    private val clock: GameClock? = null,
) : EntitySystem() {
    private val family = allOf(PlayerComponent::class, RelativisticComponent::class).get()
    private lateinit var entities: ImmutableArray<Entity>
    private var timer = DEBUG_PANEL_REFRESH_SECONDS

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        entities = engine.getEntitiesFor(family)
    }

    override fun checkProcessing(): Boolean = panel.isVisible

    override fun update(deltaTime: Float) {
        timer += deltaTime
        if (timer < DEBUG_PANEL_REFRESH_SECONDS) return
        timer = 0f
        panel.update(observerState, entities.firstOrNull()?.relativisticCmpOptional, clock)
    }
}
