package com.github.pepek42.asteroids.scenario

import com.github.pepek42.asteroids.environment.AsteroidSize.LARGE
import com.github.pepek42.asteroids.environment.AsteroidSize.SMALL
import com.github.pepek42.asteroids.physics.C_MIN
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT

/**
 * c in the relativity scenario: the lowest c the game allows, as a fraction of the default, so light delay and
 * time dilation show at modest speeds (10 u/s at the default c = 20).
 */
private const val RELATIVITY_SCENARIO_C_FRACTION = C_MIN / SPEED_OF_LIGHT

/** Built-in scenarios (tutorials and test rooms), in menu order. */
object Scenarios {
    /** Steering, thrust and the guns against a few rocks in a small room. */
    val FLIGHT_SCHOOL = Scenario("flight_school", arenaSize = 80f) {
        asteroid(LARGE, 14f, 0f)
        asteroid(LARGE, -12f, 10f, spin = 0.5f)
        asteroid(LARGE, 2f, -16f, spin = 1f)
        asteroid(SMALL, -18f, -14f, betaX = 0.02f)
        asteroid(SMALL, 20f, 18f, betaY = -0.02f)
    }

    /** Two enemy ships plus cover: target rings, pointers, and why enemies miss a ship that keeps moving. */
    val COMBAT = Scenario("combat", arenaSize = 100f) {
        asteroid(LARGE, 10f, 8f)
        asteroid(LARGE, -12f, -6f, spin = 0.5f)
        enemy(30f, 12f)
        enemy(-26f, -24f)
    }

    /** Low c and fast rocks far away: the speed limit, time warp, light delay and the tactical computer. */
    val RELATIVITY = Scenario(
        "relativity",
        arenaSize = 140f,
        physics = ScenarioPhysics(relativity = true, cFraction = RELATIVITY_SCENARIO_C_FRACTION, timeWarp = true),
    ) {
        asteroid(LARGE, 45f, 0f, betaY = 0.5f)
        asteroid(LARGE, -40f, 20f, betaX = 0.3f, betaY = -0.3f)
        asteroid(LARGE, 0f, -50f, betaX = -0.6f)
        asteroid(LARGE, 25f, 40f, betaX = -0.2f, betaY = -0.4f)
    }

    /** A black hole and a planet: gravity bends every path, and the horizon kills. */
    val GRAVITY = Scenario("gravity", arenaSize = 120f) {
        blackHole(32f, 0f)
        planet(-30f, 18f, 0)
        asteroid(LARGE, 0f, 24f)
        asteroid(LARGE, 4f, -24f)
        asteroid(LARGE, 18f, 14f)
        asteroid(LARGE, -14f, -20f)
    }

    val ALL: List<Scenario> = listOf(FLIGHT_SCHOOL, COMBAT, RELATIVITY, GRAVITY)

    fun byId(id: String?): Scenario? = ALL.firstOrNull { it.id == id }
}
