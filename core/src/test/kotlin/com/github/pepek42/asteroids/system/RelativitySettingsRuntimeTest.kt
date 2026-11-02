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
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.physics.BoundaryFieldParams
import com.github.pepek42.asteroids.physics.ENGINE_TIME_TO_09C
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import com.github.pepek42.asteroids.physics.gammaFromU
import com.github.pepek42.asteroids.physics.uFromV
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.render.ShimmerImpacts
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/** Runtime switches of [RelativitySettings] (ST-056) against a real Box2D world. */
class RelativitySettingsRuntimeTest {
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = Engine()

    @AfterEach
    fun tearDown() = world.dispose()

    private class Ship(val body: Body, val rel: RelativisticComponent)

    private fun addShip(
        settings: RelativitySettings,
        x: Float = 50f,
        vx: Float = 0f,
        thrusters: Float = 0f,
        density: Float = 1f,
    ): Ship {
        val body = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(x, 50f)
            allowSleep = false
        })
        val shape = CircleShape().apply { radius = 0.5f }
        body.createFixture(shape, density)
        shape.dispose()
        body.setLinearVelocity(vx, 0f)
        val rel = RelativisticComponent()
        uFromV(vx, 0f, settings.c, settings.betaMaxMassive, rel.u)
        engine.addEntity(
            Entity().add(BodyComponent().apply { this.body = body }).add(TransformComponent()).add(rel)
                .add(MoveComponent(thrusters = thrusters))
        )
        return Ship(body, rel)
    }

    private fun physics(settings: RelativitySettings): PhysicsSystem {
        val move = MoveSystem(settings)
        val dynamics = RelativisticDynamics(settings)
        engine.addSystem(move)
        engine.addSystem(dynamics)
        return PhysicsSystem(world, engine, listOf(move, dynamics))
    }

    private fun PhysicsSystem.step() = update(PhysicsSystem.PHYSICS_UPDATE_INTERVAL + 1e-4f)

    @Test
    fun lowerCKeepsShipBelowC() {
        val lowC = 1.5f * SPEED_OF_LIGHT
        val settings = RelativitySettings().apply { c = lowC }
        val physics = physics(settings)
        val ship = addShip(settings, thrusters = 1f)
        repeat((10 * PhysicsSystem.PHYSICS_TICK_RATE).toInt()) { physics.step() }
        val speed = ship.body.linearVelocity.len()
        assertTrue(speed < lowC, "speed $speed")
        assertTrue(speed / lowC > 0.9f, "beta ${speed / lowC}")
    }

    @Test
    fun doublingCDoublesEngineAccelAndStillReaches09cInFiveSeconds() {
        val settings = RelativitySettings()
        val a0 = settings.engineProperAccel
        settings.c = 2f * SPEED_OF_LIGHT
        assertEquals(2f * a0, settings.engineProperAccel, 1e-4f)
        val physics = physics(settings)
        val ship = addShip(settings, thrusters = 1f)
        var steps = 0
        while (ship.body.linearVelocity.len() < 0.9f * settings.c && steps < 20 * PhysicsSystem.PHYSICS_TICK_RATE) {
            physics.step()
            steps++
        }
        assertEquals(ENGINE_TIME_TO_09C, steps * PhysicsSystem.PHYSICS_UPDATE_INTERVAL, 0.3f)
    }

    @Test
    fun relativityOffGivesNewtonianThrustAndKeepsUInSync() {
        val settings = RelativitySettings(relativityEnabled = false)
        val physics = physics(settings)
        // Mass 10 keeps v well below the Box2D translation clamp.
        val ship = addShip(settings, thrusters = 1f, density = 10f / (Math.PI.toFloat() * 0.25f))
        repeat(PhysicsSystem.PHYSICS_TICK_RATE.toInt()) { physics.step() }
        assertEquals(MoveSystem.MAIN_ENGINE_THRUST / ship.body.mass, ship.body.linearVelocity.len(), 1f)
        val expectedU = Vector2()
        val v = ship.body.linearVelocity
        uFromV(v.x, v.y, settings.c, settings.betaMaxMassive, expectedU)
        assertEquals(expectedU.x, ship.rel.u.x, expectedU.x * 1e-3f) // relative: u is huge near the cap
        assertEquals(1f, ship.rel.gUsed)
        assertTrue(ship.rel.tau > 0.9f)
    }

    @Test
    fun gammaMaxMassiveClampsProperVelocity() {
        val settings = RelativitySettings(gammaMaxMassive = 5f)
        val physics = physics(settings)
        val ship = addShip(settings)
        ship.rel.u.set(100f * settings.c, 0f)
        physics.step()
        assertEquals(5f, gammaFromU(ship.rel.u.x, ship.rel.u.y, settings.c), 0.01f)
    }

    @Test
    fun fieldOffGivesNoFieldAccelerationButBackstopStillReflects() {
        val settings = RelativitySettings(
            fieldEnabled = false,
            fieldParams = BoundaryFieldParams(skin = 8f),
        )
        val field = BoundaryFieldSystem(MapProvider(mock(), 100f, 100f), ShimmerImpacts(), settings)
        engine.addSystem(field)
        val physics = PhysicsSystem(world, engine, listOf(field))
        val inside = addShip(settings, x = 96f, vx = 0f)
        physics.step()
        assertEquals(0f, inside.body.linearVelocity.len(), 1e-4f)

        val out = addShip(settings, x = 99.9f, vx = 20f)
        physics.step()
        assertTrue(out.body.position.x < 100f)
        assertTrue(out.body.linearVelocity.x < 0f)
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
