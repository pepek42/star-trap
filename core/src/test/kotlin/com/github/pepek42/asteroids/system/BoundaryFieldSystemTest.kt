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
import com.github.pepek42.asteroids.physics.BoundaryFieldParams
import com.github.pepek42.asteroids.physics.RelativitySettings
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
    private val mapProvider = MapProvider(mock(), 100f, 100f)
    private val impacts = com.github.pepek42.asteroids.render.ShimmerImpacts()
    private val field = BoundaryFieldSystem(mapProvider, impacts, RelativitySettings(fieldParams = BoundaryFieldParams(skin = 8f)))
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

    @Test
    fun dynamicCircleIsTurnedBackBeforeTopAndBottomWalls() {
        val top = addBody(BodyDef.BodyType.DynamicBody, 50f, 85f, 0f, 30f)
        repeat(135) {
            step()
            assertTrue(top.position.y + 1f <= 100f, "y=${top.position.y}")
        }
        assertTrue(top.linearVelocity.y < 0f)
        engine.removeAllEntities()
        world.destroyBody(top)

        val bottom = addBody(BodyDef.BodyType.DynamicBody, 50f, 15f, 0f, -30f)
        repeat(135) {
            step()
            assertTrue(bottom.position.y - 1f >= 0f, "y=${bottom.position.y}")
        }
        assertTrue(bottom.linearVelocity.y > 0f)
    }

    private fun maxX(body: Body): Float {
        var max = 0f
        repeat(135) {
            step()
            max = maxOf(max, body.position.x)
        }
        return max
    }

    @Test
    fun fieldIsMassIndependent() {
        val lightBody = addBody(BodyDef.BodyType.DynamicBody, 85f, 50f, 30f, density = 1f)
        val light = maxX(lightBody)
        world.destroyBody(lightBody)
        engine.removeAllEntities()
        val heavy = maxX(addBody(BodyDef.BodyType.DynamicBody, 85f, 50f, 30f, density = 100f))
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
    fun cornerBackstopPutsBodyInsideAndReflectsBothAxes() {
        val body = addBody(BodyDef.BodyType.DynamicBody, 99.8f, 99.8f, 80f, 80f, radius = 0.1f)
        step()
        assertTrue(body.position.x + 0.1f < 100f, "x=${body.position.x}")
        assertTrue(body.position.y + 0.1f < 100f, "y=${body.position.y}")
        assertTrue(body.linearVelocity.x < 0f)
        assertTrue(body.linearVelocity.y < 0f)
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
    fun weakImpactEmitsOnlyWhenMovingIntoWall() {
        assertTrue(BoundaryFieldSystem.shouldEmitFieldImpact(0.07f, 1f))
        assertTrue(!BoundaryFieldSystem.shouldEmitFieldImpact(0.07f, -1f))
        assertTrue(!BoundaryFieldSystem.shouldEmitFieldImpact(0.04f, 5f))
        assertTrue(BoundaryFieldSystem.shouldEmitFieldImpact(0.2f, -1f))
    }

    @Test
    fun shallowBodyMovingIntoWallEmitsQSquared() {
        // radius 1, skin 8: x=100-1-8*(1-0.07)... place at depth q=0.07
        val q = 0.07f
        val x = 100f - 1f - 8f * (1f - q)
        addBody(BodyDef.BodyType.KinematicBody, x, 50f, 0.5f)
        step()
        assertEquals(1, impacts.count)
        assertEquals(q * q, impacts.data[3], 0.002f)
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
