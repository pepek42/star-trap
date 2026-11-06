package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
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
import com.github.pepek42.asteroids.physics.BETA_MAX_MASSIVE
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.trueLeadDirection
import com.github.pepek42.asteroids.physics.uFromV
import com.github.pepek42.asteroids.provider.WeaponProjectileProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.math.atan2

/** ST-125: a shot fired along the true-state lead direction hits the moving target from a relativistically moving ship. */
class TrueLeadIntegrationTest {
    private val settings = RelativitySettings()
    private val c = settings.c
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = Engine()
    private val sweep = ProjectileSweepSystem(world)
    private val dynamics = RelativisticDynamics(settings)
    private val physics: PhysicsSystem

    private val texture = mock<Texture> {
        on { width } doReturn 10
        on { height } doReturn 10
    }
    private val laserSprite = Sprite(texture)
    private val atlas = mock<TextureAtlas> {
        on { createSprite("player_laser", 1) } doReturn laserSprite
    }

    init {
        engine.addSystem(ContactSystem(world))
        engine.addSystem(sweep)
        engine.addSystem(dynamics)
        physics = PhysicsSystem(world, engine, listOf(dynamics, sweep))
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
        uFromV(vx, vy, c, BETA_MAX_MASSIVE, rel.u)
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
    fun shotAlongTrueLeadHitsMovingAsteroidFromFastShip() {
        val ship = addBody(0f, 0f, 0.6f * c, 0f, FactionEnum.PLAYER, EntityType.SHIP)
        val asteroid = addBody(0f, 15f, 0.3f * c, 0f, FactionEnum.NEUTRAL, EntityType.ASTEROID)
        asteroid.add(HealthComponent().apply { this.health = 100f })

        val out = Vector2()
        val t = trueLeadDirection(0f, 0f, 0.6f, 0f, 0f, 15f, 0.3f * c, 0f, c, out)
        assertTrue(t.isFinite())
        val weapon = WeaponComponent().apply { this.projectileRadius = 0.1f; this.aimAngleRad = atan2(out.y, out.x) }
        WeaponProjectileProvider(atlas, engine, world, settings).spawnProjectile(weapon, ship)

        repeat((1f / PhysicsSystem.PHYSICS_UPDATE_INTERVAL).toInt()) {
            physics.update(PhysicsSystem.PHYSICS_UPDATE_INTERVAL + 1e-4f)
        }
        assertTrue(asteroid.getComponent(HealthComponent::class.java).health < 100f)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadNatives() {
            GdxNativesLoader.load()
            Box2D.init()
            if (Gdx.app == null) Gdx.app = mock<Application> { on { logLevel } doReturn Application.LOG_NONE }
        }
    }
}
