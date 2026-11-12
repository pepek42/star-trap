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
import com.github.pepek42.asteroids.component.LaserProjectile
import com.github.pepek42.asteroids.component.ProjectileComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.RocketComponent
import com.github.pepek42.asteroids.component.TransformComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.WeaponLoadoutComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.healthCmp
import com.github.pepek42.asteroids.component.relativisticCmp
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.component.rocketCmp
import com.github.pepek42.asteroids.component.weaponCmp
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.physics.ENGINE_PROPER_ACCEL
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import com.github.pepek42.asteroids.physics.gammaFromU
import com.github.pepek42.asteroids.provider.ENEMY_HEALTH
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.provider.RocketProvider
import com.github.pepek42.asteroids.render.ShimmerImpacts
import com.github.pepek42.asteroids.weapon.ROCKET_ACCEL_FACTOR
import com.github.pepek42.asteroids.weapon.ROCKET_BURN_SECONDS
import com.github.pepek42.asteroids.weapon.ROCKET_DAMAGE
import com.github.pepek42.asteroids.weapon.ROCKET_POD_SIZE
import com.github.pepek42.asteroids.weapon.ROCKET_RELOAD_SECONDS
import com.github.pepek42.asteroids.weapon.ROCKET_SALVO_INTERVAL
import com.github.pepek42.asteroids.weapon.rocketBurnoutBeta
import com.github.pepek42.asteroids.weapon.WeaponType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.math.atanh
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * World tests for the pod rocket (ST-181): the pod through `WeaponSystem`, the motor through `RocketMotorSystem` and
 * `RelativisticDynamics`, the hit through `ContactSystem`, and the arena wall through `BoundaryFieldSystem`.
 */
class RocketSystemTest {
    private val settings = RelativitySettings()
    private val c = settings.c
    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = Engine()
    private val contact = ContactSystem(world, settings)
    private val motor = RocketMotorSystem(settings)
    private val dynamics = RelativisticDynamics(settings)
    private val mapProvider = MapProvider(mock(), ARENA, ARENA)
    private val impacts = ShimmerImpacts()
    private val field = BoundaryFieldSystem(mapProvider, impacts, settings)
    private val physics = PhysicsSystem(world, engine, listOf(field, motor, dynamics))
    private val atlas = mock<TextureAtlas> { on { createSprite("player_rocket") } doReturn Sprite() }
    private val provider = RocketProvider(atlas, engine, world, settings)
    private val weaponSystem = WeaponSystem(mock(), rocketProvider = provider)

    init {
        engine.addSystem(motor)
        engine.addSystem(dynamics)
        engine.addSystem(field)
        engine.addSystem(contact)
        engine.addSystem(weaponSystem)
    }

    @AfterEach
    fun tearDown() = world.dispose()

    private fun step() = physics.update(PhysicsSystem.PHYSICS_UPDATE_INTERVAL + 1e-4f)

