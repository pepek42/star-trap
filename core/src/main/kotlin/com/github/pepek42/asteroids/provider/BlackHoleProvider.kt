package com.github.pepek42.asteroids.provider

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.GravitySourceComponent
import com.github.pepek42.asteroids.component.GravitySourceKind
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.physics.BH_RS
import ktx.ashley.entity
import ktx.ashley.with
import ktx.box2d.body
import ktx.box2d.circle
import kotlin.math.hypot

/** Black holes keep this far (u) from every arena wall at spawn. */
const val BH_WALL_MARGIN = 30f

/** Black holes spawn at least this far (u) from the arena centre (the player spawn). */
const val BH_CENTER_CLEARANCE = 40f

/** Black holes spawn at least this far (u) from each other. */
const val BH_MIN_SEPARATION = 60f

/** Asteroids don't spawn within this distance of a black hole: `8 r_s` (8 u for `BH_RS` = 1). */
const val BH_ASTEROID_CLEARANCE = 8f * BH_RS

/** First level with two black holes. */
const val BH_TWO_HOLES_LEVEL = 3

private const val BH_MAX_ATTEMPTS = 1000

/**
 * Spawn rule for gravity sources (black holes now, planets and others later): the point ([x], [y]) is at least [wallMargin]
 * from every wall of the [mapW] x [mapH] arena, at least [centerClearance] from the arena centre, and at least [minSep] from each of
 * the first [n] points in [others] (flat x, y pairs). Pure.
 */
fun validSourcePosition(
    x: Float,
    y: Float,
    wallMargin: Float,
    others: FloatArray,
    n: Int,
    minSep: Float,
    mapW: Float,
    mapH: Float,
    centerClearance: Float = BH_CENTER_CLEARANCE,
): Boolean {
    if (x < wallMargin || y < wallMargin || x > mapW - wallMargin || y > mapH - wallMargin) return false
    if (hypot(x - mapW / 2f, y - mapH / 2f) < centerClearance) return false
    for (i in 0 until n) {
        if (hypot(x - others[2 * i], y - others[2 * i + 1]) < minSep) return false
    }
    return true
}

/** Number of black holes on [level]: one, two from level [BH_TWO_HOLES_LEVEL]. */
fun blackHoleCount(level: Int): Int = if (level >= BH_TWO_HOLES_LEVEL) 2 else 1

class BlackHoleProvider(
    private val engine: Engine,
    private val world: World,
    private val mapProvider: MapProvider,
) {
    /** Spawns the holes of [level] at rest; returns their positions as flat x, y pairs (fewer if no valid point was found). */
    fun spawn(level: Int, random: RandomXS128): FloatArray {
        val count = blackHoleCount(level)
        val mapW = mapProvider.mapWidth
        val mapH = mapProvider.mapHeight
        val positions = FloatArray(2 * count)
        var placed = 0
        for (k in 0 until count) {
            var attempts = 0
            while (attempts++ < BH_MAX_ATTEMPTS) {
                val x = random.nextFloat() * mapW
                val y = random.nextFloat() * mapH
                if (!validSourcePosition(x, y, BH_WALL_MARGIN, positions, placed, BH_MIN_SEPARATION, mapW, mapH)) continue
                spawnHole(x, y)
                positions[2 * placed] = x
                positions[2 * placed + 1] = y
                placed++
                break
            }
        }
        return positions.copyOf(2 * placed)
    }

    private fun spawnHole(x: Float, y: Float): Entity = engine.entity {
        with<BaseInfoComponent> {
            faction = FactionEnum.NEUTRAL
            entityType = EntityType.BLACK_HOLE
        }
        with<TransformComponent> { prevPosition.set(x, y) }
        with<GravitySourceComponent> {
            kind = GravitySourceKind.BLACK_HOLE
            rs = BH_RS
        }
        with<BodyComponent> {
            body = world.body(type = BodyDef.BodyType.DynamicBody) {
                allowSleep = false
                fixedRotation = true
                position.set(x, y)
                circle(radius = BH_RS) {
                    isSensor = true
                    density = 1f
                    filter.categoryBits = FactionEnum.NEUTRAL.ownCategoryBit
                    filter.maskBits = 0
                }
                userData = this@entity.entity
            }
        }
    }
}
