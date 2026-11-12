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
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.physics.BH_RS
import com.github.pepek42.asteroids.provider.PLAYER_START_HEALTH
import com.github.pepek42.asteroids.render.DISK_INNER
import com.github.pepek42.asteroids.render.DISK_OUTER
import com.github.pepek42.asteroids.render.inDiskAnnulus
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Accretion-disk damage (ST-175) on a real `World` with [DiskDamageSystem]. The hole is at ([HX], [HY]) with `rs` = [BH_RS], so the
 * annulus is `[DISK_INNER, DISK_OUTER]`. No gravity system runs, so bodies stay where they are put.
 */
class DiskDamageSystemTest {
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = Engine()
    private val damage = DiskDamageSystem()
    private val physics: PhysicsSystem

    init {
        engine.addSystem(damage)
        physics = PhysicsSystem(world, engine, listOf(damage))
    }

    @AfterEach
    fun tearDown() = world.dispose()

    private fun body(x: Float, y: Float): Body {
        val b = world.createBody(BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(x, y)
            allowSleep = false
        })
        val shape = CircleShape().apply { radius = 0.5f }
        b.createFixture(shape, 1f).isSensor = true
        shape.dispose()
        return b
    }

    private fun hole() {
        engine.addEntity(
            Entity()
                .add(BodyComponent().apply { this.body = body(HX, HY) })
                .add(TransformComponent())
                .add(BaseInfoComponent().apply { this.entityType = EntityType.BLACK_HOLE })
                .add(GravitySourceComponent())
        )
    }

    private fun entity(type: EntityType, r: Float, health: Float = PLAYER_START_HEALTH, gamma: Float? = null): HealthComponent {
        val b = body(HX + r, HY)
        val h = HealthComponent().apply { this.health = health }
        val e = Entity()
            .add(BodyComponent().apply { this.body = b })
            .add(TransformComponent())
            .add(BaseInfoComponent().apply { this.entityType = type })
            .add(h)
        if (gamma != null) e.add(RelativisticComponent().apply { gUsed = gamma })
        engine.addEntity(e)
        return h
    }

    private fun runSeconds(seconds: Float) = repeat((seconds / PHYSICS_UPDATE_INTERVAL).toInt()) { physics.update(PHYSICS_UPDATE_INTERVAL) }

    private val midDisk = (DISK_INNER + DISK_OUTER) / 2f

    @Test
    fun annulusHelperIncludesTheRingAndExcludesInsideAndOutside() {
        assertTrue(inDiskAnnulus(midDisk, 0f, BH_RS))
        assertTrue(inDiskAnnulus(0f, -midDisk, BH_RS))
        assertFalse(inDiskAnnulus(DISK_INNER * 0.9f, 0f, BH_RS))
        assertFalse(inDiskAnnulus(DISK_OUTER * 1.1f, 0f, BH_RS))
        // The annulus scales with the hole's rs.
        assertTrue(inDiskAnnulus(2f * midDisk, 0f, 2f * BH_RS))
    }

    @Test
    fun damagePerSecondCostsAQuarterOfStartHealthPerReferenceCrossing() {
        val crossingSeconds = DISK_CROSSING_LENGTH / (DISK_CROSSING_SPEED_PER_C * com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT)
        assertEquals(0.25f * PLAYER_START_HEALTH, DISK_DAMAGE_PER_SECOND * crossingSeconds, 1e-3f)
    }

    @Test
    fun shipRestingInTheDiskLosesHealthAtTheExpectedRate() {
        hole()
        val health = entity(EntityType.SHIP, midDisk)
        runSeconds(1f)
        assertEquals(PLAYER_START_HEALTH - DISK_DAMAGE_PER_SECOND, health.health, DISK_DAMAGE_PER_SECOND * 0.03f)
    }

    @Test
    fun asteroidInTheDiskLosesHealthToo() {
        hole()
        val health = entity(EntityType.ASTEROID, midDisk)
        runSeconds(1f)
        assertTrue(health.health < PLAYER_START_HEALTH)
    }

    @Test
    fun bodiesOutsideTheAnnulusLoseNothing() {
        hole()
        val inside = entity(EntityType.SHIP, DISK_INNER * 0.9f)
        val outside = entity(EntityType.SHIP, DISK_OUTER * 1.2f)
        runSeconds(1f)
        assertEquals(PLAYER_START_HEALTH, inside.health)
        assertEquals(PLAYER_START_HEALTH, outside.health)
    }

    @Test
    fun projectilesAndDebrisAreIgnored() {
        hole()
        val laser = entity(EntityType.PROJECTILE, midDisk)
        val lockOn = entity(EntityType.LOCK_ON, midDisk)
        val debris = entity(EntityType.DEBRIS, midDisk)
        runSeconds(1f)
        assertEquals(PLAYER_START_HEALTH, laser.health)
        assertEquals(PLAYER_START_HEALTH, lockOn.health)
        assertEquals(PLAYER_START_HEALTH, debris.health)
    }

    @Test
    fun damageIsScaledByProperTime() {
        hole()
        val health = entity(EntityType.SHIP, midDisk, gamma = 2f)
        runSeconds(1f)
        assertEquals(PLAYER_START_HEALTH - DISK_DAMAGE_PER_SECOND / 2f, health.health, DISK_DAMAGE_PER_SECOND * 0.02f)
    }

    @Test
    fun noHoleNoDamage() {
        val health = entity(EntityType.SHIP, midDisk)
        runSeconds(0.5f)
        assertEquals(PLAYER_START_HEALTH, health.health)
    }

    companion object {
        private const val HX = 100f
        private const val HY = 100f

        @JvmStatic
        @BeforeAll
        fun init() {
            GdxNativesLoader.load()
            Box2D.init()
            Gdx.app = mock<Application> { on { logLevel } doReturn Application.LOG_NONE }
        }
    }
}
