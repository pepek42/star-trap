package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.physics.BETA_MAX_MASSIVE
import com.github.pepek42.asteroids.physics.ENGINE_PROPER_ACCEL
import com.github.pepek42.asteroids.physics.GAMMA_MAX_MASSIVE
import com.github.pepek42.asteroids.physics.REVERSE_THRUST_FRACTION
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import com.github.pepek42.asteroids.physics.gammaFromU
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.render.ShimmerImpacts
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.math.asinh
import kotlin.math.atan2
import kotlin.math.sqrt

class RelativisticDynamicsTest {
    private val c = SPEED_OF_LIGHT
    private val a0 = ENGINE_PROPER_ACCEL
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = Engine()
    private val move = MoveSystem()
    private val dynamics = RelativisticDynamics()
    private val field = BoundaryFieldSystem(MapProvider(mock()), ShimmerImpacts())
    private lateinit var physics: PhysicsSystem

    @AfterEach
    fun tearDown() = world.dispose()

    private fun setup(listeners: (MoveSystem, RelativisticDynamics) -> List<com.github.pepek42.asteroids.physics.PhysicsStepListener> =
        { m, d -> listOf(m, d) }) {
        engine.addSystem(move)
        engine.addSystem(dynamics)
        engine.addSystem(field)
        physics = PhysicsSystem(world, engine, listeners(move, dynamics))
    }

    private class Ship(val body: Body, val rel: RelativisticComponent, val move: MoveComponent)

