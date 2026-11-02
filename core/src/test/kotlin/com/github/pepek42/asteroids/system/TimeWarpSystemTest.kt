package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.LifetimeComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import com.github.pepek42.asteroids.physics.TIME_WARP_MAX
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.math.sqrt

class TimeWarpSystemTest {
    private val c = SPEED_OF_LIGHT
    private val dt1 = PhysicsSystem.PHYSICS_UPDATE_INTERVAL
    private val maxSteps = PhysicsSystem.MAX_STEPS_PER_FRAME
    private val lagSteps = PhysicsSystem.MAX_LAG_STEPS
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = Engine()
    private val clock = GameClock()
    private val settings = RelativitySettings()

    @AfterEach
    fun tearDown() = world.dispose()

    private fun setup(gamma: Float): Pair<Entity, RelativisticComponent> {
        val dynamics = RelativisticDynamics(settings)
        engine.addSystem(TimeWarpSystem(clock, settings))
        engine.addSystem(dynamics)
        engine.addSystem(PhysicsSystem(world, engine, listOf(dynamics), clock))
        val body = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            allowSleep = false
        })
        val shape = CircleShape().apply { radius = 0.5f }
        body.createFixture(shape, 1f)
        shape.dispose()
        val rel = RelativisticComponent()
        val beta = sqrt(1f - 1f / (gamma * gamma))
        rel.u.set(gamma * beta * c, 0f)
        rel.gUsed = gamma
        rel.gammaMax = 100f
        settings.gammaMaxMassive = 100f
        body.setLinearVelocity(beta * c, 0f)
        rel.vSet.set(beta * c, 0f)
        val entity = Entity().add(BodyComponent().apply { this.body = body }).add(TransformComponent())
            .add(rel).add(PlayerComponent())
        engine.addEntity(entity)
        return entity to rel
    }

    @Test
    fun gammaFourRunsFourStepsPerFrame() {
        val entity = setup(4f).first
        val body = entity.getComponent(BodyComponent::class.java).body
        val v = body.linearVelocity.x
        engine.update(dt1)
        assertEquals(4f, clock.warp, 1e-4f)
        assertEquals(4f * dt1, clock.worldTime, 1e-5f)
        assertEquals(4f * v * dt1, body.position.x, 1e-3f)
    }

    @Test
    fun gammaTwentyIsCappedAtTen() {
        setup(20f)
        engine.update(dt1)
        assertEquals(TIME_WARP_MAX, clock.warp)
        assertTrue(clock.warpCapped)
        val before = clock.worldTime
        engine.update(0.1f)
        assertEquals(maxSteps * dt1, clock.worldDt, 1e-6f)
        assertTrue(clock.stepsCapped)
        assertTrue(clock.worldTime - before <= maxSteps * dt1 + 1e-5f)
    }

    @Test
    fun warpIsOneWhenDisabled() {
        setup(4f)
        settings.timeWarpEnabled = false
        val before = clock.worldTime
        engine.update(dt1)
        assertEquals(1f, clock.warp)
        assertEquals(dt1, clock.worldTime - before, 1e-5f)
        settings.timeWarpEnabled = true
        settings.relativityEnabled = false
        engine.update(dt1)
        assertEquals(1f, clock.warp)
    }

    @Test
    fun lagSpikeAtWarpOneIsCappedAtFiveSteps() {
        setup(1f)
        engine.update(0.5f)
        assertEquals(1f, clock.warp)
        assertTrue(clock.stepsCapped)
        assertTrue(clock.worldTime <= lagSteps * dt1 + 1e-5f)
    }

    @Test
    fun lagSpikeAtWarpFourIsCappedAtTenSteps() {
        setup(4f)
        engine.update(0.5f)
        assertEquals(4f, clock.warp, 1e-4f)
        assertEquals(maxSteps * dt1, clock.worldTime, 1e-5f)
    }

    @Test
    fun worldLifetimeExpiresFasterAtWarp() {
        setup(4f)
        engine.addSystem(LifetimeSystem(clock))
        val timed = Entity().add(LifetimeComponent(remaining = 2f, total = 2f))
        engine.addEntity(timed)
        var t = 0f
        val dt = dt1
        while (timed.getComponent(LifetimeComponent::class.java).remaining > 0f && t < 2f) {
            engine.update(dt)
            t += dt
        }
        assertEquals(0.5f, t, 2 * dt)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun init() {
            GdxNativesLoader.load()
            Box2D.init()
            Gdx.app = mock<Application>()
        }
    }
}
