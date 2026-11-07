package com.github.pepek42.asteroids.ui

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.utils.ImmutableArray
import com.github.pepek42.asteroids.component.asteroidMapper
import com.github.pepek42.asteroids.component.enemyMapper
import com.github.pepek42.asteroids.environment.AsteroidSize

/** What is left to destroy on this level, per kind (HUD target counter, owner 2026-11-07). Reused, never reallocated. */
class TargetCounts {
    /** Asteroids left, indexed by [AsteroidSize.ordinal]. */
    val asteroids = IntArray(AsteroidSize.entries.size)
    var enemies = 0

    fun clear() {
        asteroids.fill(0)
        enemies = 0
    }

    fun copyFrom(other: TargetCounts) {
        other.asteroids.copyInto(asteroids)
        enemies = other.enemies
    }

    fun sameAs(other: TargetCounts): Boolean = enemies == other.enemies && asteroids.contentEquals(other.asteroids)
}

/** HUD bundle key of the counter row for asteroids of [size]. */
fun asteroidCountKey(size: AsteroidSize): String = "hud_asteroids_" + size.name

/** Counts [targets] (the level target family) into [out]. Allocation-free. */
fun countLevelTargets(targets: ImmutableArray<Entity>, out: TargetCounts) {
    out.clear()
    for (i in 0 until targets.size()) {
        val e = targets[i]
        val asteroid = asteroidMapper.get(e)
        if (asteroid != null) out.asteroids[asteroid.asteroidSize.ordinal]++
        else if (enemyMapper.has(e)) out.enemies++
    }
}
