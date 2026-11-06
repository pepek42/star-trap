package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.bodyMapper
import com.github.pepek42.asteroids.component.moveMapper
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.utils.GdxNativesLoader
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.PolygonShape
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.math.shortestAngleDiffRad
import com.github.pepek42.asteroids.math.wrapAngleRad
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.ShipHandlingComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.physics.ENGINE_PROPER_ACCEL
import com.github.pepek42.asteroids.physics.REVERSE_THRUST_FRACTION
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import kotlin.math.abs
import kotlin.math.sqrt
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.collections.gdxArrayOf
import ktx.math.vec2
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

private const val DELTA_TIME = 0.5f

class MoveSystemTest {

    private val moveComponent = MoveComponent()
    private val bodyMock = mock<Body>()
    private val entityMock = mock<Entity> {
        on { get(bodyMapper) } doReturn BodyComponent().apply { body = bodyMock }
        on { get(moveMapper) } doReturn moveComponent
    }
    private val engineMock = mock<Engine> {
        on { getEntitiesFor(allOf(MoveComponent::class, BodyComponent::class).get()) } doReturn ImmutableArray(
            gdxArrayOf(entityMock)
        )
    }

    private val moveSystem = MoveSystem()

    @ParameterizedTest
    @CsvSource(
        "1,149.99998,259.80765",
        "0.5,74.99999,129.90382",
    )
    fun thrustersShouldWork(thrusters: Float, expectedForceX: Float, expectedForceY: Float) {
        // given
        moveComponent.thrusters = thrusters
        `when`(bodyMock.angle).doReturn(MathUtils.PI / 3)
        moveSystem.addedToEngine(engineMock)

        // when
        moveSystem.beforeStep(DELTA_TIME)

        // then
        verify(bodyMock).applyForceToCenter(vec2(expectedForceX, expectedForceY), true)
    }

    @Test
    fun thrustersShouldApplyOncePerStep() {
        // given
        moveComponent.thrusters = 1f
        `when`(bodyMock.angle).doReturn(MathUtils.PI / 3)
        moveSystem.addedToEngine(engineMock)

        // when
        moveSystem.update(DELTA_TIME)
        verify(bodyMock, never()).applyForceToCenter(any<Vector2>(), any<Boolean>())
        moveSystem.beforeStep(DELTA_TIME)
        moveSystem.beforeStep(DELTA_TIME)

        // then
        verify(bodyMock, times(2)).applyForceToCenter(vec2(149.99998f, 259.80765f), true)
    }

    @Test
    fun thrustersShouldNotWorkIfNoInput() {
        // given
        moveComponent.thrusters = 0f
        `when`(bodyMock.angle).doReturn(MathUtils.PI / 3)
        moveSystem.addedToEngine(engineMock)

        // when
        moveSystem.beforeStep(DELTA_TIME)

        // then
        verify(bodyMock, never()).applyForceToCenter(any<Vector2>(), any<Boolean>())
    }

    @ParameterizedTest
    @CsvSource("1", "3")
    fun thrustReachesExpectedVelocityThroughPhysicsSystem(stepsPerFrame: Int) {
        if (Gdx.app == null) Gdx.app = mock<Application>()
        GdxNativesLoader.load()
        Box2D.init()
        val world = World(Vector2.Zero, true)
        world.setAutoClearForces(false)
        val body = world.createBody(BodyDef().apply { type = BodyDef.BodyType.DynamicBody })
        val shape = PolygonShape().apply { setAsBox(0.5f, 0.5f) }
        body.createFixture(shape, 10f) // mass 10 keeps v well below the Box2D translation clamp
        shape.dispose()
        val engine = Engine()
        val ship = Entity()
            .add(BodyComponent().apply { this.body = body })
            .add(TransformComponent())
            .add(MoveComponent().apply { this.thrusters = 1f })
        engine.addEntity(ship)
        val move = MoveSystem()
        engine.addSystem(move)
        val physics = PhysicsSystem(world, engine, listOf(move))
        val frameTime = stepsPerFrame * PhysicsSystem.PHYSICS_UPDATE_INTERVAL + 1e-4f
        repeat(PhysicsSystem.PHYSICS_TICK_RATE.toInt() / stepsPerFrame) { physics.update(frameTime) }
        val expected = MoveSystem.MAIN_ENGINE_THRUST / body.mass
        assertEquals(expected, body.linearVelocity.len(), expected * 0.01f)
        world.dispose()
    }

    /** Proper acceleration written by [MoveSystem.beforeStep] for a ship moving at β = 0.5 along +x. */
    private fun relativisticAccel(angle: Float, thrusters: Float, settings: RelativitySettings): Vector2 {
        val beta = 0.5f
        val gamma = 1f / sqrt(1f - beta * beta)
        val rel = RelativisticComponent().apply { u.set(gamma * beta * SPEED_OF_LIGHT, 0f) }
        val body = mock<Body>()
        `when`(body.angle).doReturn(angle)
        val entity = Entity()
            .add(BodyComponent().apply { this.body = body })
            .add(MoveComponent().apply { this.thrusters = thrusters })
            .add(rel)
        val engine = mock<Engine> {
            on { getEntitiesFor(allOf(MoveComponent::class, BodyComponent::class).get()) } doReturn ImmutableArray(
                gdxArrayOf(entity)
            )
        }
        MoveSystem(settings).apply { addedToEngine(engine) }.beforeStep(DELTA_TIME)
        return Vector2(rel.accel)
    }

