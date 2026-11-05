package com.github.pepek42.asteroids.provider

import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.UNIT_SCALE
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.ShipHandlingComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TACTICAL_COMPUTER_DEFAULT_TIER
import com.github.pepek42.asteroids.component.TacticalComputerComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.weapon.WeaponType
import ktx.ashley.add
import ktx.ashley.entity
import ktx.ashley.with
import ktx.box2d.body
import ktx.box2d.box
import ktx.math.vec2

/** Box2D density of the player ship. */
const val PLAYER_DENSITY = 20f

/** Pixel size of the (square) player sprite `spaceship/disc_green` in the atlas. */
const val PLAYER_SPRITE_SIZE_PX = 24f

/** Rest mass of the player's box fixture: density times the sprite area in world units. */
fun playerRestMass(): Float {
    val side = PLAYER_SPRITE_SIZE_PX / UNIT_SCALE
    return PLAYER_DENSITY * side * side
}

class PlayerEntityProvider(
    private val engine: PooledEngine,
    private val world: World,
    private val mapProvider: MapProvider,
    private val textureAtlas: TextureAtlas,
    private val settings: RelativitySettings = RelativitySettings(),
) {
    fun spawnPlayerEntity() {
        val playerSprite = textureAtlas.createSprite("spaceship/disc_green")
        val worldUnitsWidth = playerSprite.width / UNIT_SCALE
        val worldUnitsHeight = playerSprite.height / UNIT_SCALE

        val playerSpawnPosition = mapProvider.playerSpawnLocation()
        val playerFaction = FactionEnum.PLAYER
        engine.add {
            entity {
                with<BodyComponent> {
                    body = world.body(BodyDef.BodyType.DynamicBody) {
                        // RelativisticDynamics rewrites the velocity every step, so the body must never sleep.
                        allowSleep = false
                        this.position.set(playerSpawnPosition)
                        box(
                            width = worldUnitsWidth,
                            height = worldUnitsHeight,
                        ) {
                            density = PLAYER_DENSITY
                            filter.categoryBits = playerFaction.ownCategoryBit
                            filter.maskBits = playerFaction.collidesWithMask
                        }
                        userData = this@entity.entity
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
                with<RelativisticComponent> { // starts at rest: u = 0
                    captureRestMass(this@entity.entity.bodyCmp.body)
                }
                with<ShipHandlingComponent>()
                with<PlayerComponent>()
                with<TacticalComputerComponent> { tier = TACTICAL_COMPUTER_DEFAULT_TIER }
                with<WeaponComponent> {
                    ready = true
                    weaponType = WeaponType.LASER
                    weaponPosition = vec2(worldUnitsWidth / 2, 0f)
                    shotsPerSecond = 3f
                    // Ignored for LASER: WeaponProjectileProvider always fires lasers at settings.c.
                    projectileSpeed = settings.c
                    projectileDamage = 50f
                    projectileRadius = 0.1f
                }
                with<BaseInfoComponent> {
                    faction = FactionEnum.PLAYER
                    entityType = EntityType.SHIP
                }
                with<HealthComponent> { health = 100f }
            }
        }
    }
}
