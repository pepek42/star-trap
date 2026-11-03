package com.github.pepek42.asteroids.input

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.TextureAtlas
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
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.MoveComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.gammaFromV
import com.github.pepek42.asteroids.provider.WeaponProjectileProvider
import com.github.pepek42.asteroids.system.ContactSystem
import com.github.pepek42.asteroids.system.LightHistorySystem
import com.github.pepek42.asteroids.system.PhysicsSystem
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import com.github.pepek42.asteroids.system.ProjectileSweepSystem
import com.github.pepek42.asteroids.system.RelativisticDynamics
import ktx.ashley.allOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/** Real `World`: a ship near c, an asteroid ahead of its hull, and the full honest fire-control chain. */
class AutoAimStrategyTest {
    private val settings = RelativitySettings()
    private val c = settings.c
    private val beta = 0.8f
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = Engine()
    private val clock = GameClock()
    private val observer = ObserverState()
    private val history = LightHistorySystem()
    private val dynamics = RelativisticDynamics(settings)
    private val sweep = ProjectileSweepSystem(world)
    private val physics: PhysicsSystem

    private val texture = mock<Texture> {
        on { width } doReturn 10
        on { height } doReturn 10
    }
    private val laserSprite = Sprite(texture)
    private val atlas = mock<TextureAtlas> { on { createSprite("player_laser", 1) } doReturn laserSprite }
    private val provider = WeaponProjectileProvider(atlas, engine, world, settings)

    init {
        engine.addSystem(ContactSystem(world))
        engine.addSystem(sweep)
        engine.addSystem(dynamics)
        engine.addSystem(history)
        physics = PhysicsSystem(world, engine, listOf(sweep, dynamics, history), clock)
    }

    @AfterEach
    fun tearDown() = world.dispose()

    private fun step(n: Int = 1) = repeat(n) {
        clock.worldDt = PHYSICS_UPDATE_INTERVAL + 1e-4f
        physics.update(clock.worldDt)
    }

    private fun addBody(
        type: BodyDef.BodyType, x: Float, y: Float, vx: Float, vy: Float, faction: FactionEnum, entityType: EntityType,
    ): Entity {
        val body: Body = world.createBody(BodyDef().apply {
            this.type = type
            position.set(x, y)
            linearVelocity.set(vx, vy)
        })
        val shape = CircleShape().apply { radius = 0.5f }
        body.createFixture(shape, 1f).filterData = com.badlogic.gdx.physics.box2d.Filter().apply {
            categoryBits = faction.ownCategoryBit
            maskBits = faction.collidesWithMask
        }
        shape.dispose()
        val entity = engine.createEntity()
        entity.add(BodyComponent().apply { this.body = body })
        entity.add(TransformComponent())
        entity.add(BaseInfoComponent(faction = faction, entityType = entityType))
        body.userData = entity
        engine.addEntity(entity)
        return entity
    }

    private class Scenario(val ship: Entity, val asteroid: Entity, val weapon: WeaponComponent, val move: MoveComponent, val strategy: AutoAimStrategy)

    /** Ship at β = 0.8 along +x, facing +y, asteroid 20 u ahead of the hull moving (5, 0); histories warmed up for 1 s. */
    private fun scenario(): Scenario {
        val vShip = beta * c
        val gamma = gammaFromV(vShip, 0f, c)
        val ship = addBody(BodyDef.BodyType.DynamicBody, -vShip, 0f, vShip, 0f, FactionEnum.PLAYER, EntityType.SHIP)
        ship.add(RelativisticComponent().apply { u.set(gamma * vShip, 0f) })
        ship.bodyOf().setTransform(ship.bodyOf().position, HALF_PI)
        val weapon = WeaponComponent(projectileRadius = 0.1f, shotsPerSecond = 1f)
        ship.add(weapon)
        val asteroid = addBody(BodyDef.BodyType.DynamicBody, -5f, 20f, 5f, 0f, FactionEnum.NEUTRAL, EntityType.ASTEROID)
        asteroid.add(HealthComponent(100f))
        val targets = engine.getEntitiesFor(
            allOf(HealthComponent::class, BodyComponent::class, BaseInfoComponent::class).get(),
        )
        step(PHYSICS_WARMUP_STEPS)
        observer.betaRaw.set(ship.bodyOf().linearVelocity).scl(1f / c)
        return Scenario(ship, asteroid, weapon, MoveComponent(), AutoAimStrategy(targets, history, observer, clock, settings))
    }

    private fun Entity.bodyOf(): Body = getComponent(BodyComponent::class.java).body

    private fun runStrategy(s: Scenario) {
        val input = PlayerInputState()
        s.strategy.apply(input, s.ship.bodyOf().position, s.ship.bodyOf().angle, s.move, s.weapon, 0.016f)
    }

    private fun hitWithin(s: Scenario, seconds: Float): Boolean {
        val steps = (seconds / PHYSICS_UPDATE_INTERVAL).toInt()
        repeat(steps) {
            step()
            if (s.asteroid.getComponent(HealthComponent::class.java).health < 100f) return true
        }
        return false
    }

    @Test
    fun firesAPhotonThatHitsTheAsteroidAtHighSpeed() {
        val s = scenario()
        assertEquals(beta, observer.betaRaw.len(), 1e-3f)
        // Sanity of the set-up: asteroid 20 u straight ahead of the hull.
        val dy = s.asteroid.bodyOf().position.y - s.ship.bodyOf().position.y
        assertEquals(20f, dy, 0.1f)

        runStrategy(s)
        assertTrue(s.weapon.doAttack)
        assertNotNull(s.weapon.aimAngleRad)
        provider.spawnProjectile(s.weapon, s.ship)

        assertTrue(hitWithin(s, 3f), "the auto-aimed photon should hit the asteroid")
    }

    @Test
    fun hullAimAtTheSameSpeedMisses() {
        // Control: without lead and aberration compensation the same shot misses, so the test above proves something.
        val s = scenario()
        s.weapon.aimAngleRad = null
        provider.spawnProjectile(s.weapon, s.ship)
        assertFalse(hitWithin(s, 3f))
    }

    @Test
    fun nothingInConeFallsBackToFireHeld() {
        val s = scenario()
        s.ship.bodyOf().setTransform(s.ship.bodyOf().position, -HALF_PI)
        runStrategy(s)
        assertFalse(s.weapon.doAttack)
        assertNull(s.weapon.aimAngleRad)
    }

    private companion object {
        const val HALF_PI = (Math.PI / 2).toFloat()
        const val PHYSICS_WARMUP_STEPS = 60

        @JvmStatic
        @BeforeAll
        fun loadNatives() {
            GdxNativesLoader.load()
            Box2D.init()
            Gdx.app = mock<Application> { on { logLevel } doReturn Application.LOG_NONE }
        }
    }
}
