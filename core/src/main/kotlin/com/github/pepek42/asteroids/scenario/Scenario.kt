package com.github.pepek42.asteroids.scenario

import com.github.pepek42.asteroids.environment.AsteroidSize
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT

/**
 * Physics a scenario forces for its duration; null keeps the player's New-game choice. The menu reloads the
 * player's saved choices when it opens again, so a scenario never changes them for good.
 */
class ScenarioPhysics(
    val relativity: Boolean? = null,
    /** Speed of light as a fraction of [SPEED_OF_LIGHT]; clamped by [RelativitySettings.c]. */
    val cFraction: Float? = null,
    val timeWarp: Boolean? = null,
)

/** Applies the non-null parts of [physics] to [settings]. */
fun applyScenarioPhysics(physics: ScenarioPhysics, settings: RelativitySettings) {
    physics.relativity?.let { settings.relativityEnabled = it }
    physics.cFraction?.let { settings.c = it * SPEED_OF_LIGHT }
    physics.timeWarp?.let { settings.timeWarpEnabled = it }
}

/**
 * What a scenario can place. Offsets are in u relative to the player spawn (the arena centre); velocities are
 * fractions of the runtime c, so a scenario keeps working when c changes.
 */
interface ScenarioSpawner {
    fun asteroid(size: AsteroidSize, dx: Float, dy: Float, betaX: Float = 0f, betaY: Float = 0f, spin: Float = 0f)
    fun enemy(dx: Float, dy: Float)
    fun blackHole(dx: Float, dy: Float)
    fun planet(dx: Float, dy: Float, variant: Int)
}

/**
 * A hand-made single level: a square arena of [arenaSize] u, fixed spawns and a briefing. Cleared like a normal
 * level (every asteroid and enemy destroyed). Texts live in `messages.properties` under `scenario_<id>_*`.
 */
class Scenario(
    val id: String,
    val arenaSize: Float,
    val physics: ScenarioPhysics = ScenarioPhysics(),
    val spawn: ScenarioSpawner.() -> Unit,
) {
    val titleKey: String get() = "scenario_${id}_title"
    val summaryKey: String get() = "scenario_${id}_summary"
    val briefingKey: String get() = "scenario_${id}_briefing"
}
