package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
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
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.environment.AsteroidSize
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.physics.DAMAGE_PER_REST_ENERGY
import com.github.pepek42.asteroids.physics.PLAYER_RAM_DAMAGE_FACTOR
import com.github.pepek42.asteroids.environment.ASTEROID_LARGE_HEALTH
import com.github.pepek42.asteroids.environment.ASTEROID_SPAWN_BETA_MAX
import com.github.pepek42.asteroids.environment.asteroidHealth
import com.github.pepek42.asteroids.environment.asteroidRestMass
import com.github.pepek42.asteroids.provider.playerRestMass
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.RandomXS128
import com.github.pepek42.asteroids.component.AsteroidComponent
import com.github.pepek42.asteroids.component.RemoveComponent
import com.github.pepek42.asteroids.provider.AsteroidProvider
import org.mockito.kotlin.any
import com.github.pepek42.asteroids.physics.FRAGMENT_RAM_IMMUNITY
import com.github.pepek42.asteroids.physics.FRAGMENT_RAM_IMMUNITY_STEPS
import com.github.pepek42.asteroids.physics.RAM_GAMMA_THRESHOLD
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.gammaRel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class RamDamageTest {
    private val c = RelativitySettings().c

    private fun v(beta: Float) = Vector2(beta * c, 0f)

    @Test
    fun slowImpactShouldDoNoDamage() {
        // 0.1c against a body at rest: gamma_rel - 1 is about 0.005, under the threshold.
        assertEquals(0f, ramDamage(v(0.1f), Vector2(), 100f, 100f, c))
        assertEquals(0f, ramDamage(Vector2(), Vector2(), 100f, 100f, c))
    }

    @Test
    fun damageShouldNotDependOnC() {
        val d1 = ramDamage(Vector2(0.5f * 20f, 0f), Vector2(), 50f, 80f, 20f)
        val d2 = ramDamage(Vector2(0.5f * 60f, 0f), Vector2(), 50f, 80f, 60f)
        assertEquals(d1, d2, d1 * 1e-4f)
    }

    @Test
    fun headOnShouldHitHarderThanOvertakingWithMoreClassicalEnergy() {
        // Head-on +-0.5c: gamma_rel = 1.667. Overtaking 0.9c vs 0.4c: relative speed 0.78c, gamma_rel about 1.59.
        val headOn = ramDamage(v(0.5f), v(-0.5f), 100f, 100f, c)
        val overtaking = ramDamage(v(0.9f), v(0.4f), 100f, 100f, c)
        assertTrue(headOn > overtaking, "headOn=$headOn overtaking=$overtaking")
        assertTrue(overtaking > 0f)
    }

    @Test
    fun damageShouldGrowWithMasses() {
        val base = ramDamage(v(0.6f), Vector2(), 50f, 50f, c)
        assertTrue(ramDamage(v(0.6f), Vector2(), 100f, 50f, c) > base)
        assertTrue(ramDamage(v(0.6f), Vector2(), 50f, 100f, c) > base)
        assertTrue(ramDamage(v(0.6f), Vector2(), 100f, 100f, c) > ramDamage(v(0.6f), Vector2(), 100f, 50f, c))
    }

    @Test
    fun staticOrMasslessPartnerShouldLeaveTheOtherMass() {
        val withStatic = ramDamage(v(0.6f), Vector2(), 50f, 0f, c)
        val excess = gammaRel(v(0.6f), Vector2(), c) - 1f
        assertEquals(excess * 50f * DAMAGE_PER_REST_ENERGY, withStatic, 1e-3f)
        assertEquals(withStatic, ramDamage(v(0.6f), Vector2(), 0f, 50f, c), 1e-4f)
        assertEquals(0f, ramDamage(v(0.6f), Vector2(), 0f, 0f, c))
    }

    @Test
    fun thresholdShouldCutDamageOffJustBelow() {
        // Find a speed with gamma_rel - 1 just under and just over the threshold.
        val below = ramDamage(v(0.19f), Vector2(), 100f, 100f, c)
        val above = ramDamage(v(0.21f), Vector2(), 100f, 100f, c)
        assertTrue(gammaRel(v(0.19f), Vector2(), c) - 1f < RAM_GAMMA_THRESHOLD)
        assertEquals(0f, below)
        assertTrue(above > 0f)
    }

    @Test
    fun calibrationShouldKillALargeAsteroidWithOneNearCRam() {
        // Ship (1.33 u box, density 20) and large asteroid (density 10), see DAMAGE_PER_REST_ENERGY.
        val shipMass = playerRestMass()
        val asteroidMass = 10f * Math.PI.toFloat() * AsteroidSize.LARGE.radius * AsteroidSize.LARGE.radius
        // gamma_rel = 3: beta = sqrt(1 - 1/9)
        val beta = Math.sqrt(1.0 - 1.0 / 9.0).toFloat()
        assertTrue(ramDamage(v(beta), Vector2(), shipMass, asteroidMass, c) >= 150f)
    }

    @Test
    fun gammaThreeRamShouldHurtPlayerButNotKillIt() {
        val shipMass = playerRestMass()
        val beta = Math.sqrt(1.0 - 1.0 / 9.0).toFloat()
        val raw = ramDamage(v(beta), Vector2(), shipMass, asteroidRestMass(AsteroidSize.LARGE), c)
        val playerDamage = raw * PLAYER_RAM_DAMAGE_FACTOR
        assertTrue(playerDamage > 0f)
        assertTrue(playerDamage < 100f, "player max health is 100, got $playerDamage")
        assertTrue(playerDamage > 50f, "should be a large share, got $playerDamage")
    }

    @Test
    fun spawnSpeedBumpShouldBeNegligibleForPlayer() {
        val shipMass = playerRestMass()
        val beta = ASTEROID_SPAWN_BETA_MAX * 2f
        val raw = ramDamage(v(beta), Vector2(), shipMass, asteroidRestMass(AsteroidSize.LARGE), c)
        assertTrue(raw * PLAYER_RAM_DAMAGE_FACTOR < 10f)
    }

    @Test
    fun smallAsteroidShouldHaveLessHealthThanLarge() {
        assertEquals(ASTEROID_LARGE_HEALTH, asteroidHealth(AsteroidSize.LARGE), 1e-3f)
        assertTrue(asteroidHealth(AsteroidSize.SMALL) < asteroidHealth(AsteroidSize.LARGE))
        assertEquals(
            asteroidRestMass(AsteroidSize.SMALL) / asteroidRestMass(AsteroidSize.LARGE),
            asteroidHealth(AsteroidSize.SMALL) / asteroidHealth(AsteroidSize.LARGE), 1e-4f,
        )
    }

    // --- ContactSystem integration, real Box2D world ---

    private val world = World(Vector2.Zero, true).apply { setAutoClearForces(false) }
    private val engine = Engine()
    private val contactSystem = ContactSystem(world, RelativitySettings())

    @AfterEach
    fun tearDown() = world.dispose()

    private fun addBody(
        x: Float,
        vx: Float,
        health: Float?,
        type: EntityType = EntityType.ASTEROID,
        player: Boolean = false,
        sensor: Boolean = false,
        bodyType: BodyDef.BodyType = BodyDef.BodyType.DynamicBody,
    ): Entity {
        val body = world.createBody(BodyDef().apply {
            this.type = bodyType
            position.set(x, 0f)
            linearVelocity.set(vx, 0f)
        })
        val shape = CircleShape().apply { radius = 1f }
        val fixture = body.createFixture(shape, 1f)
        fixture.isSensor = sensor
        fixture.filterData = fixture.filterData.apply {
            categoryBits = FactionEnum.NEUTRAL.ownCategoryBit
            maskBits = FactionEnum.NEUTRAL.collidesWithMask
        }
        shape.dispose()
        val entity = engine.createEntity()
        entity.add(BodyComponent().apply { this.body = body })
        entity.add(BaseInfoComponent().apply { this.faction = FactionEnum.NEUTRAL; this.entityType = type })
        if (health != null) entity.add(HealthComponent().apply { this.health = health })
        if (player) entity.add(PlayerComponent())
        entity.add(RelativisticComponent().apply {
            captureRestMass(body)
            vSet.set(vx, 0f)
        })
        body.userData = entity
        engine.addEntity(entity)
        return entity
    }

    private fun overlapStep() {
        val dt = PhysicsSystem.PHYSICS_UPDATE_INTERVAL
        // Box2D finds new pairs at the end of step 1 and fires beginContact at the start of step 2.
        world.step(dt, 6, 2)
        world.step(dt, 6, 2)
    }

    private fun health(e: Entity) = e.getComponent(HealthComponent::class.java).health

    @Test
    fun nearCCollisionShouldDamageBothBodiesOnce() {
        val a = addBody(0f, 0.9f * c, 150f)
        val b = addBody(1.5f, -0.9f * c / 4f, 150f)
        val expected = ramDamage(Vector2(0.9f * c, 0f), Vector2(-0.9f * c / 4f, 0f), Math.PI.toFloat(), Math.PI.toFloat(), c)
        overlapStep()
        assertTrue(expected > 0f)
        assertEquals(150f - expected, health(a), 1e-2f)
        assertEquals(150f - expected, health(b), 1e-2f)
    }

    @Test
    fun slowBumpShouldDoNothing() {
        val a = addBody(0f, 2f, 150f)
        val b = addBody(1.5f, 0f, 150f)
        overlapStep()
        assertEquals(150f, health(a))
        assertEquals(150f, health(b))
    }

    @Test
    fun playerShouldTakeScaledDamageAndPartnerFullDamage() {
        val player = addBody(0f, 0.9f * c, 100f, EntityType.SHIP, player = true)
        val rock = addBody(1.5f, 0f, 150f)
        overlapStep()
        val unitDiscMass = Math.PI.toFloat() // addBody: radius 1, density 1
        val expected = ramDamage(Vector2(0.9f * c, 0f), Vector2(), unitDiscMass, unitDiscMass, c) * PLAYER_RAM_DAMAGE_FACTOR
        assertTrue(expected > 0f)
        assertEquals(100f - expected, health(player), 1e-2f)
        assertTrue(health(rock) < 150f)
    }

    @Test
    fun sensorsAndHealthlessBodiesShouldBeIgnored() {
        val a = addBody(0f, 0.9f * c, 150f)
        // A kinematic sensor never produces a solid ram.
        val s = addBody(1.5f, 0f, 150f, sensor = true)
        overlapStep()
        assertEquals(150f, health(a))
        assertEquals(150f, health(s))
    }

    @Test
    fun staticWallWithoutHealthShouldStillDamageTheRammer() {
        val a = addBody(0f, 0.9f * c, 150f)
        addBody(1.5f, 0f, null, bodyType = BodyDef.BodyType.StaticBody)
        overlapStep()
        assertTrue(health(a) < 150f)
    }

    @Test
    fun fragmentsShouldNotBeRammedByTheShipThatBrokeTheParent() {
        val dt = PhysicsSystem.PHYSICS_UPDATE_INTERVAL
        val atlas = mock<TextureAtlas> { on { createSprite(any<String>()) } doReturn Sprite() }
        val provider = AsteroidProvider(atlas, engine, world, RandomXS128(1))
        engine.addSystem(DeathSystem(engine, provider, RandomXS128(1)))
        engine.addSystem(contactSystem)
        world.setContactListener(contactSystem)
        val parent = addBody(0f, 0f, 1f) // any hit breaks it
        parent.add(AsteroidComponent().apply { this.asteroidSize = AsteroidSize.LARGE })
        // Ship comes in from the side (+y) so one fragment spawns right in its path.
        val beta = Math.sqrt(1.0 - 1.0 / 9.0).toFloat()
        val ship = engine.entities.first().let {
            val body = world.createBody(BodyDef().apply {
                type = BodyDef.BodyType.DynamicBody
                position.set(1f, 3.6f)
                linearVelocity.set(0f, -beta * c)
            })
            val shape = CircleShape().apply { radius = 1f }
            val fixture = body.createFixture(shape, 1f)
            fixture.filterData = fixture.filterData.apply {
                categoryBits = FactionEnum.NEUTRAL.ownCategoryBit
                maskBits = FactionEnum.NEUTRAL.collidesWithMask
            }
            shape.dispose()
            val e = engine.createEntity()
            e.add(BodyComponent().apply { this.body = body })
            e.add(BaseInfoComponent().apply { this.faction = FactionEnum.NEUTRAL; this.entityType = EntityType.SHIP })
            e.add(HealthComponent().apply { this.health = 1000f })
            e.add(PlayerComponent())
            e.add(RelativisticComponent().apply { captureRestMass(body); vSet.set(0f, -beta * c) })
            body.userData = e
            engine.addEntity(e)
            e
        }
        var healthAfterBreak = Float.NaN
        repeat(12) {
            world.step(dt, 6, 2)
            contactSystem.afterStep(dt)
            engine.update(dt)
            if (healthAfterBreak.isNaN() && parent.getComponent(RemoveComponent::class.java) != null) {
                healthAfterBreak = health(ship)
                parent.remove(HealthComponent::class.java) // no RemoveSystem here: stop DeathSystem splitting again
            }
        }
        assertTrue(!healthAfterBreak.isNaN(), "parent should have broken")
        val fragments = engine.entities.filter { it !== parent && it !== ship && it.getComponent(AsteroidComponent::class.java) != null }
        assertEquals(2, fragments.size)
        assertEquals(healthAfterBreak, health(ship), 1e-3f, "fragments re-rammed the ship")
        fragments.forEach { assertEquals(asteroidHealth(AsteroidSize.SMALL), health(it), 1e-3f) }
    }

    @Test
    fun fragmentRamImmunityShouldExpireAfterAFewSteps() {
        engine.addSystem(contactSystem)
        val rock = addBody(0f, 0f, 30f)
        val cmp = AsteroidComponent().apply { this.ramImmunity = FRAGMENT_RAM_IMMUNITY }
        rock.add(cmp)
        val dt = PhysicsSystem.PHYSICS_UPDATE_INTERVAL
        repeat(FRAGMENT_RAM_IMMUNITY_STEPS - 1) { contactSystem.afterStep(dt) }
        assertTrue(cmp.ramImmunity > 0f)
        contactSystem.afterStep(dt)
        contactSystem.afterStep(dt)
        assertEquals(0f, cmp.ramImmunity)
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
