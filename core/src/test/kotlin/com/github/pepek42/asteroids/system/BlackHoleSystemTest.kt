package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.AsteroidComponent
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.GravitySourceComponent
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.physics.BH_RS
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.render.ShimmerImpacts
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/** Horizon, merge and arena containment of black holes (ST-085) on a real `World` with the gravity and boundary systems. */
class BlackHoleSystemTest {
    private val settings = RelativitySettings()
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = Engine()
    private val mapProvider = MapProvider(mock(), 200f, 200f)
    private val field = BoundaryFieldSystem(mapProvider, ShimmerImpacts(), settings)
    private val gravity = GravitySystem(settings)
    private val dynamics = RelativisticDynamics(settings)
    private val physics: PhysicsSystem

    init {
        engine.addSystem(field)
        engine.addSystem(gravity)
        engine.addSystem(dynamics)
        physics = PhysicsSystem(world, engine, listOf(field, gravity, dynamics))
    }

    @AfterEach
    fun tearDown() = world.dispose()

    private fun body(x: Float, y: Float, vx: Float = 0f, vy: Float = 0f, radius: Float = 0.5f): Body {
        val b = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(x, y)
            linearVelocity.set(vx, vy)
            allowSleep = false
        })
        val shape = CircleShape().apply { this.radius = radius }
        b.createFixture(shape, 1f).isSensor = true
        shape.dispose()
        return b
    }

    private fun hole(x: Float, y: Float, vx: Float = 0f, vy: Float = 0f): Entity {
        val e = Entity()
            .add(BodyComponent().apply { this.body = body(x, y, vx, vy, BH_RS) })
            .add(TransformComponent())
            .add(BaseInfoComponent(entityType = EntityType.BLACK_HOLE))
            .add(GravitySourceComponent())
        engine.addEntity(e)
        return e
    }

    private fun massive(x: Float, y: Float, player: Boolean): Entity {
        val b = body(x, y)
        val rel = RelativisticComponent().apply { captureRestMass(b) }
        val e = Entity()
            .add(BodyComponent().apply { this.body = b })
            .add(TransformComponent())
            .add(BaseInfoComponent(entityType = if (player) EntityType.SHIP else EntityType.ASTEROID))
            .add(HealthComponent(100f))
            .add(rel)
        if (player) e.add(PlayerComponent()) else e.add(AsteroidComponent())
        engine.addEntity(e)
        return e
    }

    private fun steps(seconds: Float, each: () -> Unit = {}) {
        repeat((seconds / PHYSICS_UPDATE_INTERVAL).toInt()) {
            physics.update(PHYSICS_UPDATE_INTERVAL)
            each()
        }
    }

    @Test
    fun asteroidAtRestTenUnitsAwayIsAbsorbedWithinThreeSeconds() {
        hole(100f, 100f)
        val asteroid = massive(110f, 100f, player = false)

        steps(3f)

        assertNotNull(removeMapper.get(asteroid))
    }

    @Test
    fun playerAtRestTenUnitsAwayDiesInTheHole() {
        hole(100f, 100f)
        val player = massive(110f, 100f, player = true)
        settings.playerGm = 0f

        steps(3f)

        assertEquals(0f, player.getComponent(HealthComponent::class.java).health)
        assertTrue(removeMapper.get(player) == null, "the player is never removed here")
    }

    @Test
    fun holesThreeUnitsApartMergeIntoOneAtTheMidpointKeepingMomentum() {
        settings.gravityEnabled = false
        val a = hole(98.5f, 100f, 2f, 0f)
        val b = hole(101.5f, 100f, -1f, 3f)
        val lower = if (a.getComponent(BaseInfoComponent::class.java).id < b.getComponent(BaseInfoComponent::class.java).id) a else b
        val other = if (lower === a) b else a

        physics.update(PHYSICS_UPDATE_INTERVAL)

        val g = lower.getComponent(GravitySourceComponent::class.java)
        assertEquals(2 * BH_RS, g.rs, 1e-5f)
        val body = lower.getComponent(BodyComponent::class.java).body
        // Equal rs: midpoint after one step of motion (the pair moved by v dt before the merge), momentum = sum / 2 per unit rs.
        assertEquals(100f, body.position.x, 0.1f)
        assertEquals(100f, body.position.y, 0.1f)
        assertEquals(0.5f, body.linearVelocity.x, 1e-4f)
        assertEquals(1.5f, body.linearVelocity.y, 1e-4f)
        assertNotNull(removeMapper.get(other))
        assertTrue(removeMapper.get(lower) == null)
        assertEquals(1, body.fixtureList.size)
        assertEquals(2 * BH_RS, body.fixtureList[0].shape.radius, 1e-5f)
    }

    @Test
    fun holeFlyingTowardAWallStaysInsideTheArena() {
        val h = hole(150f, 100f, 15f, 0f)
        val body = h.getComponent(BodyComponent::class.java).body

        steps(10f) {
            assertTrue(body.position.x in 0f..200f && body.position.y in 0f..200f, "left the arena at ${body.position}")
        }
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
