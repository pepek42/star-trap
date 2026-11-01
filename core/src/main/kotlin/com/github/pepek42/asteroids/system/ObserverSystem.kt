package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.Vector2
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.relativisticCmpOptional
import com.github.pepek42.asteroids.component.transformCmp
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.VISUAL_BETA_SMOOTHING
import com.github.pepek42.asteroids.physics.gammaFromU
import com.github.pepek42.asteroids.physics.gammaFromV
import com.github.pepek42.asteroids.physics.rapidity
import com.github.pepek42.asteroids.physics.smoothToward
import com.github.pepek42.asteroids.physics.velocityFromU
import ktx.ashley.allOf
import ktx.ashley.get

/** Fills [ObserverState] from the player entity once per frame, right after PhysicsSystem. */
class ObserverSystem(
    private val state: ObserverState,
    private val settings: RelativitySettings,
) : IteratingSystem(allOf(PlayerComponent::class, BodyComponent::class, TransformComponent::class).get()) {
    private val v = Vector2()

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val c = settings.c
        state.worldTime += deltaTime
        state.position.set(entity.transformCmp.interpolatedPosition)
        val rel = entity.relativisticCmpOptional
        if (rel != null) {
            velocityFromU(rel.u.x, rel.u.y, c, v)
            state.gammaRaw = gammaFromU(rel.u.x, rel.u.y, c)
            state.rapidity = rapidity(rel.u.x, rel.u.y, c)
            state.tau = rel.tau
        } else {
            v.set(entity.bodyCmp.body.linearVelocity)
            state.gammaRaw = gammaFromV(v.x, v.y, c)
            state.rapidity = rapidity(v.x * state.gammaRaw, v.y * state.gammaRaw, c)
            state.tau = state.worldTime
        }
        state.betaRaw.set(v).scl(1f / c)
        state.betaVisual.set(
            smoothToward(state.betaVisual.x, state.betaRaw.x, deltaTime, VISUAL_BETA_SMOOTHING),
            smoothToward(state.betaVisual.y, state.betaRaw.y, deltaTime, VISUAL_BETA_SMOOTHING),
        )
        state.gammaVisual = gammaFromV(state.betaVisual.x * c, state.betaVisual.y * c, c)
    }
}
