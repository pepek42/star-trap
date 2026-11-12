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
import com.github.pepek42.asteroids.debug.parseLaunchOptions
import com.github.pepek42.asteroids.component.baseInfoCmp
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

/** Real `World`: a ship near c, an asteroid ahead of its hull, and the computer (true-state) fire-control chain. */
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
        entity.add(BaseInfoComponent().apply { this.faction = faction; this.entityType = entityType })
        body.userData = entity
        engine.addEntity(entity)
        return entity
    }

    private class Scenario(val ship: Entity, val asteroid: Entity, val weapon: WeaponComponent, val move: MoveComponent, val strategy: AutoAimStrategy)

    /**
     * Ship at β = 0.8 along +x, facing +y, asteroid 20 u ahead of the hull moving (5, 0) (or 20 u behind it when
     * [asteroidAhead] is false, out of the cone); optional debris piece 10 u dead ahead, closer than the asteroid.
     * The ship drifts from x = -16 to about x = 0 during warmup, so the debris sits at x = 0 to stay in the cone.
     * Histories warmed up for 1 s.
     */
    private fun scenario(asteroidAhead: Boolean = true, withDebris: Boolean = false): Scenario {
        val vShip = beta * c
        val gamma = gammaFromV(vShip, 0f, c)
        val ship = addBody(BodyDef.BodyType.DynamicBody, -vShip, 0f, vShip, 0f, FactionEnum.PLAYER, EntityType.SHIP)
        ship.add(RelativisticComponent().apply { u.set(gamma * vShip, 0f) })
        ship.bodyOf().setTransform(ship.bodyOf().position, HALF_PI)
        val weapon = WeaponComponent().apply { this.projectileRadius = 0.1f; this.shotsPerSecond = 1f }
        ship.add(weapon)
        val asteroidY = if (asteroidAhead) 20f else -20f
        val asteroid = addBody(BodyDef.BodyType.DynamicBody, -5f, asteroidY, 5f, 0f, FactionEnum.NEUTRAL, EntityType.ASTEROID)
        asteroid.add(HealthComponent().apply { this.health = 100f })
        if (withDebris) {
            val debris = addBody(BodyDef.BodyType.DynamicBody, 0f, 10f, 0f, 0f, FactionEnum.NEUTRAL, EntityType.DEBRIS)
            debris.add(HealthComponent().apply { this.health = 10f })
        }
        val targets = engine.getEntitiesFor(
            allOf(HealthComponent::class, BodyComponent::class, BaseInfoComponent::class).get(),
        )
        step(PHYSICS_WARMUP_STEPS)
        observer.betaRaw.set(ship.bodyOf().linearVelocity).scl(1f / c)
        return Scenario(ship, asteroid, weapon, MoveComponent(), AutoAimStrategy(targets, observer, clock, settings))
    }

    @Test
    fun ignoresCloserDebrisAndTargetsAsteroid() {
        val s = scenario(withDebris = true)
        runStrategy(s)
        assertTrue(s.weapon.doAttack)
        assertEquals(s.asteroid, s.strategy.target)
    }

    @Test
    fun debrisAloneIsNeverTargeted() {
        val s = scenario(asteroidAhead = false, withDebris = true)
        runStrategy(s)
        assertFalse(s.weapon.doAttack)
        assertNull(s.strategy.target)
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

    @Test
    fun hullFacesTheCursorAndCopiesThrustAndFire() {
        val input = PlayerInputState().apply {
            thrustHeld = 0.7f
            fireHeld = true
            aimWorld.set(10f, 20f)
        }
        val move = MoveComponent()
        val weapon = WeaponComponent().apply { this.aimAngleRad = 1f }
        AutoAimStrategy(null, observer, null, settings).apply(input, Vector2(10f, 10f), 0f, move, weapon, 0.016f)
        assertEquals(Math.PI.toFloat() / 2f, move.targetAngleRad!!, 1e-5f)
        assertEquals(0.7f, move.thrusters)
        assertTrue(weapon.doAttack)
        assertNull(weapon.aimAngleRad)
    }

    @Test
    fun pilotViewConvertsCursorAngleToWorldHullAngle() {
        val input = PlayerInputState().apply {
            pilotView = true
            betaShip.set(0.9f, 0f)
            aimWorld.set(10f, 10f)
        }
        val move = MoveComponent()
        AutoAimStrategy(null, observer, null, settings).apply(input, Vector2(), 0f, move, null, 0.016f)
        // tan θ_W = γ·tan θ_P with γ = 1/sqrt(1 − 0.9²), so a 45° cursor gives 66.45°.
        assertEquals(66.45f, Math.toDegrees(move.targetAngleRad!!.toDouble()).toFloat(), 0.05f)

        input.pilotView = false
        AutoAimStrategy(null, observer, null, settings).apply(input, Vector2(), 0f, move, null, 0.016f)
        assertEquals(45f, Math.toDegrees(move.targetAngleRad!!.toDouble()).toFloat(), 0.05f)
    }

    @Test
    fun pilotHullGateNeedsRelativityAberrationAndPilotView() {
        val s = RelativitySettings().apply { pilotView = true; relativityEnabled = true; pilotAberration = true }
        assertTrue(s.pilotHullTransform)
        s.relativityEnabled = false
        assertFalse(s.pilotHullTransform)
        s.relativityEnabled = true
        s.pilotAberration = false
        assertFalse(s.pilotHullTransform)
        s.pilotAberration = true
        s.pilotView = false
        assertFalse(s.pilotHullTransform)
    }

    @Test
    fun movementWorksWithoutWeaponAndWithoutTargets() {
        AutoAimStrategy(null, observer, null, settings).apply(PlayerInputState(), Vector2(), 0f, MoveComponent(), null, 0.016f)
    }

    @Test
    fun onActivateResetsThrustersAndAim() {
        val move = MoveComponent().apply { this.thrusters = 1f }
        val weapon = WeaponComponent().apply { this.aimAngleRad = 2f }
        AutoAimStrategy(null, observer, null, settings).onActivate(move, weapon, 0f)
        assertEquals(0f, move.thrusters)
        assertNull(weapon.aimAngleRad)
    }

    @Test
    fun flagParsingDropsTheMouseScheme() {
        assertEquals(ControlScheme.TWIN_STICK, ControlScheme.fromFlag("TWIN"))
        assertEquals(ControlScheme.AUTO_AIM, ControlScheme.fromFlag("auto"))
        assertNull(ControlScheme.fromFlag("mouse"))
        assertNull(ControlScheme.fromFlag(null))
        assertEquals(ControlScheme.AUTO_AIM, parseLaunchOptions(arrayOf("--controls=auto")).controls)
        assertNull(parseLaunchOptions(arrayOf("--controls=mouse")).controls)
        assertNull(parseLaunchOptions(arrayOf("--controls=foo")).controls)
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
