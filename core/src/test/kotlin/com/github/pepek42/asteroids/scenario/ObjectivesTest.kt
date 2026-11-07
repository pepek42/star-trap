package com.github.pepek42.asteroids.scenario

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.math.MathUtils
import com.github.pepek42.asteroids.component.AsteroidComponent
import com.github.pepek42.asteroids.component.GravitySourceComponent
import com.github.pepek42.asteroids.component.GravitySourceKind
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.i18nKeys
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.system.ScenarioObjectiveSystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

class ObjectivesTest {
    @Test
    fun objectivesAdvanceInOrderOnlyWhenTheCurrentOneHolds() {
        val tracker = ObjectiveTracker(Scenarios.RELATIVITY.objectives)
        val state = ObjectiveState()
        // A later step being true does not skip the current one.
        state.pilotViewToggled = true
        assertFalse(tracker.update(state))
        assertEquals(0, tracker.index)

        state.beta = OBJECTIVE_BETA_HALF
        assertTrue(tracker.update(state))
        assertEquals(1, tracker.index)

        state.beta = OBJECTIVE_BETA_FAST
        tracker.update(state)
        // The early V press was forgotten when the earlier steps ticked: each step needs a fresh action.
        assertEquals(2, tracker.index)
        state.pilotViewToggled = true
        tracker.update(state)
        assertEquals(3, tracker.index)
        state.tacticalToggled = true
        tracker.update(state)
        assertTrue(tracker.finished)
    }

    @Test
    fun scenarioWithoutObjectivesHasNothingToTrack() {
        val tracker = ObjectiveTracker(Scenarios.COMBAT.objectives)
        assertTrue(tracker.finished)
        assertFalse(tracker.update(ObjectiveState()))
    }

    @Test
    fun objectiveTextsExist() {
        val keys = i18nKeys("messages.properties")
        for (s in Scenarios.ALL) for (o in s.objectives) assertTrue(o.textKey in keys, "missing ${o.textKey}")
    }

    @Test
    fun orbitCountsFullTurnsInsideTheRadiusAndRestartsWhenLeaving() {
        val orbit = OrbitAccumulator()
        val steps = 100
        for (i in 0..steps) orbit.update(MathUtils.PI2 * i / steps, inside = true)
        assertEquals(1f, orbit.turns, 1e-3f)
        orbit.update(0f, inside = false)
        assertEquals(0f, orbit.turns, 0f)
        // Direction does not matter.
        for (i in 0..steps / 2) orbit.update(-MathUtils.PI2 * i / steps, inside = true)
        assertEquals(0.5f, orbit.turns, 1e-2f)
    }

    @Test
    fun systemTicksThrustAndOrbitFromInputAndTrueState() {
        val observer = ObserverState()
        var changes = 0
        val system = ScenarioObjectiveSystem(observer) { changes++ }
        val engine = Engine()
        engine.addSystem(system)
        val planet = engine.createEntity()
        planet.add(engine.createComponent(TransformComponent::class.java))
        planet.add(engine.createComponent(GravitySourceComponent::class.java).apply {
            kind = GravitySourceKind.PLANET
            radius = 2f
        })
        engine.addEntity(planet)
        val ship = engine.createEntity()
        val shipTransform = engine.createComponent(TransformComponent::class.java)
        ship.add(shipTransform)
        ship.add(engine.createComponent(PlayerComponent::class.java))
        engine.addEntity(ship)

        val objectives = listOf(
            Objective("a") { it.thrusted },
            Objective("b") { it.orbits >= 1f },
        )
        system.start(objectives)
        assertEquals(1, changes)
        engine.update(0.016f)
        assertEquals(1, changes)
        system.movement(1f)
        engine.update(0.016f)
        assertEquals(2, changes)

        val radius = 2f * ORBIT_MAX_RADII * 0.5f
        val steps = 60
        for (i in 0..steps) {
            val a = MathUtils.PI2 * i / steps
            shipTransform.interpolatedPosition.set(radius * cos(a), radius * sin(a))
            engine.update(0.016f)
        }
        assertEquals(3, changes)
    }

    @Test
    fun systemCountsRemovedAsteroidsAndIdlesWithoutObjectives() {
        val observer = ObserverState()
        val system = ScenarioObjectiveSystem(observer) { }
        val engine = Engine()
        engine.addSystem(system)
        val rock = engine.createEntity()
        rock.add(engine.createComponent(AsteroidComponent::class.java))
        engine.addEntity(rock)

        // No objectives: nothing is counted or ticked.
        system.start(emptyList())
        engine.update(0.016f)
        engine.removeEntity(rock)

        var last: ObjectiveTracker? = null
        val system2 = ScenarioObjectiveSystem(observer) { last = it }
        val engine2 = Engine()
        engine2.addSystem(system2)
        val rock2 = engine2.createEntity()
        rock2.add(engine2.createComponent(AsteroidComponent::class.java))
        engine2.addEntity(rock2)
        system2.start(listOf(Objective("x") { it.rocksDestroyed >= 1 && it.rocksLeft == 0 }))
        engine2.update(0.016f)
        assertFalse(last!!.finished)
        engine2.removeEntity(rock2)
        engine2.update(0.016f)
        assertTrue(last!!.finished)
    }
}
