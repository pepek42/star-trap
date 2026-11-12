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
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.gammaFromV
import com.github.pepek42.asteroids.physics.uFromV
import com.github.pepek42.asteroids.provider.RocketProvider
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import com.github.pepek42.asteroids.weapon.ROCKET_ACCEL_FACTOR
import com.github.pepek42.asteroids.weapon.ROCKET_BURN_SECONDS
import com.github.pepek42.asteroids.weapon.ROCKET_INTERCEPT_MAX_SECONDS
import com.github.pepek42.asteroids.weapon.WeaponType
import com.github.pepek42.asteroids.weapon.rocketInterceptDir
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.math.atan2

/**
 * ST-182: a real Box2D rocket fired along [rocketInterceptDir] from a ship at half c hits a moving asteroid. The solver
 * integrates the same motor + proper-velocity update as [RocketMotorSystem] + [RelativisticDynamics], so this test also
 * pins that the two stay in step.
 */
class RocketInterceptIntegrationTest {
    private val settings = RelativitySettings()
    private val c = settings.c
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = Engine()
    private val motor = RocketMotorSystem(settings)
    private val dynamics = RelativisticDynamics(settings)
    private val physics: PhysicsSystem
    private val atlas = mock<TextureAtlas> { on { createSprite("player_rocket") } doReturn Sprite() }
    private val provider = RocketProvider(atlas, engine, world, settings)

    init {
        engine.addSystem(motor)
        engine.addSystem(dynamics)
        engine.addSystem(ContactSystem(world, settings))
        physics = PhysicsSystem(world, engine, listOf(motor, dynamics))
    }

    @AfterEach
    fun tearDown() = world.dispose()

    private fun addBody(x: Float, y: Float, vx: Float, vy: Float, faction: FactionEnum, type: EntityType): Entity {
        val body = world.createBody(BodyDef().apply {
            this.type = BodyDef.BodyType.DynamicBody
            position.set(x, y)
            allowSleep = false
        })
        val shape = CircleShape().apply { radius = 0.5f }
        val fixture = body.createFixture(shape, 1f)
        fixture.filterData = fixture.filterData.apply {
            categoryBits = faction.ownCategoryBit
            maskBits = faction.collidesWithMask
        }
        shape.dispose()
        body.setLinearVelocity(vx, vy)
        val rel = RelativisticComponent()
        rel.captureRestMass(body)
        uFromV(vx, vy, c, settings.betaMaxMassive, rel.u)
        rel.vSet.set(vx, vy)
        val entity = engine.createEntity()
        entity.add(BodyComponent().apply { this.body = body })
        entity.add(TransformComponent())
        entity.add(rel)
        entity.add(BaseInfoComponent().apply { this.faction = faction; this.entityType = type })
        body.userData = entity
        engine.addEntity(entity)
        return entity
    }

    @Test
    fun rocketFiredAlongTheSolvedAimHitsAMovingAsteroidFromAShipAtHalfC() {
        val beta0 = 0.5f
        val ship = addBody(0f, 0f, beta0 * c, 0f, FactionEnum.PLAYER, EntityType.SHIP)
        val asteroid = addBody(0f, 30f, 5f, 0f, FactionEnum.NEUTRAL, EntityType.ASTEROID)
        asteroid.add(HealthComponent().apply { health = 100f })

        val gamma = gammaFromV(beta0 * c, 0f, c)
        val out = Vector2()
        val hitTime = rocketInterceptDir(
            0f, 0f, gamma * beta0 * c, 0f,
            0f, 30f, 5f, 0f,
            ROCKET_ACCEL_FACTOR * settings.engineProperAccel, ROCKET_BURN_SECONDS,
            c, ROCKET_INTERCEPT_MAX_SECONDS, PHYSICS_UPDATE_INTERVAL,
            out,
        )
        assertTrue(hitTime.isFinite(), "the intercept solver found no solution")
        assertTrue(hitTime > 0f && hitTime < ROCKET_INTERCEPT_MAX_SECONDS, "hit time $hitTime")

        val weapon = WeaponComponent().apply {
            projectileRadius = 0.1f
            weaponType = WeaponType.ROCKET
            aimAngleRad = atan2(out.y, out.x)
        }
        provider.spawnRocket(ship, weapon)

        var steps = 0
        val maxSteps = (ROCKET_INTERCEPT_MAX_SECONDS / PHYSICS_UPDATE_INTERVAL).toInt()
        while (asteroid.getComponent(HealthComponent::class.java).health >= 100f && steps < maxSteps) {
            physics.update(PHYSICS_UPDATE_INTERVAL + 1e-4f)
            steps++
        }
        assertTrue(asteroid.getComponent(HealthComponent::class.java).health < 100f, "the rocket missed the asteroid")
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadNatives() {
            GdxNativesLoader.load()
            Box2D.init()
            Gdx.app = mock<Application> { on { logLevel } doReturn Application.LOG_NONE }
        }
    }
}
