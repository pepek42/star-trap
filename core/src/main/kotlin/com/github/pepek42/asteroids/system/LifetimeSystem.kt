package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.github.pepek42.asteroids.component.LifetimeClock
import com.github.pepek42.asteroids.component.LifetimeComponent
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.component.RemoveComponent
import com.github.pepek42.asteroids.component.lifetimeCmp
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.component.spriteMapper
import com.github.pepek42.asteroids.weapon.LIFETIME_FADE_SECONDS
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.ashley.has

/** Counts down [LifetimeComponent]. PROPER clock is treated like WORLD until ST-040. */
class LifetimeSystem(private val clock: GameClock? = null) : IteratingSystem(allOf(LifetimeComponent::class).get()) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        val lifetime = entity.lifetimeCmp
        // PROPER stays on the real (pilot) delta until ST-040.
        lifetime.remaining -= if (lifetime.clock == LifetimeClock.WORLD) clock?.worldDt ?: deltaTime else deltaTime
        if (lifetime.remaining <= 0f && !entity.has(removeMapper)) {
            entity.add(engine.createComponent(RemoveComponent::class.java))
        }
        if (lifetime.remaining < LIFETIME_FADE_SECONDS) {
            entity[spriteMapper]?.sprite?.setAlpha((lifetime.remaining / LIFETIME_FADE_SECONDS).coerceIn(0f, 1f))
        }
    }
}
