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
import com.github.pepek42.asteroids.component.GravitySourceComponent
import com.github.pepek42.asteroids.component.GravitySourceKind
import com.github.pepek42.asteroids.component.SpriteComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.render.PLANET_VARIANTS
import ktx.ashley.entity
import ktx.ashley.with
import ktx.box2d.body
import ktx.box2d.circle

/** Surface radius of a planet (u). */
const val PLANET_RADIUS = 2.5f

/** Surface gravity as a fraction of the engine's proper acceleration, so ships can take off from a planet. */
const val PLANET_SURFACE_GRAVITY_FRACTION = 0.5f

/**
 * Gravitational parameter of a planet at spawn: `g_surface * R^2` with `g_surface = PLANET_SURFACE_GRAVITY_FRACTION` times the
 * runtime engine proper acceleration `settings.engineProperAccel` (about 4.1 u/s^2 at the default c = 20).
 */
fun planetGm(settings: RelativitySettings): Float =
    PLANET_SURFACE_GRAVITY_FRACTION * settings.engineProperAccel * PLANET_RADIUS * PLANET_RADIUS

/** Heavy relative to ships and asteroids, so ramming barely moves a planet. */
const val PLANET_DENSITY = 20f

/** Asteroids don't spawn within this distance (u) of a planet centre. */
const val PLANET_ASTEROID_CLEARANCE = PLANET_RADIUS + 6f

/** Planets keep this far (u) from every arena wall at spawn. */
const val PLANET_WALL_MARGIN = 25f

/** Planets spawn at least this far (u) from the arena centre, every hole and every other planet. */
const val PLANET_MIN_SEPARATION = 30f

/** First level with two planets. */
const val PLANET_TWO_LEVEL = 4

private const val PLANET_FRICTION = 0.3f
private const val PLANET_RESTITUTION = 0.2f
private const val PLANET_MAX_ATTEMPTS = 1000

/** Number of planets on [level]: one, two from level [PLANET_TWO_LEVEL]. Pure. */
fun planetCount(level: Int): Int = if (level >= PLANET_TWO_LEVEL) 2 else 1

/** Solid gravity sources (ST-093): at rest at spawn, indestructible, no relativistic dynamics. */
class PlanetProvider(
    private val engine: Engine,
    private val world: World,
    private val mapProvider: MapProvider,
    private val settings: RelativitySettings,
    private val createSprite: (variant: Int) -> Sprite,
) {
    /**
     * Spawns the planets of [level] at valid points away from the first [n] points of [occupied] (flat x, y pairs, the black
     * holes) and from each other. Returns the planet positions as flat x, y pairs (fewer if no valid point was found).
     */
    fun spawn(level: Int, random: RandomXS128, occupied: FloatArray, n: Int): FloatArray {
        val count = planetCount(level)
        val mapW = mapProvider.mapWidth
        val mapH = mapProvider.mapHeight
        val others = FloatArray(2 * (n + count))
        occupied.copyInto(others, 0, 0, 2 * n)
        var total = n
        for (k in 0 until count) {
            var attempts = 0
            while (attempts++ < PLANET_MAX_ATTEMPTS) {
                val x = random.nextFloat() * mapW
                val y = random.nextFloat() * mapH
                if (!validSourcePosition(
                        x, y, PLANET_WALL_MARGIN, others, total, PLANET_MIN_SEPARATION, mapW, mapH, PLANET_MIN_SEPARATION,
                    )
                ) continue
                spawnPlanet(x, y, random.nextInt(PLANET_VARIANTS))
                others[2 * total] = x
                others[2 * total + 1] = y
                total++
                break
            }
        }
        return others.copyOfRange(2 * n, 2 * total)
    }

    fun spawnPlanet(x: Float, y: Float, variant: Int): Entity = engine.entity {
        with<BaseInfoComponent> {
            faction = FactionEnum.NEUTRAL
            entityType = EntityType.PLANET
        }
        with<TransformComponent> { prevPosition.set(x, y) }
        with<SpriteComponent> {
            val s = createSprite(variant)
            s.setSize(2f * PLANET_RADIUS, 2f * PLANET_RADIUS)
            s.setOriginCenter()
            sprite = s
        }
        with<GravitySourceComponent> {
            kind = GravitySourceKind.PLANET
            gm = planetGm(settings)
            radius = PLANET_RADIUS
            softening = PLANET_RADIUS
        }
        with<BodyComponent> {
            body = world.body(type = BodyDef.BodyType.DynamicBody) {
                allowSleep = false
                position.set(x, y)
                circle(radius = PLANET_RADIUS) {
                    density = PLANET_DENSITY
                    friction = PLANET_FRICTION
                    restitution = PLANET_RESTITUTION
                    filter.categoryBits = FactionEnum.NEUTRAL.ownCategoryBit
                    filter.maskBits = FactionEnum.NEUTRAL.collidesWithMask
                }
                userData = this@entity.entity
            }
        }
    }
}
