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
import kotlin.math.abs
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
        moveSystem.update(DELTA_TIME)

        // then
        verify(bodyMock).applyForceToCenter(vec2(expectedForceX, expectedForceY), true)
    }

    @Test
    fun thrustersShouldWorkOnlyOncePerStep() {
        // given
        moveComponent.thrusters = 1f
        `when`(bodyMock.angle).doReturn(MathUtils.PI / 3)
        moveSystem.addedToEngine(engineMock)

        // when
        moveSystem.update(DELTA_TIME)
        moveSystem.update(DELTA_TIME)

        // then
        verify(bodyMock).applyForceToCenter(vec2(149.99998f, 259.80765f), true)
    }

    @Test
    fun thrustersShouldNotWorkIfNoInput() {
        // given
        moveComponent.thrusters = 0f
        `when`(bodyMock.angle).doReturn(MathUtils.PI / 3)
        moveSystem.addedToEngine(engineMock)

        // when
        moveSystem.update(DELTA_TIME)

        // then
        verify(bodyMock, never()).applyForceToCenter(any<Vector2>(), any<Boolean>())
    }

    private class Sim(val angles: List<Float>, val velocities: List<Float>)

    private fun simulateTurn(
        startAngle: Float,
        targetAngle: Float?,
        seconds: Float,
        stepsPerFrame: Int = 1,
        startVelocity: Float = 0f,
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
        engine.addEntity(
            Entity()
                .add(BodyComponent().apply { this.body = body })
                .add(TransformComponent())
                .add(MoveComponent(targetAngleRad = targetAngle))
        )
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
