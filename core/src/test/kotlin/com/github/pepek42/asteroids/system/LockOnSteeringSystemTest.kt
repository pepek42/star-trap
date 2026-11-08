package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.EntityType
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.healthCmp
import com.github.pepek42.asteroids.component.lockOnCmp
import com.github.pepek42.asteroids.component.relativisticCmp
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.component.weaponCmp
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import com.github.pepek42.asteroids.provider.ENEMY_HEALTH
import com.github.pepek42.asteroids.provider.LockOnProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * World tests for the lock-on shot body (ST-164): a lock-on shot fired at a resting enemy ship hits it through the existing
 * contact path, and after its burn it coasts without any further engine acceleration.
 */
class LockOnSteeringSystemTest {
    private val settings = RelativitySettings()
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = Engine()
    private val contact = ContactSystem(world, settings)
    private val steering = LockOnSteeringSystem(settings)
    private val dynamics = RelativisticDynamics(settings)
    private val physics = PhysicsSystem(world, engine, listOf(steering, dynamics))
    private val atlas = mock<TextureAtlas> { on { createSprite("player_lock_on") } doReturn Sprite() }
    private val provider = LockOnProvider(atlas, engine, world, settings)

    init {
        engine.addSystem(steering)
        engine.addSystem(dynamics)
        engine.addSystem(contact)
    }

    @AfterEach
    fun tearDown() = world.dispose()

    private fun step() = physics.update(PhysicsSystem.PHYSICS_UPDATE_INTERVAL + 1e-4f)

    private fun addBody(
        x: Float,
        y: Float,
        faction: FactionEnum,
        type: EntityType,
        radius: Float,
        health: Float? = null,
    ): Entity {
        val body = world.createBody(BodyDef().apply {
            this.type = BodyDef.BodyType.DynamicBody
            position.set(x, y)
            allowSleep = false
        })
        val shape = CircleShape().apply { this.radius = radius }
        val fixture = body.createFixture(shape, 1f)
        fixture.filterData = fixture.filterData.apply {
            categoryBits = faction.ownCategoryBit
            maskBits = faction.collidesWithMask
        }
        shape.dispose()
        val entity = Entity()
        entity.add(BodyComponent().apply { this.body = body })
        entity.add(TransformComponent())
        entity.add(BaseInfoComponent().apply {
            this.faction = faction
            entityType = type
        })
        if (health != null) entity.add(HealthComponent().apply { this.health = health })
        body.userData = entity
        engine.addEntity(entity)
        return entity
    }

    private fun addShooter(): Entity = addBody(
        0f, 0f, FactionEnum.PLAYER, EntityType.SHIP, radius = 0.4f,
    ).apply {
        add(WeaponComponent().apply { projectileRadius = 0.1f })
    }

    @Test
    fun lockOnFiredAtRestingEnemyHitsAndIsRemoved() {
        val shooter = addShooter()
        val target = addBody(10f, 0f, FactionEnum.ENEMY, EntityType.SHIP, radius = 0.4f, health = ENEMY_HEALTH)
        val shot = provider.spawnLockOn(shooter, target, shooter.weaponCmp)
        assertNotNull(shot.lockOnCmp.target)

        var steps = 0
        while (steps < (3f / PhysicsSystem.PHYSICS_UPDATE_INTERVAL).toInt() && removeMapper.get(shot) == null) {
            step()
            steps++
        }
        assertNotNull(removeMapper.get(shot), "shot never hit the enemy ship")
        assertTrue(target.healthCmp.health <= 0f, "enemy ship took no shot damage: ${target.healthCmp.health}")
        assertTrue(shot.bodyCmp.body.linearVelocity.len() < SPEED_OF_LIGHT)
    }

    @Test
    fun engineAddsNoAccelerationAfterBurnOut() {
        val shooter = addShooter()
        val shot = provider.spawnLockOn(shooter, null, shooter.weaponCmp)
        val rel = shot.relativisticCmp

        var steps = 0
        while (shot.lockOnCmp.burnLeft > 0f && steps < (10f / PhysicsSystem.PHYSICS_UPDATE_INTERVAL).toInt()) {
            step()
            steps++
        }
        assertEquals(0f, shot.lockOnCmp.burnLeft, 0f)
        step()
        assertEquals(0f, rel.lastAccel.len(), 1e-5f, "engine still accelerating after burnout")
        assertTrue(shot.bodyCmp.body.linearVelocity.len() > 0f, "shot did not coast")
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
