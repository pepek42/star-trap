package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.physics.box2d.Body
import com.github.pepek42.asteroids.audio.ComputerSoundKind
import com.github.pepek42.asteroids.audio.ComputerSoundQueue
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.RemoveComponent
import com.github.pepek42.asteroids.component.asteroidCmp
import com.github.pepek42.asteroids.component.asteroidMapper
import com.github.pepek42.asteroids.component.enemyMapper
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.healthCmp
import com.github.pepek42.asteroids.component.playerMapper
import com.github.pepek42.asteroids.environment.AsteroidSize
import com.github.pepek42.asteroids.physics.FRAGMENT_RAM_IMMUNITY
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.provider.AsteroidProvider
import ktx.ashley.addComponent
import ktx.ashley.allOf
import ktx.ashley.has
import ktx.math.plus
import ktx.math.vec2
import com.badlogic.gdx.math.RandomXS128
import com.github.pepek42.asteroids.provider.PLAYER_START_HEALTH

class DeathSystem(
    private val engine: Engine,
    private val asteroidProvider: AsteroidProvider,
    private val random: RandomXS128,
    /** Break-up events for the ship's computer (ST-136); null in headless runs. Needs [clock] for the event time. */
    private val computerSounds: ComputerSoundQueue? = null,
    private val clock: GameClock? = null,
    /** Harness only (`--invulnerable`): the player is healed back to full instead of ending the run (black hole horizon included). */
    private val invulnerablePlayer: Boolean = false,
    /** Health the harness heals the player back to (the start health). */
    private val invulnerableHealth: () -> Float = { PLAYER_START_HEALTH },
    private val onPlayerDeath: () -> Unit = {},
) : IteratingSystem(allOf(HealthComponent::class).get()) {
    private var playerDeathReported = false

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        playerDeathReported = false
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        if (entity.healthCmp.health > 0) {
            return
        }
        if (entity.has(playerMapper)) {
            if (invulnerablePlayer) {
                entity.healthCmp.health = invulnerableHealth()
                return
            }
            // The run ends instead of removing the player; the screen switch cleans the level up.
            if (!playerDeathReported) {
                playerDeathReported = true
                onPlayerDeath()
            }
            return
        }
        if (entity.has(asteroidMapper)) {
            val body = entity.bodyCmp.body
            body.fixtureList.forEach { it.isSensor = true }
            val currentAsteroidSize = entity.asteroidCmp.asteroidSize
            pushBreakUp(body, currentAsteroidSize)
            if (currentAsteroidSize.nextSize != null) {
                spawnSmallerAsteroids(body, currentAsteroidSize)
            }
        }
        if (entity.has(enemyMapper)) {
            pushEnemyKill(entity.bodyCmp.body)
        }
        entity.addComponent<RemoveComponent>(engine)
    }

    /**
     * The computer announces a destroyed enemy with the large-asteroid break-up sound (ST-157). A dedicated ship sound
     * can replace it later.
     */
    private fun pushEnemyKill(body: Body) {
        val queue = computerSounds ?: return
        val p = body.position
        val v = body.linearVelocity
        queue.push(ComputerSoundKind.ASTEROID_BREAK_LARGE, p.x, p.y, v.x, v.y, clock?.worldTime ?: 0f)
    }

    private fun pushBreakUp(body: Body, size: AsteroidSize) {
        val queue = computerSounds ?: return
        val kind = when (size) {
            AsteroidSize.SMALL -> ComputerSoundKind.ASTEROID_BREAK_SMALL
            AsteroidSize.LARGE -> ComputerSoundKind.ASTEROID_BREAK_LARGE
        }
        val p = body.position
        val v = body.linearVelocity
        queue.push(kind, p.x, p.y, v.x, v.y, clock?.worldTime ?: 0f)
    }

    private fun spawnSmallerAsteroids(body: Body, currentAsteroidSize: AsteroidSize) {
        val position = body.position.cpy()
        val currentVelocity = body.linearVelocity.cpy()
        val speedAngle = currentVelocity.angleDeg()

        val firstPosition = vec2(0f, -currentAsteroidSize.radius / 2).rotateDeg(speedAngle)
        asteroidProvider.spawnAsteroid(
            currentAsteroidSize.nextSize!!,
            firstPosition + position,
            currentVelocity.cpy().rotateDeg(random.nextFloat() * -MAX_SPEED_DIFF_DEG),
            body.angularVelocity,
            FRAGMENT_RAM_IMMUNITY,
        )

        val secondPosition = vec2(0f, currentAsteroidSize.radius / 2).rotateDeg(speedAngle)
        asteroidProvider.spawnAsteroid(
            currentAsteroidSize.nextSize,
            secondPosition + position,
            currentVelocity.cpy().rotateDeg(random.nextFloat() * MAX_SPEED_DIFF_DEG),
            body.angularVelocity,
            FRAGMENT_RAM_IMMUNITY,
        )
    }

    companion object {
        private const val MAX_SPEED_DIFF_DEG = 20f
    }
}
