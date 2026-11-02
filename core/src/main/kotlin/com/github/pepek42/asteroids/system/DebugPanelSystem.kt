package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.bodyCmpOptional
import com.github.pepek42.asteroids.component.relativisticCmpOptional
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.ui.DebugPanel
import ktx.ashley.allOf

/** Time between readout refreshes of the debug panel (10 Hz). */
const val DEBUG_PANEL_REFRESH_SECONDS = 0.1f

/** Accumulates real time and reports when a refresh is due. Pure so the pacing is testable. */
class RefreshPacer(private val interval: Float) {
    private var timer = interval

    /** Adds [realDt] seconds; returns true (and restarts the interval) once it has elapsed. */
    fun due(realDt: Float): Boolean {
        timer += realDt
        if (timer < interval) return false
        timer = 0f
        return true
    }
}

/**
 * Refreshes the debug panel readouts at 10 Hz of real time while the panel is visible.
 * Paced on [realDelta] (not the scaled game delta) so time warp and time scale don't change the refresh rate.
 * Registered just before HudSystem.
 */
class DebugPanelSystem(
    private val panel: DebugPanel,
    private val observerState: ObserverState,
    private val clock: GameClock? = null,
    private val realDelta: () -> Float = { Gdx.graphics.rawDeltaTime },
) : EntitySystem() {
    private val family = allOf(PlayerComponent::class, RelativisticComponent::class).get()
    private lateinit var entities: ImmutableArray<Entity>
    private val pacer = RefreshPacer(DEBUG_PANEL_REFRESH_SECONDS)

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        entities = engine.getEntitiesFor(family)
    }

    override fun checkProcessing(): Boolean = panel.isVisible

    override fun update(deltaTime: Float) {
        if (!pacer.due(realDelta())) return
        val player = entities.firstOrNull()
        panel.update(observerState, player?.relativisticCmpOptional, clock, player?.bodyCmpOptional?.body?.mass ?: Float.NaN)
    }
}
