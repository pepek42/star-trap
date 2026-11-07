package com.github.pepek42.asteroids.scenario

import com.badlogic.gdx.math.MathUtils
import com.github.pepek42.asteroids.math.shortestAngleDiffRad
import kotlin.math.abs

/**
 * What an objective predicate may read: the true state and the player's input events, never anything it could change.
 * The [com.github.pepek42.asteroids.system.ScenarioObjectiveSystem] fills it every frame.
 */
class ObjectiveState {
    /** Player speed as a fraction of the runtime c (true state). */
    var beta = 0f

    /** Player Lorentz factor (true state). */
    var gamma = 1f

    var thrusted = false
    var turned = false
    var fired = false
    var pilotViewToggled = false
    var tacticalToggled = false

    /** Asteroids destroyed since the scenario started (a split counts the parent as destroyed). */
    var rocksDestroyed = 0

    /** Distance from the player to the nearest planet surface in planet radii; [Float.MAX_VALUE] if there is no planet. */
    var planetDistanceRadii = Float.MAX_VALUE

    /** Full turns of one orbit around the planet flown in a row within [ORBIT_MAX_RADII]. */
    var orbits = 0f

    /** Asteroids alive right now. */
    var rocksLeft = 0

    /** Forgets the one-shot input events, so the next step needs a fresh action. */
    fun clearEvents() {
        thrusted = false
        turned = false
        fired = false
        pilotViewToggled = false
        tacticalToggled = false
    }

    fun reset() {
        rocksLeft = 0
        beta = 0f
        gamma = 1f
        thrusted = false
        turned = false
        fired = false
        pilotViewToggled = false
        tacticalToggled = false
        rocksDestroyed = 0
        planetDistanceRadii = Float.MAX_VALUE
        orbits = 0f
    }
}

/** One tutorial step: its text lives in `messages.properties` under [textKey]; it is done when [done] holds. */
class Objective(val textKey: String, val done: (ObjectiveState) -> Boolean)

/** Walks a scenario's objectives in order; only the current one is tested. */
class ObjectiveTracker(val objectives: List<Objective>) {
    /** Index of the current objective; `objectives.size` when all are done. */
    var index = 0
        private set

    val finished: Boolean get() = index >= objectives.size

    /** Advances past every objective whose predicate holds now (in order). Returns true when anything advanced. */
    fun update(state: ObjectiveState): Boolean {
        val before = index
        while (index < objectives.size && objectives[index].done(state)) {
            index++
            state.clearEvents()
        }
        return index != before
    }
}

/** Distance from the planet surface (in planet radii) within which flying around it counts as orbiting. */
const val ORBIT_MAX_RADII = 4f

/** Heading change (rad) from the start that counts as "turned". */
const val TURN_THRESHOLD_RAD = MathUtils.PI / 4f

/** Relativity scenario: speed (fraction of c) the first step asks for. */
const val OBJECTIVE_BETA_HALF = 0.5f

/** Relativity scenario: speed (fraction of c) where gamma is clearly above 1 (gamma = 1.67). */
const val OBJECTIVE_BETA_FAST = 0.8f

/** Gravity scenario: how close to the planet (in planet radii) the first step asks to fly. */
const val OBJECTIVE_PLANET_NEAR_RADII = 8f

/**
 * Accumulates the angle the player sweeps around a centre while staying within a radius. Leaving the radius restarts
 * the count. Pure and allocation-free.
 */
class OrbitAccumulator {
    private var lastAngle = 0f
    private var tracking = false
    private var swept = 0f

    /** Full turns accumulated so far (always >= 0, direction does not matter). */
    val turns: Float get() = abs(swept) / MathUtils.PI2

    fun reset() {
        tracking = false
        swept = 0f
    }

    /** Feeds the player's bearing from the centre ([angleRad]) and whether the player is [inside] the allowed radius. */
    fun update(angleRad: Float, inside: Boolean) {
        if (!inside) {
            reset()
            return
        }
        if (tracking) swept += shortestAngleDiffRad(lastAngle, angleRad)
        lastAngle = angleRad
        tracking = true
    }
}
