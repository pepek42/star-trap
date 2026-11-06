package com.github.pepek42.asteroids.provider

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.faction.FactionEnum
import ktx.ashley.entity
import ktx.ashley.with
import ktx.box2d.body
import ktx.box2d.circle

/** Sprite size range of a debris piece (u). */
const val DEBRIS_MIN_SIZE = 0.8f
const val DEBRIS_MAX_SIZE = 1.6f

/** Collision radius as a fraction of the sprite size. */
const val DEBRIS_RADIUS_FRACTION = 0.4f

const val DEBRIS_DENSITY = 0.5f

/** Low health: one or two shots destroy a piece. */
const val DEBRIS_HEALTH = 10f

/** Debris keeps this far (u) from every arena wall, from the centre, from every gravity source and from other pieces. */
const val DEBRIS_WALL_MARGIN = 15f
const val DEBRIS_CENTER_CLEARANCE = 20f
const val DEBRIS_SOURCE_CLEARANCE = 12f
const val DEBRIS_MIN_SEPARATION = 8f

private const val DEBRIS_MAX_ATTEMPTS = 1000

/** Number of debris pieces on [level]. A function so it can grow with the level later. */
@Suppress("UNUSED_PARAMETER")
fun debrisCount(level: Int): Int = 6

/** Level set dressing (ST-094): small bodies at rest that then fall under gravity. No relativistic dynamics, not an asteroid. */
class DecorationProvider(
    private val engine: Engine,
    private val world: World,
    private val mapProvider: MapProvider,
    private val createSprite: () -> Sprite,
) {
    /**
     * Spawns the debris of [level] away from the first [n] points of [sources] (flat x, y pairs: holes and planets) and from each
     * other. Returns the debris positions as flat x, y pairs (fewer if no valid point was found).
     */
    fun spawn(level: Int, random: RandomXS128, sources: FloatArray, n: Int): FloatArray {
        val count = debrisCount(level)
        val mapW = mapProvider.mapWidth
        val mapH = mapProvider.mapHeight
        val placed = FloatArray(2 * count)
        var total = 0
        for (k in 0 until count) {
            var attempts = 0
            while (attempts++ < DEBRIS_MAX_ATTEMPTS) {
                val x = random.nextFloat() * mapW
                val y = random.nextFloat() * mapH
                if (!validSourcePosition(
                        x, y, DEBRIS_WALL_MARGIN, sources, n, DEBRIS_SOURCE_CLEARANCE, mapW, mapH, DEBRIS_CENTER_CLEARANCE,
                    )
                ) continue
                if (!validSourcePosition(x, y, 0f, placed, total, DEBRIS_MIN_SEPARATION, mapW, mapH, 0f)) continue
                val size = DEBRIS_MIN_SIZE + random.nextFloat() * (DEBRIS_MAX_SIZE - DEBRIS_MIN_SIZE)
                spawnDebris(x, y, size, random.nextFloat() * 360f)
                placed[2 * total] = x
                placed[2 * total + 1] = y
                total++
                break
            }
        }
        return placed.copyOfRange(0, 2 * total)
    }

    fun spawnDebris(x: Float, y: Float, size: Float, rotationDeg: Float): Entity = engine.entity {
        with<BaseInfoComponent> {
            faction = FactionEnum.NEUTRAL
            entityType = EntityType.DEBRIS
        }
        with<TransformComponent> {
            prevPosition.set(x, y)
            prevRotationDeg = rotationDeg
        }
        with<SpriteComponent> {
            val s = createSprite()
            s.setSize(size, size)
            s.setOriginCenter()
            sprite = s
        }
        with<HealthComponent> { health = DEBRIS_HEALTH }
        with<BodyComponent> {
            body = world.body(type = BodyDef.BodyType.DynamicBody) {
                allowSleep = false
                position.set(x, y)
                angle = Math.toRadians(rotationDeg.toDouble()).toFloat()
                circle(radius = DEBRIS_RADIUS_FRACTION * size) {
                    density = DEBRIS_DENSITY
                    filter.categoryBits = FactionEnum.NEUTRAL.ownCategoryBit
                    filter.maskBits = FactionEnum.NEUTRAL.collidesWithMask
                }
                userData = this@entity.entity
            }
        }
    }
}
