package com.github.pepek42.asteroids.provider

import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.UNIT_SCALE
import com.github.pepek42.asteroids.ai.ENEMY_ENGINE_FRACTION
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.EnemyComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.ShipHandlingComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.weapon.WeaponType
import ktx.ashley.add
import ktx.ashley.entity
import ktx.ashley.with
import ktx.box2d.body
import ktx.box2d.box
import ktx.math.vec2
import kotlin.math.max
import kotlin.math.min

/** Most enemies on any level (ST-160 rec. a). */
const val ENEMY_MAX = 3

/** Enemy health at spawn. */
const val ENEMY_HEALTH = 100f

/** Damage of one enemy laser shot. */
const val ENEMY_LASER_DAMAGE = 10f

/** Enemy hull density: same as the player's. */
const val ENEMY_DENSITY = PLAYER_DENSITY

/** Minimum spawn distance from the player: 3 light-seconds, i.e. `ENEMY_MIN_SPAWN_LIGHT_SECONDS * settings.c` world units. */
const val ENEMY_MIN_SPAWN_LIGHT_SECONDS = 3f

/** Clearance of an enemy spawn point from the arena walls, in world units. */
const val ENEMY_WALL_MARGIN = 15f

/** Clearance of an enemy spawn point from black holes and planets, in world units. */
const val ENEMY_SOURCE_CLEARANCE = 20f

private const val ENEMY_SPAWN_TRIES = 50

/** Number of enemies on [level]: none on level 1, then one more per level up to [ENEMY_MAX]. */
fun enemyCount(level: Int): Int = min(ENEMY_MAX, max(0, level - 1))

class EnemyShipProvider(
    private val engine: PooledEngine,
    private val world: World,
    private val textureAtlas: TextureAtlas,
    private val settings: RelativitySettings = RelativitySettings(),
) {
    /**
     * Spawns the enemies of [level] at rest; [avoid] is holes and planets as flat x, y pairs.
     * Returns the positions used (flat x, y pairs; fewer if no valid point was found).
     */
    fun spawn(level: Int, random: RandomXS128, mapProvider: MapProvider, avoid: FloatArray): FloatArray {
        val count = enemyCount(level)
        val mapW = mapProvider.mapWidth
        val mapH = mapProvider.mapHeight
        val player = mapProvider.playerSpawnLocation()
        val minDistance = ENEMY_MIN_SPAWN_LIGHT_SECONDS * settings.c
        val placed = FloatArray(count * 2)
        var n = 0
        for (i in 0 until count) {
            for (attempt in 0 until ENEMY_SPAWN_TRIES) {
                val x = random.nextFloat() * mapW
                val y = random.nextFloat() * mapH
                if (!validEnemyPosition(x, y, player.x, player.y, minDistance, avoid, mapW, mapH)) continue
                spawnEnemy(x, y)
                placed[2 * n] = x
                placed[2 * n + 1] = y
                n++
                break
            }
        }
        return placed.copyOf(2 * n)
    }

    private fun spawnEnemy(x: Float, y: Float) {
        val sprite = textureAtlas.createSprite("spaceship/attecker_red")
        val w = sprite.width / UNIT_SCALE
        val h = sprite.height / UNIT_SCALE
        val faction = FactionEnum.ENEMY
        engine.add {
            entity {
                with<BodyComponent> {
                    body = world.body(BodyDef.BodyType.DynamicBody) {
                        // RelativisticDynamics rewrites the velocity every step, so the body must never sleep.
                        allowSleep = false
                        position.set(x, y)
                        box(width = w, height = h) {
                            density = ENEMY_DENSITY
                            filter.categoryBits = faction.ownCategoryBit
                            filter.maskBits = faction.collidesWithMask
                        }
                        userData = this@entity.entity
                    }
                }
                with<TransformComponent>()
                with<SpriteComponent> {
                    this.sprite = sprite
                    sprite.setSize(w, h)
                    sprite.setOriginCenter()
                    sprite.setPosition(x - w / 2, y - h / 2)
                }
                with<MoveComponent>()
                with<RelativisticComponent> { captureRestMass(this@entity.entity.bodyCmp.body) }
                with<ShipHandlingComponent> { engineFactor = ENEMY_ENGINE_FRACTION }
                with<EnemyComponent>()
                with<WeaponComponent> {
                    ready = true
                    weaponType = WeaponType.LASER
                    weaponPosition = vec2(w / 2, 0f)
                    shotsPerSecond = 1f
                    // Ignored for LASER: WeaponProjectileProvider always fires lasers at settings.c.
                    projectileSpeed = settings.c
                    projectileDamage = ENEMY_LASER_DAMAGE
                    projectileRadius = 0.1f
                }
                with<BaseInfoComponent> {
                    this.faction = faction
                    entityType = EntityType.SHIP
                }
                with<HealthComponent> { health = ENEMY_HEALTH }
            }
        }
    }
}

/** Pure spawn rule: inside the walls, at least [minDistance] from the player, clear of every source in [avoid]. */
fun validEnemyPosition(
    x: Float, y: Float, playerX: Float, playerY: Float, minDistance: Float,
    avoid: FloatArray, mapW: Float, mapH: Float,
): Boolean {
    if (kotlin.math.hypot(x - playerX, y - playerY) < minDistance) return false
    return validSourcePosition(x, y, ENEMY_WALL_MARGIN, avoid, avoid.size / 2, ENEMY_SOURCE_CLEARANCE, mapW, mapH, 0f)
}