    private fun addShip(
        x: Float = 0f,
        y: Float = 0f,
        angle: Float = 0f,
        vx: Float = 0f,
        vy: Float = 0f,
        thrusters: Float = 0f,
        radius: Float = 0.5f,
        restitution: Float = 0f,
    ): Ship {
        val body = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(x, y)
            this.angle = angle
            allowSleep = false
        })
        val shape = CircleShape().apply { this.radius = radius }
        body.createFixture(shape, 1f).restitution = restitution
        shape.dispose()
        body.setLinearVelocity(vx, vy)
        val rel = RelativisticComponent()
        com.github.pepek42.asteroids.physics.uFromV(vx, vy, c, BETA_MAX_MASSIVE, rel.u)
        val moveCmp = MoveComponent(thrusters = thrusters)
        engine.addEntity(
            Entity().add(BodyComponent().apply { this.body = body }).add(TransformComponent()).add(rel).add(moveCmp)
        )
        return Ship(body, rel, moveCmp)
    }

    private fun step() = physics.update(PhysicsSystem.PHYSICS_UPDATE_INTERVAL + 1e-4f)

    private fun beta(rel: RelativisticComponent): Float {
        val g = gammaFromU(rel.u.x, rel.u.y, c)
        return rel.u.len() / g / c
    }

    @Test
    fun constantThrustMatchesHyperbolicMotion() {
        setup()
        val ship = addShip(thrusters = 1f)
        val steps = 94
        repeat(steps) { step() }
        val t = steps * PhysicsSystem.PHYSICS_UPDATE_INTERVAL
        val x = a0 * t / c
        assertEquals(x / sqrt(1 + x * x), beta(ship.rel), 0.005f)
        assertEquals(x / sqrt(1 + x * x), ship.body.linearVelocity.len() / c, 0.005f)
        assertEquals(c / a0 * asinh(x), ship.rel.tau, 0.02f)
    }

    @Test
    fun longThrustNeverReachesCap() {
        setup()
        val ship = addShip(thrusters = 1f)
        repeat(20 * 45) {
            step()
            assertTrue(ship.body.linearVelocity.len().isFinite())
        }
        // The γ cap is reached after ~10 s, where β equals BETA_MAX_MASSIVE up to Float rounding.
        assertTrue(beta(ship.rel) <= BETA_MAX_MASSIVE + 1e-5f, "beta ${beta(ship.rel)}")
        assertTrue(ship.body.linearVelocity.len() < c)
        assertTrue(gammaFromU(ship.rel.u.x, ship.rel.u.y, c) <= GAMMA_MAX_MASSIVE + 1e-3f)
        assertFalse(ship.rel.u.x.isNaN() || ship.rel.u.y.isNaN() || ship.rel.tau.isNaN())
        assertTrue(ship.body.position.x.isFinite())
    }

    @Test
    fun sidewaysThrustAtHighSpeedTurnsVelocityByGammaSquaredLess() {
        setup()
        val beta = 0.9f
        val v = beta * c
        // Facing +y while moving along +x.
        val ship = addShip(angle = MathUtils.HALF_PI, vx = v, thrusters = 1f)
        val gamma = gammaFromU(ship.rel.u.x, ship.rel.u.y, c)
        step()
        val dt = PhysicsSystem.PHYSICS_UPDATE_INTERVAL
        val expected = a0 * dt / (gamma * gamma * v)
        val vel = ship.body.linearVelocity
        val turned = atan2(vel.y, vel.x)
        assertEquals(expected, turned, expected * 0.1f)
    }

    @Test
    fun reverseThrustIsWeakerThanMain() {
        setup()
        val ship = addShip(thrusters = -1f)
        step()
        val dt = PhysicsSystem.PHYSICS_UPDATE_INTERVAL
        assertEquals(-a0 * REVERSE_THRUST_FRACTION * dt, ship.rel.u.x, 1e-3f)
    }

    @Test
    fun nanProperVelocityIsResetToZero() {
        setup()
        val ship = addShip()
        ship.rel.u.set(Float.NaN, 1f)
        step()
        assertEquals(0f, ship.rel.u.x)
        assertEquals(0f, ship.rel.u.y)
        assertTrue(ship.body.linearVelocity.x.isFinite() && ship.body.linearVelocity.y.isFinite())
    }

    @Test
    fun nanAccelerationIsDiscarded() {
        setup()
        val ship = addShip()
        ship.rel.accel.set(Float.POSITIVE_INFINITY, 0f)
        step()
        assertEquals(0f, ship.rel.u.len())
        assertEquals(0f, ship.rel.accel.len())
    }

    @Test
    fun headOnCollisionKeepsProperVelocityConsistentWithBody() {
        setup()
        val a = addShip(x = -2f, vx = 10f, restitution = 1f)
        val b = addShip(x = 2f, vx = -10f, restitution = 1f)
        var bounced = false
        repeat(40) {
            step()
            if (a.body.linearVelocity.x < 0f) bounced = true
        }
        assertTrue(bounced, "bodies never collided")
        for (s in listOf(a, b)) {
            val v = s.body.linearVelocity
            val g = 1f / sqrt(1f - v.len2() / (c * c))
            assertEquals(g * v.x, s.rel.u.x, 1e-3f)
            assertEquals(g * v.y, s.rel.u.y, 1e-3f)
        }
    }

    @Test
    fun accelerationAccumulatorIsClearedEveryStep() {
        setup()
        val ship = addShip(thrusters = 1f)
        step()
        assertEquals(0f, ship.rel.accel.len())
        ship.move.thrusters = 0f
        val uBefore = ship.rel.u.x
        step()
        assertEquals(uBefore, ship.rel.u.x, 1e-4f)
    }

    @Test
    fun entityWithoutRelativisticComponentKeepsNewtonianThrust() {
        setup()
        val body = world.createBody(BodyDef().apply { type = BodyDef.BodyType.DynamicBody })
        val shape = CircleShape().apply { radius = 0.5f }
        body.createFixture(shape, 10f / (MathUtils.PI * 0.25f)) // mass 10 keeps v below the Box2D translation clamp
        shape.dispose()
        engine.addEntity(
            Entity().add(BodyComponent().apply { this.body = body }).add(TransformComponent())
                .add(MoveComponent(thrusters = 1f))
        )
        repeat(45) { step() }
        assertEquals(MoveSystem.MAIN_ENGINE_THRUST / body.mass, body.linearVelocity.len(), 1f)
    }

    @Test
    fun boundaryFieldKeepsFastRelativisticBodyInsideArena() {
        setup { m, d -> listOf(m, field, d) }
        // 0.5c toward the +x wall of the 100x100 arena.
        val ship = addShip(x = 70f, y = 50f, vx = 30f, radius = 1f)
        repeat(100) {
            step()
            assertTrue(ship.body.position.x + 1f <= 100f, "x=${ship.body.position.x}")
        }
        assertTrue(ship.body.linearVelocity.x < 0f)
        assertTrue(ship.rel.u.x < 0f, "u must follow the field-reversed velocity, u=${ship.rel.u}")
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
