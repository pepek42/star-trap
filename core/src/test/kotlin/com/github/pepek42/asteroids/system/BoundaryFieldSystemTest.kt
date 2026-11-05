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
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.ProjectileComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.computeBoundingRadius
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.physics.BoundaryFieldParams
import com.github.pepek42.asteroids.physics.FIELD_BACKSTOP_RESTITUTION
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.render.ShimmerImpacts
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.math.sqrt

class BoundaryFieldSystemTest {
    /** Test body speed: c, so the bodies move about a third of a unit per step. */
    private val BODY_SPEED = SPEED_OF_LIGHT

    /** Steps for a [BODY_SPEED] projectile starting 5 u inside the wall to get well past it. */
    private val CROSS_STEPS = (2f * 5f / (BODY_SPEED * PhysicsSystem.PHYSICS_UPDATE_INTERVAL)).toInt()

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

    private fun addProjectile(
        engine: Engine,
        world: World,
        x: Float,
        y: Float,
        vx: Float,
    ): Body {
        val body = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.KinematicBody
            position.set(x, y)
            linearVelocity.set(vx, 0f)
        })
        val shape = CircleShape().apply { radius = 0.1f }
        body.createFixture(shape, 0f).isSensor = true
        shape.dispose()
        engine.addEntity(Entity()
            .add(BodyComponent().apply { this.body = body })
            .add(TransformComponent())
            .add(BaseInfoComponent(faction = FactionEnum.PLAYER))
            .add(ProjectileComponent()))
        return body
    }

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
        val body = addBody(BodyDef.BodyType.KinematicBody, 70f, 50f, BODY_SPEED, radius = 0.1f, sensor = true)
        repeat(90) {
            step()
            assertTrue(body.position.x < 100f, "x=${body.position.x}")
        }
        assertTrue(body.linearVelocity.x < 0f)
    }

    @Test
    fun kinematicNonProjectileStillGetsVelocityKick() {
        val body = addBody(BodyDef.BodyType.KinematicBody, 95f, 50f, BODY_SPEED, radius = 0.1f, sensor = true)
        step()
        assertTrue(body.linearVelocity.x < 60f, "vx=${body.linearVelocity.x}")
    }

    @Test
    fun projectilePassesWallUntouchedWithOneRipple() {
        val body = addProjectile(engine, world, 95f, 50f, BODY_SPEED)
        repeat(CROSS_STEPS) {
            field.beforeStep(PhysicsSystem.PHYSICS_UPDATE_INTERVAL)
            world.step(PhysicsSystem.PHYSICS_UPDATE_INTERVAL, 6, 2)
            field.afterStep(PhysicsSystem.PHYSICS_UPDATE_INTERVAL)
        }
        assertTrue(body.position.x > 100f, "x=${body.position.x}")
        assertEquals(BODY_SPEED, body.linearVelocity.x, 0f)
        assertEquals(0f, body.linearVelocity.y, 0f)
        assertEquals(1, impacts.count)
        assertEquals(100f, impacts.data[0], 1e-3f)
        assertEquals(50f, impacts.data[1], 1e-3f)
        repeat(CROSS_STEPS) {
            field.beforeStep(PhysicsSystem.PHYSICS_UPDATE_INTERVAL)
            world.step(PhysicsSystem.PHYSICS_UPDATE_INTERVAL, 6, 2)
            field.afterStep(PhysicsSystem.PHYSICS_UPDATE_INTERVAL)
        }
        assertEquals(1, impacts.count)
    }

    @Test
    fun projectileStillRipplesWhenFieldDisabled() {
        val localEngine = Engine()
        val localWorld = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
        val localImpacts = ShimmerImpacts()
        val localField = BoundaryFieldSystem(
            mapProvider,
            localImpacts,
            RelativitySettings(fieldEnabled = false, fieldParams = BoundaryFieldParams(skin = 8f)),
        )
        localEngine.addSystem(localField)
        try {
            val body = addProjectile(localEngine, localWorld, 95f, 50f, BODY_SPEED)
            repeat(CROSS_STEPS) {
                localField.beforeStep(PhysicsSystem.PHYSICS_UPDATE_INTERVAL)
                localWorld.step(PhysicsSystem.PHYSICS_UPDATE_INTERVAL, 6, 2)
                localField.afterStep(PhysicsSystem.PHYSICS_UPDATE_INTERVAL)
            }
            assertTrue(body.position.x > 100f, "x=${body.position.x}")
            assertEquals(BODY_SPEED, body.linearVelocity.x, 0f)
            assertEquals(1, localImpacts.count)
        } finally {
            localWorld.dispose()
        }
    }

    /**
     * Relativistic head-on tests need c = 60 (the research §3 table assumes it: Phi = c^2 (gamma - 1) = 4660 at
     * beta 0.9), well under C_MAX_BOX2D_SAFE. The field is A = 3000, s = 8, n = 2 without damping.
     */
    private val relC = 60f
    private val relSettings = RelativitySettings(c = relC, fieldParams = BoundaryFieldParams(skin = 8f, damping = 0f))

    private class RelSetup(val body: Body, val rel: RelativisticComponent, val physics: PhysicsSystem)

    /** Relativistic body heading in +x with Lorentz factor [gamma], starting at [x]. */
    private fun relativisticSetup(x: Float, gamma: Float, radius: Float): RelSetup {
        val relEngine = Engine()
        val relField = BoundaryFieldSystem(mapProvider, impacts, relSettings)
        val dynamics = RelativisticDynamics(relSettings)
        relEngine.addSystem(relField)
        relEngine.addSystem(dynamics)
        val body = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(x, 50f)
            allowSleep = false
        })
        val shape = CircleShape().apply { this.radius = radius }
        body.createFixture(shape, 1f)
        shape.dispose()
        val rel = RelativisticComponent()
        rel.u.set(relC * sqrt(gamma * gamma - 1f), 0f)
        relEngine.addEntity(
            Entity().add(BodyComponent().apply { this.body = body }).add(TransformComponent()).add(rel),
        )
        return RelSetup(body, rel, PhysicsSystem(world, relEngine, listOf(relField, dynamics)))
    }

    @Test
    fun relativisticBodyTurnsAroundAtResearchDepth() {
        val radius = 0.1f
        val setup = relativisticSetup(60f, 1f / sqrt(1f - 0.81f), radius)
        val body = setup.body
        val rel = setup.rel
        val wallEdge = 100f - relSettings.fieldParams.skin
        var maxDepth = 0f
        var steps = 0
        while (rel.u.x > 0f && steps < 600) {
            setup.physics.update(PhysicsSystem.PHYSICS_UPDATE_INTERVAL + 1e-4f)
            maxDepth = maxOf(maxDepth, body.position.x + radius - wallEdge)
            steps++
        }
        assertTrue(rel.u.x < 0f, "never turned around, x=${body.position.x}")
        assertEquals(6.7f, maxDepth, 0.4f)
        assertTrue(body.position.x + radius < 100f)
        // The wall was never touched: no backstop ripple.
        for (i in 0 until impacts.count) assertTrue(impacts.data[i * 4 + 3] < 1.5f)
    }

    @Test
    fun relativisticBodyBeyondFieldReachHitsBackstopAndReflectsU() {
        val radius = 0.1f
        val setup = relativisticSetup(60f, 5f, radius)
        val body = setup.body
        val rel = setup.rel
        // The field alone can't stop gamma = 5 (Phi needed 14400 > A s / 3 = 8000), so the first turn is the backstop.
        var uBefore = rel.u.len()
        var uAfterBackstop = -1f
        repeat(60) {
            uBefore = rel.u.len()
            setup.physics.update(PhysicsSystem.PHYSICS_UPDATE_INTERVAL + 1e-4f)
            if (uAfterBackstop < 0f && rel.u.x < 0f) {
                uAfterBackstop = rel.u.len()
                assertTrue(uAfterBackstop <= FIELD_BACKSTOP_RESTITUTION * uBefore + 1e-2f, "|u|=$uAfterBackstop before=$uBefore")
            }
        }
        assertTrue(uAfterBackstop > 0f, "backstop never turned the body")
        assertTrue(body.position.x + radius < 100f && body.position.x > 0f, "x=${body.position.x}")
        assertTrue(rel.u.x < 0f, "u=${rel.u}")
        assertTrue(body.linearVelocity.x < 0f)
        var backstop = false
        for (i in 0 until impacts.count) if (impacts.data[i * 4 + 3] >= 1.5f) backstop = true
        assertTrue(backstop, "no backstop ripple")
    }

    @Test
    fun relativisticBodyIgnoresFieldWhenDisabled() {
        relSettings.fieldEnabled = false
        val setup = relativisticSetup(90f, 2f, 0.1f)
        val body = setup.body
        val rel = setup.rel
        val u0 = rel.u.x
        repeat(10) { setup.physics.update(PhysicsSystem.PHYSICS_UPDATE_INTERVAL + 1e-4f) }
        assertEquals(u0, rel.u.x, 1e-3f)
        assertTrue(body.position.x > 90f)
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