    @Test
    fun brakingBurnGetsBoostedAcceleration() {
        val a0 = ENGINE_PROPER_ACCEL
        val accel = relativisticAccel(MathUtils.PI, 1f, RelativitySettings())
        assertEquals(-3f * a0, accel.x, 1e-3f)
        assertEquals(0f, accel.y, 1e-3f)
    }

    @Test
    fun brakeBoostZeroGivesBaseAcceleration() {
        val accel = relativisticAccel(MathUtils.PI, 1f, RelativitySettings(brakeBoost = 0f))
        assertEquals(-ENGINE_PROPER_ACCEL, accel.x, 1e-3f)
    }

    @Test
    fun reverseThrustAgainstMotionIsBoosted() {
        val accel = relativisticAccel(0f, -1f, RelativitySettings())
        assertEquals(-3f * ENGINE_PROPER_ACCEL * REVERSE_THRUST_FRACTION, accel.x, 1e-3f)
    }

    private class Sim(val angles: List<Float>, val velocities: List<Float>)

    private fun simulateTurn(
        startAngle: Float,
        targetAngle: Float?,
        seconds: Float,
        stepsPerFrame: Int = 1,
        startVelocity: Float = 0f,
        handling: ShipHandlingComponent? = null,
    ): Sim {
        if (Gdx.app == null) Gdx.app = mock<Application>()
        GdxNativesLoader.load()
        Box2D.init()
        val world = World(Vector2.Zero, true)
        world.setAutoClearForces(false)
        val body = world.createBody(BodyDef().apply { type = BodyDef.BodyType.DynamicBody })
        val shape = PolygonShape().apply { setAsBox(1f, 1f) }
        body.createFixture(shape, 20f)
        shape.dispose()
        body.setTransform(Vector2.Zero, startAngle)
        body.angularVelocity = startVelocity
        val engine = Engine()
        val ship = Entity()
            .add(BodyComponent().apply { this.body = body })
            .add(TransformComponent())
            .add(MoveComponent().apply { this.targetAngleRad = targetAngle })
        if (handling != null) ship.add(handling)
        engine.addEntity(ship)
        val physics = PhysicsSystem(world, engine)
        val frameTime = stepsPerFrame * PhysicsSystem.PHYSICS_UPDATE_INTERVAL + 1e-4f
        val angles = mutableListOf<Float>()
        val velocities = mutableListOf<Float>()
        repeat((seconds / frameTime).toInt()) {
            physics.update(frameTime)
            angles.add(body.angle)
            velocities.add(body.angularVelocity)
        }
        world.dispose()
        return Sim(angles, velocities)
    }

    private val fiveDeg = 5f * MathUtils.degreesToRadians

    @ParameterizedTest
    @CsvSource("1", "2", "5")
    fun shipReachesTargetQuicklyWithoutOvershoot(stepsPerFrame: Int) {
        val target = MathUtils.HALF_PI
        val sim = simulateTurn(0f, target, 0.6f, stepsPerFrame)

        assertEquals(target, sim.angles.last(), fiveDeg)
        val overshoot = sim.angles.max() - target
        assertTrue(overshoot < fiveDeg, "overshoot $overshoot")
        val maxOmega = sim.velocities.maxOf { abs(it) }
        assertTrue(maxOmega <= ShipRotationController.MAX_ANGULAR_VELOCITY + 1e-3f, "omega $maxOmega")
    }

    @ParameterizedTest
    @CsvSource("1", "2", "5")
    fun angularVelocityCappedOnHalfTurn(stepsPerFrame: Int) {
        val sim = simulateTurn(0f, MathUtils.PI - 0.01f, 1.5f, stepsPerFrame)
        val maxOmega = sim.velocities.maxOf { abs(it) }
        assertTrue(maxOmega <= ShipRotationController.MAX_ANGULAR_VELOCITY + 1e-3f, "omega $maxOmega")
    }

    @Test
    fun lowerMaxAngularVelocityIsRespectedOnHalfTurn() {
        val limit = 1.5f
        val handling = ShipHandlingComponent().apply { maxAngularVelocity = limit }
        val sim = simulateTurn(0f, MathUtils.PI - 0.01f, 2f, handling = handling)
        val maxOmega = sim.velocities.maxOf { abs(it) }
        assertTrue(maxOmega <= limit + 1e-3f, "omega $maxOmega")
        assertTrue(maxOmega > limit * 0.8f, "omega $maxOmega")
    }

    @Test
    fun shipTurnsCounterClockwiseJustBelowPi() {
        val sim = simulateTurn(0f, MathUtils.PI - 0.01f, 0.2f)
        assertTrue(sim.angles.last() > 0.1f)
    }

    @Test
    fun shipTurnsClockwiseJustAbovePi() {
        val sim = simulateTurn(0f, wrapAngleRad(MathUtils.PI + 0.01f), 0.2f)
        assertTrue(sim.angles.last() < -0.1f)
    }

    @Test
    fun impulseOpposesVelocityAtZeroError() {
        assertTrue(ShipRotationController.computeAngularImpulse(0f, 2f, 1f) < 0f)
        assertTrue(ShipRotationController.computeAngularImpulse(0f, -2f, 1f) > 0f)
    }

    @Test
    fun shipWithInitialSpinSettlesAtTarget() {
        val sim = simulateTurn(0f, 0f, 1f, startVelocity = 3f)
        assertEquals(0f, sim.angles.last(), fiveDeg)
        assertEquals(0f, sim.velocities.last(), 0.1f)
    }

    @Test
    fun entityWithoutTargetIsNotRotated() {
        val sim = simulateTurn(0f, null, 0.5f, startVelocity = 1f)
        assertEquals(1f, sim.velocities.last(), 1e-3f)
    }
}
