package com.github.pepek42.asteroids.provider

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.AsteroidComponent
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.WrapComponent
import com.github.pepek42.asteroids.environment.ASTEROID_DENSITY
import com.github.pepek42.asteroids.environment.AsteroidSize
import com.github.pepek42.asteroids.faction.FactionEnum
import ktx.ashley.entity
import ktx.ashley.with
import ktx.box2d.body
import ktx.box2d.circle
import ktx.math.plus
import ktx.math.vec2
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

class AsteroidProvider(
    private val textureAtlas: TextureAtlas,
    private val engine: Engine,
    private val world: World,
) {
    fun spawnAsteroid(size: AsteroidSize, position: Vector2, velocity: Vector2, angularSpeed: Float) {
        engine.entity {
            with<SpriteComponent> {
                val sprite = textureAtlas.createSprite(size.textureRegionName)
                sprite.setSize(size.radius * 2, size.radius * 2)
                sprite.setOriginCenter()
                this.sprite = sprite
            }
            with<BodyComponent> {
                body = world.body(type = BodyDef.BodyType.DynamicBody) {
                    allowSleep = false
                    this.position.set(position)
                    this.linearVelocity.set(velocity)
                    this.angularVelocity = angularSpeed
                    circle(radius = size.radius) {
                        density = ASTEROID_DENSITY
                        filter.maskBits = FactionEnum.NEUTRAL.entityMaskBit
                        filter.categoryBits = FactionEnum.NEUTRAL.categoryBits
                    }
                    userData = this@entity.entity
                }
            }
            with<TransformComponent> {
                prevPosition.set(position)
            }
            with<WrapComponent>()
            with<BaseInfoComponent> {
                faction = FactionEnum.NEUTRAL
                entityType = EntityType.ASTEROID
            }
            with<HealthComponent> { health = 150f }
            with<AsteroidComponent> { asteroidSize = size }
        }
    }

    fun spawnAsteroids(level: Int, mapProvider: MapProvider) {
        val maxAsteroids = 1 + level.toDouble().pow(2).toInt()
        val center = vec2(mapProvider.mapWidth / 2f, mapProvider.mapHeight / 2f)
        val radius = min(center.x, center.y)
        val step = MathUtils.PI2 / maxAsteroids
        for (i in 0 until maxAsteroids) {
            val angle = step * i * Random.nextDouble(0.9, 1.1).toFloat()
            val positionShift = vec2(radius * 0.85f, 0f).rotateRad(angle)
            spawnAsteroid(
                size = AsteroidSize.LARGE,
                position = center + positionShift,
                velocity = vec2(Random.nextDouble(-10.0, 10.0).toFloat(), Random.nextDouble(-10.0, 10.0).toFloat()),
                angularSpeed = Random.nextFloat() * 3f,
            )
        }
    }
}
