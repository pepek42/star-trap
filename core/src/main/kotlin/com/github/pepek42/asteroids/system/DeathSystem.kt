package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.physics.box2d.Body
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.RemoveComponent
import com.github.pepek42.asteroids.component.asteroidCmp
import com.github.pepek42.asteroids.component.asteroidMapper
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.healthCmp
import com.github.pepek42.asteroids.environment.AsteroidSize
import com.github.pepek42.asteroids.provider.AsteroidProvider
import ktx.ashley.addComponent
import ktx.ashley.allOf
import ktx.ashley.has
import ktx.math.plus
import ktx.math.vec2
import com.badlogic.gdx.math.RandomXS128

class DeathSystem(
    private val engine: Engine,
    private val asteroidProvider: AsteroidProvider,
    private val random: RandomXS128,
) : IteratingSystem(allOf(HealthComponent::class).get()) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        if (entity.healthCmp.health > 0) {
            return
        }
        if (entity.has(asteroidMapper)) {
            val body = entity.bodyCmp.body
            body.fixtureList.forEach { it.isSensor = true }
            val currentAsteroidSize = entity.asteroidCmp.asteroidSize
            if (currentAsteroidSize.nextSize != null) {
                spawnSmallerAsteroids(body, currentAsteroidSize)
            }
        }
        entity.addComponent<RemoveComponent>(engine)
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
            body.angularVelocity
        )

        val secondPosition = vec2(0f, currentAsteroidSize.radius / 2).rotateDeg(speedAngle)
        asteroidProvider.spawnAsteroid(
            currentAsteroidSize.nextSize,
            secondPosition + position,
            currentVelocity.cpy().rotateDeg(random.nextFloat() * MAX_SPEED_DIFF_DEG),
            body.angularVelocity
        )
    }

    companion object {
        private const val MAX_SPEED_DIFF_DEG = 20f
    }
}
