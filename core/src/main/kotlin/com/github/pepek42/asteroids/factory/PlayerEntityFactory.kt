package com.github.pepek42.asteroids.factory

import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.UNIT_SCALE
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.WrapComponent
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.weapon.WeaponType
import ktx.ashley.add
import ktx.ashley.entity
import ktx.ashley.with
import ktx.box2d.body
import ktx.box2d.box
import ktx.math.vec2

class PlayerEntityFactory(
    private val engine: PooledEngine,
    private val world: World,
    private val mapProvider: MapProvider,
) {
    fun spawnPlayerEntity(playerSprite: Sprite) {
        val worldUnitsWidth = playerSprite.width / UNIT_SCALE
        val worldUnitsHeight = playerSprite.height / UNIT_SCALE

        val playerSpawnPosition = mapProvider.playerSpawnLocation()

        engine.add {
            entity {
                with<BodyComponent> {
                    body = world.body(BodyDef.BodyType.DynamicBody) {
                        this.position.set(playerSpawnPosition)
                        box(
                            width = worldUnitsWidth,
                            height = worldUnitsHeight,
                        ) {
                            density = 20f
                        }
                    }
                }
                with<TransformComponent>()
                with<SpriteComponent> {
                    sprite = playerSprite
                    sprite.setSize(worldUnitsWidth, worldUnitsHeight)
                    sprite.setOriginCenter()
                    sprite.setPosition(
                        playerSpawnPosition.x - worldUnitsWidth / 2,
                        playerSpawnPosition.y - worldUnitsHeight / 2
                    )
                }
                with<MoveComponent>()
                with<PlayerComponent>()
                with<WrapComponent>()
                with<WeaponComponent> {
                    ready = true
                    weaponType = WeaponType.LASER
                    weaponPosition = vec2(worldUnitsWidth / 2, 0f)
                    shotsPerSecond = 3f
                    bulletSpeed = SPEED_OF_LIGHT
                    bulletDamage = 50f
                    bulletRadius = 0.1f
                }
            }
        }
    }
}
