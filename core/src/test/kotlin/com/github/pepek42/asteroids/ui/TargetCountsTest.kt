package com.github.pepek42.asteroids.ui

import com.badlogic.ashley.core.PooledEngine
import com.github.pepek42.asteroids.component.AsteroidComponent
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.EnemyComponent
import com.github.pepek42.asteroids.component.RemoveComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.ecs.levelTargetFamily
import com.github.pepek42.asteroids.environment.AsteroidSize
import com.github.pepek42.asteroids.i18nKeys
import ktx.ashley.entity
import ktx.ashley.with
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TargetCountsTest {
    private val engine = PooledEngine()

    private fun asteroid(size: AsteroidSize, removed: Boolean = false) = engine.entity {
        with<BaseInfoComponent>()
        with<TransformComponent>()
        with<AsteroidComponent> { asteroidSize = size }
        if (removed) with<RemoveComponent>()
    }

    private fun enemy() = engine.entity {
        with<BaseInfoComponent>()
        with<TransformComponent>()
        with<EnemyComponent>()
    }

    /** Debris, holes, planets and projectiles: no Asteroid/Enemy component. */
    private fun nonTarget() = engine.entity {
        with<BaseInfoComponent>()
        with<TransformComponent>()
    }

    @Test
    fun countsEachKindAndSkipsNonTargetsAndRemoved() {
        repeat(3) { asteroid(AsteroidSize.LARGE) }
        repeat(5) { asteroid(AsteroidSize.SMALL) }
        asteroid(AsteroidSize.SMALL, removed = true)
        repeat(2) { enemy() }
        repeat(4) { nonTarget() }
        val out = TargetCounts()
        countLevelTargets(engine.getEntitiesFor(levelTargetFamily()), out)
        assertEquals(3, out.asteroids[AsteroidSize.LARGE.ordinal])
        assertEquals(5, out.asteroids[AsteroidSize.SMALL.ordinal])
        assertEquals(2, out.enemies)
        assertEquals(10, engine.getEntitiesFor(levelTargetFamily()).size())
    }

    @Test
    fun sameAsAndCopy() {
        val a = TargetCounts().apply { asteroids[0] = 2; enemies = 1 }
        val b = TargetCounts()
        assertFalse(a.sameAs(b))
        b.copyFrom(a)
        assertTrue(a.sameAs(b))
    }

    @Test
    fun everyAsteroidSizeHasAHudKey() {
        val keys = i18nKeys("messages.properties")
        for (size in AsteroidSize.entries) assertTrue(asteroidCountKey(size) in keys, asteroidCountKey(size))
        assertTrue("hud_enemies" in keys)
        assertTrue("hud_targets_title" in keys)
    }
}
