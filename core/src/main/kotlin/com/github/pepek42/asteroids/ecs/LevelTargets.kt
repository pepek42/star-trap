package com.github.pepek42.asteroids.ecs

import com.badlogic.ashley.core.Family
import com.github.pepek42.asteroids.component.AsteroidComponent
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.EnemyComponent
import com.github.pepek42.asteroids.component.RemoveComponent
import com.github.pepek42.asteroids.component.TransformComponent
import ktx.ashley.allOf
import ktx.ashley.exclude
import ktx.ashley.oneOf

/**
 * Level targets: the entities the player must destroy to clear the level (asteroids and enemy ships, the same set
 * `EndLevelSystem` waits for), minus those already tagged for removal. The target computer (edge arrows, target rings,
 * HUD counts) shows exactly these; debris, holes and planets are not targets.
 */
fun levelTargetFamily(): Family =
    oneOf(AsteroidComponent::class, EnemyComponent::class)
        .allOf(BaseInfoComponent::class, TransformComponent::class)
        .exclude(RemoveComponent::class)
        .get()