    private fun addBody(x: Float, y: Float, faction: FactionEnum, type: EntityType, health: Float? = null): Entity {
        val body = world.createBody(BodyDef().apply {
            this.type = BodyDef.BodyType.DynamicBody
            position.set(x, y)
            allowSleep = false
        })
        val shape = CircleShape().apply { radius = 0.4f }
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

    private fun addShooter(x: Float, y: Float, beta: Float = 0f, aim: Float = 0f): Entity {
        val shooter = addBody(x, y, FactionEnum.PLAYER, EntityType.SHIP)
        shooter.add(WeaponComponent().apply {
            projectileRadius = 0.1f
            aimAngleRad = aim
            weaponType = WeaponType.ROCKET
        })
        shooter.add(WeaponLoadoutComponent())
        shooter.add(RelativisticComponent().apply {
            val gamma = 1f / kotlin.math.sqrt(1f - beta * beta)
            u.set(gamma * beta * c, 0f)
            vSet.set(beta * c, 0f)
        })
        shooter.bodyCmp.body.setLinearVelocity(beta * c, 0f)
        return shooter
    }

    private fun rocketCount() = engine.entities.count { it.getComponent(RocketComponent::class.java) != null }

    @Test
    fun holdingFireLaunchesFiveRocketsASalvoIntervalApartThenNoneUntilTheReloadIsOver() {
        val shooter = addShooter(10f, 10f)
        shooter.weaponCmp.doAttack = true
        val dt = 1f / 60f
        val launchTimes = ArrayList<Float>()
        var t = 0f
        val end = 2f * ROCKET_RELOAD_SECONDS + 0.5f
        while (t < end) {
            val before = rocketCount()
            weaponSystem.update(dt)
            repeat(rocketCount() - before) { launchTimes.add(t) }
            t += dt
        }
        assertEquals(2 * ROCKET_POD_SIZE, launchTimes.size, "two pods in the window: $launchTimes")
        for (i in 1 until ROCKET_POD_SIZE) {
            assertEquals(ROCKET_SALVO_INTERVAL, launchTimes[i] - launchTimes[i - 1], dt + 1e-4f)
        }
        assertEquals(ROCKET_RELOAD_SECONDS, launchTimes[ROCKET_POD_SIZE] - launchTimes[ROCKET_POD_SIZE - 1], dt + 1e-4f)
    }

    @Test
    fun rocketFromAShipAtHalfCKeepsItsHeadingSpeedsUpStaysBelowCAndReachesTheFormulaBurnoutSpeed() {
        val beta0 = 0.5f
        val shooter = addShooter(10f, ARENA / 2f, beta = beta0, aim = 0f)
        val rocket = provider.spawnRocket(shooter, shooter.weaponCmp)
        val rel = rocket.relativisticCmp
        val angle0 = rocket.bodyCmp.body.angle
        val u0 = rel.u.x
        assertEquals(beta0 * c, rocket.bodyCmp.body.linearVelocity.x, 1e-3f * c, "starts at rest relative to the ship")

        var previousSpeed = rocket.bodyCmp.body.linearVelocity.len()
        var steps = 0
        val maxSteps = (10f / PhysicsSystem.PHYSICS_UPDATE_INTERVAL).toInt()
        while (rocket.rocketCmp.burnLeft > 0f && steps < maxSteps) {
            step()
            steps++
            val v = rocket.bodyCmp.body.linearVelocity
            assertTrue(v.len() < c, "rocket reached c: ${v.len()}")
            assertTrue(v.len() >= previousSpeed - 1e-4f, "rocket slowed during the burn")
            assertEquals(0f, v.y, 1e-3f, "heading drifted")
            previousSpeed = v.len()
        }
        assertEquals(0f, rocket.rocketCmp.burnLeft)
        assertEquals(angle0, rocket.bodyCmp.body.angle, 1e-6f)
        assertTrue(rel.u.x > u0)

        // Formula: along the motion the rapidity adds up, phi = atanh(beta0) + a0 * burn / c.
        val a0 = ROCKET_ACCEL_FACTOR * settings.engineProperAccel
        val expected = c * tanh(atanh(beta0) + a0 * ROCKET_BURN_SECONDS / c)
        val speed = rocket.bodyCmp.body.linearVelocity.x
        assertEquals(expected, speed, 0.02f * expected, "burnout speed $speed vs formula $expected")
        // Defaults sanity: the card's burnout number, ENGINE_PROPER_ACCEL assumed at its default derivation.
        assertEquals(ROCKET_ACCEL_FACTOR * ENGINE_PROPER_ACCEL * ROCKET_BURN_SECONDS / SPEED_OF_LIGHT, a0 * ROCKET_BURN_SECONDS / c, 1e-4f)

        // After burnout it coasts: no further acceleration.
        val coastU = rel.u.x
        repeat(30) { step() }
        assertEquals(coastU, rel.u.x, 1e-3f)
    }

    @Test
    fun burnoutSpeedOfARocketLaunchedAtRestMatchesRocketBurnoutBeta() {
        // Shooter at rest: the burn adds exactly the formula's rapidity, so the last (partial) step must not overshoot.
        val shooter = addShooter(10f, ARENA / 2f, beta = 0f, aim = 0f)
        val rocket = provider.spawnRocket(shooter, shooter.weaponCmp)
        var steps = 0
        val maxSteps = (5f / PhysicsSystem.PHYSICS_UPDATE_INTERVAL).toInt()
        while (rocket.rocketCmp.burnLeft > 0f && steps < maxSteps) {
            step()
            steps++
        }
        assertEquals(0f, rocket.rocketCmp.burnLeft)
        val beta = rocket.bodyCmp.body.linearVelocity.x / c
        assertEquals(rocketBurnoutBeta(c, settings.engineProperAccel), beta, 0.005f * beta, "burnout beta $beta")
    }

    @Test
    fun rocketLaunchedSidewaysKeepsTheFixedHeadingAndStaysBelowC() {
        val aim = Math.PI.toFloat() / 2f
        val shooter = addShooter(ARENA / 2f, 20f, beta = 0f, aim = aim)
        val rocket = provider.spawnRocket(shooter, shooter.weaponCmp)
        while (rocket.rocketCmp.burnLeft > 0f) step()
        val v = rocket.bodyCmp.body.linearVelocity
        assertEquals(0f, v.x, 1e-2f)
        assertTrue(v.y > 0.5f * c && v.y < c, "burnout speed ${v.y}")
        assertEquals(cos(aim), rocket.rocketCmp.headingX, 1e-6f)
        assertEquals(sin(aim), rocket.rocketCmp.headingY, 1e-6f)
        assertTrue(gammaFromU(rocket.relativisticCmp.u.x, rocket.relativisticCmp.u.y, c) > 1f)
    }

    @Test
    fun rocketHitRemovesRocketDamageHealthAndTheRocket() {
        val shooter = addShooter(10f, ARENA / 2f, aim = 0f)
        val target = addBody(16f, ARENA / 2f, FactionEnum.ENEMY, EntityType.SHIP, health = ENEMY_HEALTH)
        val rocket = provider.spawnRocket(shooter, shooter.weaponCmp)
        var steps = 0
        while (removeMapper.get(rocket) == null && steps < (5f / PhysicsSystem.PHYSICS_UPDATE_INTERVAL).toInt()) {
            step()
            steps++
        }
        assertNotNull(removeMapper.get(rocket), "rocket never hit the ship")
        assertEquals(ENEMY_HEALTH - ROCKET_DAMAGE, target.healthCmp.health, 1e-4f)
        assertEquals(ROCKET_DAMAGE, ENEMY_HEALTH / 2f, 0f)
    }

    @Test
    fun aLaserShotKillsARocketThroughItsHealth() {
        val shooter = addShooter(10f, ARENA / 2f)
        val rocket = provider.spawnRocket(shooter, shooter.weaponCmp)
        val laser = Entity()
            .add(BaseInfoComponent().apply { faction = FactionEnum.ENEMY; entityType = EntityType.PROJECTILE })
            .add(ProjectileComponent().apply { projectile = LaserProjectile().apply { damage = 50f } })
        engine.addEntity(laser)
        applyProjectileHit(engine, laser, rocket, settings)
        assertTrue(rocket.healthCmp.health <= 0f, "laser left the rocket alive: ${rocket.healthCmp.health}")
    }

    @Test
    fun rocketFlyingOutIsNotSlowedByTheBoundaryFieldAndDetonatesAtTheWall() {
        val shooter = addShooter(ARENA - 6f, ARENA / 2f, beta = 0f, aim = 0f)
        val rocket = provider.spawnRocket(shooter, shooter.weaponCmp)
        val rel = rocket.relativisticCmp
        var previousU = rel.u.x
        var steps = 0
        while (removeMapper.get(rocket) == null && steps < (3f / PhysicsSystem.PHYSICS_UPDATE_INTERVAL).toInt()) {
            step()
            steps++
            if (removeMapper.get(rocket) == null) {
                assertTrue(rel.u.x >= previousU, "boundary field slowed the rocket: ${rel.u.x} < $previousU")
                previousU = rel.u.x
            }
        }
        assertNotNull(removeMapper.get(rocket), "rocket never detonated at the wall")
        assertTrue(rocket.bodyCmp.body.position.x > ARENA, "detonated before crossing the wall")
        // The ripple of a rocket exit has strength 1 (the same as a laser exit); the shooter's own field ripples are weaker.
        val exits = (0 until impacts.count).filter { impacts.data[it * 4 + 3] == 1f }
        assertEquals(1, exits.size, "exactly one exit ripple")
        assertEquals(ARENA, impacts.data[exits[0] * 4], 1e-3f)
        assertEquals(ARENA / 2f, impacts.data[exits[0] * 4 + 1], 0.5f)
    }

    companion object {
        /** A small arena (u) so the rocket reaches the wall within a few steps. */
        private const val ARENA = 100f

        @JvmStatic
        @BeforeAll
        fun init() {
            GdxNativesLoader.load()
            Box2D.init()
            Gdx.app = mock<Application> { on { logLevel } doReturn Application.LOG_NONE }
        }
    }
}
