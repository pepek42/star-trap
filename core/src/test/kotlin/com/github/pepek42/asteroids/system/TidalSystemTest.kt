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
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.GravitySourceComponent
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.physics.BH_RS
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.TIDAL_DPS
import com.github.pepek42.asteroids.physics.gmFromRs
import com.github.pepek42.asteroids.physics.tidalAccel
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Tidal stress near a black hole (ST-153) on a real `World` with `GravitySystem`. Gravity is off so the player stays where
 * it is placed ("held"); tidal damage does not depend on the pull. Assumes the default engine acceleration
 * (`settings.engineProperAccel`) and a body of radius [BODY_RADIUS]; gm is `gmFromRs(BH_RS, settings.c)`.
 */
class TidalSystemTest {
    private val settings = RelativitySettings().apply {
        gravityEnabled = false
        playerGm = 0f
    }
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = Engine()
    private val gravity = GravitySystem(settings)
    private val physics: PhysicsSystem

    init {
        engine.addSystem(gravity)
        physics = PhysicsSystem(world, engine, listOf(gravity))
    }

    @AfterEach
    fun tearDown() = world.dispose()

    private fun body(x: Float, y: Float, radius: Float): Body {
        val b = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(x, y)
            allowSleep = false
        })
        val shape = CircleShape().apply { this.radius = radius }
        b.createFixture(shape, 1f).isSensor = true
        shape.dispose()
        return b
    }

    private fun hole(x: Float, y: Float) {
        engine.addEntity(
            Entity()
                .add(BodyComponent().apply { this.body = body(x, y, BH_RS) })
                .add(TransformComponent())
                .add(BaseInfoComponent().apply { this.entityType = EntityType.BLACK_HOLE })
                .add(GravitySourceComponent())
        )
    }

    private fun player(x: Float, y: Float): Entity {
        val b = body(x, y, BODY_RADIUS)
        val e = Entity()
            .add(BodyComponent().apply { this.body = b })
            .add(TransformComponent())
            .add(BaseInfoComponent().apply { this.entityType = EntityType.SHIP })
            .add(HealthComponent().apply { health = START_HEALTH })
            .add(RelativisticComponent().apply { captureRestMass(b) })
            .add(PlayerComponent())
        engine.addEntity(e)
        return e
    }

    private fun healthAfterOneSecond(distanceRs: Float): Pair<Float, Float> {
        hole(100f, 100f)
        val p = player(100f + distanceRs * BH_RS, 100f)
        repeat((1f / PHYSICS_UPDATE_INTERVAL).toInt()) { physics.update(PHYSICS_UPDATE_INTERVAL) }
        return p.getComponent(HealthComponent::class.java).health to p.getComponent(RelativisticComponent::class.java).tidalRatio
    }

    @Test
    fun playerThreeRsFromAHoleLosesHealthAndStretchesAlongTheRadius() {
        val result = healthAfterOneSecond(3f)
        val health = result.first
        val ratio = result.second
        assertTrue(ratio > 1f, "ratio $ratio")
        assertTrue(health < START_HEALTH, "health $health")
        // Damage is about TIDAL_DPS * (ratio - 1) per second of proper time (dtau = dt at gamma 1 and no gravFactor effect in this test).
        assertTrue(START_HEALTH - health <= TIDAL_DPS * (ratio - 1f) * 1.05f)
    }

    @Test
    fun ratioMatchesTidalAccelOverEngineThreshold() {
        val ratio = healthAfterOneSecond(3f).second
        val expected = tidalAccel(3f * BH_RS, gmFromRs(BH_RS, settings.c), 2f * BODY_RADIUS) / settings.engineProperAccel
        assertEquals(expected, ratio, expected * 1e-3f)
    }

    @Test
    fun playerTwentyRsAwayIsUnharmed() {
        val result = healthAfterOneSecond(20f)
        val health = result.first
        val ratio = result.second
        assertEquals(START_HEALTH, health)
        assertTrue(ratio < 1f, "ratio $ratio")
    }

    @Test
    fun noDamageNorStretchWithTidalOff() {
        settings.tidalEnabled = false
        val result = healthAfterOneSecond(3f)
        val health = result.first
        val ratio = result.second
        assertEquals(START_HEALTH, health)
        assertEquals(0f, ratio)
    }

    @Test
    fun directionPointsFromTheHoleToTheBody() {
        hole(100f, 100f)
        val p = player(100f, 100f + 4f * BH_RS)
        physics.update(PHYSICS_UPDATE_INTERVAL)
        val rel = p.getComponent(RelativisticComponent::class.java)
        assertEquals(0f, rel.tidalDirX, 1e-4f)
        assertEquals(1f, rel.tidalDirY, 1e-4f)
    }

    companion object {
        private const val BODY_RADIUS = 0.5f
        private const val START_HEALTH = 100f

        @JvmStatic
        @BeforeAll
        fun init() {
            GdxNativesLoader.load()
            Box2D.init()
            Gdx.app = mock<Application> { on { logLevel } doReturn Application.LOG_NONE }
        }
    }
}
