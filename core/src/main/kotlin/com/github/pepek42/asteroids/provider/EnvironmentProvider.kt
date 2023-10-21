package com.github.pepek42.asteroids.provider

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.WrapComponent
import com.github.pepek42.asteroids.environment.ASTEROID_DENSITY
import com.github.pepek42.asteroids.environment.AsteroidSize
import ktx.ashley.entity
import ktx.ashley.with
import ktx.box2d.body
import ktx.box2d.circle

class EnvironmentProvider(
    private val textureAtlas: TextureAtlas,
    private val engine: Engine,
    private val world: World,
) {
    fun spawnAsteroid(size: AsteroidSize, position: Vector2, velocity: Vector2, angularSpeed: Float) {
        engine.entity {
            with<SpriteComponent> {
                val sprite = textureAtlas.createSprite(size.textureRegionName)
                sprite.setSize(size.radius, size.radius)
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
                    }
                }
            }
            with<TransformComponent> {
                prevPosition.set(position)
            }
            with<WrapComponent>()
        }
    }
}
