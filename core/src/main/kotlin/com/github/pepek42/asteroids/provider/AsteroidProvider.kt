package com.github.pepek42.asteroids.provider

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.AsteroidComponent
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.environment.ASTEROID_COUNT_PER_LEVEL_SQ
import com.github.pepek42.asteroids.environment.ASTEROID_DENSITY
import com.github.pepek42.asteroids.environment.ASTEROID_SPAWN_MIN_RADIUS
import com.github.pepek42.asteroids.environment.ASTEROID_SPAWN_BETA_MAX
import com.github.pepek42.asteroids.environment.AsteroidSize
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.physics.FIELD_SKIN
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.uFromV
import ktx.ashley.entity
import ktx.ashley.with
import ktx.box2d.body
import ktx.box2d.circle
import ktx.math.plus
import ktx.math.vec2
import kotlin.math.min
import kotlin.math.pow

class AsteroidProvider(
    private val textureAtlas: TextureAtlas,
    private val engine: Engine,
    private val world: World,
    private val random: RandomXS128,
    private val settings: RelativitySettings = RelativitySettings(),
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
                        filter.categoryBits = FactionEnum.NEUTRAL.ownCategoryBit
                        filter.maskBits = FactionEnum.NEUTRAL.collidesWithMask
                    }
                    userData = this@entity.entity
                }
            }
            with<TransformComponent> {
                prevPosition.set(position)
            }
            with<BaseInfoComponent> {
                faction = FactionEnum.NEUTRAL
                entityType = EntityType.ASTEROID
            }
            with<HealthComponent> { health = 150f }
            with<AsteroidComponent> { asteroidSize = size }
            with<RelativisticComponent> {
                uFromV(velocity.x, velocity.y, settings.c, settings.betaMaxMassive, u)
            }
        }
    }

    fun spawnAsteroids(level: Int, mapProvider: MapProvider) {
        val maxAsteroids = 1 + ASTEROID_COUNT_PER_LEVEL_SQ * level.toDouble().pow(2).toInt()
        val center = vec2(mapProvider.mapWidth / 2f, mapProvider.mapHeight / 2f)
        val maxRadius = min(center.x, center.y) - FIELD_SKIN - AsteroidSize.LARGE.radius - 1f
        val step = MathUtils.PI2 / maxAsteroids
        for (i in 0 until maxAsteroids) {
            val angle = step * i * random.nextDouble().toFloat().let { 0.9f + it * 0.2f }
            val r = ASTEROID_SPAWN_MIN_RADIUS + random.nextFloat() * (maxRadius - ASTEROID_SPAWN_MIN_RADIUS)
            val positionShift = vec2(r, 0f).rotateRad(angle)
            val speedMax = ASTEROID_SPAWN_BETA_MAX * settings.c
            val vx = random.nextFloat() * 2f * speedMax - speedMax
            val vy = random.nextFloat() * 2f * speedMax - speedMax
            spawnAsteroid(
                size = AsteroidSize.LARGE,
                position = center + positionShift,
                velocity = vec2(vx, vy),
                angularSpeed = random.nextFloat() * 3f,
            )
        }
    }
}
