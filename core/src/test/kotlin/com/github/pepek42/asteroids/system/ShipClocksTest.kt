package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.PooledEngine
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
import com.github.pepek42.asteroids.component.LifetimeClock
import com.github.pepek42.asteroids.component.LifetimeComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.properDelta
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.component.rotationTimeFactor
import com.github.pepek42.asteroids.math.shortestAngleDiffRad
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import com.github.pepek42.asteroids.provider.WeaponProjectileProvider
import ktx.ashley.has
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.math.abs
import kotlin.math.sqrt

/** ST-040: the player runs on the pilot's clock, other ships on their own proper time. */
class ShipClocksTest {
    private val step = PhysicsSystem.PHYSICS_UPDATE_INTERVAL
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }

    @AfterEach
    fun tearDown() = world.dispose()

    // ---- properDelta ----

    @Test
    fun properDeltaPicksClockByEntityKind() {
        val clock = GameClock().apply { realDt = 0.1f; worldDt = 0.4f; warp = 4f }
        val player = Entity().add(PlayerComponent()).add(RelativisticComponent().apply { gUsed = 4f })
        val other = Entity().add(RelativisticComponent().apply { gUsed = 2f })
        val plain = Entity()
        assertEquals(0.1f, player.properDelta(clock, 0.7f), 1e-6f)
        assertEquals(0.2f, other.properDelta(clock, 0.7f), 1e-6f)
        assertEquals(0.4f, plain.properDelta(clock, 0.7f), 1e-6f)
        assertEquals(0.7f, player.properDelta(null, 0.7f), 1e-6f)
        assertEquals(0.35f, other.properDelta(null, 0.7f), 1e-6f)
    }

    @Test
    fun rotationTimeFactorPicksClockByEntityKind() {
        val clock = GameClock().apply { warp = 4f }
        val player = Entity().add(PlayerComponent()).add(RelativisticComponent().apply { gUsed = 5f })
        val other = Entity().add(RelativisticComponent().apply { gUsed = 2f })
        assertEquals(0.25f, player.rotationTimeFactor(clock), 1e-6f)
        assertEquals(0.5f, other.rotationTimeFactor(clock), 1e-6f)
        assertEquals(1f, Entity().rotationTimeFactor(clock), 1e-6f)
        assertEquals(1f, player.rotationTimeFactor(null), 1e-6f)
    }

    // ---- WeaponSystem ----

    /** Runs a weapon entity for [realSeconds] of pilot time and returns the number of shots fired. */
    private fun shotsFired(player: Boolean, warp: Float, gUsed: Float, realSeconds: Float): Int {
        val provider = mock<WeaponProjectileProvider>()
        val clock = GameClock()
        val engine = PooledEngine()
        engine.addSystem(WeaponSystem(provider, clock))
        val entity = engine.createEntity().apply {
            add(BodyComponent())
            add(WeaponComponent(doAttack = true, shotsPerSecond = 4f))
            add(RelativisticComponent().apply { this.gUsed = gUsed })
            if (player) add(PlayerComponent())
        }
        engine.addEntity(entity)
        val frameDt = 1f / 60
        repeat((realSeconds / frameDt).toInt()) {
            clock.realDt = frameDt
            clock.warp = warp
            clock.worldDt = frameDt * warp
            engine.update(frameDt)
        }
        var shots = 0
        org.mockito.Mockito.mockingDetails(provider).invocations.forEach { if (it.method.name == "spawnProjectile") shots++ }
        return shots
    }

    @Test
    fun playerFireRateIsIndependentOfWarp() {
        val atRest = shotsFired(player = true, warp = 1f, gUsed = 1f, realSeconds = 3f)
        val warped = shotsFired(player = true, warp = 4f, gUsed = 4f, realSeconds = 3f)
        assertTrue(atRest >= 11, "shots at rest $atRest")
        assertTrue(abs(atRest - warped) <= 1, "rest $atRest, warped $warped")
    }

    @Test
    fun nonPlayerFireRateFollowsProperTime() {
        val slow = shotsFired(player = false, warp = 1f, gUsed = 1f, realSeconds = 3f)
        val dilated = shotsFired(player = false, warp = 1f, gUsed = 2f, realSeconds = 3f)
        assertTrue(abs(slow / 2 - dilated) <= 1, "gUsed 1: $slow, gUsed 2: $dilated")
        assertTrue(dilated < slow)
    }

    // ---- LifetimeSystem ----

    @Test
    fun properLifetimeIsStretchedByGamma() {
        val clock = GameClock()
        val engine = PooledEngine()
        engine.addSystem(LifetimeSystem(clock))
        val entity = engine.createEntity().apply {
            add(LifetimeComponent(remaining = 1.2f, total = 1.2f, clock = LifetimeClock.PROPER))
            add(RelativisticComponent().apply { gUsed = 3.2f })
        }
        engine.addEntity(entity)
        var t = 0f
        while (!entity.has(removeMapper) && t < 10f) {
            clock.worldDt = step
            clock.realDt = step
            engine.update(step)
            t += step
        }
        assertEquals(3.84f, t, step * 1.5f)
    }

    @Test
    fun worldLifetimeIgnoresGamma() {
        val clock = GameClock()
        val engine = PooledEngine()
        engine.addSystem(LifetimeSystem(clock))
        val entity = engine.createEntity().apply {
            add(LifetimeComponent(remaining = 0.5f, total = 0.5f, clock = LifetimeClock.WORLD))
            add(RelativisticComponent().apply { gUsed = 3.2f })
        }
        engine.addEntity(entity)
        clock.worldDt = 0.6f
        engine.update(0.01f)
        assertTrue(entity.has(removeMapper))
    }

    // ---- Rotation ----

    private class Rotor(val body: Body)

    private fun addRotor(engine: Engine, player: Boolean, target: Float, gUsed: Float = 1f): Rotor {
        val body = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            allowSleep = false
        })
        val shape = CircleShape().apply { radius = 0.5f }
        body.createFixture(shape, 1f)
        shape.dispose()
        engine.addEntity(
            Entity().add(BodyComponent().apply { this.body = body }).add(TransformComponent())
                .add(MoveComponent(targetAngleRad = target)).add(RelativisticComponent().apply { this.gUsed = gUsed })
                .also { if (player) it.add(PlayerComponent()) }
        )
        return Rotor(body)
    }

    /** Returns (world seconds until within 2 degrees of the target and still, max overshoot in degrees). */
    private class Turn(val seconds: Float, val overshootDeg: Float)

    private fun turnQuarter(player: Boolean, warp: Float, gUsed: Float = 1f): Turn {
        val engine = Engine()
        val clock = GameClock().apply { this.warp = warp }
        val physics = PhysicsSystem(world, engine, emptyList(), clock)
        val target = MathUtils.HALF_PI
        val rotor = addRotor(engine, player, target, gUsed)
        var settledAt = -1f
        var overshoot = 0f
        for (i in 1..45 * 20) {
            clock.worldDt = step
            clock.realDt = step / warp
            physics.update(step)
            val err = shortestAngleDiffRad(rotor.body.angle, target)
            overshoot = maxOf(overshoot, -err * MathUtils.radiansToDegrees)
            if (settledAt < 0f && abs(err) < 2f * MathUtils.degreesToRadians) settledAt = i * step
        }
        return Turn(settledAt, overshoot)
    }

    @Test
    fun playerTurnTakesSameRealTimeAtAnyWarp() {
        val rest = turnQuarter(player = true, warp = 1f)
        val warped = turnQuarter(player = true, warp = 4f)
        assertTrue(rest.seconds > 0f && warped.seconds > 0f, "never settled: ${rest.seconds} ${warped.seconds}")
        assertEquals(4f, warped.seconds / rest.seconds, 0.6f)
        assertTrue(rest.overshootDeg < 5f, "overshoot at rest ${rest.overshootDeg}")
        assertTrue(warped.overshootDeg < 5f, "overshoot warped ${warped.overshootDeg}")
    }

    @Test
    fun nonPlayerTurnIsSlowedByGamma() {
        val rest = turnQuarter(player = false, warp = 1f, gUsed = 1f)
        val dilated = turnQuarter(player = false, warp = 1f, gUsed = 2f)
        assertEquals(2f, dilated.seconds / rest.seconds, 0.3f)
        assertTrue(dilated.overshootDeg < 5f)
    }

    // ---- Asteroid spin ----

    @Test
    fun asteroidSpinIsDividedByGamma() {
        val c = SPEED_OF_LIGHT
        val engine = Engine()
        val dynamics = RelativisticDynamics()
        engine.addSystem(dynamics)
        val physics = PhysicsSystem(world, engine, listOf(dynamics))
        val body = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            allowSleep = false
        })
        val shape = CircleShape().apply { radius = 0.5f }
        body.createFixture(shape, 1f)
        shape.dispose()
        val rel = RelativisticComponent()
        // γ = 2 needs |u| = γβc = sqrt(3) c.
        rel.u.set(sqrt(3f) * c, 0f)
        engine.addEntity(Entity().add(BodyComponent().apply { this.body = body }).add(TransformComponent()).add(rel))
        body.angularVelocity = 2f
        physics.update(step + 1e-4f)
        assertEquals(2f, rel.gUsed, 0.01f)
        assertEquals(1f, body.angularVelocity, 0.02f)
        // A constant γ must not keep slowing the spin.
        physics.update(step + 1e-4f)
        assertEquals(1f, body.angularVelocity, 0.02f)
    }

    @Test
    fun drivenRotationIsNotRescaledBySpinDilation() {
        val c = SPEED_OF_LIGHT
        val engine = Engine()
        val dynamics = RelativisticDynamics()
        engine.addSystem(dynamics)
        val physics = PhysicsSystem(world, engine, listOf(dynamics))
        val body = world.createBody(BodyDef().apply { type = BodyDef.BodyType.DynamicBody })
        val shape = CircleShape().apply { radius = 0.5f }
        body.createFixture(shape, 1f)
        shape.dispose()
        val rel = RelativisticComponent().apply { u.set(sqrt(3f) * c, 0f) }
        engine.addEntity(
            Entity().add(BodyComponent().apply { this.body = body }).add(TransformComponent()).add(rel)
                .add(MoveComponent(targetAngleRad = 0f))
        )
        body.angularVelocity = 2f
        physics.update(step + 1e-4f)
        // Only the PD controller (error 0, kd damping) acts, not the 1/γ rescale.
        assertTrue(body.angularVelocity > 1.2f, "ω ${body.angularVelocity}")
        assertFalse(body.angularVelocity.isNaN())
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
