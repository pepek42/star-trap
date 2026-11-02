package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.relativisticCmp
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.isWarpCapped
import com.github.pepek42.asteroids.physics.timeWarpFactor
import com.github.pepek42.asteroids.physics.worldDelta
import ktx.ashley.allOf
import kotlin.math.max
import kotlin.math.min

/** Slack for the "steps capped" comparison. */
private const val STEPS_CAPPED_EPSILON = 1e-6f

/** Fills [GameClock] for the frame from the player's γ of the last physics step. Registered first. */
class TimeWarpSystem(
    private val clock: GameClock,
    private val settings: RelativitySettings,
) : EntitySystem() {
    private val family = allOf(PlayerComponent::class, RelativisticComponent::class).get()
    private lateinit var entities: ImmutableArray<Entity>

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        entities = engine.getEntitiesFor(family)
    }

    override fun update(deltaTime: Float) {
        clock.realDt = deltaTime
        val gamma = entities.firstOrNull()?.relativisticCmp?.gUsed ?: 1f
        val enabled = settings.timeWarpEnabled && settings.relativityEnabled
        clock.warp = timeWarpFactor(gamma, enabled, settings.timeWarpMax)
        clock.warpCapped = isWarpCapped(gamma, enabled, settings.timeWarpMax)
        val maxWorld = min(
            PhysicsSystem.MAX_STEPS_PER_FRAME * PhysicsSystem.PHYSICS_UPDATE_INTERVAL,
            max(1f, clock.warp) * PhysicsSystem.MAX_LAG_STEPS * PhysicsSystem.PHYSICS_UPDATE_INTERVAL,
        )
        clock.worldDt = worldDelta(deltaTime, clock.warp, maxWorld)
        clock.stepsCapped = deltaTime * clock.warp > maxWorld + STEPS_CAPPED_EPSILON
    }
}
