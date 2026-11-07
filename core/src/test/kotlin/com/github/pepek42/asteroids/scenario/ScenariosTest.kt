package com.github.pepek42.asteroids.scenario

import com.github.pepek42.asteroids.environment.AsteroidSize
import com.github.pepek42.asteroids.i18nKeys
import com.github.pepek42.asteroids.physics.C_MIN
import com.github.pepek42.asteroids.physics.FIELD_SKIN
import com.github.pepek42.asteroids.physics.RelativitySettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.hypot

/** Clearance (u) every spawn keeps from the player at the arena centre. */
private const val MIN_SPAWN_CLEARANCE = 8f

private class RecordingSpawner : ScenarioSpawner {
    val positions = mutableListOf<Pair<Float, Float>>()
    var targets = 0
    val betas = mutableListOf<Float>()

    override fun asteroid(size: AsteroidSize, dx: Float, dy: Float, betaX: Float, betaY: Float, spin: Float) {
        positions += dx to dy
        betas += hypot(betaX, betaY)
        targets++
    }

    override fun enemy(dx: Float, dy: Float) {
        positions += dx to dy
        targets++
    }

    override fun blackHole(dx: Float, dy: Float) {
        positions += dx to dy
    }

    override fun planet(dx: Float, dy: Float, variant: Int) {
        positions += dx to dy
    }
}

class ScenariosTest {
    @Test
    fun idsAreUniqueAndResolvable() {
        val ids = Scenarios.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        Scenarios.ALL.forEach { assertEquals(it, Scenarios.byId(it.id)) }
        assertEquals(null, Scenarios.byId("no_such_scenario"))
    }

    @Test
    fun everyScenarioHasItsTexts() {
        val keys = i18nKeys("messages.properties")
        for (s in Scenarios.ALL) {
            for (key in listOf(s.titleKey, s.summaryKey, s.briefingKey)) {
                assertTrue(key in keys, "missing i18n key $key")
            }
        }
    }

    @Test
    fun spawnsAreInsideTheArenaAndClearOfThePlayer() {
        for (s in Scenarios.ALL) {
            val rec = RecordingSpawner()
            rec.(s.spawn)()
            assertTrue(rec.targets > 0, "${s.id} has nothing to clear")
            val limit = s.arenaSize / 2f - FIELD_SKIN
            for (p in rec.positions) {
                val dx = p.first
                val dy = p.second
                assertTrue(abs(dx) <= limit && abs(dy) <= limit, "${s.id}: ($dx, $dy) outside the field skin")
                assertTrue(hypot(dx, dy) >= MIN_SPAWN_CLEARANCE, "${s.id}: ($dx, $dy) too close to the player")
            }
            rec.betas.forEach { assertTrue(it < 1f, "${s.id}: asteroid at or above c") }
        }
    }

    @Test
    fun scenarioPhysicsOverridesOnlyWhatItSets() {
        val settings = RelativitySettings()
        settings.relativityEnabled = false
        settings.timeWarpEnabled = false
        val cBefore = settings.c

        applyScenarioPhysics(ScenarioPhysics(), settings)
        assertFalse(settings.relativityEnabled)
        assertEquals(cBefore, settings.c)

        applyScenarioPhysics(Scenarios.RELATIVITY.physics, settings)
        assertTrue(settings.relativityEnabled)
        assertTrue(settings.timeWarpEnabled)
        assertEquals(C_MIN, settings.c, 1e-4f)
    }
}
