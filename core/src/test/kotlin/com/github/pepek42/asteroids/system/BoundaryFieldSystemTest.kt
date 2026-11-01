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
import com.badlogic.gdx.physics.box2d.PolygonShape
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.computeBoundingRadius
import com.github.pepek42.asteroids.provider.MapProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class BoundaryFieldSystemTest {
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = Engine()
    private val mapProvider = MapProvider(mock())
    private val impacts = com.github.pepek42.asteroids.render.ShimmerImpacts()
    private val field = BoundaryFieldSystem(mapProvider, impacts)
    private val physics: PhysicsSystem

    init {
        engine.addSystem(field)
        physics = PhysicsSystem(world, engine, listOf(field))
    }

    @AfterEach
    fun tearDown() = world.dispose()

    private fun addBody(
        type: BodyDef.BodyType,
        x: Float,
        y: Float,
        vx: Float,
        vy: Float = 0f,
        radius: Float = 1f,
        density: Float = 1f,
        sensor: Boolean = false,
    ): Body {
        val body = world.createBody(BodyDef().apply {
            this.type = type
            position.set(x, y)
            linearVelocity.set(vx, vy)
        })
        val shape = CircleShape().apply { this.radius = radius }
        val fixture = body.createFixture(shape, density)
        fixture.isSensor = sensor
        shape.dispose()
        engine.addEntity(Entity().add(BodyComponent().apply { this.body = body }).add(TransformComponent()))
        return body
    }

    private fun step() = physics.update(PhysicsSystem.PHYSICS_UPDATE_INTERVAL + 1e-4f)

    @Test
    fun dynamicCircleIsTurnedBackBeforeWall() {
        val body = addBody(BodyDef.BodyType.DynamicBody, 85f, 50f, 30f)
        repeat(135) {
            step()
            assertTrue(body.position.x + 1f <= 100f, "x=${body.position.x}")
        }
        assertTrue(body.linearVelocity.x < 0f)
    }

    private fun maxX(density: Float): Float {
        val body = addBody(BodyDef.BodyType.DynamicBody, 85f, 50f, 30f, density = density)
        var max = 0f
        repeat(135) {
            step()
            max = maxOf(max, body.position.x)
        }
        return max
    }

    @Test
    fun fieldIsMassIndependent() {
        val light = maxX(1f)
        world.destroyBody(world.run { val a = com.badlogic.gdx.utils.Array<Body>(); getBodies(a); a.first() })
        engine.removeAllEntities()
        val heavy = maxX(100f)
        assertEquals(light, heavy, 0.05f)
    }

    @Test
    fun kinematicSensorTurnsBack() {
        val body = addBody(BodyDef.BodyType.KinematicBody, 70f, 50f, 60f, radius = 0.1f, sensor = true)
        repeat(90) {
            step()
            assertTrue(body.position.x < 100f, "x=${body.position.x}")
        }
        assertTrue(body.linearVelocity.x < 0f)
    }

    @Test
    fun backstopPutsBodyInsideAndReflects() {
        val body = addBody(BodyDef.BodyType.DynamicBody, 100.5f, 50f, 5f, radius = 0.1f)
        step()
        assertTrue(body.position.x + 0.1f < 100f, "x=${body.position.x}")
        assertTrue(body.linearVelocity.x < 0f)
    }

    @Test
    fun bodyInSkinEmitsImpactOnNearestWall() {
        addBody(BodyDef.BodyType.DynamicBody, 97f, 50f, 0f, radius = 0.1f)
        step()
        assertEquals(1, impacts.count)
        assertEquals(100f, impacts.data[0])
        assertEquals(50f, impacts.data[1], 0.01f)
    }

    @Test
    fun backstopEmitsForcedStrongImpact() {
        addBody(BodyDef.BodyType.DynamicBody, 99.8f, 50f, 80f, radius = 0.1f)
        step()
        assertTrue(impacts.count >= 2)
        assertEquals(1.5f, impacts.data[(impacts.count - 1) * 4 + 3])
    }

    @Test
    fun boundingRadiusOfShapes() {
        val body = world.createBody(BodyDef().apply { type = BodyDef.BodyType.DynamicBody })
        val circle = CircleShape().apply { radius = 1f; position = Vector2(2f, 0f) }
        body.createFixture(circle, 1f)
        val box = PolygonShape().apply { setAsBox(3f, 4f) }
        body.createFixture(box, 1f)
        assertEquals(5f, computeBoundingRadius(body), 1e-4f)
        circle.dispose()
        box.dispose()
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
