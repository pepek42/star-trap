package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.GravitySourceComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.physics.BH_RS
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.provider.PLANET_RADIUS
import com.github.pepek42.asteroids.provider.PlanetProvider
import com.github.pepek42.asteroids.render.ShimmerImpacts
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/** Planet landing and being eaten by a hole (ST-093) on a real `World`. */
class PlanetSystemTest {
    private val settings = RelativitySettings().apply { playerGm = 0f; sourceCoupling = 1f }
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = Engine()
    private val mapProvider = MapProvider(mock(), 200f, 200f)
    private val field = BoundaryFieldSystem(mapProvider, ShimmerImpacts(), settings)
    private val gravity = GravitySystem(settings)
    private val dynamics = RelativisticDynamics(settings)
    private val physics: PhysicsSystem
    private val texture = mock<Texture> {
        on { width } doReturn 10
        on { height } doReturn 10
    }
    private val planets = PlanetProvider(engine, world, mapProvider, settings) { Sprite(texture) }

    init {
        engine.addSystem(field)
        engine.addSystem(gravity)
        engine.addSystem(dynamics)
        physics = PhysicsSystem(world, engine, listOf(field, gravity, dynamics))
    }

    @AfterEach
    fun tearDown() = world.dispose()

    private fun steps(seconds: Float) = repeat((seconds / PHYSICS_UPDATE_INTERVAL).toInt()) { physics.update(PHYSICS_UPDATE_INTERVAL) }

    private fun body(x: Float, y: Float, radius: Float, sensor: Boolean): Body {
        val b = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(x, y)
            allowSleep = false
        })
        val shape = CircleShape().apply { this.radius = radius }
        val f = b.createFixture(shape, 1f)
        f.isSensor = sensor
        f.filterData = f.filterData.apply {
            categoryBits = FactionEnum.PLAYER.ownCategoryBit
            maskBits = FactionEnum.PLAYER.collidesWithMask
        }
        shape.dispose()
        return b
    }

    /** Fall time from 3 planet radii at the derived surface gravity (half the default engine acceleration) plus settling; the card's 5 s assumed GM = 300. */
    private val LANDING_SECONDS = 8f

    @Test
    fun shipDroppedOnAPlanetFallsAndComesToRestOnTheSurface() {
        val planet = planets.spawnPlanet(100f, 100f, 0)
        val shipRadius = 0.5f
        val ship = Entity()
            .add(BodyComponent().apply { body = body(100f, 100f + 3f * PLANET_RADIUS, shipRadius, sensor = false) })
            .add(TransformComponent())
            .add(BaseInfoComponent().apply { this.entityType = EntityType.SHIP })
        engine.addEntity(ship)
        val shipBody = ship.getComponent(BodyComponent::class.java).body
        val planetBody = planet.getComponent(BodyComponent::class.java).body

        steps(LANDING_SECONDS)

        val d = shipBody.position.dst(planetBody.position)
        assertEquals(PLANET_RADIUS + shipRadius, d, 0.3f)
        assertEquals(0f, shipBody.linearVelocity.len(), 0.5f)
    }

    @Test
    fun planetFiveRsFromAHoleIsEatenWithinThreeSeconds() {
        val hole = Entity()
            .add(BodyComponent().apply { body = body(100f, 100f, BH_RS, sensor = true) })
            .add(TransformComponent())
            .add(BaseInfoComponent().apply { this.entityType = EntityType.BLACK_HOLE })
            .add(GravitySourceComponent())
        engine.addEntity(hole)
        val planet = planets.spawnPlanet(100f + 5f * BH_RS, 100f, 0)

        steps(3f)

        assertNotNull(removeMapper.get(planet))
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun init() {
            GdxNativesLoader.load()
            Box2D.init()
            Gdx.app = mock<Application> { on { logLevel } doReturn Application.LOG_NONE }
        }
    }
}
